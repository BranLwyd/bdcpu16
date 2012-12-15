package cc.bran.bdcpu16.hardware;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

import cc.bran.bdcpu16.Cpu;

/**
 * Implements a terminal (monitor and keyboard) for the DCPU-16 architecture. See http://dcpu.com/monitor/ and http://dcpu.com/keyboard/. The monitor additionally takes its default font & color palette from http://0x10cwiki.com/wiki/LEM1802.
 * Uses Swing to create/manage the window used to display the monitor.
 * @author Brandon Pitman
 */
public class Terminal
{
	private static final String DEFAULT_FONT_IMAGE = "assets/default-font.png"; /* location of default font image */
	
	private static final Color[] DEFAULT_PALETTE = new Color[] /* default color palette */
				{
					new Color(0x000000, false),
					new Color(0x0000aa, false),
					new Color(0x00aa00, false),
					new Color(0x00aaaa, false),
					new Color(0xaa0000, false),
					new Color(0xaa00aa, false),
					new Color(0xaa5500, false),
					new Color(0xaaaaaa, false),
					new Color(0x555555, false),
					new Color(0x5555ff, false),
					new Color(0x55ff55, false),
					new Color(0x55ffff, false),
					new Color(0xff5555, false),
					new Color(0xff55ff, false),
					new Color(0xffff55, false),
					new Color(0xffffff, false),
				};
	
	private static final BitSet DEFAULT_FONT; /* default font data */
	
	private static final int SIZE_MULTIPLIER = 4; /* size multiplier -- each "pixel" on the monitor will be multipled by this size in each dimension when drawing onto the actual screen */
	
	private static final int BORDER_SIZE = 5; /* size of border, in monitor pixels */
	private static final int GLYPH_WIDTH = 4; /* width of each glyph, in monitor pixels */
	private static final int GLYPH_HEIGHT = 8; /* height of each glyph, in monitor pixels */
	
	private static final int GLYPH_COUNT = 128; /* total number of glyphs */
	private static final int COLS = 32; /* number of columns in the terminal */
	private static final int ROWS = 12; /* number of rows in the terminal */
	private static final int PALETTE_SIZE = 16; /* number of colors in palette */
	
	private static final int KB_BUFFER_SIZE = 256; /* size of the keyboard buffer */
	
	private static final double BLINK_SPEED = 1.0; /* number of seconds between "blinks" */
	
	private JFrame frame;
	private MonitorPanel panel;
	private MonitorDevice monitor;
	private KeyboardDevice keyboard;

	/**
	 * Creates a new terminal. This will immediately create a window to display the monitor.
	 */
	public Terminal()
	{
		monitor = new MonitorDevice();
		keyboard = new KeyboardDevice();
		
		panel = new MonitorPanel();
		
		frame = new JFrame("DCPU-16 Terminal");
		frame.setContentPane(panel);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setResizable(false);
		frame.pack();
		frame.addKeyListener(keyboard);
		
		frame.setVisible(true);
	}
	
	/**
	 * Gets the device associated with the monitor.
	 * @return the device associated with the monitor
	 */
	public Device getMonitorDevice()
	{
		return monitor;
	}
	
	/**
	 * Gets the device associated with the keyboard.
	 * @return the device associated with the keyboard
	 */
	public Device getKeyboardDevice()
	{
		return keyboard;
	}
	
	/**
	 * Updates the monitor's display. This should be called at a reasonable rate--e.g. every 1/60 of a simulated second. 
	 */
	public void update()
	{
		monitor.update();
		
		frame.repaint();
	}
	
	/**
	 * This class maintains the UI of the monitor. It is intended to be hosted in a JFrame.
	 * @author Brandon Pitman
	 */
	@SuppressWarnings("serial") /* this class is never serialized so no need to add a serialVersionUID */
	private class MonitorPanel extends JPanel
	{
		private BitSet font;
		boolean powered;
		private Color[] palette;
		private int[] dataChars;
		private int[] dataForeColorIndices;
		private int[] dataBackColorIndices;
		private boolean[] dataBlink;
		private int borderColorIndex;
		private boolean blinked;
		private int blinkCharacterCount;
		
		private final Lock imageLock = new ReentrantLock();
		private boolean imageDirty;
		private BufferedImage image;
		
