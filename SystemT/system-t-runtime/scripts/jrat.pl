#!/usr/bin/perl
###############################################################################
# jrat.pl
#
# Script for profiling with JRat
#

use strict;
use warnings;

# Additional stuff to add to the classpath
my @CLASSPATH_ELEMS = (
	"./bin",
	"./lib/derby.jar",
	"./lib/derbynet.jar",
	"./lib/derbytools.jar",
	"./lib/junit-4.1.jar",
	"./lib/avatar/aomIngester.jar",
	"./lib/avatar/avatarBase.jar",
	"./lib/avatar/avatarUtils.jar",
	"../resources/libs/avatar/avatarAnnotators.jar",
	"../resources/libs/avatar/avatarUtils.jar",
	"../resources/libs/multilingual/dlt.jar",
	"../resources/libs/multilingual/icu4j-51_2.jar"
);

# TODO: JAVA_HOME for Linux
#my $JAVA_HOME = "C:\\Program Files\\Java\\jdk1.6.0_01\\";
#my $JAVA_HOME = $ENV{"HOME"} . "/lib/ibm-java2-i386-50";
#my $JAVA_HOME = $ENV{"HOME"} . "/lib/jdk1.6.0_01";

my $JAVA_HOME = "/usr/lib/jvm/java-5-ibm";

my $JAVA = "$JAVA_HOME/bin/java";

# Location of the JRat library
my $JRAT_JAR = "./lib/shiftone-jrat.jar";

#my $JAVA_OPT = "-Xmx700m";
#my $JAVA_OPT = "-Xrunjavaperf";
#my $JAVA_OPT = "-Xrunhprof:cpu=samples,depth=3 -Xmx2G";
#my $JAVA_OPT = "-Xrunhprof:heap=sites";
my $JAVA_OPT = "-javaagent:$JRAT_JAR -Xmx2G";

#my $RUNJAVA = "'${JAVA_HOME}/bin/java' $JAVA_OPT";
my $RUNJAVA = "$JAVA $JAVA_OPT";

#my $NICE = "/usr/bin/nice --adjustment=20";

#my $CLASS = "com.ibm.avatar.aog.test.AOGAnnotatorLWTest";
#my $CLASS = "com.ibm.avatar.algebra.test.SQLEnronTests";
my $CLASS = "com.ibm.avatar.algebra.test.SpeedTests";

my $cp = $ENV{"CLASSPATH"};
foreach my $cpelem (@CLASSPATH_ELEMS) {
	$cp .= ":$cpelem";
}

print STDERR "Classpath is: $cp\n";

my $cmd = "$RUNJAVA -cp '$cp' $CLASS";

print STDERR "Running:\n$cmd\n";

system $cmd;

