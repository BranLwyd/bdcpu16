package cc.bran.bdcpu16.hardware;

import java.nio.CharBuffer;

import cc.bran.bdcpu16.Cpu;

/**
 * Implements the Floppy Drive hardware for the DCPU-16. See http://dcpu.com/floppy-drive/.
 * @author Brandon Pitman
 */
public class FloppyDrive implements Device
{	
	/* disk constants (all of these come from the spec) */
	private static final int SECTORS_PER_TRACK = 18; /* number of sectors in each track */
	private static final int TOTAL_SECTORS = 1440; /* total number of sectors */
	private static final int WORDS_PER_SECTOR = 512; /* number of words in each sector */
	private static final double SEEK_TIME = 0.0024; /* time in seconds to seek between tracks */
	private static final int WORDS_PER_SECOND = 30700; /* number of words that can be read/written in one second */
	
	/* per-CPU constants */
	private int seekCycles; /* number of cycles to seek to a new track */
	private int rwCycles; /* number of cycles to read/write a sector */
	
	/* general state */
	private Cpu cpu;
	private char[] mem;
	private State state;
	private Error error;
	private char interruptMessage;
	private CharBuffer disk;
	private int curTrack;
	
	/* current operation information */
	private OperationType opType;
	private char startAddr;
	
	/**
	 * Creates a new floppy drive device.
	 * @param dcpuClockspeed the clock speed of the DCPU in Hz
	 */
	public FloppyDrive(int dcpuClockspeed)
	{		
		state = State.NO_MEDIA;
		error = Error.NONE;
		interruptMessage = 0;
		
		disk = null;
		
		opType = OperationType.NONE;
	}
	
	@Override
	public void attach(Cpu cpu)
	{
		this.cpu = cpu;
		mem = cpu.memory();
		
		seekCycles = (int)(SEEK_TIME * cpu.clockSpeed());
		rwCycles = cpu.clockSpeed() * WORDS_PER_SECTOR / WORDS_PER_SECOND;
	}

	@Override
	public int interrupt()
	{
		switch(cpu.A())
		{
		case 0: /* poll device */
			cpu.B(state.code);
			cpu.C(error.code);
			error = Error.NONE; /* we don't want to interrupt the CPU here so don't use setError() */
			break;
			
		case 1: /* set interrupt */
			interruptMessage = cpu.X();
			break;
			
		case 2: /* read sector */
			beginOp(OperationType.READ, cpu.X(), cpu.Y());
			break;
			
		case 3: /* write sector */
			beginOp(OperationType.WRITE, cpu.X(), cpu.Y());
			break;
		}
		
		return 0;
	}

	@Override
	public int id()
	{
		return 0x4f524c5;
	}

	@Override
	public char version()
	{
		return 0x000b;
	}

	@Override
	public int manufacturer()
	{
		return 0x1eb37e91;
	}
	
	/**
	 * Determines if there is a disk inserted in the drive.
	 * @return true if and only if there is a disk inserted
	 */
	public boolean diskInserted()
	{
		return (disk != null);
	}
	
	/**
	 * Sets the state & error codes for the device, and interrupts the CPU if anything has changed.
	 * @param state the new device state
	 * @param error the new device error
	 */
	private void stateAndError(State state, Error error)
	{
		if(interruptMessage != 0 && (state != this.state || error != this.error))
		{
			cpu.interrupt(interruptMessage);
		}
		
		this.state = state;
		this.error = error;
	}
	
	/**
	 * Sets the state for the device, and interrupts the CPU if anything has changed.
	 * @param state the new device state
	 */
	private void state(State state)
	{
		stateAndError(state, error);
	}
	
	/**
	 * Sets the error for the device, and interrupts the CPU if anything has changed.
	 * @param error the new device error
	 */
	private void error(Error error)
	{
		stateAndError(state, error);
	}
	
