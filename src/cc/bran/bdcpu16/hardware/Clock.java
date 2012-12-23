package cc.bran.bdcpu16.hardware;

import cc.bran.bdcpu16.Cpu;

/**
 * Implements the Generic Clock device for the DCPU-16 architecture. See http://dcpu.com/clock/.
 * Note that this class does not keep real-world time; instead the elapse() function should be called every 1/60 of a second, however you choose to measure it.
 * @author Brandon Pitman
 */
public class Clock implements Device
{
	/* the "base" number of times per second this clock will tick */
	private static final int BASIC_RATE = 60;
	
	private Cpu cpu;
	private char interruptMessage;
	
	private int denom;
	private int cycles;
	private int ticks;
	private int totalTicks;
	
	@Override
	public void attach(Cpu cpu)
	{
		this.cpu = cpu;
		
		interruptMessage = 0;
		denom = 0;
		cycles = 0;
		ticks = 0;
		totalTicks = 0;
	}

	@Override
	public int interrupt()
	{
		switch(cpu.A())
		{
		case 0:
			denom = cpu.B() * cpu.clockSpeed();
			cycles = 0;
			ticks = 0;
			totalTicks = 0;
			break;
			
		case 1:
			cpu.C((char)totalTicks);
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
	
	@Override
	public void step(int cycleCount)
	{
		/* 
		 * the basic logic used is: for any given number of total cycles, we should see (BASIC_RATE * cycles) / (clockspeed * B) ticks.
		 * compute the number of ticks we should have seen, and if it's greater than the number of ticks we've actually seen,
		 * increment the number of ticks appropriately. also, fix up cycles so that it doesn't get too big.
		 */
		
		if(denom == 0)
		{
			return;
		}
		
		cycles += cycleCount;
		int newTicks = (BASIC_RATE * cycles) / denom - ticks;
		ticks += newTicks;
		totalTicks += newTicks;
		
		if(cycles > denom)
		{
			/* 
			 * every denom cycles, we have had exactly BASIC_RATE * denom ticks, so these quantities are safe to remove together
			 */
			cycles -= denom;
			ticks -= BASIC_RATE * denom;
		}
		
		if(interruptMessage != 0)
		{
			while(newTicks-- > 0)
			{
				cpu.interrupt(interruptMessage);
			}
		}
	}
}
