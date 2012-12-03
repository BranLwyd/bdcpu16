package cc.bran.bdcpu16.hardware;

import cc.bran.bdcpu16.Cpu;

public interface Hardware
{
	public void attach(Cpu cpu);
	public void interrupt();
	
	public int id();
	public short version();
	public int manufacturer();
}
