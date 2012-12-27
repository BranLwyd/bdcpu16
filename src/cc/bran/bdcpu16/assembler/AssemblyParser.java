package cc.bran.bdcpu16.assembler;

import java.util.ArrayList;
import java.util.List;

import cc.bran.bdcpu16.Cpu.Register;
import cc.bran.bdcpu16.assembler.Assembly.AssemblyElement;
import cc.bran.bdcpu16.assembler.Assembly.InstructionElement;
import cc.bran.bdcpu16.assembler.Assembly.OperandInfo;
import cc.bran.bdcpu16.codegen.Operand;
import cc.bran.bdcpu16.codegen.Operator;
import cc.bran.bdcpu16.util.Either;

/**
 * This class contains the logic used to parse human-written assembly
 * into a series of AssemblyElements and labels stored in an Assembly object.
 * @author Brandon Pitman
 */
class AssemblyParser
{
	final String text;
	final Assembly assembly;

	int pos;
	int line;
	
	/**
	 * Creates a new assembly parser with given assembly text, writing to a given Assembly.
	 * @param text the text to assemble
	 * @param assembly the assembly to write to
	 */
	AssemblyParser(String text, Assembly assembly)
	{
		this.text = text.toUpperCase();
		this.assembly = assembly;
		
		pos = 0;
		line = 0;
	};
	
	/**
	 * Parses the text into the assembly object.
	 * @return true if the operation succeeded
	 */
	public boolean parse()
	{
		consumeWhitespace();
		
		while(pos < text.length())
		{
			List<String> labels = consumeLabels();
			if(labels == null)
			{
				addError("problem parsing labels");
				return false;
			}
			
			consumeWhitespace();
			AssemblyElement elem  = null;
			if(pos == text.length()) { elem = assembly.new DummyElement(); }
			if(elem == null) { elem = consumeInstruction(); }
			if(elem == null) { elem = consumeDirective(); }
			if(elem == null)
			{
				addError("problem parsing statement");
				return false;
			}
			
			for(String label : labels)
			{
				assembly.labels.put(label, elem);
			}
			assembly.elements.add(elem);
			consumeWhitespace();
		}
		
		return true;
	}
	
	/**
	 * Consumes one or more labels, which are written as a colon followed by an identifier
	 * or an identifier followed by a colon (ignoring whitespace).
	 * @return a list of labels that were parsed, or null if there was a parsing error
	 */
	private List<String> consumeLabels()
	{
		final int startPos = pos;
		final ArrayList<String> labels = new ArrayList<String>();
		
		while(true)
		{
			boolean gotColon = consume(':');
			
			int preIdentifierPos = pos;
			String label = consumeIdentifier();
			if(label == null)
			{
				if(gotColon)
				{			
					pos = startPos;
					return null;
				}
				else
				{
					pos = preIdentifierPos;
					return labels;
				}
			}
			
			if(!gotColon && !consume(':'))
			{
				pos = preIdentifierPos;
				return labels;
			}
			
			labels.add(label);
			
			consumeWhitespace();
		}
	}
	
	/**
	 * Consumes a single instruction.
	 * @return an AssemblyElement corresponding to the parsed instruction, or null if there was a parsing error
	 */
	private AssemblyElement consumeInstruction()
	{
		final int startPos = pos;
		
		Operator operator;
		OperandInfo firstOperand, secondOperand;

		String operatorString = consumeIdentifier();
		if(operatorString == null)
		{
			return null;
		}
		
		try
		{
			operator = Operator.valueOf(operatorString);
		}
		catch(IllegalArgumentException ex)
		{
			/* what a vexing exception */
			pos = startPos;
			return null;
		}

		consumeWhitespace();
		
		if((firstOperand = consumeOperand(!operator.singleOperand())) == null)
		{
			pos = startPos;
			return null;
		}
		
		if(operator.singleOperand())
		{
			InstructionElement elem = assembly.new InstructionElement();
			elem.operator = operator;
			elem.operandA = firstOperand;
			
			return elem;
		}
		
		consumeWhitespace();

		if(!consume(','))
		{
			pos = startPos;
			return null;
		}
		
		consumeWhitespace();

		if((secondOperand = consumeOperand(false)) == null)
		{
			pos = startPos;
			return null;
		}
		
		InstructionElement elem = assembly.new InstructionElement();
		elem.operator = operator;
		elem.operandA = secondOperand;
		elem.operandB = firstOperand;
		return elem;
	}
	
	/**
	 * Consumes a single directive.
	 * @return an AssemblyElement correspodning to the directive, or null if there was a parsing error
	 */
	private AssemblyElement consumeDirective()
	{
		final int startPos = pos;
		AssemblyElement elem;

		String directiveString = consumeIdentifier();
		if(directiveString == null)
		{
			return null;
		}
		
		switch(directiveString)
		{
		case "DAT": elem = consumeDataDirective(); break;
		
		default: elem = null;
		}
		
		if(elem == null)
		{
			pos = startPos;
		}
		
		return elem;
	}

