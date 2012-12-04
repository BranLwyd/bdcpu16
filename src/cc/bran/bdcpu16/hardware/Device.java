package cc.bran.bdcpu16.hardware;

import cc.bran.bdcpu16.Cpu;

public interface Device
{
	public void attach(Cpu cpu);
	public int interrupt();
	
	public int id();
	public short version();
	public int manufacturer();
}
