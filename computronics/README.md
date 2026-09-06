# J's Computronics

Computers for Minecraft that do the logistics of your base. You build the hardware, install a system on
it, and the network of machines you end up with stores your items, moves them, and crafts for you.

This is the computing mod of the [J's Tech Series](../README.md); its id is `jsc`.

## What it is

Most storage mods give you a box that swallows items. This one gives you hardware. Items live as data on
disks, disks sit in drives and servers, servers mount in racks, and a mainframe indexes the whole
network. More storage means more servers. Faster means a better processor. Pull a disk out and its data
leaves with it.

Every computer needs a processor, memory, a system disk and something to show a screen on. Hardware comes
in eras, from the vintage machines with floppy drives to the current ones with USB sticks, and the era of
a computer decides which systems it runs and how fast it does things.

Systems are installed from media. The Frames desktops (95, XP and 11) and a handful of Linux systems each
ship their own programs: a file explorer, a text editor, a command prompt, settings, a system monitor, and
the programs that talk to the network. Programs you install on top come on their own discs.

Everything the network does is an Operation: select, insert, move, craft. Operations take time that
depends on the hardware doing them, and the mainframe's task manager shows the queue while the network
works through it. The command prompt speaks IQL, a small query language that compiles to the same
Operations the graphical programs use.

Crafting is machine work. A Crafting Computer runs recipes from its Recipe ROM; recipes are authored on the
Pattern Studio, burned onto media by a Pattern Encoder, and loaded through the Crafting Manager. A
crafting cable, a switch and buses connect the computer to the machines that do the processing, other mods'
machines included, and a request for an item plans every step, from raw stock to the finished product,
across as many machines as the network has.

## What is in the box today

- Data cables, routers, a mainframe multiblock, servers and racks per era, a supercomputer rack, and the
  Cluster Management Computer that installs and monitors racked machines.
- Personal computers, crafting computers, monitors and their peripheral cables; drives for floppies, CDs,
  DVDs and USB sticks; a dock station.
- The Frames 95, XP and 11 desktops and five Linux distributions with three desktop environments, with a
  boot manager, dual boot and package managers.
- The Network Interactor for storage and crafting requests, the Network Manager for the mainframe, the
  Crafting Manager, the Craft Planner, Storage Insights, the Automation Manager, and a few small programs.
- Machine autocrafting with multi-stage recipes, parallel stages and crafting-card threads; fluids and
  chemicals travel through the network like items.
- JEI support: the ingredient list sits beside every monitor screen and recipes transfer straight into the
  Pattern Studio. Mekanism machines can be driven through the network when Mekanism is present.

Recipes for the computing blocks are missing on purpose: they arrive with the industrial chains that
make their parts. Play it in creative for now.

## Requirements

- Minecraft 1.21.1 and NeoForge 21.1.248 or newer.
- [J's Core](../core/README.md) at the same version (required).
- [GeckoLib](https://github.com/bernie-g/geckolib) 4.7 or newer (required).
- [JEI](https://github.com/mezz/JustEnoughItems) (optional, for recipe lookup beside the monitors).
- [Mekanism](https://github.com/mekanism/Mekanism) (optional, its machines and chemicals join the network).

It does not need [J's Industrial](../industrial/README.md): the network drives any machine that exposes
the usual item and energy capabilities, whichever mod it comes from.
