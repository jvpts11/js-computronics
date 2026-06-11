# J's Computronics

**A tech mod where the computers are real.** Build machines out of actual hardware — motherboards,
CPUs, RAM sticks, PCIe cards, PSUs, disks — wire them into a network, and run your base through it.

- **Minecraft:** 1.21.1 · **Loader:** NeoForge · **License:** LGPL-3.0-only
- **Status:** early, active development — no public builds yet

---

## The idea

Most storage mods give you a magic box: cables in, infinite digital soup out. J's Computronics
takes the other road. Here, **everything is data** — items and fluids are digital information that
lives on real disks, inside real servers, indexed by a real mainframe. If you want more storage,
you build more servers. If you want faster operations, you install a better CPU. If you pull the
disk out of a computer, its data goes with it.

The network is not a black box either. It runs **Operations** — SQL-flavored commands like
`SELECT`, `INSERT`, `MOVE` and `CRAFT` — that the mainframe decomposes into SubOperations, spreads
across the network's hardware, and executes over real time. Disk tiers have latency. CPUs have
throughput. GPUs add parallel queues. An overloaded mainframe genuinely queues your work, and the
task manager shows you exactly what is running where.

The long-term goal is a complete, deeply integrated tech ecosystem — industry, logistics, and far
beyond — all orchestrated through the computer network, and tunable enough that modpack authors can
enable exactly the slice they want.

## What works today

**The data network**
- Ethernet and high-bandwidth backbone cables, bridged by routers — one mainframe per network,
  with conflict detection, orphaned-network recovery and active-passive failover
- Server Routers that segment large builds into datacenter sections, plus a Datacenter Station
  kiosk to operate a whole section at once

**Computers you actually assemble**
- Motherboard form factors with real slot layouts: the board you install decides how many CPUs,
  RAM sticks, PCIe cards and disks fit
- Hardware that matters: CPU cores/clock set orchestration speed, RAM buffers operations, GPUs
  multiply parallel queues, disks (HDD / SSD / NVMe, multiple capacities) hold the data
- Mainframes (multiblock), Personal Computers, Servers racked into cabinets, and Crafting Computers

**Storage and logistics**
- Disk-backed storage: items *and fluids* stored as data, with per-disk capacity
- Import/Export buses mounted directly on data cables; tanks for fluid I/O
- A Monitor terminal with Local / Storage / Network / Craft / Operations / Task Manager /
  Maintenance tabs — including index maintenance (`ANALYZE`, `REINDEX`, `VACUUM`, `DROP`) for
  people who like their storage networks like they like their databases

**Autocrafting**
- Encode recipes on a Pattern Encoder, carry them on pattern discs (write-once or rewritable),
  and load them into a Crafting Computer's Recipe ROM — hard-capped at 50 patterns per computer,
  so you scale horizontally with specialized crafters instead of one all-knowing box
- A recursive `CRAFT` operation that plans the whole tree, locks its ingredients, crafts
  intermediates, returns every surplus, and supports partial crafting when stock runs short
- A Craft tab in the terminal: live catalog with availability at a glance, a request popup with
  the full bill of materials, and running/recent craft panels

**Industry (first steps)**
- An FE energy network with lossless proportional distribution, plus the first machines —
  more industrial tiers are on the way

## Building from source

```bash
git clone https://github.com/jvpts11/js-computronics.git
cd js-computronics
./gradlew build        # or gradlew.bat build on Windows
```

Useful dev tasks: `runClient`, `runServer`, `runData` (datagen), `runGameTestServer`
(the in-world integration test suite), `test` (unit tests).

Requires Java 21. The built jar lands in `build/libs/`.

## Status & feedback

The mod is in active solo development and the gameplay loop is still being built out — expect
sharp edges, missing recipes and placeholder art. Public releases will start once the core loop
is fun end-to-end.

Found something broken, or have thoughts on the direction? Open an issue — feedback is very
welcome at this stage.

## License

[LGPL-3.0-only](LICENSE) — © jvpts11
