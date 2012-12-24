package cc.bran.bdcpu16.hardware;

import cc.bran.bdcpu16.Cpu;

/**
 * This class represents a hardware device that can be attached to the DCPU-16.
 * @author Brandon Pitman
 */
public interface Device
{
	/**
	 * Notifies the device that it is attached to a CPU. Should generally only be called by the Cpu class.
	 * @param cpu the cpu this device is attached to
	 */
	public void attach(Cpu cpu);
	
	/**
	 * Sends an interrupt to the device. Should generally only be called by the Instruction class.
	 * @return the number of extra cycles taken for the interrupt (above those used by the HWI instruction)
	 */
	public int interrupt();
	
	/**
	 * The CPU calls this method if the device requests to be woken up using Cpu.scheduleWake().
	 * Note that the number of cycles that passed may not exactly match the number requested.
	 * @param cycles the number of cycles that passed since the call to Cpu.scheduleWake()
	 * @param context the context that was passed into Cpu.scheduleWake()
	 */
	public void wake(int cycles, int context);
	
	/**
	 * Determines the ID of the hardware device. This should be a constant value for a given device.
	 * @return the ID of the hardware device
	 */
	public int id();
	
	/**
	 * Determines the version of the hardware device. This should be a constant value for a given device.
	 * @return the version of the hardware device
	 */
	public char version();
	
	/**
	 * Determines the manufacturer of the hardware device. This should be a constant value for a given device.
	 * @return the manufacturer of the hardware device
	 */
	public int manufacturer();
}
