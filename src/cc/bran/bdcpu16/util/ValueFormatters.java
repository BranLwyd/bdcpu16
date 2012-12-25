package cc.bran.bdcpu16.util;

/**
 * This is a static class that is used to access various predefined value formatters.
 * @author Brandon Pitman
 */
public class ValueFormatters
{
	private static final ValueFormatter hexFormatter;
	private static final ValueFormatter decFormatter;
	
	static
	{
		decFormatter = new ValueFormatter()
		{
			@Override
			public String formatValue(char value)
			{
				return Integer.toString((int)value);
			}
		};
		
		hexFormatter = new ValueFormatter()
		{
			@Override
			public String formatValue(char value)
			{
				return String.format("0x%04X", (int)value);
			}
		};
	}
	
	private ValueFormatters() { } /* unused -- static class */
	
	/**
	 * This method returns the default value formatter.
	 * @return the default value formatter
	 */
	public static ValueFormatter getDefaultValueFormatter()
	{
		return hexFormatter;
	}
	
	/**
	 * This method returns the hexadecimal value formatter.
	 * @return the hexadecimal value formatter
	 */
	public static ValueFormatter getHexValueFormatter()
	{
		return hexFormatter;
	}
	
	/**
	 * This method returns the decimal value formatter.
	 * @return the decimal value formatter
	 */
	public static ValueFormatter getDecValueFormatter()
	{
		return decFormatter;
	}
}
