#!/usr/bin/perl
###############################################################################
# offline.pl
#
# Script for running programs without an Eclipse instance.  Assumes that 
# you have used ant to compile build/avatarAlgebra.jar
#

use strict;
use warnings;

# Additional stuff to add to the classpath
my @CLASSPATH_ELEMS = (
	"./build/avatarAlgebra.jar",
	"../resources/libs/multilingual/dlt.jar",
	"../resources/libs/multilingual/icu4j-51_2.jar",
        "../resources/libs/junit-4.4/junit-4.4.jar"
);

#my $JAVA_HOME = "C:\\Program Files\\Java\\jdk1.6.0_01\\";
#my $JAVA_HOME = $ENV{"HOME"} . "/lib/ibm-java2-i386-50";
#my $JAVA_HOME = $ENV{"HOME"} . "/lib/jdk1.6.0_01";

#my $JAVA_HOME = $ENV{"JAVA_HOME"};
#my $JAVA_HOME = "/home/freiss/jdk1.6.0_03";
my $JAVA_HOME = "/usr/lib/jvm/java-6-sun";
#my $JAVA_HOME = "/usr/lib/jvm/java-6-ibm";
#my $JAVA_HOME = "/usr/lib/jvm/java-5-ibm";
#my $JAVA_HOME = "/usr/lib/jvm/java-6-32bit-sun";
#my $JAVA_HOME = "/usr/lib/jvm/java-5-ibm";
#my $JAVA_HOME = "/usr/lib/jvm/java-5-32bit-ibm";
#my $JAVA_HOME = "/usr/lib/jvm/jre-openjdk";

my $JAVA = "$JAVA_HOME/bin/java";

# Options copied from 
# http://code.google.com/p/the-cassandra-project/source/browse/
# trunk/bin/start-server
my $JAVA_OPT = "-Xms2G -Xmx2G -Xmn256M  -XX:SurvivorRatio=8 -XX:TargetSurvivorRatio=90 -XX:+AggressiveOpts -XX:+UseParNewGC -XX:+UseConcMarkSweepGC";
#my $JAVA_OPT = "-Xms1G -Xmx1G";
#my $JAVA_OPT = "-Xrunjavaperf";
#my $JAVA_OPT = "-Xrunhprof:cpu=samples,depth=4 -Xmx2G";
#my $JAVA_OPT = "-Xrunhprof:heap=sites";

#my $RUNJAVA = "'${JAVA_HOME}/bin/java' $JAVA_OPT";
my $RUNJAVA = "$JAVA $JAVA_OPT";

#my $NICE = "/usr/bin/nice --adjustment=20";

#my $CLASS = "com.ibm.avatar.aog.test.AOGAnnotatorLWTest";
#my $CLASS = "com.ibm.avatar.algebra.test.SQLEnronTests";
#my $CLASS = "com.ibm.avatar.algebra.test.SpeedTests";
#my $CLASS = "com.ibm.avatar.algebra.test.RegexTests";
my $CLASS = "com.ibm.avatar.algebra.test.stable.MultiCoreTests";

# Generate the classpath from scratch.
my $cp = join ':', @CLASSPATH_ELEMS;

#my $cp = $ENV{"CLASSPATH"};
#foreach my $cpelem (@CLASSPATH_ELEMS) {
#	$cp .= ":$cpelem";
#}

print STDERR "Classpath is: $cp\n";

my $cmd = "$RUNJAVA -cp '$cp' $CLASS";

print STDERR "Running:\n$cmd\n";

system $cmd;

