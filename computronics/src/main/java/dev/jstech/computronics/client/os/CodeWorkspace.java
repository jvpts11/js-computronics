/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.operation.payload.DiskFilesPayload;
import dev.jstech.computronics.operation.payload.RequestDiskFilesPayload;
import dev.jstech.computronics.operation.payload.RequestFileContentPayload;
import dev.jstech.computronics.operation.payload.SaveFilePayload;
import dev.jstech.computronics.os.edit.CodeRuns;
import dev.jstech.computronics.os.edit.InkPalette;
import dev.jstech.core.JsCore;
import dev.jstech.core.language.IProgrammingLanguage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What every editor in the mod has to do, whatever it looks like: know which programs are on the
 * machine's disk, hold the ones the player opened, colour them, ask the compiler what is wrong with
 * them, and put them back on the disk.
 *
 * <p>Three windows show all of that differently and none of them should own a second copy of it, so it
 * lives here once and each window draws whatever it wants of it. Nothing here knows a language either:
 * the file's extension picks one out of the registry the Core keeps.
 */
public final class CodeWorkspace implements CodeFileReplies.IReader {

    /** Where a machine keeps what the player writes; the folder the prompt starts in. */
    public static final String HOME = "progs";

    /** One file the player has open. */
    public static final class Doc {

        private final String path;
        private final CodeArea area = new CodeArea();
        private boolean dirty;
        private List<IProgrammingLanguage.Complaint> complaints = List.of();

        private Doc(final String path) {
            this.path = path;
        }

        /** Its whole path on the disk. */
        public String path() {
            return this.path;
        }

        /** Just the file name, which is what a tab is labelled with. */
        public String name() {
            final int slash = this.path.lastIndexOf('/');
            return slash >= 0 && slash < this.path.length() - 1 ? this.path.substring(slash + 1) : this.path;
        }

        /** The text area it is edited in. */
        public CodeArea area() {
            return this.area;
        }

        /** Whether it has been changed since it was last put back on the disk. */
        public boolean dirty() {
            return this.dirty;
        }

        /** What the compiler said about it, as it stands. */
        public List<IProgrammingLanguage.Complaint> complaints() {
            return this.complaints;
        }
    }

    private final BlockPos host;
    private final List<DiskFilesPayload.WireFile> files = new ArrayList<>();
    private final List<Doc> docs = new ArrayList<>();
    private int current = -1;
    private String status = "";
    private InkPalette palette = InkPalette.LIGHT;

    public CodeWorkspace(final BlockPos host) {
        this.host = host;
    }

    /* What is on the disk */

    /** Asks the machine which programs it holds. */
    public void refresh() {
        CodeFileReplies.expectListing(this);
        PacketDistributor.sendToServer(new RequestDiskFilesPayload(this.host, HOME));
    }

    /** The programs on the disk: the files some language in the registry claims. */
    public List<DiskFilesPayload.WireFile> files() {
        return this.files;
    }

    @Override
    public void onListing(final DiskFilesPayload listing) {
        this.files.clear();
        for (final DiskFilesPayload.WireFile file : listing.files()) {
            if (!file.directory() && languageOf(file.path()) != null) {
                this.files.add(file);
            }
        }
    }

    /* What is open */

    /** The open files, in the order they were opened. */
    public List<Doc> docs() {
        return this.docs;
    }

    /** The tab labels, with a mark on the ones that have unsaved changes. */
    public List<String> tabLabels() {
        final List<String> labels = new ArrayList<>(this.docs.size());
        for (final Doc doc : this.docs) {
            labels.add(doc.name() + (doc.dirty ? "*" : ""));
        }
        return labels;
    }

    /** The file being edited, or null when none is. */
    public Doc current() {
        return this.current >= 0 && this.current < this.docs.size() ? this.docs.get(this.current) : null;
    }

    /** Which of the open files is being edited. */
    public int currentIndex() {
        return this.current;
    }

    /** Puts the keyboard on one of the open files. */
    public void setCurrent(final int index) {
        this.current = index >= 0 && index < this.docs.size() ? index : -1;
    }

    /** Opens a file, asking the machine for it when it is not already open. */
    public void open(final String path) {
        for (int i = 0; i < this.docs.size(); i++) {
            if (this.docs.get(i).path.equals(path)) {
                this.current = i;
                return;
            }
        }
        CodeFileReplies.expectContent(this);
        PacketDistributor.sendToServer(new RequestFileContentPayload(this.host, path));
    }

