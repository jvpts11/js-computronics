# J's Core

The shared library of the [J's Tech Series](../README.md); its id is `jscore`. Every mod of the series
requires it, at the same version. It is not a mod to play on its own: install it because another mod of
the series asks for it.

## What it holds

The things two or more mods of the series need, or that define the language they share:

- The two axes of progression: the hardware eras (Vintage, Legacy, Standard and the ones to come) and the
  industrial tiers (T0 to T9), kept apart on purpose.
- The material catalogue: the dusts, plates and other forms of the metals the mods process, registered
  here so that every mod trades the same items. They are the one thing the core adds to the game; the
  industrial mod shows them in its creative tab and gives them their recipes.
- The data network: the node hierarchy, the network and node identities, the topology elements, and the
  Operations framework the network runs on.
- The energy network (FE), the point-to-point links between a computer and its peripherals, and the
  capabilities a block exposes to take part in any of that.
- Multiblock shapes and validation, the configuration system, the series' internal event bus, persistence
  helpers, the unit formatter, and the GUI toolkit the screens of every mod are drawn with, including the
  [components](../docs/UI_COMPONENTS.md) desktop programs are composed from.

## For addon authors

The public API for addons is still being carved out; until it is, everything here is internal and may
change between versions. The series keeps one version across all its mods, so an addon should require
the core and the mod it extends at the same version.

## Requirements

- Minecraft 1.21.1 and NeoForge 21.1.248 or newer.
