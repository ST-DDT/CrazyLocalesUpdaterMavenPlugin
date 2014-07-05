package de.st_ddt.crazy.maven.localesupdater;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

@Mojo(name = "update", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE, requiresDependencyCollection = ResolutionScope.COMPILE)
public class LocalesUpdaterMojo extends AbstractMojo
{

	private static final Pattern PATTERN_VAR = Pattern.compile("\\{([a-zA-Z0-9]+)\\}");
	private static final String MESSAGESFILE = "messages.lang";
	private static final String MESSAGELINKSFILE = "messagelinks.lang";
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static final String newLine = System.lineSeparator();
	/**
	 * The current Maven project.
	 */
	@Component
	private MavenProject project;
	/**
	 * The java source directory
	 */
	@Parameter(defaultValue = "${project.build.sourceDirectory}", property = "sourceDirectory")
	private File sourceFolder;
	/**
	 * Source/Target location for the language files.
	 */
	@Parameter(defaultValue = "src/main/resources/resource/lang", property = "languagefolder", required = true)
	private File languageFolder;
	/**
	 * The artifacts that should be included in the lang files.
	 */
	@Parameter(property = "includeArtifacts")
	private List<String> includeArtifacts;
	private final Map<String, String> entries = new TreeMap<>(new EntryComparator());
	private final Map<String, String> internalLinks = new TreeMap<>(new EntryComparator());
	private final Map<String, String> externalLinks = new TreeMap<>(new EntryComparator());
	private final List<VarLink> applicableVars = new ArrayList<VarLink>();

	@Override
	public void execute() throws MojoExecutionException
	{
		final File f = languageFolder;
		getLog().info("Proccessing language files in: " + f);
		if (!f.exists())
		{
			f.mkdirs();
			getLog().info("Language folder created!");
		}
		getLog().info("Searching message links...");
		for (final Artifact artifact : project.getArtifacts())
		{
			getLog().debug("Checking " + artifact.getArtifactId());
			final File file = artifact.getFile();
			try (ZipFile jarFile = new ZipFile(file))
			{
				final boolean include = includeArtifacts.contains(artifact.getArtifactId());
				final ZipEntry messagesEntry = jarFile.getEntry("resource/lang/" + MESSAGESFILE);
				if (messagesEntry != null && include)
					try (InputStream in = jarFile.getInputStream(messagesEntry))
					{
						searchEntries(in, entries);
					}
				final ZipEntry linksEntry = jarFile.getEntry("resource/lang/" + MESSAGELINKSFILE);
				if (linksEntry != null)
					try (InputStream in = jarFile.getInputStream(linksEntry))
					{
						if (include)
							searchEntries(in, internalLinks);
						else
							searchEntries(in, externalLinks);
					}
			}
			catch (final Exception e)
			{
				throw new MojoExecutionException("Could not read dependency jar", e);
			}
		}
		getLog().info("Searching message links completed (" + externalLinks.size() + " found)");
		getLog().info("Updating messages file...");
		// Search internal files
		final Set<File> files = new HashSet<File>();
		searchFiles(sourceFolder, files);
		for (final File file : files)
			try
			{
				searchMessages(file, entries, applicableVars);
			}
			catch (final Exception e)
			{
				throw new MojoExecutionException("Could not read " + file.getPath(), e);
			}
		// Remove internal links
		final Iterator<Entry<String, String>> it = entries.entrySet().iterator();
		while (it.hasNext())
		{
			final Entry<String, String> entry = it.next();
			final Matcher matcher = PATTERN_VAR.matcher(entry.getKey());
			if (matcher.find())
			{
				internalLinks.put(entry.getKey(), entry.getValue());
				it.remove();
			}
		}
		externalLinks.putAll(internalLinks);
		// Add linktargets
		if (!externalLinks.isEmpty())
		{
			getLog().info("- Checking message links...");
			for (final VarLink link : applicableVars)
			{
				if (getLog().isDebugEnabled())
					getLog().info("  - " + link.getVariable() + " => " + link.getValue());
				int count = 0;
				final Pattern pattern = Pattern.compile("\\{" + link.getVariable() + "\\}");
				for (final Entry<String, String> entry : externalLinks.entrySet())
				{
					final Matcher matcher = pattern.matcher(entry.getKey());
					if (matcher.find())
					{
						getLog().debug("    - Match: " + entry.getKey());
						count++;
						final String key = matcher.replaceAll(link.getValue());
						final String oldValue = entries.get(key);
						if (oldValue == null || oldValue.isEmpty())
							entries.put(key, entry.getValue());
					}
					else
						getLog().debug("    - No Match: " + entry.getKey());
				}
				if (count > 0)
					if (getLog().isDebugEnabled())
						getLog().info("    - Found " + count + " matches!");
					else
						getLog().info("  - " + link.getVariable() + " => " + link.getValue() + " (" + count + " matches)");
			}
			getLog().info("- Checking message links completed");
		}
		// Save messages.lang
		getLog().info("- Writing messages file...");
		writeMessages(new File(languageFolder, MESSAGESFILE), entries);
		getLog().info("- Writing messages file completed");
		getLog().info("- Writing message links file...");
		writeMessages(new File(languageFolder, MESSAGELINKSFILE), internalLinks);
		getLog().info("- Writing message links file completed");
		getLog().info("Updating messages file completed");
		// Search for languages
		final File[] languages = languageFolder.listFiles(new LangFileFilter());
		if (languages == null)
			return;
		final Map<String, String> blindEntries = new LinkedHashMap<>();
		for (final String key : entries.keySet())
			blindEntries.put(key, "");
		getLog().info("Updating translation files:");
		for (final File language : languages)
		{
			getLog().info("- " + language.getName());
			final Map<String, String> translations = new LinkedHashMap<>(blindEntries);
			searchEntries(language, translations);
			writeTranslations(language, blindEntries, translations);
		}
		getLog().info("Updating translation files completed");
	}

