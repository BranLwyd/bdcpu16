package cc.bran.bdcpu16;

/**
 * Represents a single decoded DCPU-16 instruction.
 * @author Brandon Pitman
 */
class Instruction
{
	/**
	 * Map from operation value to operator for normal instructions.
	 */
	private static final Operator normalInstructions[] =
		{
			null        , Operator.SET, Operator.ADD, Operator.SUB, Operator.MUL, Operator.MLI, Operator.DIV, Operator.DVI,
			Operator.MOD, Operator.MDI, Operator.AND, Operator.BOR, Operator.XOR, Operator.SHR, Operator.ASR, Operator.SHL,
			Operator.IFB, Operator.IFC, Operator.IFE, Operator.IFN, Operator.IFG, Operator.IFA, Operator.IFL, Operator.IFU,
			null        , null        , Operator.ADX, Operator.SBX, null        , null        , Operator.STI, Operator.STD,
		};
	
	/**
	 * Map from operation value to operator for special instructions.
	 */
	private static final Operator specialInstructions[] =
		{
			null        , Operator.JSR, null        , null        , null        , null        , null        , null        ,
			Operator.INT, Operator.IAG, Operator.IAS, Operator.RFI, Operator.IAQ, null        , null        , null        ,
			Operator.HWN, Operator.HWQ, Operator.HWI, null        , null        , null        , null        , null        ,
			null        , null        , null        , null        , null        , null        , null        , null        ,
		};
	
	private final Cpu cpu;
	
	private final Operator operator;
	private final Operand operandA;
	private final Operand operandB;
	private final int wordsUsed;
	
	/**
	 * Creates a new instruction object.
	 * @param cpu the CPU associated with this instruction
	 * @param instructionValue the value in memory to decode into an instruction
	 */
	public Instruction(Cpu cpu, short instructionValue)
	{
		this.cpu = cpu;
		
		short operatorValue = (short)(instructionValue & 0x1f);
		short bValue = (short)((instructionValue >> 5) & 0x1f);
		short aValue = (short)((instructionValue >> 10) & 0x3f);
		
		operandA = cpu.getOperandForValue(aValue);
		
		if(operatorValue != 0)
		{
			operator = Instruction.normalInstructions[operatorValue];
			operandB = cpu.getOperandForValue(bValue);
		}
		else
		{
			operator = Instruction.specialInstructions[bValue];
			operandB = null;
		}
		
		wordsUsed = 1 + operandA.wordsUsed() + (operandB != null ? operandB.wordsUsed() : 0);
	}
	
	/**
	 * Determines if this instruction's value doesn't correspond to a legal DCPU-16 instruction. 
	 * @return a boolean indicating if this instruction is illegal
	 */
	public boolean illegal()
	{
		return (operator == null);
	}
	
