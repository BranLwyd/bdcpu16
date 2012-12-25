package cc.bran.bdcpu16.codegen;

import cc.bran.bdcpu16.codegen.Operator;
import cc.bran.bdcpu16.codegen.Operand;
import cc.bran.bdcpu16.util.ValueFormatter;
import cc.bran.bdcpu16.util.ValueFormatters;

/**
 * This class decodes instruction values and provides information about the instruction, its operator, and its operands.
 * @author Brandon Pitman
 */
public class InstructionDecoder
{
	/**
	 * This constructor is private because InstructionDecoder is a static class.
	 */
	private InstructionDecoder() { }
	
	/**
	 * Determines if a given instruction value is legal. If you want to decode the instruction, it is more efficient to call decode() and check the return value against null.
	 * @param instructionValue the instruction value
	 * @return true if and only if the instruction value corresponds to a legal instruction
	 */
	public static boolean isInstructionLegal(char instructionValue)
	{		
		int operatorValue = instructionValue & 0x1f;
		if(operatorValue == 0)
		{
			return (Operator.getSpecialOperator((instructionValue >> 5) & 0x1f) != null);
		}
		else
		{
			return (Operator.getNormalOperator(operatorValue) != null);
		}
	}
	
	/**
	 * Decodes an instruction. Returns null if the instruction is illegal.
	 * @param instructionValue the value of the instruction to decode
	 * @return the decoded instruction, or null for illegal instructions
	 */
	public static DecodedInstruction decode(char instructionValue)
	{
		Operator operator;
		Operand operandA, operandB;
		
		int operatorValue = instructionValue & 0x1f;
		if(operatorValue == 0)
		{
			/* special instruction */
			operator = Operator.getSpecialOperator((instructionValue >> 5) & 0x1f);
			operandA = Operand.getOperand((instructionValue >> 10) & 0x3f, false);
			operandB = null;
		}
		else
		{
			/* normal instruction */
			operator = Operator.getNormalOperator(operatorValue);
			operandA = Operand.getOperand((instructionValue >> 10) & 0x3f, false);
			operandB = Operand.getOperand((instructionValue >> 5) & 0x1f, true);
		}
		
		if(operator == null)
		{
			return null;
		}
		
		return new DecodedInstruction(operator, operandA, operandB);
	}
	
	/**
	 * This class represents a decoded instruction and has members representing the operator and each operand.
	 * If the instruction is special, the B operand will be null.
	 * @author Brandon Pitman
	 */
	public static class DecodedInstruction
	{
		public final Operator operator;
		public final Operand operandA;
		public final Operand operandB;
		
		/**
		 * Creates a new decoded instruction. 
		 * @param operator the operator for this instruction 
		 * @param operandA the A operand for this instruction
		 * @param operandB the B operand for this instruction
		 */
		private DecodedInstruction(Operator operator, Operand operandA, Operand operandB)
		{
			this.operator = operator;
			this.operandA = operandA;
			this.operandB = operandB;
		}
		
		/**
		 * Gets the total number of words used by this instruction.
		 * @return the total number of words used by this instruction
		 */
		public int wordsUsed()
		{
			return 1 + (operandA.usesWord() ? 1 : 0)
					 + (operandB != null && operandB.usesWord() ? 1 : 0);
		}
		
		@Override
		public String toString()
		{
			return toString(ValueFormatters.getDefaultValueFormatter(), "next", "next");
		}
		
		/**
		 * Gets a string representation of this instruction.
		 * @param formatter the value formatter to use to format literal values
		 * @return a string representation of the instruction
		 */
		public String toString(ValueFormatter formatter)
		{
			return toString(formatter, "next", "next");
		}
		
		/**
		 * Gets a string representation of this instruction.
		 * @param nextValueOne the next word in memory after the base address of the instruction
		 * @param nextValueTwo the next word in memory after nextValueOne
		 * @return a string representation of the instruction
		 */
		public String toString(char nextValueOne, char nextValueTwo)
		{
			final ValueFormatter defaultFormatter = ValueFormatters.getDefaultValueFormatter();
			
			return toString(defaultFormatter, defaultFormatter.formatValue(nextValueOne), defaultFormatter.formatValue(nextValueTwo));
		}
		
		/**
		 * Gets a string representation of this instruction.
		 * @param formatter the value formatter to use to format literal values
		 * @param nextValueOne the next word in memory after the base address of the instruction
		 * @param nextValueTwo the next word in memory after nextValueOne
		 * @return a string representation of the instruction
		 */
		public String toString(ValueFormatter formatter, char nextValueOne, char nextValueTwo)
		{
			return toString(formatter, formatter.formatValue(nextValueOne), formatter.formatValue(nextValueTwo));
		}
		
		/**
		 * Gets a string representation of this instruction.
		 * @param formatter the value formatter to use to format literal values 
		 * @param nextValueOne a string representation of the next word in memory after the base address of the instruction 
		 * @param nextValueTwo a string representation of the next word in memory after nextWordOne
		 * @return a string representation of the instruction
		 */
		private String toString(ValueFormatter formatter, String nextValueOne, String nextValueTwo)
		{
			if(operandB == null)
			{
				return String.format("%s %s", operator.toString(), operandA.toString(formatter, nextValueOne));
			}
			
			final String aString = operandA.toString(formatter, nextValueOne);
			final String bString = operandB.toString(formatter, (operandA.usesWord() ? nextValueTwo : nextValueOne));
			return String.format("%s %s, %s", operator.toString(), bString, aString);
		}
	}
}
