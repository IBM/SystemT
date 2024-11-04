#!/usr/bin/perl

################################################################################
# aggprof.pl
#
# Aggregate hprof output to produce a picture of where time is going.
#
# Usage: aggprof.pl <hprof output>
################################################################################

use strict;
use warnings;

################################################################################
# CONSTANTS

my $USAGE = "Usage: aggprof.pl <hprof output>";

# Table of prefixes that we want to aggregate.
my @PREFIXES = (
    "com.ibm.oti",

    "sun.net",
    "sun.nio",
    "sun.misc",

    "java.io",
    "java.lang.ref",
    "java.net",
    "java.nio",
    "java.util.jar",
    "java.util.regex",
    "java.util.zip",
    "java.security",
    "java.text",

    "com.ibm.avatar.algebra.aggregate",
    "com.ibm.avatar.algebra.base",
    "com.ibm.avatar.algebra.consolidate",
    "com.ibm.avatar.algebra.datamodel",
    "com.ibm.avatar.algebra.extract",
    "com.ibm.avatar.algebra.function",
    "com.ibm.avatar.algebra.joinpred",
    "com.ibm.avatar.algebra.output",
    "com.ibm.avatar.algebra.predicate",
    "com.ibm.avatar.algebra.relational",
    "com.ibm.avatar.algebra.scan",
    "com.ibm.avatar.algebra.util.dict",
    "com.ibm.avatar.algebra.util.document",
    "com.ibm.avatar.algebra.util.regex",
    "com.ibm.avatar.algebra.util.string",
    #"com.ibm.avatar.algebra.util.tokenize",
    "com.ibm.avatar.aog",
    "com.ibm.avatar.api",
    "com.ibm.avatar.aql",

    "com.ibm.avatar.algebra.join.NLJoin",
    "com.ibm.avatar.algebra.join.SortMergeJoin",

    "com.ibm.systemt.regex",

    "java.lang.Character",
    "java.lang.Class",
    "java.lang.Integer",
    "java.lang.J9VMInternals",
    "java.lang.Long",
    "java.lang.String",
    "java.lang.StringBuilder",

    "java.util.Arrays",
    "java.util.AbstractCollection",
    "java.util.AbstractList",
    "java.util.Formatter",
    "java.util.HashMap",
    "java.util.HashSet",
    "java.util.LinkedList",
    "java.util.MapEntry",
    "java.util.TreeMap",
    "java.util.TreeSet",
);

################################################################################
# BEGIN SCRIPT

die $USAGE unless 1 == scalar @ARGV;

my $infile = $ARGV[0];

open IN, $infile;

# Skip to the table at the bottom of the file. 
my $line;
while ($line = <IN>) {
    if ($line =~ /^rank\s+self\s+accum/) {
        goto END_OF_LOOP;
    }
}
END_OF_LOOP:

my $total_count = 0;

# We'll build up a table of path=>count mappings
my %path_to_count;

while ($line = <IN>) {
    chomp $line;

    $line =~ /^\s*(\d+)\s+(\d+.\d\d)%\s+(\d+.\d\d)%\s+(\d+)\s+(\d+)\s+([^\s]+)/ 
        or goto DONE;

    my $pct_time = $2;
    my $count = $4;
    my $method = $6;


    # File this guy under the shortest prefix that matches.
    my $shortest_prefix = $method;
    foreach my $prefix (@PREFIXES) {
        
        my $prefix_len = length $prefix;
        if ($prefix_len < length $shortest_prefix) {
            if ($prefix eq (substr $method, 0, length $prefix)) {
                $shortest_prefix = $prefix;
            }
        }
    }

    #printf STDERR "%s => %s => count %d\n", $method, $shortest_prefix, $count;

    $total_count += $count;
    $path_to_count{$shortest_prefix} += $count;

}
DONE:

close IN;

# Print out the total counts, broken down by path prefix.
printf "%-65s %5s %5s\n"
."---------------------------------------"
."---------------------------------------\n", 
"Key", "count", "pct";

#foreach my $path (sort keys %path_to_count) {
#    my $count = $path_to_count{$path};
#
#    my $pct = (100.0 * $count) / $total_count;
#
#    printf "%-60s %5d %5.2f\n", $path, $count, $pct;
#
#}


# Sort the paths by count, then by name.
my $max_count = 0;
foreach my $path (sort keys %path_to_count) {
    my $count = $path_to_count{$path};
    $max_count = ($count > $max_count) ? $count : $max_count;
}

my @count_to_paths;

foreach my $path (sort keys %path_to_count) {
    my $count = $path_to_count{$path};

    my $paths_ref = $count_to_paths[$count];

    if (not defined $paths_ref) {
        my @paths = ();
        $paths_ref = \@paths;
    }

    push @$paths_ref, $path;

    $count_to_paths[$count] = $paths_ref;

}

for (my $count = 0; $count < (scalar @count_to_paths); $count++) {

    my $paths_ref = $count_to_paths[$count];

    if (defined $paths_ref) {
        foreach my $path (@$paths_ref) {

            my $pct = (100.0 * $count) / $total_count;

            printf "%-65s %5d %5.2f\n", $path, $count, $pct;
        }
    }

}




