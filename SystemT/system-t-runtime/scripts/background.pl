#!/usr/bin/perl
###############################################################################
# background.pl
#
# Background job script.
#

use strict;
use warnings;

# Additional stuff to add to the classpath
my @CLASSPATH_ELEMS = (
	".",
	"./derby.jar",
	"./derbynet.jar",
	"./derbytools.jar",
	"./junit-4.1.jar",
	"./shiftone-jrat.jar",
	"../AOMIngester",
	"../resources/libs/avatar/avatarAnnotators.jar",
	"../resources/libs/avatar/avatarUtils.jar",
	"../resources/libs/xalan-2.7.0/xalan.jar"
);

# TODO: JAVA_HOME for Linux
my $JAVA_HOME = "C:\\Program Files\\Java\\jdk1.6.0_01\\";

my $JAVA_OPT = "-Xmx700m";
#my $JAVA_OPT = "-Xrunjavaperf";
#my $JAVA_OPT = "-Xrunhprof:cpu=samples";

my $RUNJAVA = "'${JAVA_HOME}/bin/java' $JAVA_OPT";

my $NICE = "/usr/bin/nice --adjustment=20";

#my $CLASS = "com.ibm.avatar.aog.test.AOGBlogTests";
my $CLASS = "com.ibm.avatar.algebra.test.AOGParserTests";

my $cp = $ENV{"CLASSPATH"};
foreach my $cpelem (@CLASSPATH_ELEMS) {
	$cp .= ";$cpelem";
}

print STDERR "Classpath is: $cp\n";

my $cmd = "$NICE $RUNJAVA -cp '$cp' $CLASS";
system $cmd;

