package cc.bran.bdcpu16.debug;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;

import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.InputVerifier;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.LayoutStyle;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import cc.bran.bdcpu16.Cpu;
import cc.bran.bdcpu16.Cpu.Register;
import cc.bran.bdcpu16.codegen.InstructionDecoder;
import cc.bran.bdcpu16.codegen.InstructionDecoder.DecodedInstruction;
import cc.bran.bdcpu16.util.ValueFormatter;
import cc.bran.bdcpu16.util.ValueFormatters;

/**
 * This class provides a simple debugger UI in Swing. 
 * @author Brandon Pitman
 */
@SuppressWarnings("serial") /* DebuggerFrames are never serialized */
public class DebuggerFrame extends JFrame implements DebuggerUI
{
	private static final String CURRENT_LOCATION_MARKER = " \u25BA"; /* string used to denote current location (PC/SP) */
	private static final String BREAKPOINT_MARKER = " \u25CF"; /* string used to denote a breakpoint */
	private static final int ROW_BUFFER = 3; /* number of rows to attempt to show on each side of PC/SP */
	
	private Cpu cpu;
	private Debugger debugger;
	private DecodedInstructionCache cache;
	
	private volatile boolean exited;
	private boolean paused;
	private char expectedPC;
	private char expectedSP;

	private JTable memoryTable;
	private JTable stackTable;
	private JLabel stateLabel;
	private JMenuItem continueItem;
	private JMenuItem breakItem;
	private JMenuItem stepItem;
	
	private ValueFormatter valFormatter;
	
	private HashMap<Register, JTextField> registerFields;

