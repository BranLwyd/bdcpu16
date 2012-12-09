package cc.bran.bdcpu16.codegen;

/**
 * Represents an operand that refers to a register.
 * @author Brandon Pitman
 */
class RegisterOperand extends Operand
{
	private final Register reg;
	
	/**
	 * Creates a new register operand.
	 * @param reg the register that this operand refers to
	 */
	public RegisterOperand(Register reg)
	{
		this.reg = reg;
	}
	
	@Override
	public String getterExpression(int nwOffset, int deltaSP)
	{
		return String.format("(cpu.%s())", reg.toString());
	}

	@Override
	public String setterStatement(String valueExpression, int nwOffset, int deltaSP)
	{
		return String.format("cpu.%s((char)(%s));", reg.toString(), valueExpression);
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
	
	/**
	 * Represents the registers that can be used in a RegisterOperand.
	 * @author Brandon Pitman
	 */
	public enum Register
	{
		A, B, C, X, Y, Z, I, J, SP, PC, EX,
	}
}
