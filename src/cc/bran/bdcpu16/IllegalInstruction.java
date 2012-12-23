package cc.bran.bdcpu16;


/**
 * Represents an illegal instruction. Does not do anything if executed.
 * @author Brandon Pitman
 */
public class IllegalInstruction implements Instruction
{
	private static final IllegalInstruction instance = new IllegalInstruction();
	
	/**
	 * Gets the instance of the IllegalInstruction class.
	 * @return a new instance of the IllegalInstruction class
	 */
	public static IllegalInstruction getInstance()
	{
		return instance;
	}
	
	private IllegalInstruction() { }
	
	@Override
	public int execute(Cpu cpu)
	{
		/* just notify the CPU that this is an illegal instruction and move on */
		cpu.illegalInstructionExecuted();
		return 0;
	}

	@Override
	public boolean illegal()
	{
		return true;
	}

	@Override
	public int wordsUsed()
	{
		return 1;
	}
	
	@Override
	public boolean conditional()
	{
		/* this will be ignored */
		return false;
	}
}