		/**
		 * Creates a new MonitorPanel.
		 */
		public MonitorPanel()
		{
			super();
			
			setPreferredSize(new Dimension(SIZE_MULTIPLIER * (2 * BORDER_SIZE + GLYPH_WIDTH * COLS),
                                           SIZE_MULTIPLIER * (2 * BORDER_SIZE + GLYPH_HEIGHT * ROWS)));
			
			font = (BitSet)DEFAULT_FONT.clone();
			powered = false;
			palette = Arrays.copyOf(DEFAULT_PALETTE, PALETTE_SIZE);
			dataChars = new int[COLS * ROWS];
			dataForeColorIndices = new int[COLS * ROWS];
			dataBackColorIndices = new int[COLS * ROWS];
			dataBlink = new boolean[COLS * ROWS];
			borderColorIndex = 0;
			blinked = false;
			blinkCharacterCount = 0;
			
			imageDirty = true;
			image = new BufferedImage(2 * BORDER_SIZE + GLYPH_WIDTH * COLS, 2 * BORDER_SIZE + GLYPH_HEIGHT * ROWS, BufferedImage.TYPE_INT_ARGB);
		}
		
		@Override
		public void paintComponent(Graphics g)
		{
			long start = System.nanoTime();
			
			if(imageDirty)
			{
				/* if we can't get the lock, we're still receiving data for the update, so don't worry about updating the image for now & just draw the old frame */
				if(imageLock.tryLock())
				{
					try
					{
						updateImage();
						imageDirty = false;
					}
					finally
					{
						imageLock.unlock();
					}
				}
			}
			
			g.drawImage(image, 0, 0, getWidth(), getHeight(), 0, 0, image.getWidth(), image.getHeight(), null);
			
			long end = System.nanoTime();
			g.setColor(Color.white);
			g.drawString(String.format("draw time: %fms", (end - start) / 1e6), 0, getHeight());
		}
		
		/**
		 * Updates the backing image for the monitor.
		 */
		private void updateImage()
		{
			Graphics g = image.getGraphics();
			
			if(powered)
			{
				g.setColor(palette[borderColorIndex]);
				g.fillRect(0,  0, getWidth(), getHeight());
				
				for(int i = 0; i < COLS; ++i)
				{
					for(int j = 0; j < ROWS; ++j)
					{
						final int offset = COLS * j + i;
						final int foreColorIndex;
						final int backColorIndex;
						
						if(blinked && dataBlink[offset])
						{
							/* switch fore and back colors */
							foreColorIndex = dataBackColorIndices[offset];
							backColorIndex = dataForeColorIndices[offset];
						}
						else
						{
							/* use normal colors */
							foreColorIndex = dataForeColorIndices[offset];
							backColorIndex = dataBackColorIndices[offset];
						}
						
						drawGlyph(g, i, j, dataChars[offset], palette[foreColorIndex], palette[backColorIndex]);
					}
				}
			}
			else
			{
				/* monitor is off -- just clear the screen */
				g.setColor(Color.black);
				g.fillRect(0, 0, getWidth(), getHeight());
			}
		}
		
		/**
		 * Draws a single glyph on the monitor.
		 * @param g the graphics to draw to
		 * @param col the monitor column to draw the glyph on
		 * @param row the monitor row to draw the glyph on
		 * @param charNum the number of the character
		 * @param foreColor the foreground color of the glyph
		 * @param backColor the background color of the glyph
		 */
		private void drawGlyph(Graphics g, int col, int row, int charNum, Color foreColor, Color backColor)
		{	
			/* background */
			g.setColor(backColor);
			g.fillRect(BORDER_SIZE + GLYPH_WIDTH * col,
					   BORDER_SIZE + GLYPH_HEIGHT * row,
					   GLYPH_WIDTH,
					   GLYPH_HEIGHT);
			
			/* foreground -- iterate over set bits to determine the pixels to draw */
			g.setColor(foreColor);
			
			final int startBit = GLYPH_WIDTH * GLYPH_HEIGHT * charNum;
			final int lastBit = GLYPH_WIDTH * GLYPH_HEIGHT * (charNum + 1);
			int curBit = font.nextSetBit(startBit);
			while(curBit != -1 && curBit < lastBit)
			{
				final int relBit = curBit - startBit;
				final int gRow = GLYPH_HEIGHT - 1 - (relBit % GLYPH_HEIGHT);
				final int gCol = relBit / GLYPH_HEIGHT;
				
				g.fillRect(BORDER_SIZE + GLYPH_WIDTH * col + gCol,
						   BORDER_SIZE + GLYPH_HEIGHT * row + gRow,
						   1,
						   1);
				
				curBit = font.nextSetBit(curBit + 1);
			}
		}
		
