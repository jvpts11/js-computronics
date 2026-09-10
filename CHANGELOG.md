# Changelog

All notable changes to the J's Tech Series are recorded here, newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); version numbers and phase letters follow
[docs/RELEASING.md](docs/RELEASING.md).

## [Unreleased]

### Added
- Computers can now be programmed with the series' own programming language, Cannon. You write it at any computer, compile
  it there with `cannonc` down to assembly, and run what comes out. The compiler produces a listing you can open and read a
  line at a time, so any output can be verified.
- For now, a program is one of two things, and says which by how it is written. One with a `Main` runs at the
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
  low, and is called when it does: on the crossing, not for as long as it stays crossed.
- Programs can be handed to other people. `canpack` wraps one up with everything it needs into a single
  readable file, publishes it to the network's Mirror, and anyone on that network installs it like any other
  package, marked as a player's own. An installed one gets an icon on the desktop and a terminal to run in.
- A program's memory is counted like everything else the machine holds, so the Task Manager lists it beside
  the windows and services, and a machine without the memory for one says so instead of trying.
- The file explorer knows a source file, a compiled program and a package on sight, and running one is a
  double-click.
- Mods may add a programming language of their own, and remove this one. A language that registers itself
  gets the prompt, the terminal, the task manager, saving and the tick budget without writing any of them.
- Five editors to write a program in, each a different bargain between what it shows you and what it costs
  the machine to keep open. Virtual Studio is the whole workshop in one window, and the only one that tells you
  what a line will cost the program before you write it. Virtual Studio Code offers the same suggestions in a
  fifth of the memory, with the machine's own console welded into the bottom of the window. Exposure suggests
  nothing and instead compiles every program on the disk, so changing something shared shows which of the
  others stopped building. Vim and Emacs open no window at all: they take over the terminal they were started
  from, which is what lets a rack server with no screen be programmed, or one reached from another machine.
  Emacs splits that terminal, so a program and what the compiler said about it are readable at once.
- Whatever language a file is written in colours it, marks in the margin where the compiler stopped, and
  suggests what can follow a name, in every one of the editors. A language a mod adds gets all of it.
- Virtual Studio works in solutions. It opens on a Start Window: what you opened lately, or a project or
  folder or file to open, or a new project to create. Creating one picks a template first, filtered by
  language, platform and kind, then names the project and its solution, and writes a solution file, a
  project file and a first source that already builds. The Solution Explorer shows every project with its
  dependencies, properties, sources and what it built; Build compiles a project with the libraries it
  references and writes the listing where the project file says; Start builds the startup project and
  runs it at the terminal; Package hands the project to `canpack`.
- Virtual Studio Code works the way its namesake does: a Welcome page, a folder opened as a tree beside
  the editor, a command palette on Ctrl+Shift+P, F5 to build and run the open file at the terminal welded
  into the window, and an Extensions page listing every language the machine knows.
- Installing takes time. Setup runs as a job on the machine: a window with a progress bar on a desktop,
  lines at the prompt, cancellable from either, and how long it takes depends on where the program comes
  from (a floppy is slow, a DVD faster, the network faster still) and how big the program is. A machine
  that goes away mid-setup picks it up where it was.
- `setup.exe` on an installation disc, double-clicked in the explorer, starts that setup.
- An installed program is on the disk. Its folder under Program Files (or Program Files (x86), for one
  older than the system) holds the program, its settings file, its libraries, a font when it brought one
  and what came on its disc, and the system's own folder is called Frames, with the same kind of contents.
  The explorer, the prompt and the studios read them like any other file.
- An installation disc reads as a disc: its sources folder holds the setup, the packed program and its
  checksums, its support folder the read-me and the checksum list.
- Every terminal on a machine is its own session, with its own place on the disk: two Command Prompts
  can be in two folders, and what one prints is only in that one. Closing a Fedora terminal and opening
  it again shows what it printed before.
- Cannon strings can carry values: `$"Total {a + b}"` fills the braces with what the expression comes to.
  Two braces in a row are one brace of text.
- The code editors in both studios grew up: text can be selected with the mouse or with Shift and the
  arrows, cut, copied and pasted; Ctrl+Z and Ctrl+Y undo and redo; the font zooms with Ctrl and the
  wheel; a new line keeps the indentation of the one before; a bracket or quote closes itself; Tab and
  Shift+Tab indent the selected lines; and the marks in the margin say what the compiler said when the
  cursor rests on them.
- Files close. Ctrl+W, the cross on the tab or File > Close closes the open file, asking first when it
  has changes. Ctrl+W in Virtual Studio Code does the same.
- The explorers of both studios show the same icons the file explorer shows, and the file explorer's
  columns can be dragged wider or narrower.
- Implement Interface: with the cursor on a class that says it implements one, Edit > Implement Interface
  (or Ctrl+.) writes the methods the interface asks for that the class does not have yet.
