package cc.bran.bdcpu16;


/**
 * Represents a single decoded DCPU-16 instruction.
 * @author Brandon Pitman
 */
public interface Instruction
{	
	/**
	 * Executes the instruction.
	 * @param cpu the CPU to execute the instruction on
	 * @return the number of cycles used in executing this instruction
	 */
	int execute(Cpu cpu);
	
	/**
	 * Determines if the instruction is illegal.
	 * @return true if and only if the instruction is illegal
	 */
	boolean illegal();
	
	/**
	 * Determines the number of words of memory used to encode this instruction.
	 * @return the number of words of memory used to encode this instruction
	 */
	int wordsUsed();
	
	/**
	 * Determines if this is a conditional instruction (one of the IF* instructions).
	 * Used by the CPU to implement skip semantics.
	 * @return true if and only if the instruction is conditional
	 */
	boolean conditional();
}