		/**
		 * Sets the font directly. The bitsets values determine whether each pixel of each character is considered foreground or background; the characters are in order by character number, and each character's pixels are specified in row-major order from bottom-left to top-right.
		 * @param font the bitset containing the font data
		 */
		public void font(BitSet font)
		{
			this.font = (BitSet)font.clone();
			
			imageDirty = true;
		}
		
		/**
		 * Sets the font data from an array. See http://dcpu.com/monitor/ for the expected format.
		 * @param array the array to read font data from
		 * @param offset the offset to start reading data from
		 */
		public void font(char[] array, int offset)
		{
			for(int i = 0; i < GLYPH_COUNT; ++i)
			{
				font(array[offset + 2 * i], array[offset + 2 * i + 1], i);
			}
		}
		
		/**
		 * Sets the font data for a single glyph. See http://dcpu.com/monitor/for the expected format.
		 * @param value0 the first word of glyph data
		 * @param value1 the second word of glyph data
		 * @param offset the character number to modify
		 */
		public void font(char value0, char value1, int offset)
		{
			int bit = GLYPH_WIDTH * GLYPH_HEIGHT * offset;
			
			/* process value0 */
			for(int i = 0; i < 16; ++i)
			{
				font.set(bit++, (value0 & 0x8000) != 0);
				value0 <<= 1;
			}
			
			/* process value1 */
			for(int i = 0; i < 16; ++i)
			{
				font.set(bit++, (value1 & 0x8000) != 0);
				value1 <<= 1;
			}
			
			imageDirty = true;
		}
		
		/**
		 * Sets the palette data directly from an array. The input array should be at least PALETTE_SIZE large.
		 * @param palette the array of colors to use in the palette
		 */
		public void palette(Color[] palette)
		{
			this.palette = Arrays.copyOf(palette, PALETTE_SIZE);
			
			imageDirty = true;
		}
		
		/**
		 * Sets the palette data from an array. See http://dcpu.com/monitor/ for the expected format.
		 * @param array the array to read palette data from
		 * @param offset the offset to start reading from
		 */
		public void palette(char[] array, int offset)
		{
			for(int i = 0; i < PALETTE_SIZE; ++i)
			{
				palette(array[offset + i], i);
			}
		}
		
		/**
		 * Sets a single palette entry from a palette value. See http://dcpu.com/monitor/ for the expected format.
		 * @param value the value to enter into the palette
		 * @param offset the palette entry to modify
		 */
		public void palette(char value, int offset)
		{
			/* each color gets a 4 bit value, but we need to stretch it to an 8-bit value; duplicate the 4 bits we get into the high and low 4 bits of the resulting value */
			int   redValue = (value >> 8) & 0xf;
			int greenValue = (value >> 4) & 0xf;
			int  blueValue = (value >> 0) & 0xf;
			
			redValue   = (  redValue << 4) |   redValue;
			greenValue = (greenValue << 4) | greenValue;
			blueValue  = ( blueValue << 4) |  blueValue;
			
			palette[offset] = new Color(redValue, greenValue, blueValue);
			
			imageDirty = true;
		}
		
		/**
		 * Sets the data (screen memory) from an array. See http://dcpu.com/monitor/ for the expected format.
		 * @param array the array to read screen content from
		 * @param offset the offset to start reading from
		 */
		public void data(char[] array, int offset)
		{
			for(int i = 0; i < COLS * ROWS; ++i)
			{
				data(array[offset + i], i);
			}
		}
		
		/**
		 * Sets a single glyph's worth of data (screen memory). See http://dcpu.com/monitor/ for the expected format.
		 * @param value the value to place into screen memory
		 * @param offset the index of the screen memory to set
		 */
		public void data(char value, int offset)
		{
			final int foreColorIndex = (value >> 12) & 0xf;
			final int backColorIndex = (value >> 8) & 0xf;
			final boolean blink = (value & 0x80) != 0;
			final int charNum = value & 0x7f;
			
			/* update blinkCharCount */
			if(blink && !dataBlink[offset])
			{
				blinkCharacterCount++;
			}
			else if(!blink && dataBlink[offset])
			{
				blinkCharacterCount--;
			}
			
			dataChars[offset] = charNum;
			dataForeColorIndices[offset] = foreColorIndex;
			dataBackColorIndices[offset] = backColorIndex;
			dataBlink[offset] = blink;
			
			imageDirty = true;
		}
		
