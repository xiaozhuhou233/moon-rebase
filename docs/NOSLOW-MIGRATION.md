# NoSlow and original Moon source locations

Original Nightly 256ae01 sources (located only; current GUI modules are unchanged):
- KillAura: `D:/bypassmoon/nightly-killaura-scaffold/source/cfr/com/moon/lb.java`
- ScaffoldWalk: `D:/bypassmoon/nightly-killaura-scaffold/source/cfr/com/moon/lK.java`
- Archive: `D:/bypassmoon/Moon-Nightly-KillAura-Scaffold-HUD-Rotation-2026-10-31.zip`

These are decompiler outputs, not fully recovered readable implementations. KillAura has four unstructured sections; ScaffoldWalk has one failed method and three unstructured sections. The destination backup modules have later OpenB4/Lua changes and must not be mistaken for these originals.

NoSlow is ported from the supplied Pulsive `NoSlowDown.java`, under Player with Watchdog (default) and NCP modes. Normal configuration, key binds and mode suffix use the native registry. It cancels local item-use input slowdown without taking sprint ownership. Watchdog changes outgoing motion Y by +0.05 and ground=false only in PRE on even ticks at a 1/64-aligned physical Y, while moving/using an item and Speed is disabled. Physical position is untouched. NCP sends the donor's null-stack C08 once per use cycle.

Adaptations: active destination KillAura targets suppress NoSlow motion/packet changes (the donor scans Aura's nearby blocking candidates). NCP's never-reset `blocking` flag is reset on use end, item/slot/mode changes, world changes and disable. Empty donor packet listener is omitted. Donor BlockStepEvent cancellation was not consumed by its dispatch, so no new step-height behavior is introduced. Existing Sprint and Myau autoblock behaviors are unchanged.

Validation: Java 24 compile passed; NoSlowGameCheck passed 33 assertions in an isolated integrated world. Subsequent native KillAura / ScaffoldWalk migration is documented in MOON-NATIVE-MIGRATION.md.