- Virtual Studio's dock splits into Error List, Output and Terminal; the Error List is a table of code,
  description, file and line, and the panels and the Solution Explorer resize by dragging their edges.
  Starting a console program runs it in the dock's terminal.
- The Create a new project page shows the templates used lately beside the list, filters by language,
  platform and project type, and the Configure page asks where the project goes, with a "..." to browse.
- Cannon has namespaces and usings, and every type lives in one. `namespace Tools;` at the top of a file
  puts its types there, or a `namespace Tools { }` block does, and blocks nest. Types in the same
  namespace see each other plainly; anything else is brought in with a using: `using Tools.*;` for the
  whole namespace, `using Tools.Counter;` for one type, and `Tools.Counter` names one from anywhere. A
  file with no namespace, or a name used without its using, is told so by the compiler.
- The language's own library is the `System` namespace, with a namespace inside it per subject: the
  console and files under `System.IO`, lists and maps under `System.Collections`, `Math` under
  `System.Utils`, the machine under `System.Machine`, the network under `System.Network`, what the
  Mainframe has been doing under `System.Operations`. A program says which of it it uses.
- Structs and records. A struct is a value: assigning one copies it, two are equal when their fields
  are. A record is written as a line, `record Point(int X, int Y);`, and gets its fields, its
  constructor, its `ToString` and its equality from that line.
- Classes nest: a class declared inside another is named through it, `Farm.Counter`, and is a type in
  every way the outer one is.
- Programs take input. `Console.ReadLine()` waits for a line typed at the terminal the program runs in,
  and `Console.HasLine()` says whether one is waiting; a program waiting reads as "input" in the Task
  Manager. Typing at a terminal with a program in front goes to the program. `ReadInt`, `ReadLong`,
  `ReadDouble` and `ReadBool` wait the same way and hand the line back as a value, and stop the program
  with the text they could not read when it was not one.
- A program can say what it is called. `Program.SetName("Sorter")`, under `System.Execution`, is the
  name the Task Manager, `cannon ps` and the machine's own process list show for it, and `Program.Name`
  reads it back. A program that gives itself no name is listed as `cannonrt`, the runtime running it,
  the way an interpreted program shows up under its interpreter on any machine. The name survives the
  world being away and back.
- `Convert` turns text into values the other way round: `ToInt`, `ToLong`, `ToFloat`, `ToDouble`,
  `ToBool` and `ToString`, which stop on text that is not one, and `TryInt`, `TryLong`, `TryDouble` and
  `TryBool`, which say whether it was and hand the value out sideways, for a program that would rather
  ask again.
- The file explorer's address bar is a text field once it is clicked into, with the whole path
  selected: Ctrl+C copies it, typing replaces it, a click in the text puts the caret there, and Shift
  with the arrows (or a drag) selects part of it to copy. Every text field on every desktop selects
  the same way, and Ctrl+A selects all of one.
- The file explorer's right button, on an empty part of a folder, offers "Open in" the desktop's
  terminal, which comes up with its prompt in that folder.
- The Solution Explorer works with the right button: a source offers Open, Exclude From Project and
  Delete (which asks first, then takes the file off the disk, off its tab and out of the project); a
  project offers Build, Set as Startup Project, Add New Item, Add Existing Item, Add Project Reference
  and Properties; the solution offers Build, Clean and Add New Project.
- A machine remembers what its windows had open, not only that they were open: after the game itself
  was closed, Virtual Studio comes back on its solution with the same files on its tabs, Virtual
  Studio Code and Exposure on their folder and files, and the explorer on its folder.
- The editors suggest the program's own names, not only the language's: after a dot, what the variable,
  the field, `this`, `base` or the type before it has, read one name at a time through a chain; on a
  bare name, the variables in reach, the members of the class around the caret and every type the
  program declares, in the other files of the folder and in the libraries the project references too.
  The suggestions come while the line is still broken, which is when they are asked for.
- Every terminal moves the caret within the line: Left and Right, Home and End, Ctrl with Left or Right
  by a word, and Delete.
- Vim knows more of itself: w, b, e, ^, G and gg to move, dw and D to cut, yy, p and P to copy a line
  and put it back, u and Ctrl+R to undo and redo, and `:12` to go to a line. Emacs answers C-f, C-b,
  C-n, C-p, C-a, C-e, C-d, C-k, C-y, C-x u, M-< and M->, and asks before leaving with changes unwritten.
  Both slide a long line sideways to keep the caret in view.
- The code editors have a scroll bar down the side and one along the bottom, slide sideways with Shift
  and the wheel, and take a whole step of indentation back on Backspace. The right button on the code
  opens Cut, Copy, Paste, Toggle Line Comment and the refactorings, which is where Implement Interface
  lives now (Ctrl+. still works). Virtual Studio's zoom is a control in the status bar and under View;
  Virtual Studio Code keeps it under View > Appearance.
