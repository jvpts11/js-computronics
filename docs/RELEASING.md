# Versions, phases and releases

This page is the rule book for version numbers. It says what a version number means, when the number
changes, what the letter at the end stands for, and what a release is.

## The version format

A version is `MAJOR.MINOR.PATCH` followed by one phase letter, for example `0.1.0a` or `1.0.0r`.

- `MAJOR.MINOR.PATCH` follows [Semantic Versioning](https://semver.org/). Before `1.0.0`, `MINOR` goes
  up every time a finished slice of work reaches `main`, and `PATCH` goes up only for releases that
  contain fixes and nothing else.
- The letter is the development phase the mod is in (see below). It changes only when the mod crosses
  from one phase into the next, and the numbers never restart when it does.
- Every mod in the J's Tech Series carries the same version while the series is in lockstep (until the
  core library reaches its own `1.0`).

The version lives in one place, `mod_version` in `gradle.properties`. The jar name, the mod metadata
and the in-game mod list all read it from there.

### Development builds

A jar built from any commit other than the tagged release commit is a snapshot and says so:
`0.2.0a-SNAPSHOT.6a288a7`, where the last part is the commit it was built from. The mod loader orders a
snapshot below its release and above the previous release, so a development jar can never be mistaken
for, or override, a released one. The tagged release is built with `-Prelease`, which drops the suffix.

## Phases

| Phase   | Letter | Begins when                                                                    |
|---------|--------|--------------------------------------------------------------------------------|
| Alpha   | `a`    | Now. The first numbered version, `0.1.0a`, is alpha.                          |
| Beta    | `b`    | Computing is complete, Industrial is at least half done, Space has started.    |
| Delta   | `d`    | Space is at least half done, Transport and Warfare have started.               |
| Gamma   | `g`    | Transport and Warfare are at least half done.                                  |
| Release | `r`    | Computing, Industrial, Warfare, Transport and Space are complete: `1.0.0r`.    |

The remaining modules of the series (Agriculture, Civil Works, Robotics, Geology, Oceanics) come after
`1.0.0r`, in the `1.x.yr` line.

What the words in the table mean, so that a phase change is a checkable fact and not a feeling:

- **Started**: the module exists as its own mod in the series and its first vertical slice is on
  `main`, playable end to end and covered by tests.
- **Half done**: every module keeps a closed list of milestones split into two halves; a module is half
  done when the whole first half is playable. For Industrial the halves are the tier ladder: tiers T0 to
  T4 of T0 to T9.
- **Complete**: every milestone of the module's current design is implemented, with its chapter in the
  in-game guide and its test battery. For Computing this means the network, the operating systems and
  programs, storage, autocrafting and the servers, in the three hardware eras that exist today (Vintage,
  Legacy, Standard); the later eras are milestones of later phases, because they only make sense once
  Industrial and Space feed them.

The phase letter is decided at the release that satisfies the gate, and the changelog entry for that
release says which phase begins.

## When a version starts and ends

- A version starts when the branch for its slice opens: `mod_version` is bumped on that branch right
  away, so every jar built from it is a snapshot of the coming version.
- A version ends when the branch merges into `main` (a recorded merge) and the merge is tagged
  `v<version>`. That tag is the release.
- Fixes made on `main` between slices are `PATCH` releases, tagged the same way.

## Cutting a release

1. Merge the slice into `main` with a merge commit.
2. Confirm `mod_version` in `gradle.properties` is the version being released.
3. Move the `Unreleased` entries of `CHANGELOG.md` under the new version and date; note a phase change
   there if the release crosses a gate.
4. Tag the commit `v<version>` (for example `v0.1.0a`).
5. Build the release jar from that tag with `./gradlew build -Prelease`; it is named
   `jsc-<minecraft version>-<version>.jar`.
6. Keep a copy of the jar in a local `releases/` folder (ignored by git); every version stays there.
7. Publish the tag and the jar on GitHub.
