#!/usr/bin/env perl
# Emits an ICU common data package as a C byte array named by $ARGV[0].
use strict;
use warnings;

my $name = shift or die "usage: dat2c.pl <symbol> < in.dat > out.c\n";
binmode STDIN;
local $/;
my @bytes = unpack("C*", <STDIN>);
my $n = scalar @bytes;
print "const unsigned char ${name}[$n] __attribute__((aligned(16))) = {\n";
for (my $i = 0; $i < $n; $i += 24) {
  my $end = $i + 23 < $n - 1 ? $i + 23 : $n - 1;
  print join(",", @bytes[$i .. $end]), ",\n";
}
print "};\n";
