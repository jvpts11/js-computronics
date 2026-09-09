/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.edit.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A solution: the projects that are worked on together, and which of them starts.
 *
 * <p>A solution is a folder with this file in it and a folder per project under it. The file lists
 * the projects by the path of their project file, relative to the solution folder, so a solution can
 * be read without listing its folder.
 *
 * @param name     what the solution is called, which is also its folder
 * @param projects the project files, relative to the solution folder
 * @param startup  the name of the project that runs on Start, or empty for the first that can
 */
public record SolutionFile(String name, List<String> projects, String startup) {

    /** The extension a solution file carries. */
    public static final String EXTENSION = "sln";

    public SolutionFile {
        projects = List.copyOf(projects);
        startup = startup == null ? "" : startup;
    }

    /** The file name a solution of that name keeps itself in. */
    public static String fileName(final String name) {
        return name + "." + EXTENSION;
    }

    /** The relative path of a project's file inside the solution: its folder, then its file. */
    public static String projectPath(final String projectName) {
        return projectName + "/" + ProjectFile.fileName(projectName);
    }

    /** The name of the project a relative project path is for. */
    public static String projectNameOf(final String projectPath) {
        final int slash = projectPath.lastIndexOf('/');
        final String file = slash >= 0 ? projectPath.substring(slash + 1) : projectPath;
        final int dot = file.lastIndexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }

    /** The solution with one more project, or the same one when it is already there. */
    public SolutionFile withProject(final String projectPath) {
        if (this.projects.contains(projectPath)) {
            return this;
        }
        final List<String> more = new ArrayList<>(this.projects);
        more.add(projectPath);
        return new SolutionFile(this.name, more, this.startup.isEmpty() ? projectNameOf(projectPath) : this.startup);
    }

    /** The solution starting from another project. */
    public SolutionFile withStartup(final String projectName) {
        return new SolutionFile(this.name, this.projects, projectName);
    }

    /** The file's text. */
    public String write() {
        final StringBuilder out = new StringBuilder();
        out.append("solution: ").append(this.name).append('\n');
        for (final String project : this.projects) {
            out.append("project: ").append(project).append('\n');
        }
        out.append("startup: ").append(this.startup).append('\n');
        return out.toString();
    }

    /** Reads a file's text, skipping what it cannot read. */
    public static SolutionFile read(final String text) {
        String name = "";
        final List<String> projects = new ArrayList<>();
        String startup = "";
        for (final String raw : (text == null ? "" : text).split("\n")) {
            final String line = raw.trim();
            final int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            final String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            final String value = line.substring(colon + 1).trim();
            switch (key) {
                case "solution" -> name = value;
                case "project" -> {
                    if (!value.isEmpty()) {
                        projects.add(value);
                    }
                }
                case "startup" -> startup = value;
                default -> { }
            }
        }
        return new SolutionFile(name, projects, startup);
    }
}