		/**
		 * Determines if the monitor is powered on.
		 * @return ture if and only if the monitor is powered on
		 */
		public boolean powered()
		{
			return powered;
		}
		
		/**
		 * Turns the monitor on or off.
		 * @param powered true if and only if the monitor should be turned on
		 */
		public void powered(boolean powered)
		{
			this.powered = powered;
			
			imageDirty = true;
		}
		
		/**
		 * Blinks the display. (Any glyphs with the blink attribute set will reverse the foreground and background colors.)
		 */
		public void blink()
		{
			this.blinked = !blinked;
			
			if(blinkCharacterCount > 0)
			{
				imageDirty = true;
			}
		}
		
		/**
		 * Notifies the monitor that an update to the font/palette/screen data is about to begin.
		 */
		public void beginUpdate()
		{
			imageLock.lock();
		}
		
		/**
		 * Notifies the monitor than an update to the font/palette/screen data has ended.
		 */
		public void endUpdate()
		{
			imageLock.unlock();
		}
	}
	
	/**
	 * This class handles the Device logic to interface the monitor to the DCPU-16.
	 * @author Brandon Pitman
	 */
	private class MonitorDevice implements Device
	{	
		private Cpu cpu;
		private char[] mem;
		int blinkCycles;
		int cycles;
		
		private char addressScreen;
		private char[] expectedScreen;
		
		private char addressFont;
		private char[] expectedFont;
		
		private char addressPalette;
		private char[] expectedPalette;
		
		/**
		 * Updates data structures in preparation to redraw the screen.
		 */
		public void update()
		{
			boolean updated = false;
			
			try
			{
				if(!panel.powered())
				{
					return;
				}
				
				/* update font */
				if(addressFont != 0)
				{
					for(int i = 0; i < GLYPH_COUNT; ++i)
					{
						if(expectedFont[2*i] != mem[addressFont + 2*i] || expectedFont[2*i + 1] != mem[addressFont + 2*i + 1])
						{
							if(!updated)
							{
								updated = true;
								panel.beginUpdate();
							}
							
							expectedFont[2*i] = mem[addressFont + 2*i];
							expectedFont[2*i + 1] = mem[addressFont + 2*i + 1];
							
							panel.font(expectedFont[2*i], expectedFont[2*i + 1], i);
						}
					}
				}
				
				/* update palette */
				if(addressPalette != 0)
				{
					for(int i = 0; i < PALETTE_SIZE; ++i)
					{
						if(expectedPalette[i] != mem[addressPalette + i])
						{
							if(!updated)
							{
								updated = true;
								panel.beginUpdate();
							}
							
							expectedPalette[i] = mem[addressPalette + i];
							
							panel.palette(expectedPalette[i], i);
						}
					}
				}
				
				/* update data -- no need to check addressData against 0 because of the poweredOn check above */
				for(int i = 0; i < COLS * ROWS; ++i)
				{
					if(expectedScreen[i] != mem[addressScreen + i])
					{
						if(!updated)
						{
							updated = true;
							panel.beginUpdate();
						}
						
						expectedScreen[i] = mem[addressScreen + i];
						
						panel.data(expectedScreen[i], i);
					}
				}
			}
			finally
			{
				if(updated)
				{
					panel.endUpdate();
				}
			}
		}
		
		@Override
		public void attach(Cpu cpu)
		{
			this.cpu = cpu;
			mem = cpu.memory();
			blinkCycles = (int)(BLINK_SPEED * cpu.clockSpeed());
			
			addressScreen = 0;
			addressFont = 0;
			addressPalette = 0;
					
			expectedScreen = new char[COLS * ROWS];
			expectedFont = new char[2 * GLYPH_COUNT];
			expectedPalette = new char[GLYPH_COUNT];
		}

