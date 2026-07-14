---
navigation:
  title: Advanced Molecular Assemblers
  icon: advanced_molecular_assemblers:molecular_assembler_2x
categories:
- machines
item_ids:
- advanced_molecular_assemblers:molecular_assembler_2x
- advanced_molecular_assemblers:molecular_assembler_4x
- advanced_molecular_assemblers:molecular_assembler_8x
- advanced_molecular_assemblers:molecular_assembler_16x
- advanced_molecular_assemblers:molecular_assembler_32x
---

# Advanced Molecular Assemblers

Advanced Molecular Assemblers accept crafting jobs from adjacent
<ItemLink id="ae2:pattern_provider" /> blocks.

The number in each assembler's name is its number of independent crafting lanes.
For example, the 8x Molecular Assembler can work on eight crafting jobs at once.
Each lane runs at the same speed and power cost as a normal
<ItemLink id="ae2:molecular_assembler" />.

All lanes share the assembler's Acceleration Cards. Supplying enough crafting
co-processors and power is necessary to keep every lane busy.

The upgrade panel accepts up to ten Acceleration Cards in a 2x5 grid. Cards six
through ten extend the speed curve from 0.75 to 2 crafts per active lane per
tick. Each lane buffers up to two additional Pattern Provider jobs, keeps their
ingredients separate, and completes at most two crafts in one tick. Blocked
outputs stop the lane immediately without consuming its queued jobs.

The arrow controls select a lane. Each lane has its own encoded-pattern slot and
crafting grid, so a crafting pattern and ingredients can also be inserted
manually just like in a normal Molecular Assembler. A lane containing a manual
pattern is reserved for that pattern until it is removed; Pattern Provider jobs
use the remaining empty lanes.

The interface can also be used to inspect Pattern Provider jobs and remove an
output that cannot be returned to its provider.
