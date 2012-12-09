package cc.bran.bdcpu16.codegen;

import cc.bran.bdcpu16.codegen.RegisterOperand.Register;

/**
 * Represents an operand, that is, one of the a or b values in an instruction. Has functionality to generate
 * code for a get expression or a set statement.
 * @author Brandon Pitman
 *
 */
abstract class Operand
{
	private static final Operand operands[] =
		{
			/* 0x00 to 0x07 -- general registers */
			new RegisterOperand(Register.A),
			new RegisterOperand(Register.B),
			new RegisterOperand(Register.C),
			new RegisterOperand(Register.X),
			new RegisterOperand(Register.Y),
			new RegisterOperand(Register.Z),
			new RegisterOperand(Register.I),
			new RegisterOperand(Register.J),
			
			/* 0x08 to 0x0f -- memory addressed by general registers */
			new MemoryOperand(new RegisterOperand(Register.A)),
			new MemoryOperand(new RegisterOperand(Register.B)),
			new MemoryOperand(new RegisterOperand(Register.C)),
			new MemoryOperand(new RegisterOperand(Register.X)),
			new MemoryOperand(new RegisterOperand(Register.Y)),
			new MemoryOperand(new RegisterOperand(Register.Z)),
			new MemoryOperand(new RegisterOperand(Register.I)),
			new MemoryOperand(new RegisterOperand(Register.J)),
			
			/* 0x10 to 0x17 -- memory addressed by register plus next word */
			new MemoryOperand(new RegisterOperand(Register.A), new NextWordOperand()),
			new MemoryOperand(new RegisterOperand(Register.B), new NextWordOperand()),
			new MemoryOperand(new RegisterOperand(Register.C), new NextWordOperand()),
			new MemoryOperand(new RegisterOperand(Register.X), new NextWordOperand()),
			new MemoryOperand(new RegisterOperand(Register.Y), new NextWordOperand()),
			new MemoryOperand(new RegisterOperand(Register.Z), new NextWordOperand()),
			new MemoryOperand(new RegisterOperand(Register.I), new NextWordOperand()),
			new MemoryOperand(new RegisterOperand(Register.J), new NextWordOperand()),
			
			/* 0x18 to 0x1a -- stack operations */
			null, /* PUSH/POP handled specially */
			new MemoryOperand(new RegisterOperand(Register.SP)),
			new MemoryOperand(new RegisterOperand(Register.SP), new NextWordOperand()),
			
			/* 0x1b to 0x1d -- special registers */
			new RegisterOperand(Register.SP),
			new RegisterOperand(Register.PC),
			new RegisterOperand(Register.EX),
			
			/* 0x1e to 0x1f -- next word operands */
			new MemoryOperand(new NextWordOperand()),
			new NextWordOperand(),
			
			/* 0x20 to 0x3f -- immediate literals */
			new LiteralOperand(-1),
			new LiteralOperand(0),
			new LiteralOperand(1),
			new LiteralOperand(2),
			new LiteralOperand(3),
			new LiteralOperand(4),
			new LiteralOperand(5),
			new LiteralOperand(6),
			new LiteralOperand(7),
			new LiteralOperand(8),
			new LiteralOperand(9),
			new LiteralOperand(10),
			new LiteralOperand(11),
			new LiteralOperand(12),
			new LiteralOperand(13),
			new LiteralOperand(14),
			new LiteralOperand(15),
			new LiteralOperand(16),
			new LiteralOperand(17),
			new LiteralOperand(18),
			new LiteralOperand(19),
			new LiteralOperand(20),
			new LiteralOperand(21),
			new LiteralOperand(22),
			new LiteralOperand(23),
			new LiteralOperand(24),
			new LiteralOperand(25),
			new LiteralOperand(26),
			new LiteralOperand(27),
			new LiteralOperand(28),
			new LiteralOperand(29),
			new LiteralOperand(30),
		};
	
	private static final Operand pushOp = new PushOperand();
	private static final Operand popOp = new PopOperand();
	
	/**
	 * Gets the operand for a given operand value.
	 * @param operandValue the operand value
	 * @param isB is this the B operand?
	 * @return the operand for the given operand value
	 */
	public static Operand getOperand(int operandValue, boolean isB)
	{
		if(operandValue == 0x18)
		{
			return (isB ? pushOp : popOp);
		}
		
		return operands[operandValue];
	}
	
	/**
	 * Returns a Java expression to get the value of this operand.
	 * @param nwOffset the offset to use for "next word" operands
	 * @return a string containing a Java expression for the value of this operand
	 */
	public abstract String getterExpression(int nwOffset, int deltaSP);
	
	/**
	 * Returns a Java statement to set the value of this operand.
	 * @param valueOperand Java expression for the value to set the operand to
	 * @param nwOffset the offset to use for "next word" operands
	 * @return a string containing a Java expression that sets the value of this operand to valueExpression
	 */
	public abstract String setterStatement(String valueExpression, int nwOffset, int deltaSP);
	
	/**
	 * Gets the number of extra words used by this operand.
	 * @return the number of extra words used by this operand
	 */
	public abstract int wordsUsed();
	
	/**
	 * Gets the change in SP caused by this operand.
	 * @return the change in SP caused by this operand
	 */
	public abstract int deltaSP();
}
