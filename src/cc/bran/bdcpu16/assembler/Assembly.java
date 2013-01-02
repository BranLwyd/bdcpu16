package cc.bran.bdcpu16.assembler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import cc.bran.bdcpu16.Cpu;
import cc.bran.bdcpu16.codegen.Operand;
import cc.bran.bdcpu16.codegen.Operator;

/**
 * This class represents a DCPU-16 assembly.
 * @author Brandon Pitman
 */
public class Assembly
{
	final List<AssemblyElement> elements;
	final Map<String, AssemblyElement> labels;
	final List<String> errors;
	char[] binary;
	
	/**
	 * Creates a new assembly. Private constructor--use Assembly.parse() instead.
	 */
	private Assembly()
	{
		elements = new ArrayList<AssemblyElement>();
		labels = new HashMap<String, AssemblyElement>();
		errors = new ArrayList<String>();
	}
	
	/**
	 * Gets the binary representation of this assembly, or null if there were errors parsing the assembly.
	 * @return the binary representation of this assembly
	 */
	public char[] binary()
	{
		return binary;
	}
	
	/**
	 * Gets a list of errors that occurred while parsing the assembly.
	 * @return a list of errors that occurred while parsing the assembly
	 */
	public List<String> errors()
	{
		return errors;
	}
	
	/**
	 * Parses assembly text to produce a new assembly object.
	 * @param assemblyText the text of the assembly
	 * @return an assembly object
	 */
	public static Assembly parse(String assemblyText)
	{
		Assembly assembly = new Assembly();
		AssemblyParser parser = new AssemblyParser(assemblyText, assembly);
		
		/* step 1: parse */
		if(!parser.parse())
		{
			return assembly;
		}
		
		/* step 2: nonimmediate literal -> immediate literal promotion */
		if(!assembly.promoteLiterals())
		{
			return assembly;
		}
		
		/* step 3: fix address of each operand */
		if(!assembly.fixAddresses())
		{
			return assembly;
		}
		
		/* step 4: resolve labels to memory addresses */
		if(!assembly.resolveLabels())
		{
			return assembly;
		}
		
		/* step 5: encode in memory */
		if(!assembly.generateBinary())
		{
			return assembly;
		}
		
		return assembly;
	}
	
	/**
	 * Promotes non-immediate literal operands to immediate literal operands when possible. This saves both space and execution time for the instruction.
	 * @return true if the operation succeeded
	 */
	boolean promoteLiterals()
	{
		/* attempt promotion of any literals which already have a value (as opposed to referring to a label) */
		for(AssemblyElement elem : elements)
		{
			if(!(elem instanceof InstructionElement))
			{
				/* ignore non-instructions */
				continue;
			}

			final InstructionElement inst = (InstructionElement)elem;
			
			if(!inst.operandA.operand.literal())
			{
				/* ignore instructions that do not have a literal operand */
				continue;
			}
			
			if(!inst.operandA.value.isLiteral())
			{
				/* ignore literal operands that use a label */
				continue;
			}
			
			final char literal = inst.operandA.value.literal();
			
			/* cast to short so that 0xFFFF is mapped to -1 and is turned into an immediate properly */
			Operand newOperand = Operand.getImmediateLiteralOperand(literal);
			if(newOperand == null)
			{
				/* ignore literals that are too large to be immediate */
				continue;
			}
			
			/* success! switch out the operand and null out the next-word value */
			inst.operandA.operand = newOperand;
		}

		return true;
	}
	
