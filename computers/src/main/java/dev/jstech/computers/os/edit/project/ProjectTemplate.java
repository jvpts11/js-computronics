/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.edit.project;

import java.util.List;

/**
 * What a new project starts as: the shapes a program can have, each with the code that shape begins
 * with, so a player picks one and has something that already builds.
 *
 * <p>These are the templates for the language the mod ships. A language an addon brings shows up in
 * the studio's list with none, until it registers its own.
 */
public enum ProjectTemplate {

    CONSOLE_APP("Console App", ProjectFile.Kind.CONSOLE,
            "A program with a Main that runs at the terminal it was started from, prints as it goes, "
                    + "and is gone when it returns.",
            List.of("Console")),
    SCRIPT("Script", ProjectFile.Kind.SCRIPT,
            "A program that stays up: set up once, called every tick, told when it is stopped. "
                    + "For watching the network and reacting to it.",
            List.of("Script")),
    CLASS_LIBRARY("Class Library", ProjectFile.Kind.LIBRARY,
            "Classes shared by other projects. Has no entry point of its own and compiles into theirs.",
            List.of("Library")),
    EMPTY_PROJECT("Empty Project", ProjectFile.Kind.EMPTY,
            "A project with no files in it. For starting from nothing.",
            List.of());

    /** The language every template here is for, by registry id. */
    public static final String LANGUAGE = "jsc:cannon";

    /** The platforms every template here runs on, for the filter. */
    public static final List<String> PLATFORMS = List.of("Frames", "Linux");

    private final String title;
    private final ProjectFile.Kind kind;
    private final String description;
    private final List<String> tags;

    ProjectTemplate(final String title, final ProjectFile.Kind kind, final String description,
                    final List<String> tags) {
        this.title = title;
        this.kind = kind;
        this.description = description;
        this.tags = tags;
    }

    public String title() {
        return this.title;
    }

    public ProjectFile.Kind kind() {
        return this.kind;
    }

    public String description() {
        return this.description;
    }

    /** The words the filters match: the language, the platforms, and the kind. */
    public List<String> tags() {
        final List<String> out = new java.util.ArrayList<>();
        out.add("Cannon");
        out.addAll(PLATFORMS);
        out.addAll(this.tags);
        return out;
    }

    /** Whether the template's kind is the one asked for, or anything when nothing was. */
    public boolean isKind(final String kindFilter) {
        return kindFilter == null || kindFilter.isEmpty() || this.tags.contains(kindFilter);
    }

    /** The name of the first source a project of this template starts with, or empty for none. */
    public String firstSource(final String projectName) {
        return this == EMPTY_PROJECT ? "" : projectName + ".can";
    }

    /** The project file a new project of this template starts as. */
    public ProjectFile project(final String projectName) {
        final String first = firstSource(projectName);
        return new ProjectFile(projectName, this.kind, LANGUAGE,
                first.isEmpty() ? List.of() : List.of(first), List.of(),
                this.kind == ProjectFile.Kind.LIBRARY || this.kind == ProjectFile.Kind.EMPTY
                        ? "" : ProjectFile.defaultEntry(projectName));
    }

    /**
     * The code the first source starts with: a program of the shape that already builds, named after
     * the project so the type is not a name the language has taken.
     */
    public String source(final String projectName) {
        return switch (this) {
            case CONSOLE_APP -> """
                    using System.IO.*;

                    namespace %s;

                    class %s {
                        static void Main() {
                            Console.PrintLine("Hello from %s");
                        }
                    }
                    """.formatted(projectName, projectName, projectName);
            case SCRIPT -> """
                    using System.*;
                    using System.IO.*;

                    namespace %s;

                    class %s : IScript {
                        public void OnInit() {
                            Console.PrintLine("%s is up");
                        }

                        public void OnTick() {
                        }

                        public void OnDestroy() {
                        }
                    }
                    """.formatted(projectName, projectName, projectName);
            case CLASS_LIBRARY -> """
                    namespace %s;

                    public class %s {
                        public static int Answer() {
                            return 42;
                        }
                    }
                    """.formatted(projectName, projectName);
            case EMPTY_PROJECT -> "";
        };
    }
}
