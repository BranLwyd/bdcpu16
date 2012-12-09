package cc.bran.bdcpu16;

import java.util.Arrays;

import cc.bran.bdcpu16.codegen.InstructionCompiler;
import cc.bran.bdcpu16.hardware.Device;

/**
 * Represents a DCPU-16 CPU. Includes functionality for CPU simulation, interrupts, attached hardware devices, CPU cycle counting.
 * 
 * Functionality is based on version 1.7 of the DCPU-16 specification, available at http://dcpu.com/.
 * 
 * @author Brandon Pitman
 */
public class Cpu
{
	/* general settings */
	private static final int MEMORY_SIZE = Character.MAX_VALUE + 1; /* memory size in words -- equals number of values a char can take on -- 0x10000 */
	private static final int MAX_SIMULTANEOUS_INTERRUPTS = 256;
	
	/* CPU state variables */
	private CpuState state;
	private char[] mem;
	private char rA, rB, rC, rX, rY, rZ, rI, rJ;
	private char pc, sp, ex, ia;
	private boolean skip;
	
	boolean interruptsEnabled;
	private char[] interruptQueue;
	private int iqHead, iqTail;
	
	final Device[] attachedDevices;
	
	/**
	 * Creates a new CPU with no attached hardware devices.
	 */
	public Cpu()
	{
		this(null);
	}
	
	 /**
	  * Creates a new CPU, attaching some hardware devices.
	  * @param attachedDevices the hardware to attach
	  */
	public Cpu(Device[] attachedDevices)
	{
		state = CpuState.RUNNING;
		mem = new char[MEMORY_SIZE];
		rA = rB = rC = rX = rY = rZ = rI = rJ = pc = sp = ex = ia = 0;
		skip = false;
		
		interruptsEnabled = true;
		interruptQueue = new char[MAX_SIMULTANEOUS_INTERRUPTS];
		iqHead = 0; iqTail = 0;
		
		if(attachedDevices != null)
		{	
			this.attachedDevices = Arrays.copyOf(attachedDevices, attachedDevices.length);
			
			for(Device dev : attachedDevices)
			{
				dev.attach(this);
			}
		}
		else
		{
			this.attachedDevices = new Device[0];
		}
	}
	
	/**
	 * Steps the CPU. Stepping consists of handling an interrupt from the queue (if any), then running a single instruction.
	 * @return the number of CPU cycles taken by the operation; 0 if there is an error condition
	 */
	public int step()
	{
		if(error())
		{
			return 0;
		}
		
		/* check interrupts */
		if(interruptsEnabled && !skip && (iqHead != iqTail))
		{
			char interruptMessage = interruptQueue[iqHead];
			iqHead = (iqHead + 1) % MAX_SIMULTANEOUS_INTERRUPTS;
			
			if(ia != 0)
			{
				interruptsEnabled = false;
				
				mem[--sp] = pc;
				mem[--sp] = rA;
				
				pc = ia;
				rA = interruptMessage;
			}
		}
		
		/* fetch/decode instruction */
		Instruction inst = InstructionCompiler.getInstruction(mem[pc]);
		if(inst.illegal())
		{
			state = CpuState.ERROR_ILLEGAL_INSTRUCTION;
			return 0;
		}
		pc += inst.wordsUsed();
		
		/* execute instruction */
		if(skip)
		{
			skip = inst.conditional();
			return 1;
		}
		
		return inst.execute(this);
	}
	
	/**
	 * Adds an interrupt to the interrupt queue. This should generally only be used by attached hardware (or by the software interrupt instruction handler).
	 * @param message the message to include in the interrupt
	 */
	public void interrupt(char message)
	{
		interruptQueue[iqTail] = message;
		iqTail = (iqTail + 1) % MAX_SIMULTANEOUS_INTERRUPTS;
		
		if(iqHead == iqTail)
		{
			/* we just overflowed the interrupt queue; per the spec we should catch fire, but this will have to do */
			state = CpuState.ERROR_INTERRUPT_QUEUE_FILLED;
		}
	}
	
	/**
	 * Gets the state of the CPU.
	 * @return the state of the CPU
	 */
	public CpuState state()
	{
		return state;
	}
	
	/**
	 * Determines if the CPU is in an error state. 
	 * @return a boolean indicating if the CPU is in an error state
	 */
	public boolean error()
	{
		return (state != CpuState.RUNNING);
	}
	
	/**
	 * Reads a single memory address.
	 * @param address the address to read
	 * @return the value in memory at the given address
	 */
	public char memory(char address)
	{
		return mem[address];
	}
	
	/**
	 * Writes a single memory address.
	 * @param address the address to write
	 * @param value the value to write to that address
	 */
	public void memory(char address, char value)
	{
		mem[address] = value;
	}
	
	/**
	 * Gets the array containing the CPU's memory.
	 * @return the array containing the CPU's memory
	 */
	public char[] memory()
	{
		return mem;
	}
	
