package cc.bran.bdcpu16.codegen;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.security.SecureClassLoader;
import java.util.ArrayList;
import java.util.List;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import cc.bran.bdcpu16.IllegalInstruction;
import cc.bran.bdcpu16.Instruction;
import cc.bran.bdcpu16.InstructionProvider;

public class InstructionCompiler implements InstructionProvider
{
	private static final String PACKAGE = "cc.bran.bdcpu16.instructions";
	private static final String SIMPLE_CLASS_NAME = "CompiledInstruction";
	
	private static int nextClassNum = 0;
	
	private static Instruction[] instructionCache = new Instruction[65536];
	
	@Override
	public Instruction getInstruction(char instructionValue)
	{
		if(instructionCache[instructionValue] == null)
		{
			instructionCache[instructionValue] = compileInstruction(instructionValue);
		}
		
		return instructionCache[instructionValue];
	}
	
	/**
	 * Compiles an instruction.
	 * @param instructionValue the instruction value to compile
	 * @return the compiled instruction
	 */
	private static Instruction compileInstruction(char instructionValue)
	{
		final String simpleClassName = String.format("%s%d", SIMPLE_CLASS_NAME, nextClassNum++);
		final String className = String.format("%s.%s", PACKAGE, simpleClassName);
		
		String code = getCodeForInstruction(PACKAGE, simpleClassName, instructionValue, true);
		if(code == null)
		{
			return IllegalInstruction.getInstance();
		}
		
		Instruction compiledInstruction = null;
		
		try
		{
			/* this code, and the supporting classes below, taken from http://www.javablogging.com/dynamic-in-memory-compilation/ */
			
			JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
			JavaFileManager fileManager = new ClassFileManager(compiler.getStandardFileManager(null, null, null));
			List<JavaFileObject> sourceFiles = new ArrayList<JavaFileObject>();
			sourceFiles.add(new CharSequenceJavaFileObject(className, code));
			compiler.getTask(null, fileManager, null, null, null, sourceFiles).call();
			compiledInstruction = (Instruction)fileManager.getClassLoader(null).loadClass(className).newInstance();
		}
		catch(ClassNotFoundException ex)
		{
			ex.printStackTrace();
			System.exit(1);
		}
		catch(InstantiationException ex)
		{
			ex.printStackTrace();
			System.exit(1);
		}
		catch(IllegalAccessException ex)
		{
			ex.printStackTrace();
			System.exit(1);
		}
		
		return compiledInstruction;
	}
	
	/**
	 * Determines if a given instruction value is legal, without generating code for it. If you want to generate code, it is more efficient to call getCodeForInstruction() directly and the return value against null.
	 * @param instructionValue the instruction value
	 * @return true if and only if the instruction value corresponds to a legal instruction
	 */
	static boolean isInstructionLegal(char instructionValue)
	{
		int operatorValue = instructionValue & 0x1f;
		if(operatorValue == 0)
		{
			return (Operator.getSpecialOperator((instructionValue >> 5) & 0x1f) != null);
		}
		else
		{
			return (Operator.getNormalOperator(operatorValue) != null);
		}
	}
	
	/**
	 * Generates Java code for a class that executes a given instruction value.
	 * @param simpleClassName the simple class name (sans package) to place code in
	 * @param instructionValue the instruction value to generate code for
	 * @param publicClass if True, a public class will be generated; if not, no access modifier (package private)
	 * @return code for a class that executes the given instruction, or null if the instruction is illegal
	 */
	static String getCodeForInstruction(String packageName, String simpleClassName, char instructionValue, boolean publicClass)
	{	
		/* decode instruction */
		Operator operator;
		Operand operandA, operandB;
		
		int operatorValue = instructionValue & 0x1f;
		if(operatorValue == 0)
		{
			/* special instruction */
			operator = Operator.getSpecialOperator((instructionValue >> 5) & 0x1f);
			operandA = Operand.getOperand((instructionValue >> 10) & 0x3f, false);
			operandB = null;
		}
		else
		{
			/* normal instruction */
			operator = Operator.getNormalOperator(operatorValue);
			operandA = Operand.getOperand((instructionValue >> 10) & 0x3f, false);
			operandB = Operand.getOperand((instructionValue >> 5) & 0x1f, true);
		}
		
		/* check if illegal instruction */
		if(operator == null)
		{
			return null;
		}
		
		/* compute statistics */
		final int totalWordsUsed = 1 + operandA.wordsUsed() + (operandB == null ? 0 : operandB.wordsUsed());
		final int deltaSP = operandA.deltaSP() + (operandB == null ? 0 : operandB.deltaSP());

		/* generate code */
		StringBuilder sb = new StringBuilder();
		sb.append("package ");
		sb.append(packageName);
		sb.append(";");
		
		sb.append("import cc.bran.bdcpu16.Cpu;");
		sb.append("import cc.bran.bdcpu16.Instruction;");
		sb.append("import cc.bran.bdcpu16.hardware.Device;");
		
		if(publicClass)
		{
			sb.append("public ");
		}
		sb.append("class ");
		sb.append(simpleClassName);
		sb.append(" implements Instruction {");
		
		sb.append("public int wordsUsed() { return ");
		sb.append(totalWordsUsed);
		sb.append("; }");
		
		sb.append("public boolean illegal() { return false; }");
		
		sb.append("public boolean conditional() { return ");
		sb.append(operator.isConditional());
		sb.append("; }");
		
		sb.append("public int execute(Cpu cpu) { ");
		if(deltaSP != 0)
		{
			sb.append(String.format("cpu.SP((char)(cpu.SP()+(%d)));", deltaSP));
		}
		sb.append(operator.operatorSpecificCode(operandA, operandB));
		if(operator.generateReturn())
		{
			sb.append("return ");
			sb.append(totalWordsUsed);
			sb.append(";");
		}
		sb.append("}");
		
		sb.append("}");
		
		return sb.toString();
	}
	
	private static class ClassFileManager extends ForwardingJavaFileManager<StandardJavaFileManager>
	{
		private JavaClassObject classObject;
		
		public ClassFileManager(StandardJavaFileManager standardManager)
		{
			super(standardManager);
		}
		
		@Override
		public ClassLoader getClassLoader(Location location)
		{
			return new SecureClassLoader()
			{
				@Override
				protected Class<?> findClass(String name) throws ClassNotFoundException
				{
					byte[] b = classObject.getBytes();
					return super.defineClass(name, classObject.getBytes(), 0, b.length);
				}
			};
		}
		
		@Override
		public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind, FileObject sibling) throws IOException
		{
			classObject = new JavaClassObject(className, kind);
			return classObject;
		}
	}
	
	private static class JavaClassObject extends SimpleJavaFileObject
	{
		protected final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		public JavaClassObject(String name, Kind kind)
		{
			super(URI.create("string:///" + name.replace('.', '/') + kind.extension), kind);
		}
		
		public byte[] getBytes()
		{
			return bos.toByteArray();
		}
		
		@Override
		public OutputStream openOutputStream() throws IOException
		{
			return bos;
		}
	}
	
	private static class CharSequenceJavaFileObject extends SimpleJavaFileObject
	{
		private CharSequence content;
		
		protected CharSequenceJavaFileObject(String className, CharSequence content)
		{
			super(URI.create("string:///" + className.replace('.',  '/') + Kind.SOURCE.extension), Kind.SOURCE);
			this.content = content;
		}
		
		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors)
		{
			return content;
		}	
	}
}
