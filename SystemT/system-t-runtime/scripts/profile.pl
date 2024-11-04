#!/usr/bin/perl
###############################################################################
# profile.pl
#
# Script for running programs on avatar5.
#

use strict;
use warnings;

# Additional stuff to add to the classpath
my @CLASSPATH_ELEMS = (
	"./bin",
	"./lib/SimpleRegex/systemT_regex.jar",
	"./lib/htmlparser-2.0-ibm/enc-htmlparser.jar"
);


#my $JAVA_HOME = "/usr/lib/jvm/java-6-ibm";
my $JAVA_HOME = "/usr/lib/jvm/java-6-openjdk";
#my $JAVA_HOME = "/usr/lib/jvm/java-6-sun";

my $JAVA = "$JAVA_HOME/bin/java";

#my $JAVA_OPT = "";
#my $JAVA_OPT = "-Xms10M -Xmx2000M";
#my $JAVA_OPT = "-Dcom.sun.management.jmxremote";
#my $JAVA_OPT = "-Xmx128M";
#my $JAVA_OPT = "-Xmx1G";
#my $JAVA_OPT = "-Xms1G -Xmx1G";
#my $JAVA_OPT = "-Xrunjavaperf";
my $JAVA_OPT = "-Xrunhprof:cpu=samples,depth=4 -Xmx2G";
#my $JAVA_OPT = "-Xrunhprof:cpu=samples,depth=12 -Xmx2G";
#my $JAVA_OPT = "-Xmx1G -Xrunhprof:heap=sites,depth=6,file=eda.hprof.txt";
#my $JAVA_OPT = "-Xmx1G -Xrunhprof:heap=sites,depth=6,file=lotus.hprof.txt";


#my $RUNJAVA = "'${JAVA_HOME}/bin/java' $JAVA_OPT";
my $RUNJAVA = "$JAVA $JAVA_OPT";


my $CLASS = "com.ibm.avatar.algebra.test.stable.SpeedTests";
#my $CLASS = "com.ibm.avatar.algebra.test.stable.MemoryTests";
#my $CLASS = "com.ibm.avatar.algebra.test.unstable.W3LATests";
#my $CLASS = "com.ibm.avatar.algebra.test.stable.RegexTests";
#my $CLASS = "com.ibm.avatar.algebra.test.uima.MultilingualTests";

my $cp = $ENV{"CLASSPATH"};
foreach my $cpelem (@CLASSPATH_ELEMS) {
	$cp .= ":$cpelem";
}

print STDERR "Classpath is: $cp\n";

my $cmd = "$RUNJAVA -cp '$cp' $CLASS";

print STDERR "Running:\n$cmd\n";

system $cmd;

