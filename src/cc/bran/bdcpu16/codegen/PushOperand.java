package cc.bran.bdcpu16.codegen;

/**
 * Represents a Push operand.
 * @author Brandon Pitman
 */
public class PushOperand extends Operand
{

	@Override
	public String getterExpression(int nwOffset, int deltaSP)
	{
		return "(cpu.memory(cpu.SP()))";
	}

	@Override
	public String setterStatement(String valueExpression, int nwOffset, int deltaSP)
	{
		return String.format("cpu.memory(cpu.SP(), (char)(%s));", valueExpression);
	}

	@Override
	public int wordsUsed()
	{
		return 0;
	}

	@Override
	public int deltaSP()
	{
		return -1;
	}

}
