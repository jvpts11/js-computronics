# J's Tech Series

Technology mods for Minecraft 1.21.1 on NeoForge, one mod per area, on top of a shared core library. The
computer network of J's Computronics is the backbone that ties the areas together: the machines of every
other mod end up as data on that network, stored, moved and driven by the computers you build.

The series is in alpha. See [what a version means](docs/RELEASING.md), [what changed](CHANGELOG.md) and the
[releases page](https://github.com/jvpts11/js-computronics/releases) for the builds.

## The mods

| Mod | Id | What it is |
|---|---|---|
| [J's Core](core/README.md) | `jscore` | The shared library. Every mod of the series requires it. No gameplay of its own beyond the material items the others share. |
| [J's Computronics](computronics/README.md) | `jsc` | Computers, the data network, the systems and programs, storage and autocrafting. |
| [J's Industrial](industrial/README.md) | `jsindustrial` | Energy, machines and the processing chains that feed the network. |

Planned, in the order the series grows: Space, Warfare, Transport, Agriculture, Civil Works, Robotics,
Geology and Oceanics. The phases the series goes through, and what each phase needs before it starts, are
in [docs/RELEASING.md](docs/RELEASING.md).

Every mod carries the same version and they are released together. Put J's Core and the mods you want in
`mods/`, all at the same version; each mod's page lists its own requirements. J's Computronics and J's
Industrial do not need each other: the computing mod drives any machine that exposes the usual item and
energy capabilities, and the industrial mod's machines work with any FE generator.

## Repository layout

One repository, several mods, all built at the same version:

- `core/`: J's Core (`jscore`), the shared library.
- `computronics/`: J's Computronics (`jsc`), the computing mod.
- `industrial/`: J's Industrial (`jsindustrial`), the industrial mod.
- `tests/`: the development-only test mod. **It is not a mod to install.** It holds the tests of every mod
  and hosts the development runs; it is never released and adds nothing to the game. See
  [tests/README.md](tests/README.md).
- `docs/`: the public documentation: code style, versions and releases.

## Building from source

```
git clone https://github.com/jvpts11/js-computronics.git
cd js-computronics
./gradlew build
```

Java 21. Each mod's jar lands in its own `build/libs`, for example `computronics/build/libs/jsc-1.21.1-<version>.jar`;
a build that is not the tagged release carries a `-SNAPSHOT.<commit>` suffix. Useful tasks: `runClient`,
`runServer`, `test` (pure logic, JUnit), `runGameTestServer` (the mods in a headless server),
`runClientTests0` (a real client that drives the screens and takes screenshots), and `:core:runData`,
`:computronics:runData` and `:industrial:runData` to regenerate a mod's data.

## Contributing

Read [docs/CODE_STYLE.md](docs/CODE_STYLE.md) before opening a pull request, and
[AI_POLICY.md](AI_POLICY.md) if you work with an AI assistant. Bug reports and ideas go in the issues.

## License

[LGPL-3.0-only](LICENSE), © jvpts11.
