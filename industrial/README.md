# J's Industrial

Energy, machines and the processing chains that feed the network. This is the industrial mod of the
[J's Tech Series](../README.md); its id is `jsindustrial`.

## Everything here is still groundwork

This is the oldest and least worked on part of the series. What is in it was written early in the alpha
to find out how a machine, a recipe and an energy buffer should be built at all, and it has been left
mostly alone since, while the computing side was taken forward.

So take none of it as settled. The textures and the models are first drafts. The numbers were picked to
make something run, not balanced against anything. The four machines below are the ones it took to prove
the shape of the thing, not a considered opening tier. Names, recipes and behaviour can all change, and
when the tier ladder further down is actually built, most of what is here will be redone rather than
extended.

## What is in the box today

Four machines, each a block that faces the way you placed it, with a screen of its own:

- The Coal Generator burns furnace fuel and produces energy (FE), pushing it into the machines next to it.
- The Macerator grinds ores and raw ores into two dusts each, and ingots into dust: one ore becomes two
  ingots once the dust is smelted.
- The Compressor presses ingots into plates.
- The Electric Furnace smelts with energy instead of fuel, using the vanilla smelting recipes.

The dusts and plates themselves are the material items of J's Core, so every mod of the series shares
them; this mod adds their recipes and tags them the common way (`c:dusts/iron`, `c:plates/copper`, and so
on), so machines from other mods accept them and their ingots work here.

Every machine exposes its inventory and its energy buffer as capabilities: hoppers and pipes move items in
and out, and any FE generator can power it. When J's Computronics is installed, the Pattern Studio knows
which machine runs which recipe type, so a macerating or compressing recipe transfers straight onto the
right machine.

## Where it is going

Industry is a ladder of tiers, T0 to T9, each with its own materials, machines and energy. The tiers are
the milestones of this mod: the series enters beta once the first half of the ladder, T0 to T4, is
playable (see [docs/RELEASING.md](../docs/RELEASING.md)).

## Requirements

- Minecraft 1.21.1 and NeoForge 21.1.248 or newer.
- [J's Core](../core/README.md) at the same version (required).

It does not need [J's Computronics](../computronics/README.md), and never will: the machines work on their
own, with any FE source, and the computing mod only adds ways to drive them.
