package cc.bran.bdcpu16.codegen;

/**
 * Represents an operand that stands for the "next word" in memory.
 * @author Brandon Pitman
 */
class NextWordOperand extends Operand
{
	@Override
	public String getterExpression(int nwOffset, int deltaSP)
	{
		return String.format("(cpu.memory((char)(cpu.PC()+(%d))))", nwOffset);
	}

	@Override
	public String setterStatement(String valueExpression, int nwOffset, int deltaSP)
	{
		/* next word is defined by the spec to be a literal value, even though it's pulled from memory, and literal values can't be written to */
		return "";
	}

	@Override
	public int wordsUsed()
	{
		return 1;
	}

	@Override
	public int deltaSP()
	{
		return 0;
	}

}
