# J's Computronics

A logistics mod where you build actual computers to handle your items.

Minecraft 1.21.1, NeoForge. Early development, no public builds yet.

## The idea

Most storage mods give you a magic box that swallows your items. I wanted the opposite: storage that is actual hardware. Items live as data on disks, disks sit inside servers, servers go in racks, and a mainframe keeps an index of the whole thing. If you want more storage you build more servers. If you want it faster you install a better CPU. If you rip a disk out of a computer, the data leaves with it.

Everything the network does is an Operation (SELECT, INSERT, MOVE, CRAFT and so on). Operations take real time depending on the hardware involved, and there's a task manager screen where you can watch your network struggle with the workload you gave it.

Autocrafting works through pattern discs: you encode recipes on a disc, load them into a crafting computer (50 recipes each, build more computers if you need more), and a supercomputer cluster can run a lot of crafts in parallel if you feed it co-processors.

Fluids are stored the same way as items, on the same disks. There's also the start of an industrial side (energy network, first machines) that will grow into the main progression.

I'm not going to list every block here, the mod is changing weekly. The short version: data cables, routers, server racks, a mainframe multiblock, personal computers, monitors, import/export buses, tanks, pattern encoders/readers, crafting computers and a supercomputer made of individual nodes.

## Building

```
git clone https://github.com/jvpts11/js-computronics.git
cd js-computronics
./gradlew build
```

Java 21. The jar ends up in `build/libs`. For development: `runClient`, `runData`, `test`, `runGameTestServer`.

## Status

Solo project. Lots of placeholder art, recipes missing, numbers not final. I want the core loop to be fun before putting out builds. If you find something broken or have ideas, open an issue.

## Contributing

Read [docs/CODE_STYLE.md](docs/CODE_STYLE.md) before opening a pull request. If you work with an AI assistant, read [AI_POLICY.md](AI_POLICY.md) first: it says what is welcome, what a pull request must state, and what gets rejected.

## License

[LGPL-3.0-only](LICENSE) - © jvpts11
