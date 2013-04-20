package cc.bran.bdcpu16.hardware;

import cc.bran.bdcpu16.Cpu;

/**
 * Implements the Generic Clock device for the DCPU-16 architecture. See http://dcpu.com/clock/.
 * Note that this class does not keep real-world time; instead the clock rate is pegged to the number
 * of CPU cycles that have elapsed and the CPU's virtual clock rate.
 * @author Brandon Pitman
 */
public class Clock implements Device
{
	/* the "base" number of times per second this clock will tick */
	private static final int BASIC_RATE = 60;
	
	private Cpu cpu;
	private char interruptMessage;
	
	private int curContext;
	private int ticks;
	private int waitCycles;
	
	public Clock()
	{
		interruptMessage = 0;
		curContext = 0;
		ticks = 0;
		waitCycles = 0;
	}
	
	@Override
	public void attach(Cpu cpu)
	{
		this.cpu = cpu;
	}

	@Override
	public int interrupt()
	{
		switch(cpu.A())
		{
		case 0: /* set rate */
			curContext++;
			ticks = 0;
			waitCycles = cpu.clockSpeed() * cpu.B() / BASIC_RATE;
			if(waitCycles != 0)
			{
				cpu.scheduleWake(this, waitCycles, curContext);
			}
			break;
			
		case 1: /* get tick count */
			cpu.C((char)ticks);
			break;
			
		case 2: /* set interrupt message */
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
	
	@Override
	public void wake(int cycles, int context)
	{
		if(context != curContext)
		{
			/* this wake is for a previous rate */
			return;
		}
		
		ticks++;
		if(interruptMessage != 0)
		{
			cpu.interrupt(interruptMessage);
		}

		cpu.scheduleWake(this, waitCycles, curContext);
	}
}
