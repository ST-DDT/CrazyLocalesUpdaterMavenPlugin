package de.st_ddt.crazy.maven.localesupdater;

public final class VarLink
{

	private final String variable;
	private final String value;

	public VarLink(final String variable, final String value)
	{
		super();
		this.variable = variable;
		this.value = value;
	}

	public final String getVariable()
	{
		return variable;
	}

	public final String getValue()
	{
		return value;
	}
}
