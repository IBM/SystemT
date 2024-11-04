#!/usr/bin/perl


################################################################################
# annotate.pl
#
# Driver script for the annotator pipeline demo for Spock.com
################################################################################



use strict;
use warnings;

# Additional stuff to add to the classpath
my @CLASSPATH_ELEMS = (
        # Local compiled classes
	".",

        # Project-local libraries
	"./junit-4.4.jar",
	"./shiftone-jrat.jar",

        # Avatar libraries
	"../AOMIngester",
	"../resources/libs/avatar/avatarAnnotators.jar",
	"../resources/libs/avatar/avatarUtils.jar",

        # External libraries
        "../resources/libs/multilingual/dlt.jar",
        "../resources/libs/multilingual/icu4j-51_2.jar",
);

my $JAVA_HOME = $ENV{"JAVA_HOME"};

my $RUNJAVA = "${JAVA_HOME}/bin/java";

#my $NICE = "/usr/bin/nice --adjustment=20";

my $CLASS = "com.ibm.avatar.algebra.util.BatchAQLRunner";

my $DATADIR = "../../data";
my $RESOURCES_DIR = "../resources";

my $DOCSFILE = "$DATADIR/spock_alldata.del";
my $DICTSDIR = "$RESOURCES_DIR/Notes8Resources/resources";
my $OUTPUTSDIR = "$DATADIR/output";
my $AQLFILE = "testdata/aql/lotus/namedentity-spock-urlemailnooutput.aql";
my $NTHREAD = 16;

#print STDERR "Creating '$OUTPUTSDIR'\n";
system "rm -rf $OUTPUTSDIR";
mkdir $OUTPUTSDIR;

my $ARGS = "$DOCSFILE $DICTSDIR $OUTPUTSDIR $AQLFILE $NTHREAD";

my $cp = $ENV{"CLASSPATH"};
foreach my $cpelem (@CLASSPATH_ELEMS) {
	$cp .= ":$cpelem";
}

print STDERR "Classpath is: $cp\n";

my $cmd = "$RUNJAVA -cp '$cp' $CLASS $ARGS";

print STDERR "Running:\n$cmd\n";

system $cmd;

