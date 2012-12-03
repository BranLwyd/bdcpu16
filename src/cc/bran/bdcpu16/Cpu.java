package cc.bran.bdcpu16;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import cc.bran.bdcpu16.hardware.Device;

/* TODO: count cycles (add support for simulating for a single cycle?) */
/* TODO: write assembler */
/* TODO: write debugger */
public class Cpu
{
	/* general settings */
	private static final int MEMORY_SIZE = ((short)-1 & 0xffff) + 1; /* memory size in words -- equals number of values a short can take on -- 0x10000 */
	private static final int MAX_SIMULTANEOUS_INTERRUPTS = 256;
	
	private short[] mem;
	short rA, rB, rC, rX, rY, rZ, rI, rJ;
	short pc, sp, ex, ia;
	
	private boolean interruptsEnabled;
	private short[] interruptQueue;
	private int queueHead, queueTail;
	
	final Device[] attachedHardware;
	
	private MemoryMapHandler[] memoryReadHandlers;
	private MemoryMapHandler[] memoryWriteHandlers;
	private Map<MemoryMapHandler, AddressInterval> memoryHandlerMap;
	
	private Instruction[] instructionCache;
	private Operand[] operandCache;
	
	public Cpu()
	{
		this(null);
	}
	
	public Cpu(Collection<Device> attachedHardware)
	{
		mem = new short[MEMORY_SIZE];
		rA = rB = rC = rX = rY = rZ = rI = rJ = pc = sp = ex = ia = 0;
		
		interruptsEnabled = true;
		interruptQueue = new short[MAX_SIMULTANEOUS_INTERRUPTS];
		queueHead = 0; queueTail = 0;
		
		instructionCache = new Instruction[MEMORY_SIZE];
		operandCache = new Operand[Operand.OPERAND_COUNT];
		
		memoryReadHandlers = new MemoryMapHandler[MEMORY_SIZE];
		memoryWriteHandlers = new MemoryMapHandler[MEMORY_SIZE];
		memoryHandlerMap = new HashMap<MemoryMapHandler, AddressInterval>();
		
		if(attachedHardware != null)
		{
			this.attachedHardware = new Device[attachedHardware.size()];
			int i = 0;
			
			for(Device hw : attachedHardware)
			{
				this.attachedHardware[i++] = hw;
				hw.attach(this);
			}
		}
		else
		{
			this.attachedHardware = new Device[0];
		}
	}
	
	public short readMemory(short address)
	{
		final int adjustedAddress = address & 0xffff;
		
		if(memoryReadHandlers[adjustedAddress] == null)
		{
			return mem[adjustedAddress];
		}
		else
		{
			return memoryReadHandlers[adjustedAddress].memoryRead(address);
		}
	}
	
	public void writeMemory(short address, short value)
	{
		final int adjustedAddress = address & 0xffff;
		
		if(memoryWriteHandlers[adjustedAddress] == null)
		{
			mem[adjustedAddress] = value;
			instructionCache[adjustedAddress] = null;
		}
		else
		{
			memoryWriteHandlers[adjustedAddress].memoryWritten(address, value);
		}
	}
	
	public void writeMemory(short startAddress, short[] values)
	{
		final int adjustedAddress = startAddress & 0xffff;
		
		for(int i = 0; i < values.length; ++i)
		{	
			final int curAddress = adjustedAddress + i;
			
			if(memoryWriteHandlers[adjustedAddress] == null)
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
	
	public void step()
	{
		if(interruptsEnabled && queueHead != queueTail)
		{
			/* handle interrupt */
			short interruptMessage = interruptQueue[queueHead];
			queueHead = (queueHead + 1) % MAX_SIMULTANEOUS_INTERRUPTS;
			
			if(ia != 0)
			{
				interruptsEnabled = false;
				
				writeMemory(--sp, pc);
				writeMemory(--sp, rA);
				
				pc = ia;
				rA = interruptMessage;
			}
		}
		else
		{
			/* execute instruction */
			Instruction inst = getInstructionForAddress(pc++);
			inst.execute();
		}
	}
	
	public void interrupt(short message)
	{
		interruptQueue[queueTail] = message;
		queueTail = (queueTail + 1) % MAX_SIMULTANEOUS_INTERRUPTS;
		
		if(queueHead == queueTail)
		{
			/* TODO: implement crashing when the interrupt queue is filled */
			/* we just overflowed the interrupt queue. per the spec, catch fire. */
		}
	}
	
	public boolean mmap(MemoryMapHandler handler, short minAddress, short maxAddress)
	{
		int curAddr;
		boolean failed;
		
		if(memoryHandlerMap.containsKey(handler))
		{
			/* handler already mapped -- fail out */
			return false;
		}
		
		boolean handleReads = handler.handlesReads();
		boolean handleWrites = handler.handlesWrites();
		
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
		
		return false;
	}
	
	public short A()
	{
		return rA;
	}
	
	public void A(short value)
	{
		rA = value;
	}
	
	public short B()
	{
		return rB;
	}
	
	public void B(short value)
	{
		rB = value;
	}
	
	public short C()
	{
		return rC;
	}
	
	public void C(short value)
	{
		rC = value;
	}
	
	public short X()
	{
		return rX;
	}
	
	public void X(short value)
	{
		rX = value;
	}
	
	public short Y()
	{
		return rY;
	}
	
	public void Y(short value)
	{
		rY = value;
	}
	
	public short Z()
	{
		return rZ;
	}
	
	public void Z(short value)
	{
		rZ = value;
	}
	
	public short I()
	{
		return rI;
	}
	
	public void I(short value)
	{
		rI = value;
	}
	
	public short J()
	{
		return rJ;
	}
	
	public void J(short value)
	{
		rJ = value;
	}
	
	public short PC()
	{
		return pc;
	}
	
	public void PC(short value)
	{
		pc = value;
	}
	
	public short SP()
	{
		return sp;
	}
	
	public void SP(short value)
	{
		sp = value;
	}
	
	public short EX()
	{
		return ex;
	}
	
	public void EX(short value)
	{
		ex = value;
	}
	
	public short IA()
	{
		return ia;
	}
	
	public void IA(short value)
	{
		ia = value;
	}
	
	void setInterruptsEnabled(boolean enabled)
	{
		interruptsEnabled = enabled;
	}
	
	
	Instruction getInstructionForAddress(short address)
	{
		final int adjustedAddress = address & 0xffff; 
		
		if(memoryReadHandlers[adjustedAddress] != null)
		{
			/* if we are in a section that is mmap'ed, we can't trust the instruction cache as memory might change out from under us later */
			return new Instruction(this, this.readMemory(address));
		}
		
		if(instructionCache[adjustedAddress] == null)
		{
			instructionCache[adjustedAddress] = new Instruction(this, this.readMemory(address));
		}
		
		return instructionCache[adjustedAddress];
	}
	
	Operand getOperandForValue(short operandValue)
	{
		if(operandCache[operandValue] == null)
		{
			operandCache[operandValue] = new Operand(this, operandValue);
		}
		
		return operandCache[operandValue];
	}
	
	private class AddressInterval
	{
		public AddressInterval(short min, short max)
		{
			this.min = min;
			this.max = max;
		}
		
		short min;
		short max;
	}
}
