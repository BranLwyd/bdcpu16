package cc.bran.bdcpu16;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

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
	public static final int MAX_ADDRESS = Character.MAX_VALUE + 1; /* memory size in words -- equals number of values a char can take on -- 0x10000 */
	private static final int MAX_SIMULTANEOUS_INTERRUPTS = 256;
	private static final int DEFAULT_CLOCKSPEED = 100000;
	private static final InstructionProvider DEFAULT_INSTRUCTION_PROVIDER = new InstructionCompiler();
	
	/* CPU state variables */
	private CpuState state;
	private char[] mem;
	private char rA, rB, rC, rX, rY, rZ, rI, rJ;
	private char pc, sp, ex, ia;
	private boolean skip;
	
	private boolean interruptsEnabled;
	private InterruptQueueNode queueHead, queueTail;
	
	private final int clockSpeed;
	private final Device[] attachedDevices;
	
	private final InstructionProvider instProvider;
	
	/**
	 * Creates a new CPU with no attached hardware devices at the default clock speed. The default instruction provider is used.
	 */
	public Cpu()
	{
		this(DEFAULT_CLOCKSPEED, null, DEFAULT_INSTRUCTION_PROVIDER);
	}
	
	/**
	 * Creates a new CPU with no attached hardware devices at a given clock speed. The default instruction provider is used.
	 * @param clockSpeed the clock speed of the CPU, in Hz
	 */
	public Cpu(int clockSpeed)
	{
		this(clockSpeed, null, DEFAULT_INSTRUCTION_PROVIDER);
	}
	
	/**
	 * Creates a new CPU at the default clock speed with some attached hardware devices. The default instruction provider is used.
	 * @param attachedDevices the devices to attach to the CPU
	 */
	public Cpu(Device[] attachedDevices)
	{
		this(DEFAULT_CLOCKSPEED, attachedDevices, DEFAULT_INSTRUCTION_PROVIDER);
	}
	
	/**
	 * Creates a new CPU with no attached hardware devices at the default clock speed. A specified instruction provider is used.
	 * @param instProvider the instruction provider to use
	 */
	public Cpu(InstructionProvider instProvider)
	{
		this(DEFAULT_CLOCKSPEED, null, instProvider);
	}
	
	/**
	 * Creates a new CPU with a specified clock speed, attaching some hardware devices. The default instruction provider is used.
	 * @param clockSpeed the clock speed of the CPU, in Hz
	 * @param attachedDevices the devices to attach to the CPU
	 */
	public Cpu(int clockSpeed, Device[] attachedDevices)
	{
		this(clockSpeed, attachedDevices, DEFAULT_INSTRUCTION_PROVIDER);
	}
	
	/**
	 * Creates a new CPU at the default clock speed with some attached hardware devices. A specified instruction provider is used.
	 * @param attachedDevices the devices to attach to the CPU
	 * @param instProvider the instruction provider to use
	 */
	public Cpu(Device[] attachedDevices, InstructionProvider instProvider)
	{
		this(DEFAULT_CLOCKSPEED, attachedDevices, instProvider);
	}
	
	/**
	 * Creates a new CPU with no attached hardware devices at a given clock speed. A specified instruction provider is used.
	 * @param clockSpeed the clock speed of the CPU, in Hz
	 * @param instProvider the instruction provider to use
	 */
	public Cpu(int clockSpeed, InstructionProvider instProvider)
	{
		this(clockSpeed, null, instProvider);
	}
	
	 /**
	  * Creates a new CPU with a specified clock speed, attaching some hardware devices. A specified instruction provider is used.
	  * @param clockSpeed the clock speed of the CPU, in Hz
	  * @param attachedDevices the devices to attach to the CPU
	  */
	public Cpu(int clockSpeed, Device[] attachedDevices, InstructionProvider instProvider)
	{
		state = CpuState.RUNNING;
		mem = new char[MAX_ADDRESS];
		rA = rB = rC = rX = rY = rZ = rI = rJ = pc = sp = ex = ia = 0;
		skip = false;
		
		/* generate interrupt queue -- circular buffer of linked nodes */
		interruptsEnabled = true;
		InterruptQueueNode[] nodes = new InterruptQueueNode[MAX_SIMULTANEOUS_INTERRUPTS + 1];
		nodes[0] = new InterruptQueueNode();
		for(int i = 1; i < nodes.length; ++i)
		{
			nodes[i] = new InterruptQueueNode();
			nodes[i].next = nodes[i - 1];
		}
		nodes[0].next = nodes[nodes.length - 1];
		
		this.clockSpeed = clockSpeed;
		
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
		
		this.instProvider = instProvider;
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
		if(interruptsEnabled && !skip && queueHead != queueTail)
		{
			final char interruptMessage = queueHead.message;
			queueHead = queueHead.next;
				
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
		final Instruction inst = instProvider.getInstruction(mem[pc]);
		if(inst.illegal())
		{
			state = CpuState.ERROR_ILLEGAL_INSTRUCTION;
			return 0;
		}
		pc += inst.wordsUsed();
		
		/* execute instruction */
		final int cyclesElapsed;
		if(skip)
		{
			skip = inst.conditional();
			cyclesElapsed = 1;
		}
		else
		{
			cyclesElapsed = inst.execute(this);
		}
		
		/* notify hardware that some cycles have elapsed */
		for(final Device dev : attachedDevices)
		{
			dev.cyclesElapsed(cyclesElapsed);
		}
		
		return cyclesElapsed;
	}
	
	/**
	 * Adds an interrupt to the interrupt queue. This should generally only be used by attached hardware (or by the software interrupt instruction handler).
	 * @param message the message to include in the interrupt
	 */
	public void interrupt(char message)
	{
		if(queueTail.next == queueHead)
		{
			/* we just overflowed the interrupt queue; per the spec we should catch fire, but this will have to do */
			state = CpuState.ERROR_INTERRUPT_QUEUE_FILLED;
			return;
		}
		
		queueTail.message = message;
		queueTail = queueTail.next;
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
	 * Gets a specified register. 
	 * @param register the register to get
	 * @return the value in the register
	 */
	public char register(Register register)
	{
		switch(register)
		{
		case  A: return rA;
		case  B: return rB;
		case  C: return rC;
		case  X: return rX;
		case  Y: return rY;
		case  Z: return rZ;
		case  I: return rI;
		case  J: return rJ;
		case PC: return pc;
		case SP: return sp;
		case EX: return ex;
		case IA: return ia;
		default: return 0; /* can't happen */
		}
	}
	
	/**
	 * Sets a specified register.
	 * @param register the register to store to
	 * @param value the value to place in the register
	 */
	public void register(Register register, char value)
	{
		switch(register)
		{
		case  A: rA = value; break;
		case  B: rB = value; break;
		case  C: rC = value; break;
		case  X: rX = value; break;
		case  Y: rY = value; break;
		case  Z: rZ = value; break;
		case  I: rI = value; break;
		case  J: rJ = value; break;
		case PC: pc = value; break;
		case SP: sp = value; break;
		case EX: ex = value; break;
		case IA: ia = value; break;
		}
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
	 * Gets the clock speed of this CPU in Hz. This has no bearing on the number of cycles per second in
	 * reality, but is used by some hardware (such as the clock device) to drive some functionality. It
	 * can also be used in a system that is attempting real time behavior to decide how many times to
	 * call step() per second.
	 * @return the clock speed of the CPU
	 */
	public int clockSpeed()
	{
		return clockSpeed;
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
	
	/**
	 * Represents a registers in the CPU.
	 * @author Brandon Pitman
	 */
	public enum Register
	{
		A, B, C, X, Y, Z, I, J, PC, SP, EX, IA
	}
	
	private class InterruptQueueNode
	{
		public char message;
		public InterruptQueueNode next;
	}
}
