package cc.bran.bdcpu16.codegen;

import cc.bran.bdcpu16.Cpu.Register;
import cc.bran.bdcpu16.util.ValueFormatter;
import cc.bran.bdcpu16.util.ValueFormatters;

/**
 * Represents an operand, that is, one of the a or b values in an instruction. Has functionality to generate
 * code for a get expression or a set statement.
 * @author Brandon Pitman
 *
 */
public abstract class Operand
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
	
	@Override
	public String toString()
	{
		return toString(ValueFormatters.getDefaultValueFormatter(), "next");
	}
	
	/**
	 * Gets a string representation for the operand, given a specific value for the next word.
	 * @param nextWord the value for the next word
	 * @return a string representation of the operand
	 */
	public String toString(char nextWord)
	{
		return toString(ValueFormatters.getDefaultValueFormatter(), nextWord);
	}
	
	/**
	 * Gets a string representation for the operand, using a generic next-word value and a specified value formatter.
	 * @param formatter the value formatter to use to format literal values
	 * @return a string representation of the operand
	 */
	public String toString(ValueFormatter formatter)
	{
		return toString(formatter, "next");
	}
	
	/**
	 * Gets a string representation for the operand, using a specific value for the next word and a specified value formatter.
	 * @param formatter the value formatter to use to format literal values
	 * @param nextWord the value of the next word
	 * @return a string representation of the operand
	 */
	public String toString(ValueFormatter formatter, char nextWord)
	{
		return toString(formatter, formatter.formatValue(nextWord));
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
	 * Determines if this operand consumes an "extra" word of memory. (i.e. contains a "next word" portion)
	 * @return true if and only if this operand consumes an extra word of memory
	 */
	public abstract boolean usesWord();
	
	/**
	 * Gets the change in SP caused by this operand.
	 * @return the change in SP caused by this operand
	 */
	public abstract int deltaSP();
	
	/**
	 * Gets a string representation for the operand, with a given value substituted for the next word.
	 * @param formatter the value formatter to use to format literal values
	 * @param nextWord a string representation of the next word
	 * @return a string representation of the operand
	 */
	abstract String toString(ValueFormatter formatter, String nextWord);
	
	/**
	 * Represents an operand that refers to a location in memory. 
	 * @author Brandon Pitman
	 */
	static class MemoryOperand extends Operand
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
		public boolean usesWord()
		{
			for(int i = 0; i < addressOperands.length; ++i)
			{
				if(addressOperands[i].usesWord())
				{
					return true;
				}
			}
			
			return false;
		}

		@Override
		public int deltaSP()
		{
			return 0;
		}

		@Override
		String toString(ValueFormatter formatter, String nextWord)
		{
			StringBuilder sb = new StringBuilder();
			
			sb.append("[");
			sb.append(addressOperands[0].toString(formatter, nextWord));
			
			for(int i = 1; i < addressOperands.length; ++i)
			{
				sb.append(" + ");
				sb.append(addressOperands[i].toString(formatter, nextWord));
			}
			
			sb.append("]");
			
			return sb.toString();
		}
	}
	
	/**
	 * Represents an operand that takes on a specific literal value.
	 * @author Brandon Pitman
	 */
	static class LiteralOperand extends Operand
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
		public boolean usesWord()
		{
			return false;
		}

		@Override
		public int deltaSP()
		{
			return 0;
		}

		@Override
		String toString(ValueFormatter formatter, String nextWord)
		{
			return formatter.formatValue((char)value);
		}
	}
	
	/**
	 * Represents an operand that stands for the "next word" in memory.
	 * @author Brandon Pitman
	 */
	static class NextWordOperand extends Operand
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
		public boolean usesWord()
		{
			return true;
		}

		@Override
		public int deltaSP()
		{
			return 0;
		}

		@Override
		String toString(ValueFormatter formatter, String nextWord)
		{
			return nextWord;
		}
	}
	
	/**
	 * Represents an operand that refers to a register.
	 * @author Brandon Pitman
	 */
	static class RegisterOperand extends Operand
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
		public boolean usesWord()
		{
			return false;
		}
		
		@Override
		public int deltaSP()
		{
			return 0;
		}
		
		@Override
		String toString(ValueFormatter formatter, String nextWord)
		{
			return reg.toString();
		}
	}

	/**
	 * Represents a Push operand.
	 * @author Brandon Pitman
	 */
	static class PushOperand extends Operand
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
		public boolean usesWord()
		{
			return false;
		}

		@Override
		public int deltaSP()
		{
			return -1;
		}

		@Override
		String toString(ValueFormatter formatter, String nextWord)
		{
			return "PUSH";
		}
	}
	
	/**
	 * Represents a Pop operand.
	 * @author Brandon Pitman
	 */
	static class PopOperand extends Operand
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
		public boolean usesWord()
		{
			return false;
		}

		@Override
		public int deltaSP()
		{
			return 1;
		}

		@Override
		String toString(ValueFormatter formatter, String nextWord)
		{
			return "POP";
		}
	}
}
