package de.st_ddt.crazy.maven.localesupdater;

import java.io.File;
import java.io.FileFilter;
import java.util.regex.Pattern;

public class LangFileFilter implements FileFilter
{

	public final static Pattern FILEPATTERN = Pattern.compile("[a-z]{2}_[A-Z]{2}\\.lang");

	@Override
	public boolean accept(final File file)
	{
		return FILEPATTERN.matcher(file.getName()).matches();
	}
}
