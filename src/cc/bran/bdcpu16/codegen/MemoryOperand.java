package cc.bran.bdcpu16.codegen;

/**
 * Represents an operand that refers to a location in memory. 
 * @author Brandon Pitman
 */
class MemoryOperand extends Operand
{
	private final Operand[] addressOperands;
	
	/**
	 * Creates a new memory operand.
	 * @param addressOperands a list of operands; the sum of the value of these operands will be used to generate the address that this memory refers to 
	 */
	public MemoryOperand(Operand... addressOperands)
	{
		this.addressOperands = addressOperands;
	}
	
	@Override
	public String getterExpression(int nwOffset, int deltaSP)
	{
		/* this would give the wrong nwOffset to the second-and-on NextWordOperands, but fortunately we only ever have one */
		StringBuilder sb = new StringBuilder();
		
		sb.append("(cpu.memory((char)(");
		
		sb.append(addressOperands[0].getterExpression(nwOffset, deltaSP));
		for(int i = 1; i < addressOperands.length; ++i)
		{
			sb.append("+");
			sb.append(addressOperands[i].getterExpression(nwOffset, deltaSP));
		}
		
		sb.append(")))");
		
		return sb.toString();
	}

	@Override
	public String setterStatement(String valueExpression, int nwOffset, int deltaSP)
	{
		StringBuilder sb = new StringBuilder();
		
		sb.append("cpu.memory((char)(");
		
		sb.append(addressOperands[0].getterExpression(nwOffset, deltaSP));
		for(int i = 1; i < addressOperands.length; ++i)
		{
			sb.append("+");
			sb.append(addressOperands[i].getterExpression(nwOffset, deltaSP));
		}
		
		sb.append("),(char)(");
		sb.append(valueExpression);
		sb.append("));");
		
		return sb.toString();
	}

	@Override
	public int wordsUsed()
	{
		int wordsUsed = 0;
		
		for(int i = 0; i < addressOperands.length; ++i)
		{
			wordsUsed += addressOperands[i].wordsUsed();
		}
		
		return wordsUsed;
	}

	@Override
	public int deltaSP()
	{
		return 0;
	}
}
