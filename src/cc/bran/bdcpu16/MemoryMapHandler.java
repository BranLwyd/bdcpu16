package cc.bran.bdcpu16;

public interface MemoryMapHandler
{
	public boolean handlesReads();
	public boolean handlesWrites();
	
	public short memoryRead(short address);
	public void memoryWritten(short address, short value);
}
