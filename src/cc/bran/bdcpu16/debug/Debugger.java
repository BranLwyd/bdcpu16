package cc.bran.bdcpu16.debug;

import java.util.BitSet;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import cc.bran.bdcpu16.Cpu;
import cc.bran.bdcpu16.hardware.Device;

/**
 * This class implements a DCPU-16 hardware device that acts as a debugger: it has the capability to pause
 * the CPU at when certain breakpoints are hit (or at other times controlled by the UI). It depends on a
 * pluggable UI to allow the user to interact with the debugger. 
 * @author Brandon Pitman
 */
public class Debugger implements Device
{
	private Cpu cpu;
	private DebuggerUI ui;
	
	private BitSet breakpoints;
	
	private Lock waitLock;
	private Condition waitCondition;
	private volatile boolean waiting;
	private volatile boolean breaking;

	/**
	 * Creates a new Debugger device.
	 * @param ui the UI to attach to this device
	 */
	public Debugger(DebuggerUI ui)
	{
		this.ui = ui;
		
		breakpoints = new BitSet(Cpu.MAX_ADDRESS);
		
		waitLock = new ReentrantLock();
		waitCondition = waitLock.newCondition();
		waiting = false;
		breaking = false;
	}
	
	@Override
	public void attach(Cpu cpu)
	{
		this.cpu = cpu;
	}

	@Override
	public int interrupt()
	{
		/* treat as software breakpoint */
		pause();
		
		return 0;
	}

	@Override
	public void step(int cycleCount)
	{
		if(breakpoint(cpu.PC()))
		{
			breaking = true;
		}
		
		if(breaking)
		{
			beginPause();
		}
	}

	@Override
	public int id()
	{
		return 0x769336d9;
	}

	@Override
	public char version()
	{
		return 1;
	}

	@Override
	public int manufacturer()
	{
		return 0x6272616e; /* "bran" */
	}
	
	/**
	 * Initializes the debugger. This method should be called after all of the hardware devices are attached & CPU memory
	 * is initialized, but before the first call to Cpu.step().
	 */
	public void init()
	{
		if(ui.init(cpu, this))
		{
			beginPause();
		}
	}
	
	/**
	 * Causes the debugger to pause (break) as soon as possible.
	 */
	void pause()
	{
		breaking = true;
	}
	
	/**
	 * This method should only be called by the debugger UI, and only once DebuggerUI.paused() has been called.
	 * This method continues execution for a single step before breaking.
	 */
	void step()
	{
		breaking = true;
		endPause();
	}
	
	/**
	 * This method should only be called by the debugger UI, and only once DebuggerUI.paused() has been called.
	 * This method continues execution until a breakpoint is hit or pause() is called.
	 */
	void run()
	{
		breaking = false;
		endPause();
	}
	
	/**
	 * Determines if a breakpoint is set at a given address.
	 * @param address the address to check
	 * @return true if and only if a breakpoint is set at the address
	 */
	public boolean breakpoint(char address)
	{
		synchronized(breakpoints)
		{
			return breakpoints.get(address);
		} 
	}
	
	/**
	 * Sets or clears a breakpoint at a given address.
	 * @param address the address to check
	 * @param set if true, set the breakpoint; if false, clear the breakpoint
	 */
	public void breakpoint(char address, boolean set)
	{
		synchronized(breakpoints)
		{
			breakpoints.set(address, set);
		}
	}
	
	/**
	 * Clears all breakpoints.
	 */
	public void clearAllBreakpoints()
	{
		synchronized(breakpoints)
		{
			breakpoints.clear();
		}
	}
	
	/**
	 * Begins pausing. This method notifies the UI that a pause has begun, then goes to sleep
	 * until awoken by the UI calling run() or step().
	 */
	private void beginPause()
	{
		waitLock.lock();
		try
		{
			waiting = true;
			ui.paused();
			
			while(waiting)
			{
				try
				{
					waitCondition.await();
				}
				catch(InterruptedException ex) { /* ignore */ }
			}
		}
		finally
		{
			waitLock.unlock();
		}
	}
	
	/**
	 * Ends a pause. This is a helper function to run() and step().
	 */
	private void endPause()
	{
		waitLock.lock();
		try
		{
			waiting = false;
			waitCondition.signal();
		}
		finally
		{
			waitLock.unlock();
		}
	}
}
