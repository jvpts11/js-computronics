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

## Configuration

The balance of the Operations engine lives in one server config, `jstech-balance.toml`, written next to
the world save (`serverconfig/`). Every key has a documented range and is clamped into it on load, so an
out-of-range value degrades a setting instead of breaking the world; the file is re-read when it changes.

| Key | Default | What it tunes |
| --- | --- | --- |
| `hdd_latency_ticks`, `ssd_latency_ticks`, `nvme_latency_ticks` | 10, 3, 1 | The seek latency of each disk class before a transfer starts streaming. |
| `operation_waiting_timeout_ticks` | 1200 | How long an Operation waits on a locked resource or a busy executor before it gives up. |
| `operation_priority_aging_ticks` | 600 | Ticks a queued Operation waits per priority level it gains while others jump ahead; 0 disables aging. |
| `subframe_efficiency_factor` | 0.6 | The share of its own capacity a Subframe lends to the Mainframe orchestrating it. |
| `orphaned_operations_expiry_hours` | 24 | How long a saved, never-resumed Operation may sit before a reload discards it instead of resuming it; 0 never expires. |

## For addon authors

The public API for addons is still being carved out; until it is, everything here is internal and may
change between versions. The series keeps one version across all its mods, so an addon should require
the core and the mod it extends at the same version.

Two entry points are already stable enough to build on. `JsCore.events()` is the series' event bus: the
Mainframe posts the life of every Operation on it (created, started, then completed, failed or discarded,
each carrying the network, the Operation id and its type id), on the server thread, and any mod subscribes
with core types alone. `JsCore.operations()` is the registry of Operation types the mods declare, each with
an id such as `jsc:select`, a category, an argument record and a handler that hands the request to the
orchestrator; a mod can look a type up and submit through it instead of calling the orchestrator's own
methods. The argument records of the computing types still belong to the computing mod and name its
Mainframe, so submitting takes that mod on the classpath for now; observing does not.

## Requirements

- Minecraft 1.21.1 and NeoForge 21.1.248 or newer.
