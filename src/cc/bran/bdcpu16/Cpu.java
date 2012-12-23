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
	public static final int MAX_ADDRESS = Character.MAX_VALUE + 1; /* memory size in words -- equals number of values a char can take on -- 0x10000 */
	private static final int MAX_SIMULTANEOUS_INTERRUPTS = 256;
	private static final int DEFAULT_CLOCKSPEED = 100000;
	private static final InstructionProvider DEFAULT_INSTRUCTION_PROVIDER = new InstructionCompiler();
	
	/* CPU state variables */
	private State state;
	private char[] mem;
	private char rA, rB, rC, rX, rY, rZ, rI, rJ;
	private char pc, sp, ex, ia;
	private boolean skip;
	
	private boolean interruptsEnabled;
	private Node<Character> intHead, intTail;
	
	private final int clockSpeed;
	private final Node<Device> devHead;
	private final Device[] attachedDevices;
	
	private StepHandler specialHandler;
	private final StepHandler interruptHandler;
	private final StepHandler skipHandler;
	private final StepHandler errorHandler;
	
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
		/* basic state */
		state = State.RUNNING;
		mem = new char[MAX_ADDRESS];
		rA = rB = rC = rX = rY = rZ = rI = rJ = pc = sp = ex = ia = 0;
		skip = false;
		
		/* generate special instruction handlers */
		interruptHandler = new InterruptStepHandler();
		skipHandler = new SkipStepHandler();
		errorHandler = new ErrorStepHandler();
		specialHandler = null;
		
		/* generate interrupt queue -- circular buffer of (MAX_SIMULTANEOUS_INTERRUPTS + 1) nodes -- extra is used as "queue full" notifier */
		interruptsEnabled = true;
		Node<Character> curIntNode = new Node<Character>();
		intHead = intTail = curIntNode;
		for(int i = 0; i < MAX_SIMULTANEOUS_INTERRUPTS; ++i)
		{
			final Node<Character> newNode = new Node<Character>();
			newNode.next = curIntNode;
			curIntNode = newNode;
		}
		intHead.next = curIntNode;
		
		/* handle parameters */
		this.clockSpeed = clockSpeed;
		this.instProvider = instProvider;
		
		/* set up devHead linked list */
		if(attachedDevices != null && attachedDevices.length > 0)
		{
			this.attachedDevices = Arrays.copyOf(attachedDevices, attachedDevices.length);
			
			attachedDevices[0].attach(this);
			Node<Device> curDevNode = new Node<Device>();
			curDevNode.value = attachedDevices[0];
			
			for(int i = 1; i < attachedDevices.length; ++i)
			{
				attachedDevices[i].attach(this);
				
				Node<Device> newDevNode = new Node<Device>();
				newDevNode.value = attachedDevices[i];
				newDevNode.next = curDevNode;
				
				curDevNode = newDevNode;
			}
			devHead = curDevNode;

			for(Device dev : attachedDevices)
			{
				dev.attach(this);
			}
		}
		else
		{
			this.attachedDevices = new Device[0];
			devHead = null;
		}
	}
	
	/**
	 * Steps the CPU. Stepping consists of handling an interrupt from the queue (if any), then running a single instruction.
	 * @return the number of CPU cycles taken by the operation; 0 if there is an error condition
	 */
	public int step()
	{
		int cyclesUsed = -1;
		
		/* step CPU */
		if(specialHandler != null)
		{
			cyclesUsed = specialHandler.step();
		}
		
		/* standard fetch/decode/execute handler: used if no special handler, or special handler returned -1 */
		if(cyclesUsed == -1)
		{
			final Instruction inst = instProvider.getInstruction(mem[pc]);
			pc += inst.wordsUsed();
			cyclesUsed = inst.execute(this);
		}
		
		/* step devices */
		Node<Device> node = devHead;
		while(node != null)
		{
			node.value.step(cyclesUsed);
			node = node.next;
		}
		
		return cyclesUsed;
	}
	
	/**
	 * Adds an interrupt to the interrupt queue. This should generally only be used by attached hardware (or by the software interrupt instruction handler).
	 * @param message the message to include in the interrupt
	 */
	public void interrupt(char message)
	{
		if(intTail.next == intHead)
		{
			/* we just overflowed the interrupt queue; per the spec we should catch fire, but this will have to do */
			state = State.ERROR_INTERRUPT_QUEUE_FILLED;
			specialHandler = errorHandler;
			return;
		}
		
		intTail.value = message;
		intTail = intTail.next;
		
		if(interruptsEnabled && specialHandler == null)
		{
			specialHandler = interruptHandler;
		}
	}
	
	/**
	 * Gets the state of the CPU.
	 * @return the state of the CPU
	 */
	public State state()
	{
		return state;
	}
	
	/**
	 * Determines if the CPU is in an error state. 
	 * @return a boolean indicating if the CPU is in an error state
	 */
	public boolean error()
	{
		return (state != State.RUNNING);
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
		
		if(skip && specialHandler != errorHandler)
		{
			skip = true;
			specialHandler = skipHandler; 
		}
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
		
		if(interruptsEnabled && specialHandler == null && intHead != intTail)
		{
			specialHandler = interruptHandler;
		}
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
	 * This method is called by IllegalInstruction.execute() to notify the CPU that it just tried to
	 * execute an illegal instruction. This method just puts the CPU in an error state.
	 */
	void illegalInstructionExecuted()
	{
		state = State.ERROR_ILLEGAL_INSTRUCTION;
		specialHandler = errorHandler;
	}
	
	/**
	 * Represents the states that the CPU can be in.
	 * @author Brandon Pitman
	 */
	public enum State
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
	
	/**
	 * This class is a node for an extremely simple singly-linked list.
	 * @author Brandon Pitman
	 * @param <T> the value type of the linked list
	 */
	private class Node<T>
	{
		public T value;
		public Node<T> next;
	}
	
	/**
	 * This interface is implemented by classes that provide "special" CPU-stepping
	 * logic off the fast path of the standard fetch/decode/execute cycle.
	 * @author Brandon Pitman
	 */
	private interface StepHandler
	{
		public int step();
	}
	
	/**
	 * This special step handler is used when there is an interrupt available to
	 * be handled, and the CPU is ready to handle it.
	 * @author Brandon Pitman
	 */
	private class InterruptStepHandler implements StepHandler
	{
		@Override
		public int step()
		{
			final char interruptMessage = intHead.value;
			intHead = intHead.next;
			
			if(ia != 0)
			{
				interruptsEnabled = false;
				
				mem[--sp] = pc;
				mem[--sp] = rA;
				
				pc = ia;
				rA = interruptMessage;
				specialHandler = null;
				return 0;
			}
			
			/*
			 * if we're here, we don't have an interrupt handler installed (IA = 0) so interrupts will remain enabled,
			 * meaning that as long as the interrupt queue is not empty we alternate interrupt deque -> instruction
			 * execution. since I don't want to put logic in the standard handler to jump to the InterruptStepHandler,
			 * let's just simulate alternation by additionally running the standard handler (triggered by returning -1).
			 * 
			 * I expect it's pretty rare to be receiving interrupts without an interrupt handler set.
			 */
			specialHandler = (intHead == intTail ? null : interruptHandler);
			return -1;
		}
	}
	
	/**
	 * This special step handler is used when the CPU is in "skip mode" from a conditional (IF*) instruction.
	 * @author Brandon Pitman
	 */
	private class SkipStepHandler implements StepHandler
	{
		@Override
		public int step()
		{
			final Instruction inst = instProvider.getInstruction(mem[pc]);
			
			if(inst.illegal())
			{
				state = State.ERROR_ILLEGAL_INSTRUCTION;
				specialHandler = errorHandler;
				return 0;
			}
			
			if(!inst.conditional())
			{
				skip = false;
				
				if(!interruptsEnabled || intHead == intTail)
				{
					specialHandler = null;
				}
				else
				{
					specialHandler = interruptHandler;
				}
			}
			
			pc += inst.wordsUsed();
			
			return 1;
		}
	}
	
	/**
	 * This special step handler is used when the CPU is in an error state. It doesn't do anything.
	 * @author Brandon Pitman
	 */
	private class ErrorStepHandler implements StepHandler
	{
		@Override
		public int step()
		{
			/* don't do anything */
			return 0;
		}
	}
}
