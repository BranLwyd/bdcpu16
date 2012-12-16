bdcpu16
=======
DCPU-16 simulator based on version 1.7 of the DCPU-16 specification. See http://dcpu.com/.

This is currently in active development and probably won't be useful to someone who just wants to play around with the DCPU-16. Fortunately there are plenty of more mature DCPU simulators out there. :-)

Building
--------
This project is built using Apache Ant (http://ant.apache.org/). Once you have Ant installed, change to the root directory of the repository and type:

    ant

This will produce a jar file in bin/bdcpu16.jar. If you want to precompile all of the instructions (see below), you can instead type:

    ant make-precompiled

As well as bin/bdcpu16.jar, this will also produce a bin/bdcpu16-precompiled.jar. Note that instruction precompilation can take some time as it produces a compiled class file for every possible DCPU-16 instruction.

Dependencies
------------
* Java JDK: The default instruction provider compiles each instruction into Java bytecode at run time. Doing this requires the Java JDK to be present at runtime. If this isn't acceptable, there is an alternative instruction provider that allows you to precompile every instruction; using the precompiled instruction provider removes the requirement for the JDK.

Instruction precompilation
--------------------------
By default, instructions are compiled to Java bytecode as they are run. However, the cc.bran.bdcpu16.codegen.InstructionPrecompiler class can be run to generate Java source code for every legal instruction, as well as an InstructionProvider implementation that provides these instructions to the CPU.

Due to the large number of generated classes, it is recommended that you do not include these source files into your IDE as you will likely run into out-of-memory errors. Instead, I recommend loading these files into your IDE as a compiled JAR file.

To produce a jar, you can run `ant make-precompiled` from the root directory of the repository. This will generate code for the instructions and produce a precompiled jar in bin/bdcpu16-precompiled.jar.

You can then use the precompiled instruction provider in cc.bran.bdcpu16.precompiled.PrecompiledInstructionProvider by referencing the JAR file. The instruction provider should be passed to the CPU object during construction.

Memory usage
------------
Using the precompiled instructions (or using many different dynamically-compiled instructions) will use quite a bit of memory since more than 50K classes are used to represent the instructions. You will need to increase the size of the permanent generation memory in order to avoid out-of-memory issues. I find that 256MB works well. You can pass `-XX:MaxPermSize=256M` to the JVM to do this.