- The explorer's New entry opens a menu of what can be made, a folder first. A click past the last crumb
  of the address bar turns it into a path to type, copy or paste; every text field answers Ctrl+C, X
  and V.
- The desktop's right button offers what a desktop offers: Open, Open with, Rename, Delete and
  Properties on an icon; New, Refresh, Display settings, Personalize and Properties on the wallpaper.
- The system has a file window of its own, the one every program opens to choose a file, a folder or
  where to save: the places on the left as the explorer lists them, the way back, forward and up, the
  address as crumbs that can be typed over, the folder's contents with the explorer's icons and columns
  and only the files of the kind asked for, then the name, the kind and Open, Save or Select Folder. A
  double click enters a folder or takes a file, Save asks before replacing, and it wears each desktop's
  clothes. Virtual Studio, Virtual Studio Code and Exposure open and save through it; any program can.
  It is a window of its own: it comes up over the program that asked, listed with that program on the
  panel rather than as a program of its own, and holds the program until it is answered or put away.
- The panel lists programs, not windows. A program opened twice has one entry, and the entry says how
  the program stands: pinned with nothing open, open, in front, or put away, each in the panel's own
  language (Frames 11 marks the icon underneath and splits the mark for several windows; Frames XP
  pushes the button in and groups several under a count; KDE and Cinnamon underline, fill and stack).
  Resting the cursor on an entry of Frames 11, KDE or Cinnamon shows a card per window with its live
  picture; a click on a card brings that window forward, its cross closes it alone. Frames XP and 95
  list the titles instead, the way those desktops did.
- Programs pin to the panel. The right button on an entry, on a desktop icon or in Start offers Pin to
  taskbar, and a pinned program keeps its place with nothing open (on the quick launch beside Start on
  Frames XP; in place on Frames 11, KDE and Cinnamon). The pins are the machine's, kept with its other
  settings, and a fresh machine pins its file explorer. `settings pin files` and `unpin` at the prompt
  do the same.
- Exposure is an editor: File makes, opens, saves and closes files and picks the folder, Source comments,
  goes to a line and refactors, Project rebuilds the folder and runs the open program at the terminal.
- Settings > Display has a Scale: the desktop draws everything at 100, 90, 80, 75, 66 or 50 percent of
  its designed size, so a machine can fit more on its glass. Every machine starts at 75, which is the
  size that reads best on the monitor. The `settings guiscale` line at the prompt sets it too.

- Every system, desktop, service and program holds a share of the computer's RAM, in megabytes, and a program
  opens only while it still fits: a bundled program weighs a share of the system it ships with, an installed one
  what its generation weighs, so a modern tool needs the gigabytes a modern machine has. The System Monitor lists
  who holds what.
- A balloon over the notification area carries the notices a computer raises by itself, the first being the
  one about memory.
- The Task Manager, on every desktop, in the shape that desktop really had: the Close Program box on Frames
  95, the four-tab manager with its menu and status bars on Frames XP, the page rail on Frames 11, and the
  system monitor each Linux desktop's own package brings. It reads this machine (its processes and what
  each holds, its memory, processor, disks and network link) and ends the program you pick. It has no icon
  of its own: right-click the panel and it is one entry on the menu, the way these desktops offered it.
- Every panel's notification area shows whether the computer is on a data network, alongside a speaker and
  the memory bar; resting the cursor on it reads out the link and the figures.
- The Network Interactor's grid is driven from the keyboard: a dotted cell marks where it is, the arrows
  move it, Enter opens the request dialog, C the craft dialog, F stars the item and the slash puts the
  keyboard in the search. A line under the status says so.
- A click on the grid selects an item and a double click opens it. Several are selected at once by
  dragging a band across them, with Shift and the arrows, with Control and a click, or with Control+A;
  a click on empty grid drops the selection. Enter or Request then asks for all of them in one dialog, a
  quantity each, and F stars them together. The details panel lists what is selected and what it weighs.
- Two drop-downs beside the search narrow the grid to the mod that made the item and to what the item is
  (ingots, ores, raw materials, blocks, machines, tools, food and so on); both stack with the search.
- Items can be starred. A star sits on the cell, a Favourites tab at the strip's right end shows the starred
  items alone (a craftable one even when none is in stock), and the machine keeps the stars, so every window
  on it shows the same.
- Two grips reshape the window's insides: the one between the grid and the details panel gives the grid
  more columns (the inventory keeps its nine and sits centred under the wider grid), and the one above the
  inventory folds its top rows away so the grid gets their room. Where they were put, the search, the
  filters, the sort and the tab all come back with the window.
- The details panel says what the network holds of the item and where, which recipes make it and through
  which machines, what other patterns use it in, and offers Request, Craft and the star. The item's id,
  weight, tags and components follow below.