	/**
	 * Gets the A register.
	 * @return the A register
	 */
	public char A()
	{
		return rA;
	}
	
	/**
	 * Sets the A register.
	 * @param value the value to place in the A register
	 */
	public void A(char value)
	{
		rA = value;
	}
	
	/**
	 * Gets the B register.
	 * @return the B register
	 */
	public char B()
	{
		return rB;
	}
	
	/**
	 * Sets the B register.
	 * @param value the value to place in the B register
	 */
	public void B(char value)
	{
		rB = value;
	}
	
	/**
	 * Gets the C register.
	 * @return the C register
	 */
	public char C()
	{
		return rC;
	}
	
	/**
	 * Sets the C register.
	 * @param value the value to place in the C register
	 */
	public void C(char value)
	{
		rC = value;
	}
	
	/**
	 * Gets the X register.
	 * @return the X register
	 */
	public char X()
	{
		return rX;
	}
	
	/**
	 * Sets the X register.
	 * @param value the value to place in the X register
	 */
	public void X(char value)
	{
		rX = value;
	}
	
	/**
	 * Gets the Y register.
	 * @return the Y register
	 */
	public char Y()
	{
		return rY;
	}
	
	/**
	 * Sets the Y register.
	 * @param value the value to place in the Y register
	 */
	public void Y(char value)
	{
		rY = value;
	}
	
	/**
	 * Gets the Z register.
	 * @return the Z register
	 */
	public char Z()
	{
		return rZ;
	}
	
	/**
	 * Sets the Z register.
	 * @param value the value to place in the Z register
	 */
	public void Z(char value)
	{
		rZ = value;
	}
	
	/**
	 * Gets the I register.
	 * @return the I register
	 */
	public char I()
	{
		return rI;
	}
	
	/**
	 * Sets the I register.
	 * @param value the value to place in the I register
	 */
	public void I(char value)
	{
		rI = value;
	}
	
	/**
	 * Gets the J register.
	 * @return the J register
	 */
	public char J()
	{
		return rJ;
	}
	
	/**
	 * Sets the J register.
	 * @param value the value to place in the J register
	 */
	public void J(char value)
	{
		rJ = value;
	}
	
	/**
	 * Gets the PC register.
	 * @return the PC register
	 */
	public char PC()
	{
		return pc;
	}
	
	/**
	 * Sets the PC register.
	 * @param value the value to place in the PC register
	 */
	public void PC(char value)
	{
		pc = value;
	}
	
	/**
	 * Gets the SP register.
	 * @return the SP register
	 */
	public char SP()
	{
		return sp;
	}
	
	/**
	 * Sets the SP register.
	 * @param value the value to place in the SP register
	 */
	public void SP(char value)
	{
		sp = value;
	}
	
	/**
	 * Gets the EX register.
	 * @return the EX register
	 */
	public char EX()
	{
		return ex;
	}
	
	/**
	 * Sets the EX register.
	 * @param value the value to place in the EX register
	 */
	public void EX(char value)
	{
		ex = value;
	}
	
	/**
	 * Gets the IA register.
	 * @return the IA register
	 */
	public char IA()
	{
		return ia;
	}
	
	/**
	 * Sets the IA register.
	 * @param value the value to place in the IA register
	 */
	public void IA(char value)
	{
		ia = value;
	}
	
	/**
	 * Gets the skip flag.
	 * @return the skip flag
	 */
	public boolean skip()
	{
		return skip;
	}
	
	/**
	 * Sets the skip flag.
	 * @param value the new value for the skip flag
	 */
	public void skip(boolean value)
	{
		skip = value;
	}
	
	/**
	 * Gets whether interrupts are enabled or not.
	 * @return true if and only if interrupts are enabled
	 */
	public boolean interruptsEnabled()
	{
		return interruptsEnabled;
	}
	
	/**
	 * Sets whether interrupts are enabled or not.
	 * @param value whether to enable interrupts or not
	 */
	public void interruptsEnabled(boolean value)
	{
		interruptsEnabled = value;
	}
	
	/**
	 * Gets the number of hardware devices attached to the CPU.
	 * @return the number of attached hardware devices
	 */
	public int attachedDeviceCount()
	{
		return attachedDevices.length;
	}
	
	/**
	 * Gets a hardware device, given its index.
	 * @param index the index of the device
	 * @return the device at the given index
	 */
	public Device attachedDevice(int index)
	{
		return attachedDevices[index];
	}
	
	/**
	 * Represents the states that the CPU can be in.
	 * @author Brandon Pitman
	 */
	public enum CpuState
	{
		/**
		 * Normal state. No errors.
		 */
		RUNNING,
		
		/**
		 * The CPU has crashed because it tried to execute an illegal instruction.
		 */
		ERROR_ILLEGAL_INSTRUCTION,
		
		/**
		 * The CPU has crashed because it received too many interrupts.
		 */
		ERROR_INTERRUPT_QUEUE_FILLED,
	}
}
