#!/usr/bin/perl

################################################################################
# finddups.pl
#
# Find duplicate dictionary entries in a dictionary file.
#
# Usage: ./finddups.pl <dictfile>
################################################################################

use strict;
use warnings;

die " Usage: ./finddups.pl <dictfile>" unless 1 == scalar @ARGV;

my $dictfile = $ARGV[0];

# Open the dictionary file.
open IN, $dictfile or die "Couldn't open $dictfile";

# Map from entry to "1"
my %entries;

while (my $line = <IN>) {
    chomp $line;
    if (defined $entries{$line}) {
        print STDERR "Duplicate entry '$line'\n";
    } else {
        print "$line\n";
    }

    # Mark off this entry as known.
    $entries{$line} = 1;
}

close IN;