	private void searchFiles(final File folder, final Set<File> files)
	{
		for (final File file : folder.listFiles())
			if (file.isDirectory())
				searchFiles(file, files);
			else
				files.add(file);
	}

	private void searchMessages(final File source, final Map<String, String> messages, final List<VarLink> links) throws IOException
	{
		if (!source.exists())
			return;
		try (InputStream in = new FileInputStream(source);
				BufferedReader reader = new BufferedReader(new InputStreamReader(in, UTF8)))
		{
			String zeile;
			while ((zeile = reader.readLine()) != null)
				if (zeile.matches(".*@Localized\\(.*\\).*"))
				{
					final String part = zeile.split("@Localized\\(", 2)[1].split("\\)", 2)[0];
					final String[] split = part.split("\"");
					for (int i = 1; i < split.length; i += 2)
					{
						final String[] split2 = split[i].trim().split(" ", 2);
						if (split2.length == 2)
							messages.put(split2[0].trim().toUpperCase(), split2[1]);
						else if (!messages.containsKey(split2[0].toUpperCase()))
							messages.put(split2[0].trim().toUpperCase(), "");
					}
				}
				else if (zeile.matches(".*@LocalizedVariable\\(.*\\).*"))
				{
					final String part = zeile.split("@LocalizedVariable\\(", 2)[1].split("\\)", 2)[0];
					final String[] split = part.split("=");
					final String[] splitvars = split[1].split("\"");
					final String[] splitvalues = split[2].split("\"");
					for (int i = 1; i < splitvars.length; i += 2)
						links.add(new VarLink(splitvars[i], splitvalues[i]));
				}
		}
	}

	private void searchEntries(final File source, final Map<String, String> messages) throws MojoExecutionException
	{
		if (!source.exists())
			return;
		try (final InputStream in = new FileInputStream(source))
		{
			searchEntries(in, messages);
		}
		catch (final Exception e)
		{
			throw new MojoExecutionException("Error while reading " + source.getName(), e);
		}
	}

	private void searchEntries(final InputStream source, final Map<String, String> messages) throws IOException
	{
		try (final BufferedReader reader = new BufferedReader(new InputStreamReader(source, UTF8)))
		{
			// Ignore first line
			String zeile = reader.readLine();
			while ((zeile = reader.readLine()) != null)
			{
				if (zeile.startsWith("#"))
					continue;
				final String[] split = zeile.split("=", 2);
				if (split.length == 2)
					messages.put(split[0].trim().toUpperCase(), split[1]);
				else if (!messages.containsKey(split[0].trim().toUpperCase()))
					messages.put(split[0].trim().toUpperCase(), "");
			}
		}
	}

	private void writeMessages(final File target, final Map<String, String> messages) throws MojoExecutionException
	{
		if (messages.isEmpty())
			return;
		messages.remove(project.getName().toUpperCase());
		target.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(target);
				final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, UTF8)))
		{
			writer.write(project.getName().toUpperCase() + "=" + project.getName() + newLine);
			for (final Entry<String, String> locale : messages.entrySet())
				writer.write(locale.getKey() + "=" + locale.getValue() + newLine);
		}
		catch (final Exception e)
		{
			throw new MojoExecutionException("Error while writing " + target.getName(), e);
		}
		getLog().info("  - Wrote " + messages.size() + " Entries!");
	}

	private void writeTranslations(final File target, final Map<String, String> defaults, final Map<String, String> messages) throws MojoExecutionException
	{
		messages.remove(project.getName().toUpperCase());
		if (messages.isEmpty())
			return;
		try (OutputStream out = new FileOutputStream(target);
				final Writer writer = new BufferedWriter(new OutputStreamWriter(out, UTF8)))
		{
			writer.write(project.getName().toUpperCase() + "=" + project.getName() + newLine);
			for (final Entry<String, String> entry : messages.entrySet())
				if (entry.getValue().equals(""))
				{
					if (defaults.containsKey(entry.getKey()))
						writer.write("#" + entry.getKey() + "=" + newLine);
				}
				else
					writer.write(entry.getKey() + "=" + entry.getValue() + newLine);
		}
		catch (final Exception e)
		{
			throw new MojoExecutionException("Error while writing " + target.getName(), e);
		}
	}
}