	/**
	 * Executes the instruction.
	 * @return the number of cycles taken to execute this instruction
	 */
	public int execute()
	{
		short tokenA, tokenB;
		
		if(illegal())
		{
			return 0;
		}
		
		int cyclesUsed = operator.cyclesToExecute + operandA.cyclesToLookUp() + (operandB != null ? operandB.cyclesToLookUp() : 0);
		
		tokenA = operandA.lookUpReferent(false);
		tokenB = (operandB != null ? operandB.lookUpReferent(true) : 0);
		
		/* 
		 * shortVal & 0xffff effectively interprets the bits in a short value as part of an integer, allowing us to treat it as if it is unsigned.
		 * just casting the int value back to a short is enough to get the right result (eg 32768 -> -32767)
		 */
		
		int valueA, valueB, result;
		switch(operator)
		{
		case SET:
			operandB.set(tokenB, operandA.get(tokenA));
			break;

		case ADD:
			/* since specification does not consider the possibility of underflow here, treating as unsigned for this op */
			result = (operandA.get(tokenA) & 0xffff) + (operandB.get(tokenB) & 0xffff);
			operandB.set(tokenB, (short)result);
			cpu.ex = ((result & 0xffff0000) != 0 ? (short)1 : (short)0);
			break;
		
		case SUB:
			/* again, spec doesn't mention signed or unsigned, but does not include overflow as a possibility, so treat as unsigned */
			result = (operandB.get(tokenB) & 0xffff) - (operandA.get(tokenA) & 0xffff);
			operandB.set(tokenB, (short)result);
			cpu.ex = ((result & 0xffff0000) != 0 ? (short)0xffff : (short)0);
			break;
		
		case MUL:
			result = (operandA.get(tokenA) & 0xffff) * (operandB.get(tokenB) & 0xffff);
			operandB.set(tokenB, (short)result);
			cpu.ex = (short)((result >> 16) & 0xffff);
			break;
		
		case MLI:
			result = operandA.get(tokenA) * operandB.get(tokenB);
			operandB.set(tokenB, (short)result);
			cpu.ex = (short)((result >> 16) & 0xffff);
			break;
		
		case DIV:
			valueA = (operandA.get(tokenA) & 0xffff);
			result = (valueA != 0 ? ((operandB.get(tokenB) & 0xffff) << 16) / valueA : 0);
			operandB.set(tokenB, (short)(result >> 16));
			cpu.ex = (short)result;
			break;
			
		case DVI:
			valueA = operandA.get(tokenA);
			result = (valueA != 0 ? (operandB.get(tokenB) << 16) / valueA : 0);
			operandB.set(tokenB, (short)(result >> 16));
			cpu.ex = (short)result;
			break;
		
		case MOD:
			valueA = (operandA.get(tokenA) & 0xffff);
			result = (valueA != 0 ? (operandB.get(tokenB) & 0xffff) % valueA : 0);
			operandB.set(tokenB, (short)result);
			break;
		
		case MDI:
			valueA = operandA.get(tokenA);
			result = (valueA != 0 ? operandB.get(tokenB) % valueA : 0);
			operandB.set(tokenB, (short)result);
			break;
		
		case AND:
			operandB.set(tokenB, (short)(operandA.get(tokenA) & operandB.get(tokenB)));
			break;
		
		case BOR:
			operandB.set(tokenB, (short)(operandA.get(tokenA) | operandB.get(tokenB)));
			break;
			
		case XOR:
			operandB.set(tokenB, (short)(operandA.get(tokenA) ^ operandB.get(tokenB)));
			break;
		
		case SHR:
			valueA = operandA.get(tokenA) & 0xffff;
			valueB = operandB.get(tokenB) & 0xffff;
			operandB.set(tokenB, (short)(valueB >>> valueA));
			cpu.ex = (short)((valueB << 16) >> valueA);
			break;
		
		case ASR:
			valueA = operandA.get(tokenA);
			valueB = operandB.get(tokenB);
			operandB.set(tokenB, (short)(valueB >> valueA));
			cpu.ex = (short)((valueB << 16) >>> valueA);
			break;
			
		case SHL:
			result = operandB.get(tokenB) << operandA.get(tokenA);
			operandB.set(tokenB, (short)result);
			cpu.ex = (short)(result >> 16);
			break;
			
		case IFB:
			if((operandA.get(tokenA) & operandB.get(tokenB)) == 0)
			{
				cyclesUsed += skipNextUnconditionalInstruction();
			}
			break;
			
		case IFC:
			if((operandA.get(tokenA) & operandB.get(tokenB)) != 0)
			{
				cyclesUsed += skipNextUnconditionalInstruction();
			}
			break;
			
		case IFE:
			if(operandA.get(tokenA) != operandB.get(tokenB))
			{
				cyclesUsed += skipNextUnconditionalInstruction();
			}
			break;
			
		case IFN:
			if(operandA.get(tokenA) == operandB.get(tokenB))
			{
				cyclesUsed += skipNextUnconditionalInstruction();
			}
			break;
			
		case IFG:
			if((operandB.get(tokenB) & 0xffff) > (operandA.get(tokenA) & 0xffff))
			{
				cyclesUsed += skipNextUnconditionalInstruction();
			}
			break;
			
		case IFA:
			if(operandB.get(tokenB) > operandA.get(tokenA))
			{
				cyclesUsed += skipNextUnconditionalInstruction();
			}
			break;
			
		case IFL:
			if((operandB.get(tokenB) & 0xffff) < (operandA.get(tokenA) & 0xffff))
			{
				cyclesUsed += skipNextUnconditionalInstruction();
			}
			break;
			
		case IFU:
			if(operandB.get(tokenB) < operandA.get(tokenA))
			{
				cyclesUsed += skipNextUnconditionalInstruction();
			}
			break;
			
		case ADX:
			/* since specification does not consider the possibility of underflow here, treating as unsigned for this op */
			result = (operandA.get(tokenA) & 0xffff) + (operandB.get(tokenB) & 0xffff) + cpu.ex;
			operandB.set(tokenB, (short)result);
			cpu.ex = ((result & 0xffff0000) != 0 ? (short)1 : (short)0);
			break;
			
		case SBX:
			result = (operandB.get(tokenB) & 0xffff) - (operandA.get(tokenA) & 0xffff) + cpu.ex;
			operandB.set(tokenB, (short)result);
			if(result < 0)
			{
				cpu.ex = (short)0xffff;
			}
			else
			{
				cpu.ex = ((result & 0xffff0000) != 0 ? (short)1 : (short)0);
			}
			break;
			
		case STI:
			operandB.set(tokenB, operandA.get(tokenA));
			++cpu.rI;
			++cpu.rJ;
			break;
			
		case STD:
			operandB.set(tokenB, operandA.get(tokenA));
			--cpu.rI;
			--cpu.rJ;
			break;
			
		case JSR:
			cpu.writeMemory(--cpu.sp, cpu.pc);
			cpu.pc = operandA.get(tokenA);
			break;
			
		case INT:
			cpu.interrupt(operandA.get(tokenA));
			break;
			
		case IAG:
			operandA.set(tokenA, cpu.ia);
			break;
			
		case IAS:
			cpu.ia = operandA.get(tokenA);
			break;
			
		case RFI:
			cpu.rA = cpu.readMemory(cpu.sp++);
			cpu.pc = cpu.readMemory(cpu.sp++);
			cpu.interruptsEnabled = true;
			break;
			
		case IAQ:
			cpu.interruptsEnabled = (operandA.get(tokenA) == 0);
			break;
			
		case HWN:
			operandA.set(tokenA, (short)cpu.attachedHardware.length);
			break;
			
		case HWQ:
			valueA = operandA.get(tokenA) & 0xffff;
			if(valueA < cpu.attachedHardware.length)
			{
				result = cpu.attachedHardware[valueA].id();
				cpu.rA = (short)result;
				cpu.rB = (short)(result >> 16);
				cpu.rC = cpu.attachedHardware[valueA].version();
				result = cpu.attachedHardware[valueA].manufacturer();
				cpu.rX = (short)result;
				cpu.rY = (short)(result >> 16);
			}
			break;
			
		case HWI:
			valueA = operandA.get(tokenA) & 0xffff;
			if(valueA < cpu.attachedHardware.length)
			{
				cyclesUsed += cpu.attachedHardware[valueA].interrupt();
			}
			break;
		}
		
		return cyclesUsed;
	}
	