	/**
	 * Fixes the address of each instruction. This precludes the instructions from being changed in the future.
	 * @return true if the operation succeeded
	 */
	boolean fixAddresses()
	{
		int addr = 0;
		for(AssemblyElement elem : elements)
		{
			elem.address = (char)addr;
			
			addr += elem.size();
			if(addr >= Cpu.MAX_ADDRESS)
			{
				/* elements do not fit in memory */
				addError("assembly does not fit in memory");
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Resolves labels, replacing any label-values with literal-values equivalent to the label.
	 * Requires addresses to have been fixed.
	 * @return true if the operation succeeded
	 */
	boolean resolveLabels()
	{
		Map<String, Character> labelAddrMap = new HashMap<String, Character>();
		
		/* determine map label -> address */
		for(Entry<String, AssemblyElement> entry : labels.entrySet())
		{
			if(entry.getValue().address == -1)
			{
				/* unfixed address */
				addError("unfixed address (internal error)");
				return false;
			}
			
			labelAddrMap.put(entry.getKey(), (char)entry.getValue().address);
		}
		
		/* replace all labels with explicit addresses */
		for(AssemblyElement elem: elements)
		{
			if(!elem.resolveLabels(labelAddrMap))
			{
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Generates a binary representation of the assembly, suitable for loading into the DCPU's memory.
	 * @return true if the operation succeeded
	 */
	boolean generateBinary()
	{
		char[] binary = new char[Cpu.MAX_ADDRESS];
		
		for(AssemblyElement elem : elements)
		{
			if(!elem.write(binary))
			{
				return false;
			}
		}

		this.binary = binary;
		return true;
	}
	
	/**
	 * Adds an error to the error list.
	 * @param message the error to add
	 */
	private void addError(String message)
	{
		errors.add(String.format("Error: %s", message));
	}
	
	/**
	 * An assembly element corresponds to a single instruction or directive in the assembly. 
	 * @author Brandon Pitman
	 */
	public abstract class AssemblyElement
	{
		int address;
		
		/**
		 * Creates a new assembly element. 
		 */
		private AssemblyElement()
		{
			address = -1;
		}
		
		/**
		 * Gets the size of the assembly element in words
		 * @return the size of this assembly element in words
		 */
		public abstract int size();
		
		/**
		 * Resolves any labels used by the assembly element.
		 * @param addrMap a complete mapping of labels to addresses
		 * @return true if the operation succeeded
		 */
		abstract boolean resolveLabels(Map<String, Character> addrMap);
		
		/**
		 * Writes the binary representation of the assembly element to an array.
		 * @param target the array to write to
		 * @return true if the operation succeeded
		 */
		abstract boolean write(char[] target);
	}
	
	/**
	 * This is a "dummy" assembly element that emits no code. It is used when
	 * an assembly element is needed for some purpose but there is no appropriate
	 * non-dummy element available. For example, if the assembly contains a label
	 * after all of the instructions/directives, an extra dummy element will be
	 * created to be the target of this label. 
	 * @author Brandon Pitman
	 */
	class DummyElement extends AssemblyElement
	{

		@Override
		public int size()
		{
			return 0;
		}

		@Override
		boolean resolveLabels(Map<String, Character> addrMap)
		{
			return true;
		}

		@Override
		boolean write(char[] target)
		{
			return true;
		}
	}
	
	/**
	 * Represents a DCPU-16 instruction, including any extra words used for
	 * next-word operand values.
	 * @author Brandon
	 */
	class InstructionElement extends AssemblyElement
	{
		public Operator operator;
		public OperandInfo operandA;
		public OperandInfo operandB;
		
		@Override
		public int size()
		{
			return 1 + (operandA.operand.usesWord() ? 1 : 0) + (operandB != null && operandB.operand.usesWord() ? 1 : 0);
		}

		@Override
		boolean resolveLabels(Map<String, Character> addrMap)
		{
			String label;
			
			/* A operand */
			label = operandA.value.label();
			if(label != null)
			{
				if(!addrMap.containsKey(label))
				{
					/* unknown label used */
					return false;
				}
				
				operandA.value.literal(addrMap.get(label));
			}
			
			/* B operand */
			if(operandB == null)
			{
				return true;
			}
			
			label = operandB.value.label();
			if(label != null)
			{
				if(!addrMap.containsKey(label))
				{
					/* unknown label used */
					addError(String.format("unknown label %s", label));
					return false;
				}
				
				operandB.value.literal(addrMap.get(label));
			}
			
			return true;
		}

		@Override
		boolean write(char[] target)
		{
			final char instructionValue;
			if(operator.special())
			{
				instructionValue = (char)((operator.value() << 5) | (operandA.operand.value() << 10));
			}
			else
			{
				instructionValue = (char)(operator.value() | (operandB.operand.value() << 5) | (operandA.operand.value() << 10));
			}
			
			int addr = address;
			target[addr++] = instructionValue;
			
			if(operandA.operand.usesWord())
			{
				target[addr++] = operandA.value.literal();
			}
			
			if(operandB != null && operandB.operand.usesWord())
			{
				target[addr++] = operandB.value.literal();
			}
			
			return true;
		}
	}
	
	/**
	 * Represents information about a single operand in a single DCPU-16 instruction,
	 * including the next-word value if appropriate.
	 * @author Brandon Pitman
	 */
	class OperandInfo
	{
		public Operand operand;
		public final Value value;
		
		/**
		 * Creates a new OperandInfo.
		 */
		public OperandInfo()
		{
			value = new Value();
		}
		
		/**
		 * Creates a new OperandInfo.
		 * @param operand the associated operand
		 */
		public OperandInfo(Operand operand)
		{
			this();
			
			this.operand = operand;
		}
		
		/**
		 * Creates a new OperandInfo.
		 * @param operand the associated operand
		 * @param literalValue the next-word literal value
		 */
		public OperandInfo(Operand operand, char literalValue)
		{
			this(operand);
			
			value.literal(literalValue);
		}
		
		/**
		 * Creates a new OperandInfo.
		 * @param operand the associated operand
		 * @param label the next-word label value
		 */
		public OperandInfo(Operand operand, String label)
		{
			this(operand);
			
			value.label(label);
		}
		
		/**
		 * Creates a new OperandInfo.
		 * @param operand the associated operand
		 * @param literalOrLabel the next-word value (either literal or label)
		 */
		public OperandInfo(Operand operand, Value value)
		{
			this(operand);
			
			if(value != null)
			{
				if(value.isLiteral())
				{
					this.value.literal(value.literal());
				}
				else
				{
					this.value.label(value.label());
				}
			}
		}
	}
	
	/**
	 * Represents a data directive, which places a series of arbitrary words into memory
	 * at the address of the element.
	 * @author Brandon Pitman
	 */
	class DataDirectiveElement extends AssemblyElement
	{
		public final List<Value> elements;
		
		/**
		 * Creates a new data directive.
		 * @param elements the list of values (either literal or label) associated with this directive
		 */
		public DataDirectiveElement(List<Value> elements)
		{
			this.elements = elements;
		}
		
		@Override
		public int size()
		{
			return elements.size();
		}

		@Override
		boolean resolveLabels(Map<String, Character> addrMap)
		{
			for(Value value : elements)
			{
				final String label = value.label();
				if(label != null)
				{
					if(!addrMap.containsKey(label))
					{
						/* unknown label */
						addError(String.format("unknown label %s", label));
						return false;
					}
					
					value.literal(addrMap.get(label));
				}
			}
			
			return true;
		}

		@Override
		boolean write(char[] target)
		{
			int addr = address;
			for(Value value : elements)
			{
				target[addr++] = value.literal();
			}
			
			return true;
		}
	}
	
	/**
	 * This class represents a value in an assembly, which is either a specific 16-bit numerical value or a label.
	 * @author Brandon Pitman
	 */
	static class Value
	{
		private Character literal;
		private String label;

		/**
		 * Creates a new literal value with value 0.
		 */
		public Value()
		{
			this.label = null;
		}
		
		/**
		 * Creates a new literal value with a specific value.
		 * @param literal the literal value to store in this Value
		 */
		public Value(char literal)
		{
			this.literal = literal;
			this.label = null;
		}
		
		/**
		 * Creates a new label value with a specific label.
		 * @param label the label value to store in this Value
		 */
		public Value(String label)
		{
			this.label = label;
		}
		
		/**
		 * Gets the literal value. Should only be called if isLiteral() returns true.
		 * @return the literal value
		 */
		public char literal()
		{
			return literal;
		}
		
		/**
		 * Sets the literal value.
		 * @param literal the literal value to set
		 */
		public void literal(char literal)
		{
			this.literal = literal;
			this.label = null;
		}
		
		/**
		 * Determines if this is a literal value (as opposed to a label value).
		 * @return true if and only if this is a literal value
		 */
		public boolean isLiteral()
		{
			return (this.literal != null);
		}
		
		/**
		 * Gets the label value. Should only be called if isLabel() returns true.
		 * @return the label value
		 */
		public String label()
		{
			return this.label;
		}
		
		/**
		 * Sets the label value.
		 * @param label the label value to set
		 */
		public void label(String label)
		{
			this.label = label;
		}
		
		/**
		 * Determines if this is a label value (as opposed to a literal value).
		 * @return true if and only if this is a label value
		 */
		public boolean isLabel()
		{
			return (this.label != null);
		}
	}
}
