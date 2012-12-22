package cc.bran.bdcpu16.codegen;

/**
 * Represents an operand that takes on a specific literal value.
 * @author Brandon Pitman
 */
class LiteralOperand extends Operand
{
	private final int value;
	
	/**
	 * Creates a new literal operand.
	 * @param value the value for this literal operand
	 */
	public LiteralOperand(int value)
	{
		this.value = value;
	}
	
	@Override
	public String getterExpression(int nwOffset, int deltaSP)
	{
		return String.format("((char)(%d))", value);
	}

	@Override
	public String setterStatement(String valueExpression, int nwOffset, int deltaSP)
	{
		/* can't set a literal */
		return "";
	}

	@Override
	public int wordsUsed()
	{
		return 0;
	}

	@Override
	public int deltaSP()
	{
		return 0;
	}

	@Override
	public String toString(boolean hexLiterals, String nextWord)
	{
		final String formatString = (hexLiterals ? "0x%04X" : "%d");
		return String.format(formatString, (int)(char)value);
	}
}
