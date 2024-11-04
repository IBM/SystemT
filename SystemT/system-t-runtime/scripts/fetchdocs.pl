#!/usr/bin/perl

################################################################################
# fetchdocs.pl
#
# Script to retrieve documents from the W3 DB2 database.  This script is mostly
# a front end for a Java program.
################################################################################

use strict;
use warnings;

# Additional stuff to add to the classpath
my @CLASSPATH_ELEMS = (
	"./bin",
	"./lib/db2jcc.jar",
	"./lib/db2jcc_license_cu.jar"
);

# TODO: JAVA_HOME for Linux
#my $JAVA_HOME = "C:\\Program Files\\Java\\jdk1.6.0_01\\";
#my $JAVA_HOME = $ENV{"HOME"} . "/lib/ibm-java2-i386-50";
#my $JAVA_HOME = $ENV{"HOME"} . "/lib/jdk1.6.0_01";

my $JAVA_HOME = "/usr/lib/jvm/java-6-sun";
#my $JAVA_HOME = "/usr/lib/jvm/java-6-32bit-sun";
#my $JAVA_HOME = "/usr/lib/jvm/java-5-ibm";
#my $JAVA_HOME = "/usr/lib/jvm/java-5-32bit-ibm";

my $JAVA = "$JAVA_HOME/bin/java";

my $JAVA_OPT = "-Xms10M -Xmx2000M";
#my $JAVA_OPT = "-Dcom.sun.management.jmxremote";
#my $JAVA_OPT = "-Xmx1G";
#my $JAVA_OPT = "-Xms1G -Xmx1G";
#my $JAVA_OPT = "-Xrunjavaperf";
#my $JAVA_OPT = "-Xrunhprof:cpu=samples,depth=4 -Xmx2G";
#my $JAVA_OPT = "-Xrunhprof:heap=sites";


#my $RUNJAVA = "'${JAVA_HOME}/bin/java' $JAVA_OPT";
my $RUNJAVA = "$JAVA $JAVA_OPT";

my $CLASS = "com.ibm.avatar.util.db.FetchDocs";

my $cp = $ENV{"CLASSPATH"};
foreach my $cpelem (@CLASSPATH_ELEMS) {
	$cp .= ":$cpelem";
}

print STDERR "Classpath is: $cp\n";

my $cmd = "$RUNJAVA -cp '$cp' $CLASS";

print STDERR "Running:\n$cmd\n";

system $cmd;

