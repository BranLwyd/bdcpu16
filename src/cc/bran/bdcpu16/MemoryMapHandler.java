package cc.bran.bdcpu16;

/**
 * Represents a handler for a memory mapping in the DCPU-16. A memory mapping handler can intercept reads & writes to memory.
 * @author Brandon Pitman
 */
public interface MemoryMapHandler
{
	/**
	 * Does this handler intercept reads? This should be a constant value for a given handler.
	 * @return a boolean indicating if this handler intercepts reads
	 */
	public boolean interceptsReads();
	
	/**
	 * Does this handler intercept writes? This should be a constant value for a given handler.
	 * @return a boolean indicating if this handler intercepts writes
	 */
	public boolean interceptsWrites();
	
	/**
	 * Called when memory in the mapped region is read, if the handler indicated it intercepts reads.
	 * @param address the address that was read
	 * @return the value mapped into memory at this location
	 */
	public char memoryRead(char address);
	
	/**
	 * Called when memory in the mapped region is written, if the handler indicated it intercepts writes.
	 * @param address the address that was written
	 * @param value the value that was written into memory
	 */
	public void memoryWritten(char address, char value);
}