    @Override
    public void onContent(final String path, final String content, final boolean exists) {
        final Doc doc = new Doc(path);
        doc.area.setText(content)
                .setPalette(this.palette)
                .setColouring(lines -> colour(path, lines));
        this.docs.add(doc);
        this.current = this.docs.size() - 1;
        recompile(doc);
        this.status = exists ? "Opened " + doc.name() : "New file " + doc.name();
    }

    /** Puts the file being edited back on the disk. */
    public void save() {
        final Doc doc = current();
        if (doc == null) {
            return;
        }
        this.status = "Saving...";
        CodeFileReplies.expectSaved(this);
        PacketDistributor.sendToServer(new SaveFilePayload(this.host, doc.path, doc.area.text()));
    }

    @Override
    public void onSaved(final boolean ok, final String message) {
        this.status = message;
        final Doc doc = current();
        if (ok && doc != null) {
            doc.dirty = false;
        }
    }

    /** Closes the file at {@code index}, whether or not it was saved. */
    public void close(final int index) {
        if (index < 0 || index >= this.docs.size()) {
            return;
        }
        this.docs.remove(index);
        this.current = Math.min(this.current, this.docs.size() - 1);
    }

    /** Stops the machine's answers arriving after the window is gone. */
    public void release() {
        CodeFileReplies.forget(this);
    }

    /* What it looks like and what is wrong with it */

    /** The colours to paint code in, which follow the window's own ground. */
    public void setPalette(final InkPalette value) {
        this.palette = value;
        for (final Doc doc : this.docs) {
            doc.area.setPalette(value);
        }
    }

    /** The last word from the machine, for a status line to show. */
    public String status() {
        return this.status;
    }

    /** Says something in the status line. */
    public void say(final String message) {
        this.status = message == null ? "" : message;
    }

    /** The language of the open file, or null when nothing claims it. */
    public IProgrammingLanguage language() {
        final Doc doc = current();
        return doc == null ? null : languageOf(doc.path);
    }

    /** The language that claims a file, or null when nothing in the registry does. */
    public static IProgrammingLanguage languageOf(final String path) {
        final int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return null;
        }
        return JsCore.languages().byExtension(path.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** The rows of a file, coloured by whichever language owns it. */
    private static List<List<CodeRuns.Run>> colour(final String path, final List<String> lines) {
        final IProgrammingLanguage language = languageOf(path);
        if (language == null) {
            return List.of();
        }
        final List<CodeRuns.Span> spans = new ArrayList<>();
        for (final IProgrammingLanguage.Token token : language.tokenize(String.join("\n", lines))) {
            spans.add(new CodeRuns.Span(token.line(), token.column(), token.length(), inkOf(token.kind())));
        }
        return CodeRuns.byLine(lines, spans);
    }

    private static CodeRuns.Ink inkOf(final IProgrammingLanguage.Kind kind) {
        return switch (kind) {
            case KEYWORD -> CodeRuns.Ink.KEYWORD;
            case NAME -> CodeRuns.Ink.NAME;
            case TEXT -> CodeRuns.Ink.TEXT;
            case NUMBER -> CodeRuns.Ink.NUMBER;
            case COMMENT -> CodeRuns.Ink.COMMENT;
            case SYMBOL -> CodeRuns.Ink.SYMBOL;
        };
    }

    /**
     * Reads the open file the way the compiler would and puts what it complained about in its margin.
     *
     * <p>A compiler is arithmetic over text, so it runs on the machine the player is sitting at rather
     * than costing a round trip for every keystroke. Running the program is the part that needs the
     * computer in the world.
     */
    public void recompile(final Doc doc) {
        final IProgrammingLanguage language = languageOf(doc.path);
        if (language == null) {
            doc.complaints = List.of();
            doc.area.setMarks(List.of());
            return;
        }
        doc.complaints = language.compile(
                List.of(new IProgrammingLanguage.SourceText(doc.name(), doc.area.text()))).complaints();
        final List<CodeArea.Mark> marks = new ArrayList<>(doc.complaints.size());
        for (final IProgrammingLanguage.Complaint complaint : doc.complaints) {
            marks.add(new CodeArea.Mark(complaint.line(), true, complaint.code() + ": " + complaint.message()));
        }
        doc.area.setMarks(marks);
    }

    /** Notes that the player changed the open file, and reads it again. */
    public void edited() {
        final Doc doc = current();
        if (doc != null) {
            doc.dirty = true;
            recompile(doc);
        }
    }
}
