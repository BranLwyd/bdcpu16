bdcpu16
=======

DCPU-16 simulator based on version 1.7 of the DCPU-16 specification. See http://dcpu.com/.

This is currently in active development and probably won't be useful to someone who just wants to play around with the DCPU-16. Fortunately there are plenty of more mature DCPU simulators out there. :-)

Building
--------
Currently, bdcpu exists only as an Eclipse project. (A more robust build system is planned for the future.) You should be able to import the repository into Eclipse as an existing project.

Dependencies
------------
* Java JDK: The default instruction provider compiles each instruction into Java bytecode at run time. Doing this requires the Java JDK to be present at runtime. If this isn't acceptable, there is an alternative instruction provider that allows you to precompile every instruction; using the precompiled instruction provider removes the requirement for the JDK.

Instruction precompilation
--------------------------
The class cc.bran.bdcpu16.codegen.InstructionPrecompiler can be run to generate Java source code for every legal instruction, as well as an InstructionProvider implementation that provides these instructions to the CPU.

Due to the large number of generated classes, it is recommended that you do not include these source files into your IDE as you will likely run into out-of-memory errors. Instead, I recommend compiling the classes manually and including them as a compiled JAR file that you include into your project. Example instructions to compile into a JAR file, assuming that the bdcpu16 project has already been compiled:

1.  Create a temporary directory and switch to it.

        mkdir ~/tmp
        cd ~/tmp

2.  Run the InstructionPrecompiler to compile all of the instructions to Java source code. (You can use the `-help` option to see what command line options are available for the InstructionPrecompiler.)

        java -cp 'c:/users/bran/source code/bdcpu16/bin' cc.bran.bdcpu16.codegen.InstructionPrecompiler

3.  Compile the Java source code into class files.

        javac -d . -cp 'c:/users/bran/source code/bdcpu16/bin' src/cc/bran/bdcpu16/codegen/precompiled/*.java
        javac -d . -cp 'c:/users/bran/source code/bdcpu16/bin' src/cc/bran/bdcpu16/codegen/*.java
        
4.  Place the compiled class files into a JAR and move the JAR somewhere handy.

        jar cvf bdcpu16-precompiled.jar cc
        mv bdcpu16-precompiled.jar '~/Source Code/lib'

5.  Clean up.

        cd ~
        rm -force ~/tmp

You can then use the precompiled instruction provider in cc.bran.bdcpu16.codegen.PrecompiledInstructionProvider by referencing the JAR file. I'll be looking at more automated build solutions in the future, once I'm sure precompiled instructions are the way to go.

Memory usage
------------
Using the precompiled instructions (or using many different dynamically-compiled instructions) will use quite a bit of memory since more than 50K classes are used to represent the instructions. You will need to increase the size of the permanent generation memory in order to avoid out-of-memory issues. I find that 256MB works well. You can pass `-XX:MaxPermSize=256M` to the JVM to do this.
