#!/usr/bin/perl

# findclass.pl
#
# Find a class among the jar files in a directory.
#
# Usage: ./findclass.pl <class>


use strict;
use warnings;

die "Usage: findclass.pl <dir> <classname>" unless 2 == @ARGV;

my ($dir, $class) = @ARGV;


printf STDERR "Searching for class '%s' under %s\n", $class, $dir;
my @files = `find $dir 2>&1`;

# Filter out files that aren't jar files.
my @jarfiles;
foreach my $file (@files) {
    chomp $file;
    if ($file =~ /\A.*\.jar\Z/) {
        push @jarfiles, $file;
    }
}


foreach my $jarfile (@jarfiles) {

#    print STDERR "$jarfile\n";

    my @contents = `jar tvf $jarfile`;

    foreach my $file (@contents) {
        chomp $file;

        if ($file =~ /$class/) {
            printf "*** Jar file '%s' contains the file:\n%s\n",
                $jarfile, $file;
        }
    }
}

