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
	 * Determines the ID of the hardware device. This should be a constant value for a given device.
	 * @return the ID of the hardware device
	 */
	public int id();
	
	/**
	 * Determines the version of the hardware device. This should be a constant value for a given device.
	 * @return the version of the hardware device
	 */
	public short version();
	
	/**
	 * Determines the manufacturer of the hardware device. This should be a constant value for a given device.
	 * @return the manufacturer of the hardware device
	 */
	public int manufacturer();
}
