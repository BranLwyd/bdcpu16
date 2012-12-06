package cc.bran.bdcpu16.hardware;

/**
 * Implements a cycle-counting clock, which takes a given clockspeed for the CPU, then ticks at a rate
 * corresponding to the number of cycles that have passed. This is useful if your CPU needs a clock but
 * isn't attached to any other concept of time.
 * @author Brandon Pitman
 */
public class CycleClock extends Clock
{
	/* basic rate of the clock in Hz */
	private static final int BASIC_RATE = 60;
	
	final private int baseCycles;
	final private int modCycles;
	private int cyclesElapsed;
	private int cyclesNeeded;
	private int elapseCount;
	
	/**
	 * Creates a new CycleClock.
	 * @param dcpuClockspeed the CPU's clockspeed in Hz
	 */
	public CycleClock(int dcpuClockspeed)
	{
		baseCycles = dcpuClockspeed / BASIC_RATE;
		modCycles = dcpuClockspeed % BASIC_RATE;
		
		cyclesElapsed = 0;
		elapseCount = 0;
		
		cyclesNeeded = baseCycles + (0 < modCycles ? 1 : 0);
	}
	
	/**
	 * Notifies the clock that some cycles have elapsed. If more than 1/60 of a second of clock cycles have passed,
	 * calls elapse() which may then tick the clock depending on the settings from the DCPU-16.
	 * @param cycleCount the number of cycles that have elapsed since the last call to elapseCycles
	 */
	public void elapseCycles(int cycleCount)
	{
		cyclesElapsed += cycleCount;
		if(cyclesElapsed < cyclesNeeded)
		{
			return;
		}
		
		cyclesElapsed = 0;
		elapseCount = (elapseCount + 1) % BASIC_RATE;
		
		/*
		 * Compute the number of cycles we require before the next elapse. This isn't just (clockspeed / 60) due to
		 * integer division rounding--we would fire slightly too quickly unless (clockspeed % 60 == 0). Instead,
		 * for every sixty elapses, we require (1 + clockspeed / 60) cycles for the first (clockspeed % 60)
		 * elapses and (clockspeed / 60) for the remaining elapses. This gives a total of 60 * (clockspeed / 60)
		 * + (clockspeed % 60) = clockspeed cycles to get 60 elapses, which is what we want.
		 * 
		 * All this assumes BASIC_RATE stays at 60.
		 */
		cyclesNeeded = baseCycles + (elapseCount < modCycles ? 1 : 0);
		
		elapse();
	}
}
