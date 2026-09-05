# J's Computronics

Computers for Minecraft that do the logistics of your base. You build the hardware, install a system on
it, and the network of machines you end up with stores your items, moves them, and crafts for you.

Minecraft 1.21.1 on NeoForge. The mod is in alpha, version `0.1.0a`; see [what the version means](docs/RELEASING.md)
and [what changed](CHANGELOG.md). Builds are on the [releases page](https://github.com/jvpts11/js-computronics/releases).

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

Recipes for the mod's own blocks are missing on purpose: the industrial side that will provide them is
the next module of the series. Play it in creative for now.

## Where it is going

J's Computronics is the first mod of the J's Tech Series. The series is one mod per area, each its own
download, on top of a shared core library: computing (this mod), industrial, space, warfare, transport,
agriculture, civil works, robotics, geology and oceanics. The computer network stays the backbone that
ties them together. The phases the series goes through, and what each one needs before it starts, are in
[docs/RELEASING.md](docs/RELEASING.md).

## Requirements

- Minecraft 1.21.1 and NeoForge 21.1.248 or newer.
- [GeckoLib](https://github.com/bernie-g/geckolib) 4.7 or newer (required).
- [JEI](https://github.com/mezz/JustEnoughItems) (optional, for recipe lookup beside the monitors).
- [Mekanism](https://github.com/mekanism/Mekanism) (optional, its machines and chemicals join the network).

## Repository layout

One repository, several mods, all built at the same version:

- `core/`: J's Core (`jscore`), the shared library every mod of the series requires.
- `computronics/`: J's Computronics (`jsc`), this mod.
- `industrial/`: J's Industrial (`jsindustrial`), the industrial mod.
- `tests/`: the development-only test mod. **It is not a mod to install.** It holds the tests of every mod
  and hosts the development runs; it is never released and adds nothing to the game. See
  [tests/README.md](tests/README.md).

## Building from source

```
git clone https://github.com/jvpts11/js-computronics.git
cd js-computronics
./gradlew build
```

Java 21. Each mod's jar lands in its own `build/libs`, for example `computronics/build/libs/jsc-1.21.1-<version>.jar`;
a build that is not the tagged release carries a `-SNAPSHOT.<commit>` suffix. Useful tasks: `runClient`,
`runServer`, `runData`, `test` (pure logic, JUnit), `runGameTestServer` (the mods in a headless server), and
`runClientTests0` (a real client that drives the screens and takes screenshots).

## Contributing

Read [docs/CODE_STYLE.md](docs/CODE_STYLE.md) before opening a pull request, and
[AI_POLICY.md](AI_POLICY.md) if you work with an AI assistant. Bug reports and ideas go in the issues.

## License

[LGPL-3.0-only](LICENSE), © jvpts11.
