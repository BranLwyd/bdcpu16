package cc.bran.bdcpu16;

import java.util.Collection;

import cc.bran.bdcpu16.hardware.Device;

/**
 * Represents a DCPU-16 CPU. Includes functionality for CPU simulation, interrupts, attached hardware devices, CPU cycle counting.
 * 
 * This is based on version 1.7 of the DCPU-16 specification, available at http://dcpu.com/.
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
	char[] mem;
	char rA, rB, rC, rX, rY, rZ, rI, rJ;
	char pc, sp, ex, ia;
	boolean skip;
	
	boolean interruptsEnabled;
	private char[] interruptQueue;
	private int iqHead, iqTail;
	
	final Device[] attachedHardware;
	
	private Instruction[] instructionCache;
	private Operand[] operandCache;
	
	/**
	 * Creates a new CPU with no attached hardware devices.
	 */
	public Cpu()
	{
		this(null);
	}
	
	 /**
	  * Creates a new CPU, attaching some hardware devices.
	  * @param attachedHardware the hardware to attach
	  */
	public Cpu(Collection<Device> attachedHardware)
	{
		state = CpuState.RUNNING;
		mem = new char[MEMORY_SIZE];
		rA = rB = rC = rX = rY = rZ = rI = rJ = pc = sp = ex = ia = 0;
		skip = false;
		
		interruptsEnabled = true;
		interruptQueue = new char[MAX_SIMULTANEOUS_INTERRUPTS];
		iqHead = 0; iqTail = 0;
		
		instructionCache = new Instruction[MEMORY_SIZE];
		operandCache = new Operand[Operand.OPERAND_COUNT];
		
		if(attachedHardware != null)
		{
			this.attachedHardware = new Device[attachedHardware.size()];
			int i = 0;
			
			for(Device dev : attachedHardware)
			{
				this.attachedHardware[i++] = dev;
				dev.attach(this);
			}
		}
		else
		{
			this.attachedHardware = new Device[0];
		}
	}
	
	/**
	 * Reads a single memory address.
	 * @param address the address to read
	 * @return the value in memory at the given address
	 */
	public char readMemory(char address)
	{
		return mem[address];
	}
	
	/**
	 * Writes a single memory address.
	 * @param address the address to write
	 * @param value the value to write to that address
	 */
	public void writeMemory(char address, char value)
	{
		mem[address] = value;
	}
	
	public char[] getMemory()
	{
		return mem;
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
		
		if(interruptsEnabled && iqHead != iqTail)
		{
			/* handle interrupt */
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
		
		/* execute instruction */
		Instruction inst = getInstructionForAddress(pc++);
		if(inst.illegal())
		{
			state = CpuState.ERROR_ILLEGAL_INSTRUCTION;
			return 0;
		}
		return inst.execute();
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
	 * Gets the instruction for a given address.
	 * @param address the address to get the instruction for
	 * @return the instruction decoded from that address
	 */
	Instruction getInstructionForAddress(char address)
	{
		if(instructionCache[address] == null)
		{
			instructionCache[address] = new Instruction(this, mem[address]);
		}
		
		return instructionCache[address];
	}
	
	/**
	 * Gets the operand for a given operand value. See the Operand class.
	 * @param operandValue the value of the operand
	 * @return the operand for that value
	 */
	Operand getOperandForValue(char operandValue)
	{
		if(operandCache[operandValue] == null)
		{
			operandCache[operandValue] = new Operand(this, operandValue);
		}
		
		return operandCache[operandValue];
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
