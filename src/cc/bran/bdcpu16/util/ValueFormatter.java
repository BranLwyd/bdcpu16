package cc.bran.bdcpu16.util;

/**
 * This interface represents the ability to format DCPU-16 values (aka words) into
 * a string format for display.
 * @author Brandon Pitman
 */
public interface ValueFormatter
{
	/**
	 * Formats a value from the DCPU-16 as a string for display.
	 * @param value the value to format
	 * @return a string representation of the value
	 */
	public String formatValue(char value);
}
