package cc.bran.bdcpu16.debug;

import cc.bran.bdcpu16.Cpu;

/**
 * Represents the UI for the debugger & defines the interface between the debugger device and its UI.
 * @author Brandon Pitman
 *
 */
public interface DebuggerUI
{
	/**
	 * Initializes the debugger UI. Called when the device is initialized.
	 * @param cpu the CPU the device is attached to
	 * @param debugger the debugger device
	 * @return if true, pause immediately
	 */
	boolean init(Cpu cpu, Debugger debugger);
	
	/**
	 * The debugger device will call this method when it is pausing the CPU (because of a breakpoint, because
	 * DebuggerUI.init() returned true, or because Debugger.pause() was called). Once this method is called,
	 * the debugger will pause the CPU thread until Debugger.run() or Debugger.step() is called. 
	 */
	void paused();
}
