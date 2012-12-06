package cc.bran.bdcpu16;

/**
 * Represents an operand in a single instruction. Corresponds to the Values table in the DCPU-16 specification.
 * A single operand is shared between every Instruction that uses it, and therefore this class is and should remain immutable.
 * @author Brandon Pitman
 */
class Operand
{
	static final int OPERAND_COUNT = 0x40;
	
	/**
	 * Specifies where the referent of the operand lives. Indexed by operand value.
	 */
	private static final OperandSource operandSources[] =
		{
			/* 0x00 to 0x1f -- regular operands */
			OperandSource.A      , OperandSource.B      , OperandSource.C      , OperandSource.X      ,
			OperandSource.Y      , OperandSource.Z      , OperandSource.I      , OperandSource.J      ,
			OperandSource.Memory , OperandSource.Memory , OperandSource.Memory , OperandSource.Memory ,
			OperandSource.Memory , OperandSource.Memory , OperandSource.Memory , OperandSource.Memory ,
			OperandSource.Memory , OperandSource.Memory , OperandSource.Memory , OperandSource.Memory ,
			OperandSource.Memory , OperandSource.Memory , OperandSource.Memory , OperandSource.Memory ,
			OperandSource.Memory , OperandSource.Memory , OperandSource.Memory , OperandSource.SP     ,
			OperandSource.PC     , OperandSource.EX     , OperandSource.Memory , OperandSource.Literal,
			
			/* 0x20 to 0x3f -- immediate literals */
			OperandSource.Literal, OperandSource.Literal, OperandSource.Literal, OperandSource.Literal,
			OperandSource.Literal, OperandSource.Literal, OperandSource.Literal, OperandSource.Literal,
			OperandSource.Literal, OperandSource.Literal, OperandSource.Literal, OperandSource.Literal,
			OperandSource.Literal, OperandSource.Literal, OperandSource.Literal, OperandSource.Literal,
			OperandSource.Literal, OperandSource.Literal, OperandSource.Literal, OperandSource.Literal,
			OperandSource.Literal, OperandSource.Literal, OperandSource.Literal, OperandSource.Literal,
			OperandSource.Literal, OperandSource.Literal, OperandSource.Literal, OperandSource.Literal,
			OperandSource.Literal, OperandSource.Literal, OperandSource.Literal, OperandSource.Literal,
		};
	
	/**
	 * Specifies the number of additional words used past the instruciton itself (for operands specifying they use "next word"). Indexed by operand value.
	 */
	private static final int wordsUsed[] =
		{
			/* 0x00 to 0x1f -- regular operands */
			0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, 0, 0, 0,
			1, 1, 1, 1, 1, 1, 1, 1,
			0, 0, 1, 0, 0, 0, 1, 1,
			
			/* 0x20 to 0x3f -- immediate literals */
			0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, 0, 0, 0,
		};
	
	private final Cpu cpu;
	private final char operandValue;
	
	/**
	 * Creates a new operand. (Typically this should only be called by the Cpu class.) 
	 * @param cpu the CPU associated with this operand
	 * @param operandValue the numerical value of the operand
	 */
	Operand(Cpu cpu, char operandValue)
	{
		this.cpu = cpu;
		this.operandValue = operandValue;
	}
	
	/**
	 * Determines the number of cycles required to look up the referent of this operand.
	 * @return the number of cycles to look up the referent of this operand
	 */
	public int cyclesToLookUp()
	{
		/* currently, the number of cycles to look up the referent is equal to the number of words used by the operand */
		return Operand.wordsUsed[operandValue];
	}
	
	/**
	 * Determines the number of "extra" words used (i.e. by "next word") to determine the referent of this operand.
	 * @return the number of words used to determine the referent of this operand
	 */
	public int wordsUsed()
	{
		return Operand.wordsUsed[operandValue];
	}
	
