#!/bin/bash

export CLASSPATH=${CLASSPATH}:./derby.jar:./derbynet.jar:./derbytools.jar
export CLASSPATH=${CLASSPATH}:./junit-4.1.jar
export CLASSPATH=${CLASSPATH}:./automaton.jar
export CLASSPATH=${CLASSPATH}:../AOMIngester

# jconsole (doesn't do any method profiling :( )
#java -Dcom.sun.management.jmxremote com.ibm.avatar.algebra.test.EnronTests

# HProf (Currently doesn't provide any useful information :( )
#java -Xrunhprof:heap=dump,cpu=samples com.ibm.avatar.algebra.test.EnronTests

# Jive
#./jivejava edu.brown.bloom.jive.JiveMain -JC $CLASSPATH \
#    com.ibm.avatar.algebra.test.EnronTests

# Jove
#./jivejava edu.brown.bloom.jove.JoveMain -JC $CLASSPATH \
#    com.ibm.avatar.algebra.test.EnronTests

./jivejava edu.brown.bloom.jove.JoveMain -JC $CLASSPATH RegexTest