	/**
	 * Creates a new DebuggerFrame.
	 */
	public DebuggerFrame()
	{
		super("DCPU-16 Debugger");
		
		this.cache = new DecodedInstructionCache();
		
		this.exited = false;
		this.paused = false;
		
		valFormatter = ValueFormatters.getHexValueFormatter();
		
		addWindowListener(new DebuggerFrameWindowAdapter());
		
		Container contentPane = getContentPane();
		GroupLayout layout = new GroupLayout(contentPane);
		contentPane.setLayout(layout);
		
		/* create & set up components */
		JLabel memoryLabel = new JLabel("Memory");
		memoryTable = new JTable(new MemoryTableModel());
		JScrollPane memoryPane = new JScrollPane(memoryTable);
		JLabel stackLabel = new JLabel("Stack");
		stackTable = new JTable(new StackTableModel());
		JScrollPane stackPane = new JScrollPane(stackTable);
		stateLabel = new JLabel("State: ");
		JLabel aLabel = new JLabel("A ");
		JLabel bLabel = new JLabel("B ");
		JLabel cLabel = new JLabel("C ");
		JLabel xLabel = new JLabel("X ");
		JLabel yLabel = new JLabel("Y ");
		JLabel zLabel = new JLabel("Z ");
		JLabel iLabel = new JLabel("I ");
		JLabel jLabel = new JLabel("J ");
		JLabel pcLabel = new JLabel("PC ");
		JLabel spLabel = new JLabel("SP ");
		JLabel exLabel = new JLabel("EX ");
		JLabel iaLabel = new JLabel("IA ");
		
		registerFields = new HashMap<Register, JTextField>();
		DebuggerFrameActionListener actionListener = new DebuggerFrameActionListener();
		RegisterFieldFocusListener focusListener = new RegisterFieldFocusListener();
		RegisterFieldInputVerifier verifier = new RegisterFieldInputVerifier();
		Font monoFont = Font.decode("Monospaced");
		
		for(Register reg : Register.values())
		{
			JTextField regField = new JTextField(6);
			regField.putClientProperty("register", reg);
			regField.setActionCommand("setRegister");
			regField.addActionListener(actionListener);
			regField.addFocusListener(focusListener);
			regField.setInputVerifier(verifier);
			regField.setFont(monoFont);
			
			registerFields.put(reg, regField);
		}

		TableCellRenderer cellRenderer = new DefaultTableCellRenderer()
		{
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
			{
				return super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
			}
		};
		
		memoryTable.addMouseListener(new MemoryTableMouseAdapter());
		memoryTable.setShowGrid(false);
		memoryTable.setIntercellSpacing(new Dimension(0, 0));
		memoryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		memoryTable.setDefaultRenderer(Object.class, cellRenderer);
		memoryTable.setFont(monoFont);
		memoryTable.getColumnModel().getColumn(0).setMaxWidth(25);
		memoryTable.setTableHeader(null);
		
		memoryPane.setColumnHeaderView(null);
		memoryPane.setPreferredSize(new Dimension(410, memoryPane.getPreferredSize().height));
		
		stackTable.addMouseListener(new StackTableMouseAdapter());
		stackTable.setShowGrid(false);
		stackTable.setIntercellSpacing(new Dimension(0, 0));
		stackTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		stackTable.setDefaultRenderer(Object.class, cellRenderer);
		stackTable.setFont(monoFont);
		stackTable.getColumnModel().getColumn(0).setMaxWidth(25);
		stackTable.setTableHeader(null);
		
		stackPane.setColumnHeaderView(null);
		stackPane.setPreferredSize(new Dimension(160, memoryPane.getPreferredSize().height));
		stackPane.setMaximumSize(new Dimension(160, memoryPane.getMaximumSize().height));
		
		/* add components to frame */
		contentPane.add(memoryLabel);
		contentPane.add(memoryPane);
		contentPane.add(stackLabel);
		contentPane.add(stackPane);
		contentPane.add(aLabel);
		contentPane.add(bLabel);
		contentPane.add(cLabel);
		contentPane.add(xLabel);
		contentPane.add(yLabel);
		contentPane.add(zLabel);
		contentPane.add(iLabel);
		contentPane.add(jLabel);
		contentPane.add(pcLabel);
		contentPane.add(spLabel);
		contentPane.add(exLabel);
		contentPane.add(iaLabel);
		
		for(JTextField regField : registerFields.values())
		{
			contentPane.add(regField);
		}
		
		/* set up layout */
		/* set up horizontal groups */
		Group horizMemoryGroup =
			layout.createSequentialGroup()
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5)
				.addGroup(layout.createParallelGroup()
					.addComponent(memoryLabel)
					.addComponent(memoryPane))
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5)
				.addGroup(layout.createParallelGroup()
					.addComponent(stackLabel)
					.addComponent(stackPane))
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5);
		
		Group horizStateGroup =
			layout.createSequentialGroup()
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5)
				.addComponent(stateLabel)
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5);
		
		Group horizRegisterGroup =
			layout.createSequentialGroup()
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5)
				.addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
					.addComponent(aLabel)
					.addComponent(xLabel))
				.addGroup(layout.createParallelGroup()
					.addComponent(registerFields.get(Register.A))
					.addComponent(registerFields.get(Register.X)))
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5)
				.addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
					.addComponent(bLabel)
					.addComponent(yLabel))
				.addGroup(layout.createParallelGroup()
					.addComponent(registerFields.get(Register.B))
					.addComponent(registerFields.get(Register.Y)))
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5)
				.addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
					.addComponent(cLabel)
					.addComponent(zLabel))
				.addGroup(layout.createParallelGroup()
					.addComponent(registerFields.get(Register.C))
					.addComponent(registerFields.get(Register.Z)))
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5)
				.addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
					.addComponent(iLabel)
					.addComponent(jLabel))
				.addGroup(layout.createParallelGroup()
					.addComponent(registerFields.get(Register.I))
					.addComponent(registerFields.get(Register.J)))
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5)
				.addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
					.addComponent(pcLabel)
					.addComponent(exLabel))
				.addGroup(layout.createParallelGroup()
					.addComponent(registerFields.get(Register.PC))
					.addComponent(registerFields.get(Register.EX)))
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5)
				.addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
					.addComponent(spLabel)
					.addComponent(iaLabel))
				.addGroup(layout.createParallelGroup()
					.addComponent(registerFields.get(Register.SP))
					.addComponent(registerFields.get(Register.IA)))
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5);
		
		layout.setHorizontalGroup(
			layout.createParallelGroup()
				.addGroup(horizMemoryGroup)
				.addGroup(horizStateGroup)
				.addGroup(horizRegisterGroup));
		
		/* set up vertical groups */
		Group vertMemoryGroup =
			layout.createSequentialGroup()
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5)
				.addGroup(layout.createParallelGroup()
					.addComponent(memoryLabel)
					.addComponent(stackLabel))
				.addGroup(layout.createParallelGroup()
					.addComponent(memoryPane)
					.addComponent(stackPane))
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5);
		
		Group vertStateGroup =
			layout.createSequentialGroup()
				.addComponent(stateLabel)
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5);
		
		Group vertRegisterGroup =
			layout.createSequentialGroup()
				.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
					.addComponent(aLabel)
					.addComponent(registerFields.get(Register.A))
					.addComponent(bLabel)
					.addComponent(registerFields.get(Register.B))
					.addComponent(cLabel)
					.addComponent(registerFields.get(Register.C))
					.addComponent(iLabel)
					.addComponent(registerFields.get(Register.I))
					.addComponent(pcLabel)
					.addComponent(registerFields.get(Register.PC))
					.addComponent(spLabel)
					.addComponent(registerFields.get(Register.SP)))
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5)
				.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
					.addComponent(xLabel)
					.addComponent(registerFields.get(Register.X))
					.addComponent(yLabel)
					.addComponent(registerFields.get(Register.Y))
					.addComponent(zLabel)
					.addComponent(registerFields.get(Register.Z))
					.addComponent(jLabel)
					.addComponent(registerFields.get(Register.J))
					.addComponent(exLabel)
					.addComponent(registerFields.get(Register.EX))
					.addComponent(iaLabel)
					.addComponent(registerFields.get(Register.IA)))
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 5, 5);
				
		layout.setVerticalGroup(
			layout.createSequentialGroup()
				.addGroup(vertMemoryGroup)
				.addGroup(vertStateGroup)
				.addGroup(vertRegisterGroup));
		
		/* set up menu */
		JMenuBar menuBar = new JMenuBar();
		
		JMenu controlMenu = new JMenu("Control");
		controlMenu.setMnemonic(KeyEvent.VK_C);
		menuBar.add(controlMenu);
		
		continueItem = new JMenuItem("Continue", KeyEvent.VK_C);
		continueItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
		continueItem.setActionCommand("continue");
		continueItem.addActionListener(actionListener);
		controlMenu.add(continueItem);
		
		breakItem = new JMenuItem("Break", KeyEvent.VK_B);
		breakItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0));
		breakItem.setActionCommand("break");
		breakItem.addActionListener(actionListener);
		controlMenu.add(breakItem);
		
		stepItem = new JMenuItem("Step", KeyEvent.VK_S);
		stepItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0));
		stepItem.setActionCommand("step");
		stepItem.addActionListener(actionListener);
		controlMenu.add(stepItem);
		
		controlMenu.addSeparator();
		
		JMenuItem exitItem = new JMenuItem("Exit", KeyEvent.VK_X);
		exitItem.setActionCommand("exit");
		exitItem.addActionListener(actionListener);
		controlMenu.add(exitItem);
		
		JMenu settingsMenu = new JMenu("Settings");
		settingsMenu.setMnemonic(KeyEvent.VK_S);
		menuBar.add(settingsMenu);
		
		ButtonGroup valueGroup = new ButtonGroup();
		JRadioButtonMenuItem hexViewItem = new JRadioButtonMenuItem("Hex values");
		hexViewItem.setMnemonic(KeyEvent.VK_H);
		hexViewItem.setSelected(true);
		hexViewItem.setActionCommand("hexValues");
		hexViewItem.addActionListener(actionListener);
		valueGroup.add(hexViewItem);
		settingsMenu.add(hexViewItem);
		
		JRadioButtonMenuItem decViewItem = new JRadioButtonMenuItem("Decimal values");
		decViewItem.setMnemonic(KeyEvent.VK_D);
		decViewItem.setActionCommand("decValues");
		decViewItem.addActionListener(actionListener);
		valueGroup.add(decViewItem);
		settingsMenu.add(decViewItem);
		
		settingsMenu.addSeparator();
		
		JCheckBoxMenuItem stepOverSkippedItem = new JCheckBoxMenuItem("Step over skipped instructions");
		stepOverSkippedItem.setMnemonic(KeyEvent.VK_S);
		stepOverSkippedItem.setActionCommand("stepOverSkipped");
		stepOverSkippedItem.addActionListener(actionListener);
		stepOverSkippedItem.setSelected(true);
		settingsMenu.add(stepOverSkippedItem);
		
		setJMenuBar(menuBar);
		
		pack();
		setMinimumSize(getSize());
	}
	
	@Override
	public boolean init(Cpu cpu, Debugger debugger)
	{
		this.cpu = cpu;
		this.debugger = debugger;
		
		this.expectedPC = cpu.PC();
		this.expectedSP = cpu.SP();
		
		debugger.stepOverSkipped(true);
		
		setVisible(true);
		
		return true;
	}
	
	@Override
	public void paused()
	{
		int row;
		
		if(exited)
		{
			debugger.run();
			return;
		}
		
		paused = true;

		updateAllFields();
		
		/* make sure PC, SP visible & try to make sure they're not too close to the edge of the visible area */
		row = cpu.PC() - ROW_BUFFER;
		final int pcStart = (row < 0 ? 0 : row);
		row = cpu.PC() + ROW_BUFFER;
		final int pcEnd = (row >= memoryTable.getModel().getRowCount() ? memoryTable.getModel().getRowCount() - 1 : row);
		memoryTable.scrollRectToVisible(memoryTable.getCellRect(pcStart, 0, true));
		memoryTable.scrollRectToVisible(memoryTable.getCellRect(pcEnd, 0, true));
		memoryTable.setRowSelectionInterval(cpu.PC(), cpu.PC());
		
		row = cpu.SP() - ROW_BUFFER;
		final int spStart = (row < 0 ? 0 : row);
		row = cpu.SP() + ROW_BUFFER;
		final int spEnd = (row >= stackTable.getModel().getRowCount() ? stackTable.getModel().getRowCount() - 1 : row); 
		stackTable.scrollRectToVisible(stackTable.getCellRect(spStart, 0, true));
		stackTable.scrollRectToVisible(stackTable.getCellRect(spEnd, 0, true));
		stackTable.setRowSelectionInterval(cpu.SP(), cpu.SP());
		
		editMode(true);
		repaint(); /* strangely, the JTables' UI can be put out-of-whack by the scrollRectToVisible() calls above. forcing a repaint fixes it. */ 
	}
	
	/**
	 * Sets or clears edit mode, which enables/disables all edit controls appropriately.
	 * @param enabled true if and only if edit mode is enabled
	 */
	private void editMode(boolean enabled)
	{
		continueItem.setEnabled(enabled);
		breakItem.setEnabled(!enabled);
		stepItem.setEnabled(enabled);
		
		memoryTable.setEnabled(enabled);
		stackTable.setEnabled(enabled);
		
		for(JTextField regField : registerFields.values())
		{
			regField.setEnabled(enabled);
		}
	}
	
	/**
	 * Updates all fields to match the state of the CPU.
	 */
	private void updateAllFields()
	{
		cache.invalidateAll();
		updateStateLabel();
		
		for(Register reg : registerFields.keySet())
		{
			updateRegisterField(reg);
		}
	}
	
	/**
	 * Updates the state label to the current state of the CPU.
	 */
	private void updateStateLabel()
	{
		StringBuilder sb = new StringBuilder();

		final String stateString = (paused && !cpu.error() ? "PAUSED" : cpu.state().toString());
		sb.append("State: ");
		sb.append(stateString);
		
		if(cpu.skip() && !cpu.interruptsEnabled())
		{
			sb.append(" (skipping, interrupts disabled)");
		}
		else if(cpu.skip())
		{
			sb.append(" (skipping)");
		}
		else if(!cpu.interruptsEnabled())
		{
			sb.append(" (interrupts disabled)");
		}
		
		stateLabel.setText(sb.toString());
	}
	
	/**
	 * Updates the field for a given register.
	 * @param register the register whose field will be updated
	 */
	private void updateRegisterField(Register register)
	{
		final JTextField regField = registerFields.get(register);
		
		updateRegisterField(register, regField);
	}
	
	/**
	 * Updates the field for a given register. The arguments are assumed to be consistent with one another.
	 * @param register the register whose field will be updated
	 * @param registerField the field which will be updated
	 */
	private void updateRegisterField(Register register, JTextField registerField)
	{
		final char value = cpu.register(register);
		
		registerField.setText(valFormatter.formatValue(value));
		
		if(register == Register.PC && value != expectedPC)
		{
			((MemoryTableModel)memoryTable.getModel()).fireTableCellUpdated(expectedPC, 0);
			((MemoryTableModel)memoryTable.getModel()).fireTableCellUpdated(value, 0);
			
			expectedPC = value;
		}
		else if(register == Register.SP)
		{
			((StackTableModel)stackTable.getModel()).fireTableCellUpdated(expectedSP, 0);
			((StackTableModel)stackTable.getModel()).fireTableCellUpdated(value, 0);
			
			expectedSP = value;
		}
	}
	
	/**
	 * Attempts to set a register based on the value the user has entered into the register's field.
	 * This method can fail if the user has entered an invalid value, in which case an error popup
	 * will be shown.
	 * @param registerField the field associated with the register to set
	 * @return true if and only if setting the register was successful.
	 */
	private boolean trySetRegister(JTextField registerField)
	{
		return trySetRegister(registerField, false);
	}
	
	/**
	 * Attempts to set a register based on the value the user has entered into the register's field.
	 * This method can fail if the user has entered an invalid value, in which case an error popup
	 * will be shown unless the silentlyFail parameter is set.
	 * @param registerField the field associated with the register to set
	 * @param silentlyFail if set, do not display UI upon failure
	 * @return true if and only if setting the register was successful
	 */
	private boolean trySetRegister(JTextField registerField, boolean silentlyFail)
	{
		final Register reg = (Register)registerField.getClientProperty("register");
		final Character value = interpretValue(registerField.getText());
		
		if(value == null)
		{
			/* input is bogus */
			if(!silentlyFail)
			{
				showInterpretedValueError();
			}
			
			return false;
		}

		cpu.register(reg, value.charValue());
		updateRegisterField(reg, registerField);
		
		return true;
	}
	
	/**
	 * Flips a breakpoint: enables it if it is disabled, and disables it if it is enabled.
	 * @param address the address of the breakpoint to flip
	 */
	private void flipBreakpoint(char address)
	{
		debugger.breakpoint(address, !debugger.breakpoint(address));
		
		((MemoryTableModel)memoryTable.getModel()).fireTableCellUpdated(address, 0);
	}
	
	/**
	 * Prompts the user for a new value to place in memory at a given address, and then sets it. This can fail
	 * if the user enters an invalid value, in which case an error popup will be shown.
	 * @param address the address to set in memory
	 */
	private void trySetMemoryValue(char address)
	{
		final String prompt = String.format("New value for memory at 0x%04X:", (int)address);
		final String valueString = valFormatter.formatValue(cpu.memory(address));
		final String newValueString = (String)JOptionPane.showInputDialog(this, prompt, "Edit Memory Value", JOptionPane.QUESTION_MESSAGE, null, null, valueString);
		
		if(newValueString == null)
		{
			/* user clicked cancel */
			return;
		}
		
		final Character newValue = interpretValue(newValueString);
		
		if(newValue == null)
		{
			/* user entered a bogus value */
			showInterpretedValueError();
			return;
		}
		
		cpu.memory(address, newValue.charValue());
		cache.invalidate(address);
	}
	
	/**
	 * This method shows an error popup appropriate for when the user has entered an invalid memory or register value.
	 */
	private void showInterpretedValueError()
	{
		JOptionPane.showMessageDialog(this, "Could not interpret value. Values should be decimal or hexadecimal numbers between 0 (0x0000) and 65535 (0xFFFF). No changes made.", "Error", JOptionPane.WARNING_MESSAGE);
	}
	
	/**
	 * Attempts to interpret a value entered by the user as a numeric value. Accepts decimal and
	 * hexadecimal input. This method can fail, in which case it will return null.
	 * @param value the value entered by the user
	 * @return the equivalent numeric value, or null on error
	 */
	private Character interpretValue(String value)
	{
		final int interpretedValue;
		
		if(value == null)
		{
			return null;
		}
		
		if(value.startsWith("0x"))
		{
			/* parse as hex */
			
			try
			{
				interpretedValue = Integer.parseInt(value.substring(2), 16);
			}
			catch(NumberFormatException e)
			{
				return null;
			}
		}
		else
		{
			/* parse as dec */
			try
			{
				interpretedValue = Integer.parseInt(value);
			}
			catch(NumberFormatException e)
			{
				return null;
			}
		}
		
		if(interpretedValue < Character.MIN_VALUE || interpretedValue > Character.MAX_VALUE)
		{
			/* out of range */
			return null;
		}
			
		return (char)interpretedValue;
	}
	
	/**
	 * Sets the display to decimal or hexadecimal mode.
	 * @param displayHex if true, display hex values; if false, display decimal
	 */
	private void displayHex(boolean displayHex)
	{
		valFormatter = (displayHex ? ValueFormatters.getHexValueFormatter() : ValueFormatters.getDecValueFormatter());
		
		updateAllFields();
	}
	
	/**
	 * Checks register fields: if any of them have focus, attempts to update the CPU's state
	 * to match what has been entered in the field.
	 * @return false if setting the field failed; true if no field needed to be set, or setting the field succeeded
	 */
	private boolean checkRegisterFields()
	{
		for(JTextField regField : registerFields.values())
		{
			if(regField.hasFocus())
			{
				if(!trySetRegister(regField))
				{
					return false;
				}
				
				break;
			}
		}
		
		return true;
	}
	
	/**
	 * Exits the debug pause and continues running the PC.
	 */
	private void run()
	{
		if(!checkRegisterFields())
		{
			return;
		}
		
		paused = false;
		
		/* clear UI as it will not be accurate */
		for(JTextField regField : registerFields.values())
		{
			regField.setText("");
		}
		((MemoryTableModel)memoryTable.getModel()).fireTableCellUpdated(cpu.PC(), 0);
		((StackTableModel)stackTable.getModel()).fireTableCellUpdated(cpu.SP(), 0);
		updateStateLabel();
		
		editMode(false);
		debugger.run();
	}
	
	/**
	 * Causes the debugger to pause the CPU as soon as possible.
	 */
	private void pause()
	{
		/* don't change editMode here: we must wait for the debugger to tell us to pause before we can actually change anything */
		debugger.pause();
	}
	
	/**
	 * Exits the debug pause and steps the PC.
	 */
	private void step()
	{
		if(!checkRegisterFields())
		{
			return;
		}
		
		/* don't disable UI to avoid flicker; we should pause again very quickly, but even if not, the paused setting will stop the user from making any changes */
		paused = false;
		debugger.step();
	}
	
	/**
	 * Closes the debug UI and effectively shuts down the debugger.
	 */
	private void exit()
	{
		exited = true;
		setVisible(false);
		debugger.clearAllBreakpoints();
		debugger.run();
		dispose();
	}
	
	/**
	 * Table model for the memory table.
	 * @author Brandon Pitman
	 */
	private class MemoryTableModel extends AbstractTableModel
	{
		@Override
		public int getColumnCount() { return 2; }
		
		@Override
		public int getRowCount() { return Cpu.MAX_ADDRESS; }
		
		@Override
		public Object getValueAt(int row, int col)
		{
			switch(col)
			{
			case 0:
				if(paused && row == cpu.PC())
				{
					return CURRENT_LOCATION_MARKER;
				}
				else if(debugger.breakpoint((char)row))
				{
					return BREAKPOINT_MARKER;
				}
				break;
			
			case 1:
				final String decodeString = cache.get((char)row);
				return String.format("[0x%04X] %6s    %s", row, valFormatter.formatValue(cpu.memory((char)row)), decodeString);
			}
			
			return "";
		}
	};
	
	/**
	 * Mouse adapter for the memory table.
	 * @author Brandon Pitman
	 */
	private class MemoryTableMouseAdapter extends MouseAdapter
	{
		@Override
		public void mouseClicked(MouseEvent e)
		{
			if(e.getClickCount() != 2)
			{
				return;
			}
			
			final int row = memoryTable.getSelectedRow();
			final int col = memoryTable.getSelectedColumn();
			
			switch(col)
			{
			case 0:
				flipBreakpoint((char)row);
				break;
			
			case 1:
				if(paused)
				{
					trySetMemoryValue((char)row);
				}
				break;
			}
		}
	}
	
	/**
	 * Table model for the stack table.
	 * @author Brandon Pitman
	 */
	private class StackTableModel extends AbstractTableModel
	{
		@Override
		public int getColumnCount() { return 2; }
		
		@Override
		public int getRowCount() { return Cpu.MAX_ADDRESS; }
		
		@Override
		public Object getValueAt(int row, int col)
		{
			switch(col)
			{
			case 0:
				if(paused && row == cpu.SP())
				{
					return CURRENT_LOCATION_MARKER;
				}
				break;
				
			case 1:
				return String.format("[0x%04X] %6s", row, valFormatter.formatValue(cpu.memory((char)row)));
			}
			
			return "";
		}
	}
	
	/**
	 * Mouse adapter for the stack table.
	 * @author Brandon Pitman
	 */
	private class StackTableMouseAdapter extends MouseAdapter
	{
		@Override
		public void mouseClicked(MouseEvent e)
		{
			if(e.getClickCount() != 2)
			{
				return;
			}
			
			final int row = memoryTable.getSelectedRow();
			final int col = memoryTable.getSelectedColumn();
			
			switch(col)
			{
			case 0:
				/* do nothing */
				break;
			
			case 1:
				if(paused)
				{
					trySetMemoryValue((char)row);
				}
				break;
			}
		}
	}
	
	/**
	 * Focus listener for all of the register fields.
	 * @author Brandon Pitman
	 */
	private class RegisterFieldFocusListener extends FocusAdapter
	{
		@Override
		public void focusLost(FocusEvent e)
		{
			if(!paused || exited)
			{
				/* we get a focus lost event on closing the window, but it doesn't matter */
				return;
			}
			
			/* the input verifier will have already yelled at the user if the input is bad (see RegisterFieldInputVerifier.verify()), so it's okay to silently fail here */
			trySetRegister((JTextField)e.getComponent(), true);
		}
	}
	
	/**
	 * Input verifier for the register fields.
	 * @author Brandon Pitman
	 */
	private class RegisterFieldInputVerifier extends InputVerifier
	{
		@Override
		public boolean verify(JComponent input)
		{
			if(!paused || exited)
			{
				/* we get a focus lost event on closing the window, but it doesn't matter */
				return false;
			}
			
			final JTextField field = (JTextField)input;
			final Character value = interpretValue(field.getText());
			
			if(value == null)
			{
				showInterpretedValueError();
				return false;
			}
			
			return true;
		}
	}
	
	/**
	 * Action listener for all componeents in the DebuggerFrame.
	 * @author Brandon Pitman
	 */
	private class DebuggerFrameActionListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent e)
		{
			String command = e.getActionCommand();
			
			if("setRegister".equals(command))
			{
				if(paused)
				{
					trySetRegister((JTextField)e.getSource());
				}
			} else if("continue".equals(command)) {
				if(paused)
				{
					run();
				}
			} else if("step".equals(command)) {
				if(paused)
				{
					step();
				}
			} else if("break".equals(command)) {
				if(!paused)
				{
					pause();
				}
			} else if("exit".equals(command)) {
				exit();
			} else if("hexValues".equals(command)) {
				displayHex(true);
			} else if("decValues".equals(command)) {
				displayHex(false);
			} else if("stepOverSkipped".equals(command)) {
				debugger.stepOverSkipped(!debugger.stepOverSkipped());
			}
		}
	}
	
	/**
	 * Window adapter for the DebuggerFrame.
	 * @author Brandon Pitman
	 */
	private class DebuggerFrameWindowAdapter extends WindowAdapter
	{
		@Override
		public void windowClosing(WindowEvent e)
		{			
			exit();
		}
	}
	
	/**
	 * Caches information about decoded instructions to make browsing the memory table faster.
	 * Also supports cache invalidation when memory changes. Responsible for notifying the
	 * appropriate UI to update. 
	 * @author Brandon Pitman
	 */
	private class DecodedInstructionCache
	{
		private HashMap<Character, CacheEntry> cache;
		private int maxAddress;
		
		/**
		 * Creates a new DecodedInstructionCache.
		 */
		public DecodedInstructionCache()
		{
			this.cache = new HashMap<Character, CacheEntry>();
			maxAddress = 0;
		}
		
		/**
		 * Invalidates every entry in the cache.
		 */
		public synchronized void invalidateAll()
		{
			cache.clear();
			maxAddress = 0;
			
			((MemoryTableModel)memoryTable.getModel()).fireTableDataChanged();
			((StackTableModel)stackTable.getModel()).fireTableDataChanged();
		}
		
		/**
		 * Invalidates the instruction containing a specific memory address.
		 * @param address the memory address to invalidate
		 */
		public synchronized void invalidate(char address)
		{
			if(address >= maxAddress)
			{
				/* not in the cache */
				return;
			}
			
			CacheEntry entry = cache.get(address);
			
			if(entry == null)
			{
				/* not in the cache */
				return;
			}
			
			/* invalidate this instruction and everything after */
			final int oldMaxAddress = maxAddress;
			maxAddress = entry.baseAddress;
			
			final AbstractTableModel memoryTableModel = (MemoryTableModel)memoryTable.getModel();
			final AbstractTableModel stackTableModel = (StackTableModel)stackTable.getModel();
			
			memoryTableModel.fireTableRowsUpdated(entry.baseAddress, oldMaxAddress);
			stackTableModel.fireTableCellUpdated(address, 1);
		}
		
		/**
		 * Gets a string describing the instruction encoded at a given memory address. For multi-word
		 * instructions, the second-and-on memory address will return a continuation value "...".
		 * @param address the address to get an instruction description for
		 * @return a string describing the instruction at a given address
		 */
		public synchronized String get(char address)
		{
			while(address >= maxAddress)
			{
				decodeNextInstruction();
			}
			
			final CacheEntry entry = cache.get(address);
			
			if(address == entry.baseAddress)
			{
				return entry.value;
			}
			else
			{
				/* this is a value inside of an instruction */
				return " ...";
			}
		}
		
		/**
		 * Decodes the next instruction and stores it into the cache. Starts from memory location 0 and moves to higher addresses.
		 * Assumes that there is an instruction to decode. 
		 * @return the cache entry for the decoded instruction
		 */
		private CacheEntry decodeNextInstruction()
		{
			DecodedInstruction decoded = InstructionDecoder.decode(cpu.memory((char)maxAddress));
			final int wordsUsed;
			final String decodedString;
			
			if(decoded == null)
			{
				/* invalid instruction */
				wordsUsed = 1;
				decodedString = "";
			}
			else
			{
				wordsUsed = decoded.wordsUsed();
				decodedString = decoded.toString(valFormatter, cpu.memory((char)(maxAddress+1)), cpu.memory((char)(maxAddress+2)));
			}
			
			CacheEntry entry = new CacheEntry(decodedString, (char)maxAddress);
			for(int i = 0; i < wordsUsed; ++i)
			{
				cache.put((char)(maxAddress + i), entry);
			}
			
			maxAddress += wordsUsed;
			return entry;
		}
		
		/**
		 * This class represents an entry into the DecodedInstructionCache.
		 * @author Brandon Pitman
		 *
		 */
		private class CacheEntry
		{
			public final String value;
			public final char baseAddress;
			
			/**
			 * Creates a new CacheEntry.
			 * @param value a string representation of the instruction containing this address
			 * @param baseAddress the base address of this instruction (the smallest memory address contained by this instruction)
			 */
			public CacheEntry(String value, char baseAddress)
			{
				this.value = value;
				this.baseAddress = baseAddress;
			}
		}
	}
}
