package cc.bran.bdcpu16.hardware;

import cc.bran.bdcpu16.Cpu;

/**
 * Implements the Generic Clock device for the DCPU-16 architecture. See http://dcpu.com/clock/.
 * Note that this class does not keep real-world time; instead the elapse() function should be called every 1/60 of a second, however you choose to measure it.
 * @author Brandon Pitman
 */
public class Clock implements Device
{
	private Cpu cpu;
	private int ticks;
	private char interruptMessage;
	private int inverseRate;
	private int elapsed;
	
	@Override
	public void attach(Cpu cpu)
	{
		this.cpu = cpu;
		
		ticks = 0;
		interruptMessage = 0;
		inverseRate = 0;
		elapsed = 0;
	}

	@Override
	public int interrupt()
	{
		switch(cpu.A())
		{
		case 0:
			ticks = 0;
			elapsed = 0;
			inverseRate = cpu.B();
			break;
			
		case 1:
			cpu.C((char)ticks);
			break;
			
		case 2:
			interruptMessage = cpu.B();
			break;
		}
		
		return 0;
	}

	@Override
	public int id()
	{
		return 0x12d0b402;
	}

	@Override
	public char version()
	{
		return 1;
	}

	@Override
	public int manufacturer()
	{
		/* no manufacturer listed in spec -- we need to make one up */
		return 0x6272616e; /* "bran" */
	}
	
	/**
	 * Indicates to the clock that 1/60 of a second has elapsed.
	 */
	public void elapse()
	{
		if(inverseRate == 0)
		{
			return;
		}
		
		elapsed++;
		if(elapsed != inverseRate)
		{
			return;
		}
		
		elapsed = 0;
		ticks++;
		
		if(interruptMessage != 0)
		{
			cpu.interrupt(interruptMessage);
		}
	}
}
