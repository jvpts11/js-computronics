# Code style

The J's Tech Series is a set of NeoForge mods for Minecraft 1.21.1, written in Java 21 and built with
Gradle and ModDevGradle. Everything in the repository is written in English: code, comments, Javadoc, translation
keys and values, commit messages, branch names and issues. The style below is what a reviewer expects to
see in a pull request.

## File header

Every `.java` file starts with this block comment, above the `package` line:

```java
/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers...;
```

The SPDX tag identifies the licence; the full text lives in `LICENSE`. Do not use the line-comment form and
do not paste the long licence preamble. The last line names the mod the file belongs to: J's Core, J's
Computers or J's Industrial.

## Modelling

- Records for values and data-transfer types; fields are `final`; prefer immutability and expose as little
  as possible.
- Sealed interfaces and sealed classes for closed hierarchies, so a `switch` can be exhaustive and nothing
  extends what should not be extended.
- Wrapper types for strongly typed identifiers (a `NetworkUuid`, a `NodeUuid`) instead of raw `UUID`s
  travelling through the code.

## Naming

- Classes in `PascalCase`, methods and fields in `camelCase`, constants in `UPPER_SNAKE_CASE`.
- Translation keys follow the vanilla pattern: `block.jsc.macerator`, `item.jsc.aerogel`,
  `jsc.gui.operation.pending`.
- Enum constants read as what they are: `OperationStatus.PENDING`, `OperationStatus.COMPLETED_PARTIAL`.

## Imports and layout

- No unused imports, no wildcard imports.
- Members in a readable order: fields, constructor, public methods, private methods.

## Comments

Comments explain why, not what. A comment must stand on its own: a reader of the public repository has
nothing but the code, so a comment that points at a document they cannot see explains nothing. Write the
technical reasoning out.

## Tests

- Pure logic, with no dependency on Minecraft or NeoForge, is tested with JUnit 5 under `src/test/java`,
  mirroring the package of the class under test. Such a test never imports `net.minecraft.*` or
  `net.neoforged.*`.
- Anything that touches the game (registries, items, blocks, block entities, menus, capabilities,
  networking, datagen, any runtime object) is a GameTest under `src/main/java/.../gametest/`, run with
  `runGameTestServer`. When in doubt, it is a GameTest.
- Test methods are named `methodName_expectedBehavior`. Use `@Test` and, for simple setup, `@BeforeEach`;
  do not use `@DisplayName` or `@Nested`. Helpers that need no instance state are `private static`.
- Run `runData` after any change to a block state property, a model, a texture or a language entry, and
  check that it finished successfully.

## Commits and branches

- A plain imperative title, no trailing period, followed by an optional body that explains what changed
  and why: `Add the energy network distribution`, `Fix the peripheral cable length clamp`. No type
  prefixes such as `feat:` or `fix:`.
- Branch names in English, in three parts: the mods the branch touches, the kind of work, and its name:
  `<mod>,<mod>/<kind>/<name>`. The mods are the subproject names separated by commas, with no spaces
  (git does not allow them in a ref); the kind is `feature`, `fix` or whatever the work is. Examples:
  `core/fix/payload-streamcodec`, `computers,core/feature/user-software`. The first part says at a
  glance which jars a branch changes.
- Commits carry the author's own authorship and nothing else.

## NeoForge 1.21.1

The APIs this project uses are the 1.21.1 ones. Newer NeoForge lines changed several of them, so before
copying a snippet from the web, check it against 21.1. In particular: data components instead of raw
item NBT; the block, entity and item capability system, never `LazyOptional`; `SavedData` with
`save(CompoundTag, HolderLookup.Provider)` and a `SavedData.Factory`; payloads registered through
`RegisterPayloadHandlersEvent`; screens with the `renderBg` / `renderLabels` / `render` signatures.