		@Override
		public int interrupt()
		{
			switch(cpu.A())
			{
			case 0: /* MEM_MAP_SCREEN */
				setScreenAddress(cpu.B());
				break;
				
			case 1: /* MEM_MAP_FONT */
				setFontAddress(cpu.B());
				break;
				
			case 2: /* MEM_MAP_PALETTE */
				setPaletteAddress(cpu.B());
				break;
				
			case 3: /* SET_BORDER_COLOR */
				panel.borderColorIndex = cpu.B() & 0xf;
				break;
				
			case 4: /* MEM_DUMP_FONT */
				if(cpu.B() + 2 * GLYPH_COUNT <= Cpu.MAX_ADDRESS)
				{
					dumpFont(DEFAULT_FONT, mem, cpu.B());
					return 256;
				}
				
			case 5: /* MEM_DUMP_PALETTE */
				if(cpu.B() + PALETTE_SIZE <= Cpu.MAX_ADDRESS)
				{
					dumpPalette(DEFAULT_PALETTE, mem, cpu.B());
				}
				return 16;
			}
			
			return 0;
		}
		
		/**
		 * Sets the video memory address.
		 * @param addressScreen the new video memory address
		 */
		private void setScreenAddress(char addressScreen)
		{
			if(addressScreen + COLS * ROWS > Cpu.MAX_ADDRESS)
			{
				/* address will cause an overflow -- disconnect screen */
				addressScreen = 0;
			}
			
			this.addressScreen = addressScreen;
			
			panel.beginUpdate();
			try
			{	
				if(addressScreen == 0)
				{
					panel.powered(false);
				}
				else
				{
					panel.powered(true);
					panel.data(mem, addressScreen);
					
					for(int i = 0; i < COLS * ROWS; ++i)
					{
						expectedScreen[i] = mem[addressScreen + i];
					}
				}
			}
			finally
			{
				panel.endUpdate();
			}
		}
		
		/**
		 * Sets the font memory address.
		 * @param addressFont the new font memory address
		 */
		private void setFontAddress(char addressFont)
		{
			if(addressFont + 2 * GLYPH_COUNT > Cpu.MAX_ADDRESS)
			{
				/* address will cause an overflow -- switch to default font */
				addressFont = 0;
			}
			
			this.addressFont = addressFont;
			
			panel.beginUpdate();
			try
			{
				if(addressFont == 0)
				{
					panel.font(DEFAULT_FONT);
				}
				else
				{
					panel.font(mem, addressFont);
					
					for(int i = 0; i < 2 * GLYPH_COUNT; ++i)
					{
						expectedFont[i] = mem[addressFont + i];
					}
				}
			}
			finally
			{
				panel.endUpdate();
			}
		}
		
		/**
		 * Sets the palette memory address.
		 * @param addressPalette the new palette memory address
		 */
		private void setPaletteAddress(char addressPalette)
		{
			if(addressPalette + PALETTE_SIZE > Cpu.MAX_ADDRESS)
			{
				/* address will cause an overflow -- switch to default palette */
				addressPalette = 0;
			}
			
			this.addressPalette = addressPalette;
			
			panel.beginUpdate();
			try
			{
				if(addressPalette == 0)
				{
					panel.palette(DEFAULT_PALETTE);
				}
				else
				{
					panel.palette(mem, addressPalette);
					
					for(int i = 0; i < PALETTE_SIZE; ++i)
					{
						expectedPalette[i] = mem[addressPalette + i];
					}
				}
			}
			finally
			{
				panel.endUpdate();
			}
		}

		@Override
		public void cyclesElapsed(int cycleCount)
		{
			/* blink every now and then */
			cycles += cycleCount;
			if(blinkCycles < cycles)
			{
				panel.blink();
				cycles -= blinkCycles;
			}
		}

		@Override
		public int id()
		{
			return 0x7349f615;
		}

		@Override
		public char version()
		{
			return 0x1802;
		}

		@Override
		public int manufacturer()
		{
			return 0x1c6c8b36;
		}
	}
	
	/**
	 * This class implements the Device logic to interface the keyboard to the DCPU-16, as well as handling keyboard events received from Swing.
	 * @author Brandon Pitman
	 */
	private class KeyboardDevice implements Device, KeyListener
	{
		private Cpu cpu;
		private char interruptMessage;
		private char[] buffer;
		private int bufHead, bufTail;
		private BitSet pressed;
		
		@Override
		public void attach(Cpu cpu)
		{
			this.cpu = cpu;
			
			interruptMessage = 0;
			
			buffer = new char[KB_BUFFER_SIZE];
			bufHead = 0;
			bufTail = 0;
			
			pressed = new BitSet();
		}