	/**
	 * Consumes a data directive (not counting the inital "DAT"). A data directive is made up of a list of
	 * literal values, labels, and strings.
	 * @return an assembly element corresponding to the data element, or null if there was a parsing error
	 */
	public AssemblyElement consumeDataDirective()
	{
		final int startPos = pos;
		ArrayList<Either<Character, String>> elems = new ArrayList<Either<Character, String>>();
		
		do
		{
			consumeWhitespace();
			
			Character numericValue;
			String stringValue;
			
			/* we're looking for a list of numbers, strings, or labels */
			if((numericValue = consumeNumber()) != null)
			{
				Either<Character, String> elem = new Either<Character, String>();
				elem.left(numericValue);
				elems.add(elem);
			}
			else if((stringValue = consumeString()) != null)
			{
				for(int i = 0; i < stringValue.length(); ++i)
				{
					Either<Character, String> elem = new Either<Character, String>();
					elem.left(stringValue.charAt(i));
					elems.add(elem);
				}
			}
			else if((stringValue = consumeIdentifier()) != null)
			{
				Either<Character, String> elem = new Either<Character, String>();
				elem.right(stringValue);
				elems.add(elem);
			}
			else
			{
				pos = startPos;
				return null;
			}
			
			consumeWhitespace();
		} while(consume(','));

		return assembly.new DataDirectiveElement(elems);
	}
	
	/**
	 * Consumes an operand, which should be one of the forms used in the DCPU-16 specification.
	 * @param isB is this the B operand?
	 * @return an OperandInfo corresponding to the parsed operand, or null if there was a parsing error
	 */
	private OperandInfo consumeOperand(boolean isB)
	{
		final int startPos = pos;
		
		/* attempt to consume special operands: PUSH, POP, PEEK, PICK, [--SP], and [SP++] */
		String ident = consumeIdentifier();
		if(ident != null)
		{
			if(isB && "PUSH".equals(ident)) { return assembly.new OperandInfo(Operand.getPushOperand()); }
			if(!isB && "POP".equals(ident)) { return assembly.new OperandInfo(Operand.getPopOperand()); }
			if("PEEK".equals(ident)) { return assembly.new OperandInfo(Operand.getOperand(true, false, Register.SP)); }
			
			if("PICK".equals(ident))
			{
				consumeWhitespace();
				Character number = consumeNumber();
				
				if(number != null)
				{
					return assembly.new OperandInfo(Operand.getOperand(true, true, Register.SP), number);
				}
			}
			
			/* none of the special identifiers match */
			pos = startPos;
		}
		
		/* [--SP] and [SP++] as aliases for PUSH, POP */
		if(isB && consume('[') && consumeWhitespace() && consume("--") && consumeWhitespace() && consume("SP") && consumeWhitespace() && consume(']'))
		{
			return assembly.new OperandInfo(Operand.getPushOperand());
		}
		pos = startPos;
		
		if(!isB && consume('[') && consumeWhitespace() && consume("SP") && consumeWhitespace() & consume("++") && consumeWhitespace() && consume(']'))
		{
			return assembly.new OperandInfo(Operand.getPopOperand());
		}
		pos = startPos;

		/* all special forms exhausted -- try to consume standard operand expression */
		boolean isMemoryRef = false;
		Register register = null;
		Either<Character, String> literalOrLabel = null;
		
		if(consume('['))
		{
			isMemoryRef = true;
			consumeWhitespace();
		}
		
		do
		{
			final int pieceStartPos = pos;
			
			/* try to consume a label or a register */
			ident = consumeIdentifier();
			if(ident != null)
			{
				/* is this a register? */
				try
				{
					Register reg = Register.valueOf(ident);
					
					if(register != null)
					{
						/* we can't have two registers in a single operand */
						pos = startPos;
						return null;
					}
					
					register = reg;
					continue;
				}
				catch(IllegalArgumentException ex) { /* ignore */ }
				
				/* if not, must be a label */
				if(literalOrLabel != null)
				{
					/* we can't have two literal/labels */
					pos = startPos;
					return null;
				}
				
				literalOrLabel = new Either<Character, String>();
				literalOrLabel.right(ident);
				continue;
			}
			pos = pieceStartPos;
			
			/* try to consume a literal value */
			Character literalValue = consumeNumber();
			if(literalValue == null)
			{
				pos = startPos;
				return null;
			}
			
			if(literalOrLabel != null)
			{
				/* we can't have two literals/labels */
				pos = startPos;
				return null;
			}
			
			literalOrLabel = new Either<Character, String>();
			literalOrLabel.left(literalValue);
		} while(consumeWhitespace() && consume('+') && consumeWhitespace());
		
		if(isMemoryRef && consumeWhitespace() && !consume(']'))
		{
			pos = startPos;
			return null;
		}
		
		Operand operand = Operand.getOperand(isMemoryRef, literalOrLabel != null, register);
		if(operand == null)
		{
			/* operand is legal in form, but doesn't exist for DCPU-16. */
			return null;
		}
		
		return assembly.new OperandInfo(operand, literalOrLabel);
	}
	
