package de.st_ddt.crazy.maven.localesupdater;

import java.util.Comparator;

public class EntryComparator implements Comparator<String>
{

	@Override
	public int compare(final String o1, final String o2)
	{
		int res = valuizeStart(o1).compareTo(valuizeStart(o2));
		if (res == 0)
			res = o1.replaceAll("\\{", "").replaceAll("\\}", "").compareTo(o2.replaceAll("\\{", "").replaceAll("\\}", ""));
		if (res == 0)
			res = Integer.valueOf(o1.length()).compareTo(o2.length());
		return res;
	}

	private static Integer valuizeStart(final String entry)
	{
		if (entry.startsWith("LANGUAGE.NAME"))
			return 0;
		else if (entry.startsWith("CRAZYPLUGIN"))
			return 1;
		else if (entry.startsWith("{CRAZYPLUGIN}"))
			return 1;
		else if (entry.startsWith("{CRAZYPLAYERDATAPLUGIN}"))
			return 2;
		else if (entry.startsWith("CRAZYEXCEPTION"))
			return 3;
		else if (entry.startsWith("UNIT"))
			return 5;
		else
			return 4;
	}
}
