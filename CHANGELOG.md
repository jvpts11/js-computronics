# Changelog

All notable changes to J's Computronics are recorded here, newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); version numbers and phase letters follow
[docs/RELEASING.md](docs/RELEASING.md).

## [Unreleased]

## [0.2.0a] - 2026-09-06

The mod became a series. The code every module shares now lives in a library mod of its own, J's Core,
and the industrial machines in their own mod, J's Industrial; J's Computronics keeps the computers, the
network and the programs. The three ship together at one version. Worlds from 0.1.0a do not carry over:
the industrial blocks and the material items changed their ids.

### Added
- J's Core (`jscore`), the library every mod of the series requires: the hardware eras and the industrial
  tiers, the material catalogue, the data network, the Operations framework, energy, peripheral links,
  multiblocks, configuration, the event bus, persistence, the unit formatter and the screen toolkit.
- J's Industrial (`jsindustrial`), the machines and their recipes, with a creative tab of their own; the
  Pattern Studio pairs its recipes with the Macerator, the Compressor and the Electric Furnace.
- Priorities for network Operations: a level from LOW to HIGH, chosen in the Network Interactor's request
  and craft dialogs or with an IQL `PRIORITY` clause, and changed on a live Operation from the Task Manager.
  A queued Operation gains a level while it waits, so none starves.
- Cancelling an Operation in flight, from the Task Manager or with `cancel <id>` at the prompt; `ops` now
  lists each Operation's id.
- The time every Operation waited and ran on its log entry, the last hour's statistics per type in a Stats
  tab of the Network Manager, and a `stats` prompt command.
- One server config for the engine's balance, `jstech-balance.toml`: the disk latencies, the waiting
  timeout, the priority aging period, the Subframe share and the orphan expiry, clamped on load.
- A registry of Operation types and lifecycle events on the core's event bus, for mods that follow or drive
  the network's work.
- A README for the series and one per mod, and a page on the interface components the programs are built
  from.

### Changed
- Every desktop program and dialog draws from one set of interface components; the desktop's error dialog
  is one of them.
- Subframes lend their share of capacity and their GPUs' queues to the Mainframe orchestrating them.
- A REINDEX reads the disks on its tick and rebuilds the catalog off it; a craft request is listed as
  pending while its plan is made off the tick.
- Saved Operations left unresumed for longer than the orphan expiry are discarded on reload instead of
  resumed.
- The server config `jsc-server.toml` is replaced by `jstech-balance.toml`.

### Fixed
- A pull into a computer that was broken mid-transfer no longer keeps taking items out of the network.

## [0.1.0a] - 2026-09-05

The first numbered version. Everything before it was the unversioned groundwork of the `0.0.x` line.
The mod is in alpha: computing is the only module, and it is still growing.

### Added
- Hardware eras (Vintage, Legacy, Standard) for computers, monitors and drives, with 3D cabinets for
  the mainframes, the server racks and the USB flash drive.
- Removable media (floppies, CDs, DVDs and USB sticks), the drives that read them, and install media
  for systems and programs; This PC installs a program straight from an inserted disc.
- The Frames desktops (95, XP and 11), Linux systems with a boot manager, dual boot, package managers
  and three desktop environments, and the programs that ship with them: Command Prompt, Files, Editor,
  This PC, Settings, System Monitor, Calculator, Network Manager and the Network Interactor; the
  Crafting Manager, the Craft Planner, Storage Insights, the Automation Manager and Minesweeper install
  from their own media.
- Servers that mount in racks, one rack per era, the Supercomputer rack on its HPC fabric, and the
  Cluster Management Computer with its Cluster Manager program.
- Drive contents kept as storage volumes in their own save data, with disks sized by era.
- Machine autocrafting: the Crafting Computer, the crafting cable and switch, input, output and
  receiving buses, machine patterns, multi-stage recipes, parallel stages and crafting-card threads.
- Chemicals and fluids as network data, carried by the buses; Mekanism machines and the Fusion Reactor
  can be fed and driven through the network, including D-T fuel made from network gases.
- Crafting from the command line and from IQL through the same planner the graphical terminal uses.
- The Pattern Studio, where recipes are authored on the computer, and the Pattern Encoder, one per
  era, which burns them onto media.
- JEI: the ingredient list sits beside every monitor screen, and a recipe transfers into the Pattern
  Studio's bench or machine draft.
- Every system and program is credited to its software house, and tooltips colour their era.
- A contribution policy for automated tooling and a public documentation folder.

### Changed
- Requires NeoForge 21.1.248 or newer.

### Fixed
- The idle tick cost of big bases and the autosave freeze their drives caused.
- Desktop windows stay current after a restore.
- The Crafting Manager sees a disc inserted after its window opened.
- Breaking a drive that still holds a disc removes the drive.
- A rack server's desktop no longer crashes the monitor before the rack's era has reached the client.

[Unreleased]: https://github.com/jvpts11/js-computronics/compare/v0.2.0a...HEAD
[0.2.0a]: https://github.com/jvpts11/js-computronics/releases/tag/v0.2.0a
[0.1.0a]: https://github.com/jvpts11/js-computronics/releases/tag/v0.1.0a
