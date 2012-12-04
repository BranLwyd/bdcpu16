package cc.bran.bdcpu16;

class Operand
{
	static final int OPERAND_COUNT = 0x40;
	
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
	private final short operandValue;
	
	public Operand(Cpu cpu, short operandValue)
	{
		this.cpu = cpu;
		this.operandValue = operandValue;
	}
	
	public int cyclesToLookUp()
	{
		/* currently, the number of cycles to look up the referent is equal to the number of words used by the operand */
		return Operand.wordsUsed[operandValue];
	}
	
	public int wordsUsed()
	{
		return Operand.wordsUsed[operandValue];
	}
	
	public short getValue(short token)
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
		
		case Memory: return cpu.readMemory(token);
		case Literal: return token;
		}
		
		/* this should be unreachable */
		return 0;
	}
	
	public void setValue(short token, short value)
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
		
		case Memory: cpu.writeMemory(token, value); break;
		case Literal: break;
		}
	}
	
	public short lookUpValue(boolean isB)
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
		case 0x10: return (short)(cpu.rA + cpu.readMemory(cpu.pc++));
		case 0x11: return (short)(cpu.rB + cpu.readMemory(cpu.pc++));
		case 0x12: return (short)(cpu.rC + cpu.readMemory(cpu.pc++));
		case 0x13: return (short)(cpu.rX + cpu.readMemory(cpu.pc++));
		case 0x14: return (short)(cpu.rY + cpu.readMemory(cpu.pc++));
		case 0x15: return (short)(cpu.rZ + cpu.readMemory(cpu.pc++));
		case 0x16: return (short)(cpu.rI + cpu.readMemory(cpu.pc++));
		case 0x17: return (short)(cpu.rJ + cpu.readMemory(cpu.pc++));
		
		/* stack ops */
		case 0x18: return (isB ? --cpu.sp : cpu.sp++);
		case 0x19: return cpu.sp;
		case 0x1a: return (short)(cpu.sp + cpu.readMemory(cpu.pc++));
		
		/* special registers */
		case 0x1b: return 0;
		case 0x1c: return 0;
		case 0x1d: return 0;
		
		/* literal values */
		case 0x1e: return cpu.readMemory(cpu.pc++);
		case 0x1f: return cpu.readMemory(cpu.pc++);
		default: return (short)(operandValue - 0x21);
		}
	}
	
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