		@Override
		public int interrupt()
		{
			switch(cpu.A())
			{
			case 0: /* clear KB buffer */
				synchronized(buffer)
				{
					bufHead = bufTail;
				}
				break;
				
			case 1: /* get next typed key */
				synchronized(buffer)
				{
					cpu.C((char)(bufHead != bufTail ? buffer[bufHead++] : 0));
				}
				break;
				
			case 2: /* check key pressed */
				cpu.C(pressed.get(cpu.B()) ? (char)1 : (char)0);
				break;
				
			case 3: /* enable/disable interrupts */
				interruptMessage = cpu.B();
				break;
			}
			
			return 0;
		}

		@Override
		public void cyclesElapsed(int cycleCount)
		{
			/* do nothing */
		}

		@Override
		public int id()
		{
			return 0x30cf7406;
		}

		@Override
		public char version()
		{
			return 1;
		}

		@Override
		public int manufacturer()
		{
			/* no manufacturer listed in spec -- we need to make one up */
			return 0x6272616e; /* "bran" */
		}

		@Override
		public void keyPressed(KeyEvent e)
		{
			final char dcpuCode = keyCodeToDcpuKeycode(e.getKeyCode());
			if(dcpuCode == 0)
			{
				/* ignore unknown keys */
				return;
			}
			
			if(interruptMessage != 0)
			{
				cpu.interrupt(interruptMessage);
			}
			
			pressed.set(dcpuCode);
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
			final char dcpuCode = keyCodeToDcpuKeycode(e.getKeyCode());
			if(dcpuCode == 0)
			{
				/* ignore unknown keys */
				return;
			}
			
			if(interruptMessage != 0)
			{
				cpu.interrupt(interruptMessage);
			}
			
			pressed.clear(dcpuCode);
		}

		@Override
		public void keyTyped(KeyEvent e)
		{
			final char dcpuCode = keyCharToDcpuKeycode(e.getKeyChar());
			if(dcpuCode == 0)
			{
				/* ignore unknown keys */
				return;
			}
			
			if(interruptMessage != 0)
			{
				cpu.interrupt(interruptMessage);
			}
			
			synchronized(buffer)
			{
				buffer[bufTail] = dcpuCode;
				bufTail = (bufTail + 1) % KB_BUFFER_SIZE;
			}
		}
		
		/**
		 * Converts a Swing key character (from KeyEvent.getKeyChar()) to a DCPU-16 keycode. See http://dcpu.com/keyboard/ for a listing of DCPU keycodes.
		 * @param keyChar the key character from KeyEvent.getKeyChar()
		 * @return the corresponding DCPU keycode, or 0 if there is no match
		 */
		private char keyCharToDcpuKeycode(char keyChar)
		{
			if(0x20 <= keyChar && keyChar <= 0x7f)
			{
				/* ASCII characters between 0x20 and 0x7f are the same */
				return keyChar;
			}
			
			switch(keyChar)
			{
			case 0x08: return 0x10; /* backspace */
			case 0x0a: return 0x11; /* return */
			case 0x7f: return 0x13; /* delete */
			}
			
			/* otherwise unknown */
			return 0;
		}
		
		/**
		 * Converts a swing keycode (from KeyEvent.getKeyCode()) to a DCPU-16 keycode. See http://dcpu.com/keyboard/ for a listing of DCPU keycodes.
		 * @param keyCode the keycode from KeyEvent.getKeyCode()
		 * @return the corresponding DCPU keycode, or 0 if there is no match
		 */
		private char keyCodeToDcpuKeycode(int keyCode)
		{
			if(0x41 <= keyCode && keyCode <= 0x5a)
			{
				/* (uppercase) alpha -- identical */
				return (char)keyCode;
			}
			
			if(0x30 <= keyCode && keyCode <= 0x39)
			{
				/* numerics -- identical */
				return (char)keyCode;
			}
			
			if(0x60 <= keyCode && keyCode <= 0x69)
			{
				/* alternate numerics -- translate to standard numeric */
				return (char)(keyCode - 0x30);
			}
			
			switch(keyCode)
			{
			case 0x08: return 0x10; /* backspace */
			case 0x0a: return 0x11; /* return */
			case 0x9b: return 0x12; /* insert */
			case 0x7f: return 0x13; /* delete */
			case 0x25: return 0x82; /* arrow left */
			case 0x26: return 0x80; /* arrow up */
			case 0x27: return 0x83; /* arrow right */
			case 0x28: return 0x81; /* arrow down */
			case 0x10: return 0x90; /* shift */
			case 0x11: return 0x91; /* control */
			case 0x20: return 0x20; /* space */
			case 0x2c: return 0x2c; /* comma */
			case 0x23: return 0x2e; /* period */
			case 0x2f:
			case 0x6f: return 0x2f; /* slash (two forms) */
			case 0x3b: return 0x3b; /* semicolon */
			case 0xde: return 0x27; /* apostrophe */
			case 0x5b: return 0x5b; /* left square bracket */
			case 0x5d: return 0x5d; /* right square bracket */
			case 0x5c: return 0x5c; /* backslash */
			case 0xc0: return 0x60; /* backtick */
			case 0x2d:
			case 0x6d: return 0x2d; /* minus (two forms) */
			case 0x3d: return 0x3d; /* equals */
			case 0x6a: return 0x2a; /* star */
			case 0x6b: return 0x2b; /* plus */
			}
			
			return 0;
		}
	}
	