- An item the network makes more than one way (a processing recipe and a multi-stage one, say) shows the
  recipes side by side in the craft dialog, each with its machines, stages, time and inputs against the
  stock. Picking one plans with it and a strip says what it does differently from the other: the ingredients
  swapped, the stages added, the seconds gained or lost for the amount asked, and what it is short of. The
  pick is remembered per item, so the next request opens on it. Left and Right pick from the keyboard.
- The craft dialog's plan reads a short ingredient in red and says under it what the network would craft to
  cover it, or that nothing on the network makes it.
- Every zone of the Network Interactor has a boundary of its own now, drawn by the desktop's skin: the
  toolbar on a band, the items in a captioned, sunken field of drawn cells (empty ones too, so an empty
  network looks like one), the inventory in a second field, the details in a framed panel with a header
  strip, and a status bar with segments and a storage gauge. With nothing chosen, the details panel and the
  Status tab show the network's card: the Mainframe, the storage used of what the servers hold, the counts,
  and what is running.

### Changed
- The computing mod is J's Computers. It was J's Computronics, and Computronics is the name of a mod
  that already exists. The mod id stays `jsc`, so nothing a world holds changes name.
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
- Installing scales with the machine: the same program takes twice as long on each older generation of
  hardware, so a Vintage machine reading a floppy really waits and a modern one reading the network
  hardly does.
- `apt`, `dnf`, `pacman` and `pckmgr` speak as they do: a package is fetched, unpacked and set up with
  its own version, and the progress bar grows in place on one line instead of printing a new line each
  time. Removing goes the same way.
- Setup on a Linux machine happens at the prompt only; the Setup window is a Frames thing.
- A file whose extension belongs to no language is opened as text, not compiled: an assembly listing is
  read, not built.
- `cannonc` says how to run what it wrote. Compiling and running are two commands, and the prompt now
  names the second one after the first has finished.
- Virtual Studio Code's side panel is the folder and nothing else: the folder's name, then its tree,
  with no caption over it and no list of open editors above it, since the tabs already say what is
  open. A file or a folder in the tree opens on a double click; one click only picks it.

### Removed
- The one-line console at the bottom of the Network Interactor. The window does by itself what it was there
  for, and the Command Prompt is the terminal, with a history.

### Fixed
- The installation disc's setup no longer sits in the local disk's root, and the installed program's
  files are where the shortcut says they are.
- Two open terminals no longer mirror each other's output and folder.
- Virtual Studio's menus open and close on the click; the File menu no longer stays open with the others
  dead beside it.
- The name of a new file starts with the cursor before the extension, and the arrow keys move it.
- The "Open with Virtual Studio" entry of the explorer's menu is one entry, not a row of them.
- Errors are listed in a table with room for the description instead of running into each other.
- Backspace on the indented line under an opening brace no longer pulls the closing brace up onto the
  line above: a space before the caret and the end of a line after it were being taken for a pair.
- The editors read the whole folder when they mark errors, so a class that extends one written in the
  file beside it is no longer told that class does not exist.
- Two methods with the same name and the same parameters are refused as one declared twice.
- Typing a B with Ctrl held at a computer no longer switches the game's narrator on and off: the game
  saw no text box and took the key for itself.
- A running program with a long name no longer drops the player's connection when the machine's
  settings are read: every name in that message is cut to what the message can carry.
- A program with a struct or a record in it runs. The compiler wrote them into the listing under
  their own words and the machine did not know those words, so it said the listing was not something
  Cannon could run.
- A record joined to a string reads as its fields, `Item { Name = iron, Qty = 3 }`, the way its own
  `ToString` writes it, rather than as the name of its type. Any class with a `ToString` of its own
  reads that way in a sentence.
- A click on the empty end of an editor's tab strip no longer closes the last tab. It counted as that
  tab's close mark, so a few stray clicks there emptied the editor.
- Vim and Emacs open the file that was named. A file named from inside its folder (`vim Program.can`
  after `cd progs`) was asked for by its bare name, so the editor opened an empty file of that name
  instead of the one on the disk.
- Emacs takes a command after `M-x`. The letters typed after it were run into it (`M-xcompile`), so
  `M-x compile` and `C-x u` could never be read.
- A terminal program stopped on a read no longer stays on the machine's list for ever when it loses
  the terminal. Only the program in front of the terminal gets what is typed, so one that was left
  behind by another program taking the terminal could never be answered; it sat at no cost and some
  memory, listed as running. It is stopped the moment the terminal moves on, and a machine finding one
  in that state clears it.

## [0.2.0a] - 2026-09-06

The mod became a series. The code every module shares now lives in a library mod of its own, J's Core,
and the industrial machines in their own mod, J's Industrial; J's Computers keeps the computers, the
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
