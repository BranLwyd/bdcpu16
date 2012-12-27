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
	private final int value;
	
	private static final Operand operands[] =
		{
			/* 0x00 to 0x07 -- general registers */
			new RegisterOperand(0x00, Register.A),
			new RegisterOperand(0x01, Register.B),
			new RegisterOperand(0x02, Register.C),
			new RegisterOperand(0x03, Register.X),
			new RegisterOperand(0x04, Register.Y),
			new RegisterOperand(0x05, Register.Z),
			new RegisterOperand(0x06, Register.I),
			new RegisterOperand(0x07, Register.J),
			
			/* 0x08 to 0x0f -- memory addressed by general registers */
			new MemoryOperand(0x08, new RegisterOperand(Register.A)),
			new MemoryOperand(0x09, new RegisterOperand(Register.B)),
			new MemoryOperand(0x0a, new RegisterOperand(Register.C)),
			new MemoryOperand(0x0b, new RegisterOperand(Register.X)),
			new MemoryOperand(0x0c, new RegisterOperand(Register.Y)),
			new MemoryOperand(0x0d, new RegisterOperand(Register.Z)),
			new MemoryOperand(0x0e, new RegisterOperand(Register.I)),
			new MemoryOperand(0x0f, new RegisterOperand(Register.J)),
			
			/* 0x10 to 0x17 -- memory addressed by register plus next word */
			new MemoryOperand(0x10, new RegisterOperand(Register.A), new NextWordOperand()),
			new MemoryOperand(0x11, new RegisterOperand(Register.B), new NextWordOperand()),
			new MemoryOperand(0x12, new RegisterOperand(Register.C), new NextWordOperand()),
			new MemoryOperand(0x13, new RegisterOperand(Register.X), new NextWordOperand()),
			new MemoryOperand(0x14, new RegisterOperand(Register.Y), new NextWordOperand()),
			new MemoryOperand(0x15, new RegisterOperand(Register.Z), new NextWordOperand()),
			new MemoryOperand(0x16, new RegisterOperand(Register.I), new NextWordOperand()),
			new MemoryOperand(0x17, new RegisterOperand(Register.J), new NextWordOperand()),
			
			/* 0x18 to 0x1a -- stack operations */
			null, /* PUSH/POP handled specially */
			new MemoryOperand(0x19, new RegisterOperand(Register.SP)),
			new MemoryOperand(0x1a, new RegisterOperand(Register.SP), new NextWordOperand()),
			
			/* 0x1b to 0x1d -- special registers */
			new RegisterOperand(0x1b, Register.SP),
			new RegisterOperand(0x1c, Register.PC),
			new RegisterOperand(0x1d, Register.EX),
			
			/* 0x1e to 0x1f -- next word operands */
			new MemoryOperand(0x1e, new NextWordOperand()),
			new NextWordOperand(0x1f),
			
			/* 0x20 to 0x3f -- immediate literals */
			new LiteralOperand(0x20, -1),
			new LiteralOperand(0x21,  0),
			new LiteralOperand(0x22,  1),
			new LiteralOperand(0x23,  2),
			new LiteralOperand(0x24,  3),
			new LiteralOperand(0x25,  4),
			new LiteralOperand(0x26,  5),
			new LiteralOperand(0x27,  6),
			new LiteralOperand(0x28,  7),
			new LiteralOperand(0x29,  8),
			new LiteralOperand(0x2a,  9),
			new LiteralOperand(0x2b, 10),
			new LiteralOperand(0x2c, 11),
			new LiteralOperand(0x2d, 12),
			new LiteralOperand(0x2e, 13),
			new LiteralOperand(0x2f, 14),
			new LiteralOperand(0x30, 15),
			new LiteralOperand(0x31, 16),
			new LiteralOperand(0x32, 17),
			new LiteralOperand(0x33, 18),
			new LiteralOperand(0x34, 19),
			new LiteralOperand(0x35, 20),
			new LiteralOperand(0x36, 21),
			new LiteralOperand(0x37, 22),
			new LiteralOperand(0x38, 23),
			new LiteralOperand(0x39, 24),
			new LiteralOperand(0x3a, 25),
			new LiteralOperand(0x3b, 26),
			new LiteralOperand(0x3c, 27),
			new LiteralOperand(0x3d, 28),
			new LiteralOperand(0x3e, 29),
			new LiteralOperand(0x3f, 30),
		};
	
	private static final Operand pushOp = new PushOperand(0x18);
	private static final Operand popOp = new PopOperand(0x18);
	
	/**
	 * Gets the push operand.
	 * @return the push operand
	 */
	public static Operand getPushOperand()
	{
		return pushOp;
	}
	
	/**
	 * Gets the pop operand.
	 * @return the pop operand
	 */
	public static Operand getPopOperand()
	{
		return popOp;
	}
	
	/**
	 * Gets an operand based on several specified operational characteristics. Note that this method will never
	 * return the PUSH/POP operands, but all other operands can be returned by this method.
	 * @param hasMemoryRef true if this operand dereferences memory (other than implicitly via a Next Word piece)
	 * @param hasLiteral true if this operand is a literal, or is a memory access to an address offset by a literal
	 * @param register the register associated with this operand, or null if none
	 * @return the operand matching the specified characteristics, or null if there is no such operand 
	 */
	public static Operand getOperand(boolean hasMemoryRef, boolean hasLiteral, Register register)
	{
		if(!hasMemoryRef && hasLiteral && register != null)
		{
			/* only time we can have a register and a literal is when we are doing a memory read */
			return null;
		}
		
		/* non-register operands (0x1e - 0x1f) */
		if(register == null)
		{
			if(hasMemoryRef) { return operands[0x1e]; }
			return operands[0x1f];
		}
		
		/* special registers (0x1b - 0x0x1d) */
		if(!hasMemoryRef && !hasLiteral)
		{
			switch(register)
			{
			case SP: return operands[0x1b];
			case PC: return operands[0x1c];
			case EX: return operands[0x1d];
			
			default: break; /* fall through -- standard registers will be handled below */
			}
		}
		
		/* [SP] / PEEK & [SP + next] / PICK n (0x19 - 0x1a) */
		if(hasMemoryRef && register == Register.SP)
		{
			if(hasLiteral) { return operands[0x1a]; }
			return operands[0x19];
		}
		
		/* note: PUSH/POP (0x18) is not handled by this method */
		
		/* "basic" operands (0x00 - 0x17) */
		int opIndex = 0;
		if(hasMemoryRef) { opIndex += 0x08; }
		if(hasLiteral) { opIndex += 0x08; }
		
		switch(register)
		{
		case A: opIndex += 0; break;
		case B: opIndex += 1; break;
		case C: opIndex += 2; break;
		case X: opIndex += 3; break;
		case Y: opIndex += 4; break;
		case Z: opIndex += 5; break;
		case I: opIndex += 6; break;
		case J: opIndex += 7; break;
		
		default: return null;
		}
		
		return operands[opIndex];
	}
	
	/**
	 * Gets the immediate literal operand for a given value. Immediate literals can only represent the range
	 * [-1, 30] inclusive.
	 * @param value the value to retrieve the immediate literal operand for
	 * @return the given operand, or null if the given value cannot be represented as an immediate literal
	 */
	public static Operand getImmediateLiteralOperand(int value)
	{
		if(value < -1 || value > 30)
		{
			return null;
		}
		
		return operands[0x21 + value];
	}
	
	/**
	 * Gets the operand for a given operand value.
	 * @param operandValue the operand value
	 * @param isB true only if this is the B operand
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
	 * Creates a new operand.
	 * @param value
	 */
	Operand(int value)
	{
		this.value = value;
	}
	
	/**
	 * Gets the numeric value of this operand, as it would be encoded in memory.
	 * @return the numeric value of this operand
	 */
	public int value()
	{
		return value;
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
	 * Determines whether this operand is a literal operand.
	 * @return true if and only if this operand is a literal
	 */
	public abstract boolean literal();
	
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
		 * @param value the operand value as it would be encoded in memory
		 * @param addressOperands a list of operands; the sum of the value of these operands will be used to generate the address that this memory refers to 
		 */
		public MemoryOperand(int value, Operand... addressOperands)
		{
			super(value);
			
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

		@Override
		public boolean literal()
		{
			return false;
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
		 * @param operandValue the operand value as it would be encoded in memory
		 * @param literalValue the value for this literal operand
		 */
		public LiteralOperand(int operandValue, int literalValue)
		{
			super(operandValue);
			
			this.value = literalValue;
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

		@Override
		public boolean literal()
		{
			return true;
		}
	}
	
	/**
	 * Represents an operand that stands for the "next word" in memory.
	 * @author Brandon Pitman
	 */
	static class NextWordOperand extends Operand
	{
		/**
		 * Creates a new next word operand with no operand value. This constructor should only be used for an operand that will be placed inside another operand.
		 */
		public NextWordOperand()
		{
			super(-1);
		}
		
		/**
		 * Creates a new next word operand.
		 * @param value the operand value as it would be encoded in memory
		 */
		public NextWordOperand(int value)
		{
			super(value);
		}
		
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

		@Override
		public boolean literal()
		{
			return true;
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
		 * Creates a new register operand with no operand value. This constructor shoudld be used only for an operand that is to be placed inside of another operand.
		 * @param reg the register that this operand refers to
		 */
		public RegisterOperand(Register reg)
		{
			super(-1);
			
			this.reg = reg;
		}
		
		/**
		 * Creates a new register operand.
		 * @param value the operand value as it would be encoded in memory
		 * @param reg the register that this operand refers to
		 */
		public RegisterOperand(int value, Register reg)
		{
			super(value);
			
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

		@Override
		public boolean literal()
		{
			return false;
		}
	}

	/**
	 * Represents a Push operand.
	 * @author Brandon Pitman
	 */
	static class PushOperand extends Operand
	{
		/**
		 * Creates a new push operand.
		 * @param value the operand value as it would be encoded in memory
		 */
		public PushOperand(int value)
		{
			super(value);
		}
		
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

		@Override
		public boolean literal()
		{
			return false;
		}
	}
	
	/**
	 * Represents a Pop operand.
	 * @author Brandon Pitman
	 */
	static class PopOperand extends Operand
	{
		/**
		 * Creates a new pop operand
		 * @param value the operand value as it would be encoded in memory
		 */
		public PopOperand(int value)
		{
			super(value);
		}

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

		@Override
		public boolean literal()
		{
			return false;
		}
	}
}