	/**
	 * Inserts a disk into the drive. The drive must currently be empty. The buffer that is passed in will be modified unless it is read only, in which case the floppy drive will treat the disk as write-protected.
	 * @param diskContents the contents of the disk
	 * @return true if and only if the disk was successfully inserted
	 */
	public boolean insert(CharBuffer diskContents)
	{
		if(diskInserted())
		{
			return false;
		}
		
		/* make sure the buffer is large enough */
		diskContents.limit(TOTAL_SECTORS * WORDS_PER_SECTOR);
		disk = diskContents;
		curTrack = 0;
		state(disk.isReadOnly() ? State.READY_WP : State.READY);
		
		return true;
	}
	
	/**
	 * Ejects the disk from the drive. The drive must currently have a disk.
	 * @return true if and only if the disk was successfully ejected
	 */
	public boolean eject()
	{
		if(!diskInserted())
		{
			return false;
		}
		
		if(opType != OperationType.NONE)
		{
			stateAndError(State.NO_MEDIA, Error.EJECT);
		}
		else
		{
			state(State.NO_MEDIA);
		}
		
		disk = null;
		opType = OperationType.NONE;
		
		return true;
	}
	
	/**
	 * Begins an operation (read/write).
	 * @param opType the type of operation (read or write)
	 * @param diskSector the disk sector to read or write
	 * @param startingMemAddr the starting memory address to read or write
	 */
	private void beginOp(OperationType opType, int diskSector, char startingMemAddr)
	{
		/* check that this op is applicable */
		if(state == State.NO_MEDIA)
		{
			error(Error.NO_MEDIA);
			cpu.B((char)0);
			return;
		}
		
		if(state == State.BUSY)
		{
			error(Error.BUSY);
			cpu.B((char)0);
			return;
		}
		
		if(opType == OperationType.WRITE && state == State.READY_WP)
		{
			error(Error.PROTECTED);
			cpu.B((char)0);
			return;
		}
		
		if(diskSector >= TOTAL_SECTORS)
		{
			cpu.B((char)0);
			/* there is no defined error for trying to read beyond the end of the disk */
			return;
		}
		
		/* good to go. set up the operation data structure & request wakeup from the CPU in the appropriate number of cycles */
		this.opType = opType;
		disk.position(WORDS_PER_SECTOR * diskSector);
		this.startAddr = startingMemAddr;
		
		final int reqTrack = diskSector / SECTORS_PER_TRACK;
		int seekTracks = curTrack - reqTrack;
		if(seekTracks < 0)
		{
			seekTracks = -seekTracks;
		}
		final int waitCycles = rwCycles + seekCycles * seekTracks;
		curTrack = reqTrack;
		
		cpu.scheduleWake(this, waitCycles);
		state(State.BUSY);
		cpu.B((char)1);
	}
	
	@Override
	public void wake(int cycles, int context)
	{
		switch(opType)
		{
		case READ: disk.get(mem, startAddr, WORDS_PER_SECTOR); break;
			
		case WRITE: disk.put(mem, startAddr, WORDS_PER_SECTOR); break;
			
		default: break; /* can't happen */ 
		}
		
		opType = OperationType.NONE;
		state(disk.isReadOnly() ? State.READY_WP : State.READY);
	}

	/**
	 * Represents the current state of the floppy drive.
	 * @author Brandon Pitman
	 */
	private enum State
	{
		NO_MEDIA(0x0000),
		READY(0x0001),
		READY_WP(0x0002),
		BUSY(0x0003);
		
		final char code;
		
		private State(int code)
		{
			this.code = (char)code;
		}
	}
	
	/**
	 * Represents the current error condition of the floppy drive.
	 * @author Brandon Pitman
	 */
	private enum Error
	{
		NONE(0x0000),
		BUSY(0x0001),
		NO_MEDIA(0x0002),
		PROTECTED(0x0003),
		EJECT(0x0004),
		BAD_SECTOR(0x0005),
		BROKEN(0xffff);
		
		final char code;
		
		private Error(int code)
		{
			this.code = (char)code;
		}
	}
	
	/**
	 * Represents the current operation that the floppy drive is performing.
	 * @author Brandon Pitman
	 *
	 */
	private enum OperationType
	{
		NONE,
		READ,
		WRITE,
	}
}
