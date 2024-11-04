#!/usr/bin/perl

###############################################################################
# getdocids.pl
#
# Extract the document IDs from the output of HTMLVis.
###############################################################################

use strict;
use warnings;

my @docids = ();

while (my $line = <>) {
	
	chomp $line;
	
	if ($line =~ /Document#(\d+)/) {
		my $docid = $1;
		
		push @docids, $docid;
		
#		print STDERR "Doc $docid\n";
	}
	
}

printf STDERR "Found %d document IDs.\n", scalar @docids;

# Generate the list for insertion into DB2
#foreach my $docid (@docids) {
#	print "$docid\n";
#}

# Generate insert statements to load the table blogids
foreach my $docid (@docids) {
	print "insert into blogids(id) values ($docid)\n";
}