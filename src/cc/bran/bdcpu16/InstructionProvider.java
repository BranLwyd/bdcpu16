package cc.bran.bdcpu16;

/**
 * This interface represents the ability for a class to provide an executable instruction for a given DCPU-16 instruction value.
 * @author Brandon Pitman
 */
public interface InstructionProvider
{
	/**
	 * Gets the instruction for a given instruction value.
	 * @param instructionValue the instruction value to get
	 * @return an instruction that executes the given instruction value
	 */
	public Instruction getInstruction(char instructionValue);
}