	/**
	 * Consumes a string.
	 * @return the string, or null if there was a parsing error
	 */
	private String consumeString()
	{
		final int startPos = pos;
		StringBuilder sb = new StringBuilder();
		
		if(!consume('"'))
		{
			return null;
		}
		
		while(pos < text.length())
		{
			char ch = text.charAt(pos++);
			
			if(ch == '"')
			{
				break;
			}
			
			if(ch == '\\')
			{
				Character escCh = consume();
				if(escCh == null)
				{
					pos = startPos;
					return null;
				}
				
				switch(escCh)
				{
				case 'b':  sb.append('\b'); break;
				case 'f':  sb.append('\f'); break;
				case 'n':  sb.append('\n'); break;
				case 'r':  sb.append('\r'); break;
				case 't':  sb.append('\t'); break;
				case '"':  sb.append('"');  break;
				case '\\': sb.append('\\'); break;
				
				default:   pos = startPos;  return null;
				}
				
				continue;
			}
			
			if(ch < 0x20 || ch > 0x7e)
			{
				/* only printable ASCII characters are permitted */
				pos = startPos;
				return null;
			}
			
			sb.append(ch);
		}
		
		return sb.toString();
	}
	
	/**
	 * Consumes a number. Numbers may be specified in decimal or hexadecimal format.
	 * @return the consumed number, or null if there was a parsing error
	 */
	private Character consumeNumber()
	{
		final int startPos = pos;

		boolean negative = consume('-');
		
		final String allowedCharacters;
		final int radix;
		if(consume("0X"))
		{
			allowedCharacters = "0123456789ABCDEF";
			radix = 16;
		}
		else
		{
			allowedCharacters = "0123456789";
			radix = 10;
		}
		
		String digits = consumeWhileIn(allowedCharacters);
		if(digits == null)
		{
			pos = startPos;
			return null;
		}
		
		int parsedValue = 0;
		try
		{
			parsedValue = Integer.parseInt(digits, radix);
		}
		catch(NumberFormatException ex)
		{
			pos = startPos;
			return null;
		}
		
		return (char)((negative ? -1 : 1) * parsedValue);
	}
	
	/**
	 * Consumes a (Java) identifier.
	 * @return the identifier, or null if there was a parsing error
	 */
	private String consumeIdentifier()
	{
		if(pos == text.length() || !Character.isJavaIdentifierStart(text.charAt(pos)))
		{
			return null;
		}
		
		int length = 1;
		while(pos + length < text.length() && Character.isJavaIdentifierPart(text.charAt(pos + length)))
		{
			length++;
		}
		
		pos += length;
		return text.substring(pos - length, pos);
	}
	
	/**
	 * Consumes any available whitespace.
	 * @return true
	 */
	private boolean consumeWhitespace()
	{
		return consumeWhitespace(false);
	}
	
	/**
	 * Consumes any available whitespace.
	 * @param required true if whitespace is required
	 * @return true if either the required flag is not set, or if some whitespace was consumed
	 */
	private boolean consumeWhitespace(boolean required)
	{
		boolean consumed;
		boolean everConsumed = false;
		
		do
		{
			consumed = false;

			/* attempt to consume actual whitespace */
			if(pos < text.length() && Character.isWhitespace(text.charAt(pos)))
			{
				if(text.charAt(pos) == '\n') { line++; }
				
				consumed = true;
				while(++pos < text.length() && Character.isWhitespace(pos));
			}

			/* attempt to consume a comment */
			if(consume(';'))
			{
				consumed = true;
				while(pos < text.length() && text.charAt(pos++) != '\n');
				if(pos < text.length()) { line++; }
			}
		} while(consumed && (everConsumed = true));
		
		return (!required || everConsumed);
	}

	/**
	 * Consumes a single character.
	 * @param match the character to consume
	 * @return true on success
	 */
	private boolean consume(char match)
	{
		if(pos == text.length() || text.charAt(pos) != match)
		{
			return false;
		}
		
		pos++;
		return true;
	}
	
	/**
	 * Consumes a string.
	 * @param match the string to consume
	 * @return true on success
	 */
	private boolean consume(String match)
	{
		if(pos + match.length() > text.length() || !match.equals(text.substring(pos, pos + match.length())))
		{
			return false;
		}
		
		pos += match.length();
		return true;
	}
	
	/**
	 * Consumes the next character.
	 * @return the character consumed, or null if there is no next character
	 */
	private Character consume()
	{
		if(pos == text.length())
		{
			return null;
		}
		
		return text.charAt(pos++);
	}
	
	/**
	 * Consumes a series of characters as long as they are within a specified character set.
	 * @param characterSet the character set to consume
	 * @return a string containing the consumed characters, or null if no characters could be consumed
	 */
	private String consumeWhileIn(String characterSet)
	{
		int length = 0;
		while(pos < text.length() && characterSet.indexOf(text.charAt(pos)) != -1)
		{
			pos++;
			length++;
		}
		
		if(length == 0)
		{
			return null;
		}
		
		return text.substring(pos - length, pos);
	}
	
	/**
	 * Adds an error to the assembly.
	 * @param message the error message
	 */
	private void addError(String message)
	{
		assembly.errors.add(String.format("Error: %s (on line %d)", message, line));
	}
}