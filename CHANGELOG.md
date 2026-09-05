# Changelog

All notable changes to J's Computronics are recorded here, newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); version numbers and phase letters follow
[docs/RELEASING.md](docs/RELEASING.md).

## [Unreleased]

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

[Unreleased]: https://github.com/jvpts11/js-computronics/compare/v0.1.0a...HEAD
[0.1.0a]: https://github.com/jvpts11/js-computronics/releases/tag/v0.1.0a
