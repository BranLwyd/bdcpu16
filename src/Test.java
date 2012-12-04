import java.util.ArrayList;
import java.util.List;

import cc.bran.bdcpu16.Cpu;
import cc.bran.bdcpu16.MemoryMapHandler;
import cc.bran.bdcpu16.hardware.Device;


public class Test
{

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		illegalInstructionTest();
	}
	
	public static void illegalInstructionTest()
	{
		Cpu cpu = new Cpu();
		cpu.writeMemory((short)0, buildInst(0x18, 0x00, 0x00)); /* illegal instruction */
		
		while(cpu.running())
		{
			cpu.step();
		}
		
		System.out.println(cpu.state().toString());
	}
	
	public static void infiniteLoopTest()
	{
		Cpu cpu = new Cpu();
		cpu.writeMemory((short)0, buildInst(0x01, 0x1c, 0x21)); /* SET PC, 0 */
		
		while(true)
		{
			cpu.step();
		}
	}
	
	public static void timingTest()
	{
		final int CYCLES = 500000000; /* this is about how many cycles a 20 MHz computer will do in 1/60 second */
		long start, end;
		int cycleCount = 0;
		
		Cpu cpu = new Cpu();
		cpu.writeMemory((short)0, buildInst(0x01, 0x1c, 0x21)); /* SET PC, 0 */
		
		System.out.println(String.format("performing %d cycles...", CYCLES));
		
		cycleCount = 0;
		start = System.nanoTime();
		while(cycleCount < CYCLES)
		{
			/* is this getting optimized? */
			cycleCount++;
		}
		end = System.nanoTime();
		long loopTime = end - start;
		
		cycleCount = 0;
		start = System.nanoTime();
		while(cycleCount < CYCLES)
		{
			cycleCount += cpu.step();
		}
		end = System.nanoTime();
		long executionTime = end - start - loopTime;
		
		System.out.println(String.format("execution time: %f ms", executionTime / 1e6));
		System.out.println(String.format("approximate clock speed: %f Mhz", 1e3 * cycleCount / executionTime));
	}
	
	public static void simpleTest()
	{
		List<Device> hwList = new ArrayList<Device>();
		hwList.add(new MemDumper());
		Cpu cpu = new Cpu(hwList);
		
		short inst1 = buildInst(0x01, 0x01, 0x23); /* SET B, 4 */
		short inst2 = buildInst(0x00, 0x12, 0x21); /* HWI 0 */
		
		cpu.writeMemory((short)0, inst1);
		cpu.writeMemory((short)1, inst2);
		
		int totalCycles = 0;
		for(int i = 0; i < 2; ++i)
		{
			int cyclesUsed = cpu.step();
			totalCycles += cyclesUsed;

			System.out.println(String.format("instruction used %d cycles, total %d used so far.", cyclesUsed, totalCycles));
			
		}
		
		System.out.println("done.");
	}
	
	private static short buildInst(int operator, int b, int a)
	{
		return (short)((operator & 0x1f) + ((b & 0x1f) << 5) + ((a & 0x3f) << 10));
	}
	
	private static class MapHandler implements MemoryMapHandler
	{

		@Override
		public boolean handlesReads()
		{
			return true;
		}

		@Override
		public boolean handlesWrites()
		{
			return true;
		}

		@Override
		public short memoryRead(short address)
		{
			System.out.println(String.format("memory read: 0x%04x", address));
			return 0;
		}

		@Override
		public void memoryWritten(short address, short value)
		{
			System.out.println(String.format("memory written: 0x%04x <- 0x%04x", address, value));
		}
	}
	
	private static class MemDumper implements Device
	{
		private Cpu cpu;
		
		@Override
		public void attach(Cpu cpu)
		{
			this.cpu = cpu;
		}

		@Override
		public int interrupt()
		{
			short startAddress = cpu.A();
			int length = cpu.B() & 0xffff;
			
			for(int i = 0; i < length; ++i)
			{
				System.out.print(String.format("%04x%s", cpu.readMemory((short)(startAddress + i)), (i % 16 == 15 ? "\n" : " ")));
			}
			
			if(length % 16 != 0)
			{
				System.out.println();
			}
			
			return 0;
		}

		@Override
		public int id()
		{
			return 1;
		}

		@Override
		public short version()
		{
			return 1;
		}

		@Override
		public int manufacturer()
		{
			return 0x6272616e; /* ASCII for "bran" */
		}
		
	}
}
