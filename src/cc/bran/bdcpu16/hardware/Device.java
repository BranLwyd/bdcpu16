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
	 * Notifies the hardware that the CPU has been stepped, to allow updates to processes that are happening
	 * "simultaneously" with the operation of the CPU. Typically the cycle count will be a small number (<10)
	 * but this should not be depended upon. (An HWI instruction can take an arbitrary number of cycles, for
	 * example.) The cycle count could also be zero, in the case of an error condition or a handled interrupt. 
	 * @param cycleCount the number of cycles that have passed
	 */
	public void step(int cycleCount);
	
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
