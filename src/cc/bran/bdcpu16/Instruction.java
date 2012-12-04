package cc.bran.bdcpu16;

class Instruction
{
	private static final Operator normalInstructions[] =
		{
			null        , Operator.SET, Operator.ADD, Operator.SUB, Operator.MUL, Operator.MLI, Operator.DIV, Operator.DVI,
			Operator.MOD, Operator.MDI, Operator.AND, Operator.BOR, Operator.XOR, Operator.SHR, Operator.ASR, Operator.SHL,
			Operator.IFB, Operator.IFC, Operator.IFE, Operator.IFN, Operator.IFG, Operator.IFA, Operator.IFL, Operator.IFU,
			null        , null        , Operator.ADX, Operator.SBX, null        , null        , Operator.STI, Operator.STD,
		};
	
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
	
	public int execute()
	{
		short tokenA, tokenB;
		
		int cyclesUsed = operator.cyclesToExecute + operandA.cyclesToLookUp() + (operandB != null ? operandB.cyclesToLookUp() : 0);
		
		tokenA = operandA.lookUpValue(false);
		tokenB = (operandB != null ? operandB.lookUpValue(true) : 0);
		
		/* 
		 * shortVal & 0xffff effectively interprets the bits in a short value as part of an integer, allowing us to treat it as if it is unsigned.
		 * just casting the int value back to a short is enough to get the right result (eg 32768 -> -32767)
		 */
		
		int valueA, valueB, result;
		switch(operator)
		{
		case SET:
			operandB.setValue(tokenB, operandA.getValue(tokenA));
			break;

		case ADD:
			/* since specification does not consider the possibility of underflow here, treating as unsigned for this op */
			result = (operandA.getValue(tokenA) & 0xffff) + (operandB.getValue(tokenB) & 0xffff);
			operandB.setValue(tokenB, (short)result);
			cpu.ex = ((result & 0xffff0000) != 0 ? (short)1 : (short)0);
			break;
		
		case SUB:
			/* again, spec doesn't mention signed or unsigned, but does not include overflow as a possibility, so treat as unsigned */
			result = (operandB.getValue(tokenB) & 0xffff) - (operandA.getValue(tokenA) & 0xffff);
			operandB.setValue(tokenB, (short)result);
			cpu.ex = ((result & 0xffff0000) != 0 ? (short)0xffff : (short)0);
			break;
		
		case MUL:
			result = (operandA.getValue(tokenA) & 0xffff) * (operandB.getValue(tokenB) & 0xffff);
			operandB.setValue(tokenB, (short)result);
			cpu.ex = (short)((result >> 16) & 0xffff);
			break;
		
		case MLI:
			result = operandA.getValue(tokenA) * operandB.getValue(tokenB);
			operandB.setValue(tokenB, (short)result);
			cpu.ex = (short)((result >> 16) & 0xffff);
			break;
		
		case DIV:
			valueA = (operandA.getValue(tokenA) & 0xffff);
			result = (valueA != 0 ? ((operandB.getValue(tokenB) & 0xffff) << 16) / valueA : 0);
			operandB.setValue(tokenB, (short)(result >> 16));
			cpu.ex = (short)result;
			break;
			
		case DVI:
			valueA = operandA.getValue(tokenA);
			result = (valueA != 0 ? (operandB.getValue(tokenB) << 16) / valueA : 0);
			operandB.setValue(tokenB, (short)(result >> 16));
			cpu.ex = (short)result;
			break;
		
		case MOD:
			valueA = (operandA.getValue(tokenA) & 0xffff);
			result = (valueA != 0 ? (operandB.getValue(tokenB) & 0xffff) % valueA : 0);
			operandB.setValue(tokenB, (short)result);
			break;
		
		case MDI:
			valueA = operandA.getValue(tokenA);
			result = (valueA != 0 ? operandB.getValue(tokenB) % valueA : 0);
			operandB.setValue(tokenB, (short)result);
			break;
		
		case AND:
			operandB.setValue(tokenB, (short)(operandA.getValue(tokenA) & operandB.getValue(tokenB)));
			break;
		
		case BOR:
			operandB.setValue(tokenB, (short)(operandA.getValue(tokenA) | operandB.getValue(tokenB)));
			break;
			
		case XOR:
			operandB.setValue(tokenB, (short)(operandA.getValue(tokenA) ^ operandB.getValue(tokenB)));
			break;
		
		case SHR:
			valueA = operandA.getValue(tokenA) & 0xffff;
			valueB = operandB.getValue(tokenB) & 0xffff;
			operandB.setValue(tokenB, (short)(valueB >>> valueA));
			cpu.ex = (short)((valueB << 16) >> valueA);
			break;
		
		case ASR:
			valueA = operandA.getValue(tokenA);
			valueB = operandB.getValue(tokenB);
			operandB.setValue(tokenB, (short)(valueB >> valueA));
			cpu.ex = (short)((valueB << 16) >>> valueA);
			break;
			
		case SHL:
			result = operandB.getValue(tokenB) << operandA.getValue(tokenA);
			operandB.setValue(tokenB, (short)result);
			cpu.ex = (short)(result >> 16);
			break;
			
		case IFB:
			if((operandA.getValue(tokenA) & operandB.getValue(tokenB)) == 0)
			{
				cyclesUsed += skipNextUnconditionalInstruction();
			}
			break;
			
		case IFC:
			if((operandA.getValue(tokenA) & operandB.getValue(tokenB)) != 0)
			{
				cyclesUsed += skipNextUnconditionalInstruction();
			}
			break;
			
		case IFE:
			if(operandA.getValue(tokenA) != operandB.getValue(tokenB))
			{
				cyclesUsed += skipNextUnconditionalInstruction();
			}
			break;
			
		case IFN:
			if(operandA.getValue(tokenA) == operandB.getValue(tokenB))
			{
				cyclesUsed += skipNextUnconditionalInstruction();
			}
			break;
			
		case IFG:
			if((operandB.getValue(tokenB) & 0xffff) > (operandA.getValue(tokenA) & 0xffff))
			{
				cyclesUsed += skipNextUnconditionalInstruction();
			}
			break;
			
		case IFA:
			if(operandB.getValue(tokenB) > operandA.getValue(tokenA))
			{
				cyclesUsed += skipNextUnconditionalInstruction();
			}
			break;
			
		case IFL:
			if((operandB.getValue(tokenB) & 0xffff) < (operandA.getValue(tokenA) & 0xffff))
			{
				cyclesUsed += skipNextUnconditionalInstruction();
			}
			break;
			
		case IFU:
			if(operandB.getValue(tokenB) < operandA.getValue(tokenA))
			{
				cyclesUsed += skipNextUnconditionalInstruction();
			}
			break;
			
		case ADX:
			/* since specification does not consider the possibility of underflow here, treating as unsigned for this op */
			result = (operandA.getValue(tokenA) & 0xffff) + (operandB.getValue(tokenB) & 0xffff) + cpu.ex;
			operandB.setValue(tokenB, (short)result);
			cpu.ex = ((result & 0xffff0000) != 0 ? (short)1 : (short)0);
			break;
			
		case SBX:
			result = (operandB.getValue(tokenB) & 0xffff) - (operandA.getValue(tokenA) & 0xffff) + cpu.ex;
			operandB.setValue(tokenB, (short)result);
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
			operandB.setValue(tokenB, operandA.getValue(tokenA));
			++cpu.rI;
			++cpu.rJ;
			break;
			
		case STD:
			operandB.setValue(tokenB, operandA.getValue(tokenA));
			--cpu.rI;
			--cpu.rJ;
			break;
			
		case JSR:
			cpu.writeMemory(--cpu.sp, cpu.pc);
			cpu.pc = operandA.getValue(tokenA);
			break;
			
		case INT:
			cpu.interrupt(operandA.getValue(tokenA));
			break;
			
		case IAG:
			operandA.setValue(tokenA, cpu.ia);
			break;
			
		case IAS:
			cpu.ia = operandA.getValue(tokenA);
			break;
			
		case RFI:
			cpu.rA = cpu.readMemory(cpu.sp++);
			cpu.pc = cpu.readMemory(cpu.sp++);
			cpu.setInterruptsEnabled(true);
			break;
			
		case IAQ:
			cpu.setInterruptsEnabled(operandA.getValue(tokenA) == 0);
			break;
			
		case HWN:
			operandA.setValue(tokenA, (short)cpu.attachedHardware.length);
			break;
			
		case HWQ:
			valueA = operandA.getValue(tokenA) & 0xffff;
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
			valueA = operandA.getValue(tokenA) & 0xffff;
			if(valueA < cpu.attachedHardware.length)
			{
				cyclesUsed += cpu.attachedHardware[valueA].interrupt();
			}
			break;
		}
		
		return cyclesUsed;
	}
	
	private int skipNextUnconditionalInstruction()
	{
		Instruction inst;
		int instructionsSkipped = 0;
		
		do
		{
			inst = cpu.getInstructionForAddress(cpu.pc);
			cpu.pc += inst.wordsUsed;
			instructionsSkipped++;
		} while(inst.operator.isConditional());
		
		return instructionsSkipped;
	}
	
	/* operator flags */
	private static final int OP_COND = 0x1;
	
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
		
		private Operator(int cyclesToExecute)
		{
			this.cyclesToExecute = cyclesToExecute;
			this.opFlags = 0;
		}
		
		private Operator(int cyclesToExecute, int opFlags)
		{
			this.cyclesToExecute = cyclesToExecute;
			this.opFlags = opFlags;
		}
		
		public boolean isConditional() { return (opFlags & OP_COND) != 0; }
	}
}