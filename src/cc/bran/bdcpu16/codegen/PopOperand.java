package cc.bran.bdcpu16.codegen;

/**
 * Represents a Pop operand.
 * @author Brandon Pitman
 */
public class PopOperand extends Operand
{

	@Override
	public String getterExpression(int nwOffset, int deltaSP)
	{
		return String.format("(cpu.memory((char)(cpu.SP()+(%d))))", -deltaSP);
	}

	@Override
	public String setterStatement(String valueExpression, int nwOffset, int deltaSP)
	{
		return String.format("cpu.memory((char)(cpu.SP()+(%d)), (char)(%s));", -deltaSP, valueExpression);
	}

	@Override
	public int wordsUsed()
	{
		return 0;
	}

	@Override
	public int deltaSP()
	{
		return 1;
	}

	@Override
	public String toString(boolean hexLiterals, String nextWord)
	{
		return "POP";
	}
}