	/**
	 * Gets the value of the referent of this operand.
	 * @param token the referent token (from lookUpReferent)
	 * @return the value of the referent of this operand
	 */
	public char get(char token)
	{	
		switch(Operand.operandSources[operandValue])
		{
		/* registers */
		case A: return cpu.rA;
		case B: return cpu.rB;
		case C: return cpu.rC;
		case X: return cpu.rX;
		case Y: return cpu.rY;
		case Z: return cpu.rZ;
		case I: return cpu.rI;
		case J: return cpu.rJ;
		case SP: return cpu.sp;
		case PC: return cpu.pc;
		case EX: return cpu.ex;
		
		case Memory: return cpu.mem[token];
		case Literal: return token;
		}
		
		/* this should be unreachable */
		return 0;
	}
	
	/**
	 * Sets the value of the referent of this operand.
	 * @param token the referent token (from lookUpReferent)
	 * @param value the value to set the referent of this operand to
	 */
	public void set(char token, char value)
	{	
		switch(Operand.operandSources[operandValue])
		{
		/* registers */
		case A: cpu.rA = value; break;
		case B: cpu.rB = value; break;
		case C: cpu.rC = value; break;
		case X: cpu.rX = value; break;
		case Y: cpu.rY = value; break;
		case Z: cpu.rZ = value; break;
		case I: cpu.rI = value; break;
		case J: cpu.rJ = value; break;
		case SP: cpu.sp = value; break;
		case PC: cpu.pc = value; break;
		case EX: cpu.ex = value; break;
		
		case Memory: cpu.mem[token] = value; break;
		case Literal: break;
		}
	}
	
	/**
	 * Determines the referent of this operand given the current state of the CPU.
	 * @param isB is this operand in the "B" slot? (if false, A is assumed)
	 * @return a referent token that can be passed into get/set in order to read/write the referent of this operand
	 */
	public char lookUpReferent(boolean isB)
	{
		switch(operandValue)
		{
		/* general registers */
		case 0x00: return 0;
		case 0x01: return 0;
		case 0x02: return 0;
		case 0x03: return 0;
		case 0x04: return 0;
		case 0x05: return 0;
		case 0x06: return 0;
		case 0x07: return 0;
		
		/* memory addressed by general registers */
		case 0x08: return cpu.rA;
		case 0x09: return cpu.rB;
		case 0x0a: return cpu.rC;
		case 0x0b: return cpu.rX;
		case 0x0c: return cpu.rY;
		case 0x0d: return cpu.rZ;
		case 0x0e: return cpu.rI;
		case 0x0f: return cpu.rJ;
		
		/* memory addressed by general register plus next word */
		case 0x10: return (char)(cpu.rA + cpu.mem[cpu.pc++]);
		case 0x11: return (char)(cpu.rB + cpu.mem[cpu.pc++]);
		case 0x12: return (char)(cpu.rC + cpu.mem[cpu.pc++]);
		case 0x13: return (char)(cpu.rX + cpu.mem[cpu.pc++]);
		case 0x14: return (char)(cpu.rY + cpu.mem[cpu.pc++]);
		case 0x15: return (char)(cpu.rZ + cpu.mem[cpu.pc++]);
		case 0x16: return (char)(cpu.rI + cpu.mem[cpu.pc++]);
		case 0x17: return (char)(cpu.rJ + cpu.mem[cpu.pc++]);
		
		/* stack ops */
		case 0x18: return (isB ? --cpu.sp : cpu.sp++);
		case 0x19: return cpu.sp;
		case 0x1a: return (char)(cpu.sp + cpu.mem[cpu.pc++]);
		
		/* special registers */
		case 0x1b: return 0;
		case 0x1c: return 0;
		case 0x1d: return 0;
		
		/* literal values */
		case 0x1e: return cpu.mem[cpu.pc++];
		case 0x1f: return cpu.mem[cpu.pc++];
		default: return (char)(operandValue - 0x21);
		}
	}
	
	/**
	 * Represents the possible sources of an operand
	 * @author Brandon Pitman
	 */
	private enum OperandSource
	{
		/* registers -- no associated token */
		A, B, C, X, Y, Z, I, J, SP, PC, EX,
		
		/* memory -- associated token is address */
		Memory,
		
		/* literal value -- associated token is literal value */
		Literal
	}
}
