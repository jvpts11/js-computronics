# Changelog

All notable changes to the J's Tech Series are recorded here, newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); version numbers and phase letters follow
[docs/RELEASING.md](docs/RELEASING.md).

## [Unreleased]

### Added
- The computers can be programmed. Cannon is a language of the world: you write it at the machine, compile
  it there with `cannonc`, and run what comes out. The compiler produces a listing you can open and read a
  line at a time, because a program you cannot look inside is one you cannot trust.
- A program is one of two things, and says which by how it is written. One with a `Main` runs at the
  terminal that started it, holds the prompt, prints as it goes and is gone when it returns. One that
  implements `IScript` stays up: set up once, called every tick, told when it is stopped, and still running
  after the world has been away and come back.
- A program spends only what the machine's processors are worth in a tick, so an old computer really does
  print line by line where a fast one finishes at once, and one that loops forever costs its machine the
  same tick as one that does nothing.
- What a program can reach: the machine's own drives, with the same paths the prompt uses; what the machine
  is made of and what it is running; what the network holds, could hold, and which servers hold it; what the
  Mainframe has been doing this past hour; and the network itself, to pull, push, craft and cancel. Every row
  it leaves in the network's log names the program that asked.
- A program can ask to be told instead of asking. It says once that it wants to know when something runs
  low, and is called when it does — on the crossing, not for as long as it stays crossed.
- Programs can be handed to other people. `canpack` wraps one up with everything it needs into a single
  readable file, publishes it to the network's Mirror, and anyone on that network installs it like any other
  package, marked as a player's own. An installed one gets an icon on the desktop and a terminal to run in.
- A program's memory is counted like everything else the machine holds, so the Task Manager lists it beside
  the windows and services, and a machine without the memory for one says so instead of trying.
- The file explorer knows a source file, a compiled program and a package on sight, and running one is a
  double-click.
- Mods may add a programming language of their own, and remove this one. A language that registers itself
  gets the prompt, the terminal, the task manager, saving and the tick budget without writing any of them.

- Every system, desktop, service and program holds a share of the computer's RAM, in megabytes, and a program
  opens only while it still fits: a bundled program weighs a share of the system it ships with, an installed one
  what its generation weighs, so a modern tool needs the gigabytes a modern machine has. The System Monitor lists
  who holds what.
- A balloon over the notification area carries the notices a computer raises by itself, the first being the
  one about memory.
- The Task Manager, on every desktop, in the shape that desktop really had: the Close Program box on Frames
  95, the four-tab manager with its menu and status bars on Frames XP, the page rail on Frames 11, and the
  system monitor each Linux desktop's own package brings. It reads this machine — its processes and what
  each holds, its memory, processor, disks and network link — and ends the program you pick. It has no icon
  of its own: right-click the panel and it is one entry on the menu, the way these desktops offered it.
- Every panel's notification area shows whether the computer is on a data network, alongside a speaker and
  the memory bar; resting the cursor on it reads out the link and the figures.

### Changed
- The Task Manager ends a program you wrote, as it ends a window. What the machine itself is made of still
  cannot be ended, because that is the machine and not something you started.
- The network knows how much it could hold, not only how much it does: a server offers its drives, a
  personal computer the share its owner published.
- Work asked for through IQL says so in the network's log. It was reading as the shell's.
- The desktop's open-program counter became a memory meter, "used/total MB", in every desktop's panel.
- Frames XP wears its own shell again: the Start pill with its flag, task buttons carrying each program's
  icon and showing the window in front as pushed in, a Start menu that opens on the player's own face and
  name, and a wallpaper with clouds over its hill.
- Desktop icons sit on a grid wide enough for their names, wrapped over two lines and written in the smaller
  text, so a long one no longer runs across the icon beside it and the desktop keeps its room.
- Task buttons share the strip out between them instead of each keeping a fixed width, so the open programs
  stay visible however many there are.
- An Operation's provenance rows name the computer that asked and the program it asked through, as in
  "lab-pc (Interactor)"; a bus reads as its kind and its name.

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

[Unreleased]: https://github.com/jvpts11/js-tech-series/compare/v0.2.0a...HEAD
[0.2.0a]: https://github.com/jvpts11/js-tech-series/releases/tag/v0.2.0a
[0.1.0a]: https://github.com/jvpts11/js-tech-series/releases/tag/v0.1.0a
