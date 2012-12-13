bdcpu16
=======

DCPU-16 simulator based on version 1.7 of the DCPU-16 specification. See http://dcpu.com/.

This is currently in active development and probably won't be useful to someone who just wants to play around with the DCPU-16. Fortunately there are plenty of more mature DCPU simulators out there. :-)

Requirements:
* Java JDK: The default instruction provider compiles each instruction into Java bytecode at run time. Doing this requires the Java JDK to be present at runtime. If this isn't acceptable, there is an alternative instruction provider that allows you to precompile every instruction; using this instruction provider will allow you to run this on the JRE rather than the JDK.

Precompiling instructions
-------------------------
The class cc.bran.bdcpu16.codegen.InstructionPrecompiler can be run to generate Java source code for every legal instruction, as well as an InstructionProvider implementation that provides these instructions to the CPU.

Due to the number of generated classes, it is recommended that you do not include these source files into your IDE--you may run into out-of-memory issues. Instead, I recommend compiling these manually and including them as a compiled JAR file that you include in the main project. Example instructions to compile into a JAR file, assuming that the bdcpu16 project has already been compiled:
1.  Create a temporary directory and switch to it.

        mkdir ~/tmp
        cd ~/tmp

2.  Run the InstructionPrecompiler to compile all of the instructions to Java source code. (Try using the `-help` option to see what command line options are available for the InstructionPrecompiler.)

        java -cp 'c:/users/bran/source code/bdcpu16/bin' cc.bran.bdcpu16.codegen.InstructionPrecompiler

3.  Compile the Java source code into class files.

        mkdir bin
        javac -d bin -cp 'c:/users/bran/source code/bdcpu16/bin' src/cc/bran/bdcpu16/codegen/precompiled/*.java
        javac -d bin -cp 'c:/users/bran/source code/bdcpu16/bin' src/cc/bran/bdcpu16/codegen/*.java
        
4.  Place the compiled class files into a JAR and move the JAR somewhere handy.

        cd bin
        jar cvf bdcpu16-precompiled.jar *
        mv bdcpu16-precompiled.jar '~/Source Code/lib'

5.  Clean up.

        cd ~
        rm -force ~/tmp

Then you can use the precompiled instruction provider in cc.bran.bdcpu16.codegen.PrecompiledInstructionProvider by referencing the JAR file. I'll be looking at more automated build solutions in the future, once I'm sure precompiled instructions are the way to go.

Memory usage
------------
Using the precompiled instructions (or using many different dynamically-compiled instructions) will use quite a bit of memory since more than 50K classes are used to represent the instructions. You will need to increase the size of the permanent generation memory in order to avoid out of memory issues. I find that 256MB works well. You can pass `-XX:MaxPermSize=256M` to the JVM to do this.