	/**
	 * Skips the next unconditional instruction (skipping as many conditional instructions first as is necessary). 
	 * @return the number of instructions skipped
	 */
	private int skipNextUnconditionalInstruction()
	{
		Instruction inst;
		int instructionsSkipped = 0;
		
		do
		{
			inst = cpu.getInstructionForAddress(cpu.pc);
			if(inst.illegal())
			{
				break;
			}
			
			cpu.pc += inst.wordsUsed;
			instructionsSkipped++;
		} while(inst.operator.isConditional());
		
		return instructionsSkipped;
	}
	
	/* operator flags */
	private static final int OP_COND = 0x1;
	
	/**
	 * Represents an operator, corresponding to the lower 5 bits of the instruction (for normal instructions) or the next 5 bits of the instruction (for special instructions). 
	 * @author Brandon Pitman
	 */
	private enum Operator
	{
		/* basic arithmetic */
		SET(1), ADD(2), SUB(2), MUL(2), MLI(2), DIV(3), DVI(3), MOD(3), MDI(3),
		
		/* bitwise arithmetic */
		AND(1), BOR(1), XOR(1), SHR(1), ASR(1), SHL(1),
		
		/* conditional */
		IFB(2, OP_COND), IFC(2, OP_COND), IFE(2, OP_COND), IFN(2, OP_COND), IFG(2, OP_COND), IFA(2, OP_COND), IFL(2, OP_COND), IFU(2, OP_COND),
		
		/* arithmetic with overflow */
		ADX(3), SBX(3),
		
		/* loop helpers */
		STI(2), STD(2),
		
		/* special */
		JSR(3),
		
		/* special: interrupts */
		INT(4), IAG(1), IAS(1), RFI(3), IAQ(2),
		
		/* special: hardware */
		HWN(2), HWQ(4), HWI(4);
		
		final int cyclesToExecute;
		final int opFlags;
		
		/**
		 * Creates a new operator.
		 * @param cyclesToExecute the base number of cycles it takes to execute this instruction (this may be modified at execution time)
		 */
		private Operator(int cyclesToExecute)
		{
			this.cyclesToExecute = cyclesToExecute;
			this.opFlags = 0;
		}
		
		/**
		 * Creates a new operator.
		 * @param cyclesToExecute the base number of cycles it takes to execute this instruction (this may be modified at execution time)
		 * @param opFlags the flags to associate with this operator; these should be taken from the OP_ constants above
		 */
		private Operator(int cyclesToExecute, int opFlags)
		{
			this.cyclesToExecute = cyclesToExecute;
			this.opFlags = opFlags;
		}
		
		/**
		 * Determines if the operator is one of the conditional operators (IF*).
		 * @return a boolean determining if the operator is conditional
		 */
		public boolean isConditional() { return (opFlags & OP_COND) != 0; }
	}
}