	/**
	 * Dumps a given font into an array in the format defined by the DCPU-16 spec.
	 * @param font the font to write
	 * @param array the array to write to
	 * @param offset the offset to begin writing
	 */
	private static void dumpFont(BitSet font, char[] array, int offset)
	{
		int bit = 0;
		char value;
		
		for(int i = 0; i < GLYPH_COUNT; ++i)
		{
			/* first word */
			value = 0;
			for(int j = 0; j < 16; ++j)
			{
				value <<= 1;
				if(font.get(bit++))
				{
					value |= 1;
				}
			}
			array[offset + 2*i] = value;
			
			/* second word */
			value = 0;
			for(int j = 0; j < 16; ++j)
			{
				value <<= 1;
				if(font.get(bit++))
				{
					value |= 1;
				}
			}
			array[offset + 2*i] = value;
		}
	}
	
	/**
	 * Dumps a given palette (array of Colors) into an array in the format specified by the DCPU-16 spec.
	 * @param palette the palette to write
	 * @param array the array to write to
	 * @param offset the offset to begin writing
	 */
	private static void dumpPalette(Color[] palette, char[] array, int offset)
	{
		for(int i = 0; i < PALETTE_SIZE; ++i)
		{
			final Color color = palette[i];
			
			array[offset + i] = (char)((color.getRed() << 8) | (color.getBlue() << 4) | (color.getGreen() << 0));
		}
	}
	
	/**
	 * Loads a font description from an image. The image should lay out the glyphs in a horizontal strip.
	 * @param font the font to write to
	 * @param fontImage the image to read from
	 */
	private static void loadFontFromImage(BitSet font, BufferedImage fontImage)
	{
		if(fontImage.getWidth() != GLYPH_COUNT * GLYPH_WIDTH || fontImage.getHeight() != GLYPH_HEIGHT)
		{
			throw new IllegalArgumentException(String.format("fontImage must be %d x %d", GLYPH_COUNT * GLYPH_WIDTH, GLYPH_HEIGHT));
		}
		
		int[] imageData = fontImage.getRGB(0, 0, GLYPH_COUNT * GLYPH_WIDTH, GLYPH_HEIGHT, null, 0, GLYPH_COUNT * GLYPH_WIDTH);
		
		int bit = 0;
		for(int i = 0; i < GLYPH_COUNT * GLYPH_WIDTH; ++i)
		{
			for(int j = GLYPH_HEIGHT - 1; j >= 0; --j)
			{
				/* count pixel as "set" if the high bit is set in any of the RGB channels */
				final boolean pixelSet = ((imageData[GLYPH_COUNT * GLYPH_WIDTH * j + i] & 0xf0f0f0) != 0);
				
				font.set(bit++, pixelSet);
			}
		}
	}
	
	static
	{		
		/* initialize default font */
		BufferedImage fontImage = null;
		try
		{
			fontImage = ImageIO.read(new File(DEFAULT_FONT_IMAGE));
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
			System.exit(1);
		}
		
		DEFAULT_FONT = new BitSet(GLYPH_COUNT * GLYPH_HEIGHT * GLYPH_WIDTH);
		loadFontFromImage(DEFAULT_FONT, fontImage);
	}
}
