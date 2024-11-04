#!/usr/bin/perl
###############################################################################
# bench.pl
#
# Dedicated script for running the Benchmarks class.
#

use strict;
use warnings;

# Additional stuff to add to the classpath
my @CLASSPATH_ELEMS = (
	"./bin",
	"./lib/SimpleRegex/systemT_regex.jar",
	"./lib/htmlparser-2.0-ibm/enc-htmlparser.jar",
        "./lib/commons-codec-1.4/commons-codec-1.4.jar",

        "./lib/uima-2.3.0/uima-core.jar",
#        "./lib/uima-2.2.2-fp1/uima-core.jar",

        "./lib/multilingual/an_dlt.jar",
        "./lib/multilingual/dlt.jar",
        "./lib/multilingual/icu4j-51_2.jar",
);

#my $JAVA_HOME = "C:\\Program Files\\Java\\jdk1.6.0_01\\";
#my $JAVA_HOME = $ENV{"HOME"} . "/lib/ibm-java2-i386-50";
#my $JAVA_HOME = $ENV{"HOME"} . "/lib/jdk1.6.0_01";

#my $JAVA_HOME = "/usr/lib/jvm/java-6-ibm";
#my $JAVA_HOME = "/usr/lib/jvm/java-6-32bit-sun";
#my $JAVA_HOME = "/usr/lib/jvm/java-5-ibm";
#my $JAVA_HOME = "/usr/lib/jvm/java-1.5.0-ibm";
#my $JAVA_HOME = "/usr/lib/jvm/java-5-32bit-ibm";
my $JAVA_HOME = "/usr/lib/jvm/java-6-openjdk";


my $JAVA = "$JAVA_HOME/bin/java";

my $JAVA_OPT = "-Xmx2G -Xss1M";
#my $JAVA_OPT = "-Xms10M -Xmx2000M";
#my $JAVA_OPT = "-Dcom.sun.management.jmxremote";
#my $JAVA_OPT = "-Xmx1G";
#my $JAVA_OPT = "-Xms1G -Xmx1G";
#my $JAVA_OPT = "-Xrunjavaperf";
#my $JAVA_OPT = "-Xrunhprof:cpu=samples,depth=4 -Xmx4G";
#my $JAVA_OPT = "-Xrunhprof:heap=sites";


#my $RUNJAVA = "'${JAVA_HOME}/bin/java' $JAVA_OPT";
my $RUNJAVA = "$JAVA $JAVA_OPT";


#my $CLASS = "com.ibm.avatar.algebra.test.stable.SpeedTests";
my $CLASS = "com.ibm.avatar.algebra.test.uima.MultilingualTests";
#my $CLASS = "com.ibm.avatar.algebra.test.stable.RegexTests";


my $cp = $ENV{"CLASSPATH"};
foreach my $cpelem (@CLASSPATH_ELEMS) {
	$cp .= ":$cpelem";
}

print STDERR "Classpath is: $cp\n";

my $cmd = "$RUNJAVA -cp '$cp' $CLASS";

print STDERR "Running:\n$cmd\n";

system $cmd;

