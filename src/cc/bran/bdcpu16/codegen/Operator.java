package cc.bran.bdcpu16.codegen;

import java.util.EnumSet;

/**
 * Represents an operator, corresponding to the lower 5 bits of the instruction (for normal instructions) or the next 5 bits of the instruction (for special instructions). 
 * @author Brandon Pitman
 */
public enum Operator
{
	/* basic arithmetic */
	SET(0x01, 1), ADD(0x02, 2), SUB(0x03, 2), MUL(0x04, 2), MLI(0x05, 2), DIV(0x06, 3), DVI(0x07, 3), MOD(0x08, 3), MDI(0x09, 3),
	
	/* bitwise arithmetic */
	AND(0x0a, 1), BOR(0x0b, 1), XOR(0x0c, 1), SHR(0x0d, 1), ASR(0x0e, 1), SHL(0x0f, 1),
	
	/* conditional */
	IFB(0x10, 2, OperatorFlags.Conditional), IFC(0x11, 2, OperatorFlags.Conditional), IFE(0x12, 2, OperatorFlags.Conditional), IFN(0x13, 2, OperatorFlags.Conditional),
	IFG(0x14, 2, OperatorFlags.Conditional), IFA(0x15, 2, OperatorFlags.Conditional), IFL(0x16, 2, OperatorFlags.Conditional), IFU(0x17, 2, OperatorFlags.Conditional),
	
	/* arithmetic with overflow */
	ADX(0x1a, 3), SBX(0x1b, 3),
	
	/* loop helpers */
	STI(0x1e, 2), STD(0x1f, 2),
	
	/* special */
	JSR(0x01, 3, OperatorFlags.Special),
	
	/* special: interrupts */
	INT(0x08, 4, OperatorFlags.Special), IAG(0x09, 1, OperatorFlags.Special), IAS(0x0a, 1, OperatorFlags.Special), RFI(0x0b, 3, OperatorFlags.Special), IAQ(0x0c, 2, OperatorFlags.Special),
	
	/* special: hardware */
	HWN(0x10, 2, OperatorFlags.Special), HWQ(0x11, 4, OperatorFlags.Special), HWI(0x12, 4, OperatorFlags.Special, OperatorFlags.NoReturn);
	
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
	
	private final int operatorValue;
	private final int cyclesToExecute;
	private final EnumSet<OperatorFlags> opFlags;
	
	/**
	 * Gets the normal operator corresponding to a given operator value.
	 * @param operatorValue the operator value
	 * @return the corresponding normal instruction
	 */
	public static Operator getNormalOperator(int operatorValue)
	{
		return normalInstructions[operatorValue];
	}
	
	/**
	 * Gets the special operator corresponding to a given operator value.
	 * @param operatorValue the operator value
	 * @return the corresponding special instruction
	 */
	public static Operator getSpecialOperator(int operatorValue)
	{
		return specialInstructions[operatorValue];
	}
	
	/**
	 * Creates a new operator.
	 * @param operatorValue the numeric value of this operator 
	 * @param cyclesToExecute the base number of cycles it takes to execute this instruction (this may be modified at execution time)
	 */
	private Operator(int operatorValue, int cyclesToExecute)
	{
		this.operatorValue = operatorValue;
		this.cyclesToExecute = cyclesToExecute;
		this.opFlags = EnumSet.noneOf(OperatorFlags.class);
	}
	
	/**
	 * Creates a new operator.
	 * @param operatorValue the numeric value of this operator
	 * @param cyclesToExecute the base number of cycles it takes to execute this instruction (this may be modified at execution time)
	 * @param firstFlag the first flag to associate with this operator
	 * @param remainingFlags the remaining flags to associate with this operator
	 */
	private Operator(int operatorValue, int cyclesToExecute, OperatorFlags firstFlag, OperatorFlags... remainingFlags)
	{
		this.operatorValue = operatorValue;
		this.cyclesToExecute = cyclesToExecute;
		this.opFlags = EnumSet.of(firstFlag, remainingFlags);
	}
	
	/**
	 * Returns the numeric value of this operator, as it would be encoded in memory.
	 * @return the numeric value of this operator
	 */
	public int value()
	{
		return operatorValue;
	}
	
	/**
	 * Gets the number of cycles it takes to execute this operator, not counting any cycles consumed fetching the operands.
	 * @return the number of cycles it takes to execute this operator
	 */
	public int cyclesToExecute()
	{
		return cyclesToExecute;
	}
	
	/**
	 * Determines if the operator is one of the conditional operators (IF*).
	 * @return true if and only if the operator is conditional
	 */
	public boolean conditional() { return opFlags.contains(OperatorFlags.Conditional); }

	/**
	 * Determines if the operator takes one operand rather than two. (i.e. if it is special)
	 * @return true if and only if the operator takes one operand 
	 */
	public boolean singleOperand() { return special(); }
	
	/**
	 * Determines if the operator is special.
	 * @return true if and only if the operator is special
	 */
	public boolean special() { return opFlags.contains(OperatorFlags.Special); }
	
