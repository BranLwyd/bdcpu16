package cc.bran.bdcpu16;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
	private static final int MEMORY_SIZE = ((short)-1 & 0xffff) + 1; /* memory size in words -- equals number of values a short can take on -- 0x10000 */
	private static final int MAX_SIMULTANEOUS_INTERRUPTS = 256;
	
	/* CPU state variables */
	private CpuState state;
	private short[] mem;
	short rA, rB, rC, rX, rY, rZ, rI, rJ;
	short pc, sp, ex, ia;
	
	boolean interruptsEnabled;
	private short[] interruptQueue;
	private int iqHead, iqTail;
	
	final Device[] attachedHardware;
	
	private MemoryMapHandler[] memoryReadHandlers;
	private MemoryMapHandler[] memoryWriteHandlers;
	private Map<MemoryMapHandler, AddressInterval> memoryHandlerMap;
	
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
		mem = new short[MEMORY_SIZE];
		rA = rB = rC = rX = rY = rZ = rI = rJ = pc = sp = ex = ia = 0;
		
		interruptsEnabled = true;
		interruptQueue = new short[MAX_SIMULTANEOUS_INTERRUPTS];
		iqHead = 0; iqTail = 0;
		
		instructionCache = new Instruction[MEMORY_SIZE];
		operandCache = new Operand[Operand.OPERAND_COUNT];
		
		memoryReadHandlers = new MemoryMapHandler[MEMORY_SIZE];
		memoryWriteHandlers = new MemoryMapHandler[MEMORY_SIZE];
		memoryHandlerMap = new HashMap<MemoryMapHandler, AddressInterval>();
		
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
	 * @param direct if set, ignore any memory mapping and return what is actually in the CPU's memory (this should generally only be used by memory map handlers)
	 * @return the value in memory at the given address
	 */
	public short readMemory(short address, boolean direct)
	{
		final int adjustedAddress = address & 0xffff;
		
		if(direct || memoryReadHandlers[adjustedAddress] == null)
		{
			return mem[adjustedAddress];
		}
		else
		{
			return memoryReadHandlers[adjustedAddress].memoryRead(address);
		}
	}
	
	/**
	 * Reads a single memory address.
	 * @param address the address to read
	 * @return the value in memory at the given address
	 */
	public short readMemory(short address)
	{
		return readMemory(address, false);
	}
	
	/**
	 * Writes a single memory address.
	 * @param address the address to write
	 * @param value the value to write to that address
	 * @param direct if set, ignore any memory mapping and write directly to the CPU's memory (this should generally only be used by memory map handlers)
	 */
	public void writeMemory(short address, short value, boolean direct)
	{
		final int adjustedAddress = address & 0xffff;
		
		if(direct || memoryWriteHandlers[adjustedAddress] == null)
		{
			mem[adjustedAddress] = value;
			instructionCache[adjustedAddress] = null;
		}
		else
		{
			memoryWriteHandlers[adjustedAddress].memoryWritten(address, value);
		}
	}
	
	/**
	 * Writes a single memory address.
	 * @param address the address to write
	 * @param value the value to write to that address
	 */
	public void writeMemory(short address, short value)
	{
		writeMemory(address, value, false);
	}
	
	/**
	 * Writes an array of values into memory.
	 * @param startAddress the address to start writing
	 * @param values the array of values to write
	 * @param direct if set, ignore any memory mapping and write directly to the CPU's memory (This should generally only be used by memory map handlers)
	 */
	public void writeMemory(short startAddress, short[] values, boolean direct)
	{
		final int adjustedAddress = startAddress & 0xffff;
		
		for(int i = 0; i < values.length; ++i)
		{	
			final int curAddress = adjustedAddress + i;
			
			if(direct || memoryWriteHandlers[adjustedAddress] == null)
			{
				mem[curAddress] = values[i];
				instructionCache[curAddress] = null;
			}
			else
			{
				memoryWriteHandlers[adjustedAddress].memoryWritten((short)curAddress, values[i]);
			}
		}
	}
	
	public void writeMemory(short startAddress, short[] values)
	{
		writeMemory(startAddress, values, false);
	}
	
	/**
	 * Steps the CPU. Stepping consists of handling an interrupt from the queue (if any), then running a single instruction.
	 * @return the number of CPU cycles taken by the operation; 0 if there is an error condition
	 */
	public int step()
	{
		if(state != CpuState.RUNNING)
		{
			return 0;
		}
		
		if(interruptsEnabled && iqHead != iqTail)
		{
			/* handle interrupt */
			short interruptMessage = interruptQueue[iqHead];
			iqHead = (iqHead + 1) % MAX_SIMULTANEOUS_INTERRUPTS;
			
			if(ia != 0)
			{
				interruptsEnabled = false;
				
				writeMemory(--sp, pc);
				writeMemory(--sp, rA);
				
				pc = ia;
				rA = interruptMessage;
			}
		}
		
		/* execute instruction */
		Instruction inst = getInstructionForAddress(pc++);
		if(inst.illegal())
		{
			state = CpuState.CRASH_ILLEGAL_INSTRUCTION;
			return 0;
		}
		return inst.execute();
	}
	
	/**
	 * Adds an interrupt to the interrupt queue. This should generally only be used by attached hardware (or by the software interrupt instruction handler).
	 * @param message the message to include in the interrupt
	 */
	public void interrupt(short message)
	{
		interruptQueue[iqTail] = message;
		iqTail = (iqTail + 1) % MAX_SIMULTANEOUS_INTERRUPTS;
		
		if(iqHead == iqTail)
		{
			/* we just overflowed the interrupt queue; per the spec we should catch fire, but this will have to do */
			state = CpuState.CRASH_INTERRUPT_QUEUE_FILLED;
		}
	}
	
	/**
	 * Maps memory to a custom handler, allowing hardware to intercept memory reads and/or writes. Only a single map may be installed for a single address (even if one of the maps is read-only and one is write-only), and a single handler may be given only a single interval.
	 * @param handler the handler to install
	 * @param minAddress the minimum address to include in the map
	 * @param maxAddress the maximum address to include int he map
	 * @return a boolean indicating success
	 */
	public boolean mmap(MemoryMapHandler handler, short minAddress, short maxAddress)
	{
		int curAddr;
		boolean failed;
		
		if(memoryHandlerMap.containsKey(handler))
		{
			/* handler already mapped -- fail out */
			return false;
		}
		
		boolean handleReads = handler.interceptsReads();
		boolean handleWrites = handler.interceptsWrites();
		
		if(!handleReads && !handleWrites)
		{
			/* sanity check -- this handler does not actually handle anything */
			return true; /* we succeeded at nothing , which is exactly what was asked of us */
		}
		
		final int adjustedMinAddr = minAddress & 0xffff;
		final int adjustedMaxAddr = maxAddress & 0xffff;
		
		if(adjustedMinAddr > adjustedMaxAddr)
		{
			/* invalid interval specified */
			return false;
		}
		
		/* set up mapping */
		/* TODO: fail faster due to overlapping -- can use binary interval tree */
		failed = false;
		for(curAddr = adjustedMinAddr; curAddr <= adjustedMaxAddr; ++curAddr)
		{
			if((handleWrites && memoryWriteHandlers[curAddr] != null)
					|| (handleReads && memoryReadHandlers[curAddr] != null))
			{
				/* mmap collision -- bail out */
				failed = true;
				break;
			}
			
			if(handleWrites)
			{
				memoryWriteHandlers[curAddr] = handler;
			}
			
			if(handleReads)
			{
				memoryReadHandlers[curAddr] = handler;
			}
		}
		
		if(failed)
		{
			while(curAddr-- > adjustedMinAddr)
			{
				memoryWriteHandlers[curAddr] = null;
				memoryReadHandlers[curAddr] = null;
			}
			
			return false;
		}
		
		memoryHandlerMap.put(handler, new AddressInterval(minAddress, maxAddress));
		return true;
	}
	
	/**
	 * Unmaps mapped memory.
	 * @param handler the handler for the memory to be unmapped 
	 * @return a boolean indicating success
	 */
	public boolean munmap(MemoryMapHandler handler)
	{
		if(!memoryHandlerMap.containsKey(handler))
		{
			return false;
		}
		
		AddressInterval interval = memoryHandlerMap.get(handler);
		int adjustedMinAddress = interval.min & 0xffff;
		int adjustedMaxAddress = interval.max & 0xffff;
		
		for(int curAddr = adjustedMinAddress; curAddr <= adjustedMaxAddress; ++curAddr)
		{
			memoryReadHandlers[curAddr] = null;
			memoryWriteHandlers[curAddr] = null;
		}
		
		memoryHandlerMap.remove(handler);
		
		return true;
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
	 * Determines if the CPU has hit an error state. 
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
	public short A()
	{
		return rA;
	}
	
	/**
	 * Sets the A register.
	 * @param value the value to place in the A register
	 */
	public void A(short value)
	{
		rA = value;
	}
	
	/**
	 * Gets the B register.
	 * @return the B register
	 */
	public short B()
	{
		return rB;
	}
	
	/**
	 * Sets the B register.
	 * @param value the value to place in the B register
	 */
	public void B(short value)
	{
		rB = value;
	}
	
	/**
	 * Gets the C register.
	 * @return the C register
	 */
	public short C()
	{
		return rC;
	}
	
	/**
	 * Sets the C register.
	 * @param value the value to place in the C register
	 */
	public void C(short value)
	{
		rC = value;
	}
	
	/**
	 * Gets the X register.
	 * @return the X register
	 */
	public short X()
	{
		return rX;
	}
	
	/**
	 * Sets the X register.
	 * @param value the value to place in the X register
	 */
	public void X(short value)
	{
		rX = value;
	}
	
	/**
	 * Gets the Y register.
	 * @return the Y register
	 */
	public short Y()
	{
		return rY;
	}
	
	/**
	 * Sets the Y register.
	 * @param value the value to place in the Y register
	 */
	public void Y(short value)
	{
		rY = value;
	}
	
	/**
	 * Gets the Z register.
	 * @return the Z register
	 */
	public short Z()
	{
		return rZ;
	}
	
	/**
	 * Sets the Z register.
	 * @param value the value to place in the Z register
	 */
	public void Z(short value)
	{
		rZ = value;
	}
	
	/**
	 * Gets the I register.
	 * @return the I register
	 */
	public short I()
	{
		return rI;
	}
	
	/**
	 * Sets the I register.
	 * @param value the value to place in the I register
	 */
	public void I(short value)
	{
		rI = value;
	}
	
	/**
	 * Gets the J register.
	 * @return the J register
	 */
	public short J()
	{
		return rJ;
	}
	
	/**
	 * Sets the J register.
	 * @param value the value to place in the J register
	 */
	public void J(short value)
	{
		rJ = value;
	}
	
	/**
	 * Gets the PC register.
	 * @return the PC register
	 */
	public short PC()
	{
		return pc;
	}
	
	/**
	 * Sets the PC register.
	 * @param value the value to place in the PC register
	 */
	public void PC(short value)
	{
		pc = value;
	}
	
	/**
	 * Gets the SP register.
	 * @return the SP register
	 */
	public short SP()
	{
		return sp;
	}
	
	/**
	 * Sets the SP register.
	 * @param value the value to place in the SP register
	 */
	public void SP(short value)
	{
		sp = value;
	}
	
	/**
	 * Gets the EX register.
	 * @return the EX register
	 */
	public short EX()
	{
		return ex;
	}
	
	/**
	 * Sets the EX register.
	 * @param value the value to place in the EX register
	 */
	public void EX(short value)
	{
		ex = value;
	}
	
	/**
	 * Gets the IA register.
	 * @return the IA register
	 */
	public short IA()
	{
		return ia;
	}
	
	/**
	 * Sets the IA register.
	 * @param value the value to place in the IA register
	 */
	public void IA(short value)
	{
		ia = value;
	}
	
	/**
	 * Gets the instruction for a given address.
	 * @param address the address to get the instruction for
	 * @return the instruction decoded from that address
	 */
	Instruction getInstructionForAddress(short address)
	{
		final int adjustedAddress = address & 0xffff; 
		
		if(memoryReadHandlers[adjustedAddress] != null)
		{
			/* if we are in a section that is mmap'ed, we can't trust the instruction cache as memory might change out from under us later */
			return new Instruction(this, readMemory(address));
		}
		
		if(instructionCache[adjustedAddress] == null)
		{
			instructionCache[adjustedAddress] = new Instruction(this, readMemory(address));
		}
		
		return instructionCache[adjustedAddress];
	}
	
	/**
	 * Gets the operand for a given operand value. See the Operand class.
	 * @param operandValue the value of the operand
	 * @return the operand for that value
	 */
	Operand getOperandForValue(short operandValue)
	{
		if(operandCache[operandValue] == null)
		{
			operandCache[operandValue] = new Operand(this, operandValue);
		}
		
		return operandCache[operandValue];
	}
	
	/**
	 * A small class representing an interval of addresses. The interval is considered to be [min, max].
	 * @author Brandon Pitman
	 */
	private class AddressInterval
	{
		short min;
		short max;
		
		/**
		 * Creates a new interval.
		 * @param min the minimum address of the interval (inclusive)
		 * @param max the maximum address of the interval (inclusive)
		 */
		public AddressInterval(short min, short max)
		{
			this.min = min;
			this.max = max;
		}
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
		CRASH_ILLEGAL_INSTRUCTION,
		
		/**
		 * The CPU has crashed because it received too many interrupts.
		 */
		CRASH_INTERRUPT_QUEUE_FILLED,
	}
}
