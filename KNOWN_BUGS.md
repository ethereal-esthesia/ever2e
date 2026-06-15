# Known Bugs / Timing Notes

## Disk II byte timing is approximate

The Disk II controller still models disk data at the NIB byte level rather than
as a phase-accurate bit-cell stream. The current JVM default is split as a
practical workaround: reads advance at 31 CPU cycles per NIB byte so ProDOS
System Utilities does not report the drive as too slow, while writes advance at
32 CPU cycles per NIB byte because formatter write loops depend on that older
cadence.

The longer-term fix is to replace the fixed byte-period model with a proper
Disk II sequencer. Read latch timing, write latch serialization, motor timing,
and the physical bit-cell rate should be modeled independently of the 6656-byte
NIB container size. The C++ emulator currently has only a single fixed byte
period override, so it should receive the same sequencer fix rather than relying
on a different constant.