	/**
	 * Determines if standard return code should be generated when generating code for this operator.
	 * If not, the operator must include a return statement in its operator-specific code.
	 * @return true if and only if standard return code should be generated
	 */
	public boolean generateReturn() { return !opFlags.contains(OperatorFlags.NoReturn); }
	
	/**
	 * Generates operator-specific code, which lives inside the execute() method of a compiled class.
	 * At this point, the PC and SP have bee updated from decoding (PC by Cpu.step(), SP by code
	 * generated in InstructionCompiler.getCodeForInstruction().
	 * @param operandA operand in the A slot
	 * @param operandB operand in the B slot
	 * @return Java code to be inserted in the execute() method of a compiled Instruction class
	 */
	public String operatorSpecificCode(Operand operandA, Operand operandB)
	{
		StringBuilder sb = new StringBuilder();
		
		/* 
		 * determine offset from PC for "next word" operands.
		 * note: at this point the PC has already been incremented, so these offsets will be negative
		 */
		final int nwOffsetB = -1;
		final int nwOffsetA = nwOffsetB - (operandB != null && operandB.usesWord() ? 1 : 0);
		final int deltaSP = operandA.deltaSP() + (operandB == null ? 0 : operandB.deltaSP());
		
		switch(this)
		{
		case SET:
			sb.append(operandB.setterStatement(operandA.getterExpression(nwOffsetA, deltaSP), nwOffsetB, deltaSP));
			break;
			
		case ADD:
			sb.append("final int result = ");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append("+");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append(";");
			sb.append(operandB.setterStatement("result", nwOffsetB, deltaSP));
			sb.append("cpu.EX((char)(result >> 16));");
			break;
			
		case SUB:
			sb.append("final int result = ");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append("-");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append(";");
			sb.append(operandB.setterStatement("result", nwOffsetB, deltaSP));
			sb.append("cpu.EX((char)(result >> 16));");
			break;
			
		case MUL:
			sb.append("final int result = ");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append("*");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append(";");
			sb.append(operandB.setterStatement("result", nwOffsetB, deltaSP));
			sb.append("cpu.EX((char)(result >> 16));");
			break;
			
		case MLI:
			sb.append("final int result = (short)");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append("*(short)");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append(";");
			sb.append(operandB.setterStatement("result", nwOffsetB, deltaSP));
			sb.append("cpu.EX((char)(result >> 16));");
			break;
			
		case DIV:
			sb.append("final char valueA = ");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append("; final int result = (valueA == 0 ? 0 : (");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append(" << 16) / valueA);");
			sb.append(operandB.setterStatement("result >> 16", nwOffsetB, deltaSP));
			sb.append("cpu.EX((char)result);");
			break;
			
		case DVI:
			sb.append("final char valueA = ");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append("; final int result = (valueA == 0 ? 0 : ((short)");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append(" << 16) / (short)valueA);");
			sb.append(operandB.setterStatement("result >> 16", nwOffsetB, deltaSP));
			sb.append("cpu.EX((char)result);");
			break;
			
		case MOD:
			sb.append("final char valueA = ");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append("; final int result = (valueA == 0 ? 0 : ");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append(" % valueA);");
			sb.append(operandB.setterStatement("result", nwOffsetB, deltaSP));
			break;
			
		case MDI:
			sb.append("final char valueA = ");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append("; final int result = (valueA == 0 ? 0 : (short)");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append(" % (short)valueA);");
			sb.append(operandB.setterStatement("result", nwOffsetB, deltaSP));
			break;
			
		case AND:
			sb.append(operandB.setterStatement(String.format("%s & %s", operandA.getterExpression(nwOffsetA, deltaSP), operandB.getterExpression(nwOffsetB, deltaSP)), nwOffsetB, deltaSP));
			break;
			
		case BOR:
			sb.append(operandB.setterStatement(String.format("%s | %s", operandA.getterExpression(nwOffsetA, deltaSP), operandB.getterExpression(nwOffsetB, deltaSP)), nwOffsetB, deltaSP));
			break;
			
		case XOR:
			sb.append(operandB.setterStatement(String.format("%s ^ %s", operandA.getterExpression(nwOffsetA, deltaSP), operandB.getterExpression(nwOffsetB, deltaSP)), nwOffsetB, deltaSP));
			break;
			
		case SHR:
			sb.append("final char valueA = ");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append("; final char valueB = ");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append(";");
			sb.append(operandB.setterStatement("valueB >>> valueA", nwOffsetB, deltaSP));
			sb.append("cpu.EX((char)((valueB << 16) >> valueA));");
			break;
			
		case ASR:
			sb.append("final char valueA = ");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append("; final char valueB = ");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append(";");
			sb.append(operandB.setterStatement("valueB >> valueA", nwOffsetB, deltaSP));
			sb.append("cpu.EX((char)((valueB << 16) >>> valueA));");
			break;
			
		case SHL:
			sb.append("final int result = ");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append(" << ");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append(";");
			sb.append(operandB.setterStatement("result", nwOffsetB, deltaSP));
			sb.append("cpu.EX((char)(result >> 16));");
			break;
			
		case IFB:
			sb.append("cpu.skip((");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append(" & ");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append(") == 0);");
			break;
			
		case IFC:
			sb.append("cpu.skip((");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append(" & ");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append(") != 0);");
			break;
			
		case IFE:
			sb.append("cpu.skip(");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append(" != ");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append(");");
			break;
			
		case IFN:
			sb.append("cpu.skip(");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append(" == ");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append(");");
			break;
			
		case IFG:
			sb.append("cpu.skip(");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append(" <= ");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append(");");
			break;
			
		case IFA:
			sb.append("cpu.skip((short)");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append(" <= (short)");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append(");");
			break;
			
		case IFL:
			sb.append("cpu.skip(");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append(" >= ");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append(");");
			break;
			
		case IFU:
			sb.append("cpu.skip((short)");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append(" >= (short)");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append(");");
			break;
			
		case ADX:
			sb.append("final int result = ");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append("+");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append("+ cpu.EX()");
			sb.append(";");
			sb.append(operandB.setterStatement("result", nwOffsetB, deltaSP));
			sb.append("cpu.EX((char)(result >> 16));");
			break;
			
		case SBX:
			sb.append("final int result = ");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append("-");
			sb.append(operandB.getterExpression(nwOffsetB, deltaSP));
			sb.append("+ cpu.EX()");
			sb.append(";");
			sb.append(operandB.setterStatement("result", nwOffsetB, deltaSP));
			sb.append("cpu.EX((char)(result >> 16));");
			break;
			
		case STI:
			sb.append(operandB.setterStatement(operandA.getterExpression(nwOffsetA, deltaSP), nwOffsetB, deltaSP));
			sb.append("cpu.I((char)(cpu.I() + 1));");
			sb.append("cpu.J((char)(cpu.J() + 1));");
			break;
			
		case STD:
			sb.append(operandB.setterStatement(operandA.getterExpression(nwOffsetA, deltaSP), nwOffsetB, deltaSP));
			sb.append("cpu.I((char)(cpu.I() - 1));");
			sb.append("cpu.J((char)(cpu.J() - 1));");
			break;
			
		case JSR:
			sb.append("cpu.SP((char)(cpu.SP() - 1));");
			sb.append("cpu.memory(cpu.SP(), cpu.PC());");
			sb.append("cpu.PC(");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append(");");
			break;
			
		case INT:
			sb.append("cpu.interrupt(");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append(");");
			break;
			
		case IAG:
			operandA.setterStatement("cpu.IA()", nwOffsetA, deltaSP);
			break;
			
		case IAS:
			sb.append("cpu.IA(");
			operandA.getterExpression(nwOffsetA, deltaSP);
			sb.append(");");
			break;
			
		case RFI:
			sb.append("cpu.A(cpu.memory(cpu.SP()));");
			sb.append("cpu.PC(cpu.memory((char)(cpu.SP() + 1)));");
			sb.append("cpu.SP((char)(cpu.SP() + 2));");
			sb.append("cpu.interruptsEnabled(true);");
			break;
			
		case IAQ:
			sb.append("cpu.interruptsEnabled(0 == ");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append(");");
			break;
			
		case HWN:
			sb.append(operandA.setterStatement("cpu.attachedDeviceCount()", nwOffsetA, deltaSP));
			break;
			
		case HWQ:
			sb.append("final char valueA = ");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append("; if(valueA < cpu.attachedDeviceCount()) {");
			sb.append("final Device dev = cpu.attachedDevice(valueA);");
			sb.append("int result = dev.id();");
			sb.append("cpu.A((char)result);");
			sb.append("cpu.B((char)(result >> 16));");
			sb.append("cpu.C(dev.version());");
			sb.append("result = dev.manufacturer();");
			sb.append("cpu.X((char)result);");
			sb.append("cpu.Y((char)(result >> 16));");
			sb.append("}");
			break;
			
		case HWI: /* has NoReturn flag */
			sb.append("final char valueA = ");
			sb.append(operandA.getterExpression(nwOffsetA, deltaSP));
			sb.append("; if(valueA < cpu.attachedDeviceCount()) {");
			sb.append("return 4 + cpu.attachedDevice(valueA).interrupt();");
			sb.append("}");
			sb.append("return 4;");
			break;
		}
		
		return sb.toString();
	}
	
	/**
	 * Represents different flags that an operator may have. Used to determine code-generation.
	 * @author Brandon Pitman
	 */
	private enum OperatorFlags
	{
		/**
		 * This is a conditional operator.
		 */
		Conditional,
		
		/**
		 * This operator is "special" per the specification. (i.e. it takes one operand rather than two)
		 */
		Special,
		
		/**
		 * Do not generate standard return code for this operator. The instruction-specific code must include a return statement.
		 */
		NoReturn;
	}
}
