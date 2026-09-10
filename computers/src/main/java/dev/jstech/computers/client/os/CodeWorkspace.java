/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.operation.payload.DiskFilesPayload;
import dev.jstech.computers.operation.payload.RequestDiskFilesPayload;
import dev.jstech.computers.operation.payload.RequestFileContentPayload;
import dev.jstech.computers.operation.payload.SaveFilePayload;
import dev.jstech.computers.os.edit.CodeRuns;
import dev.jstech.computers.os.edit.InkPalette;
import dev.jstech.computers.os.edit.ProblemReport;
import dev.jstech.core.JsCore;
import dev.jstech.core.client.gui.logic.TextDocument;
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

    /*
     * The folder the workspace is on. It starts on the machine's own, and an editor that works in
     * folders the way one of them does points it wherever the player opened.
     */
    private String folder = HOME;

    /** What every folder the workspace has looked into holds, by folder, files and subfolders alike. */
    private final java.util.Map<String, List<DiskFilesPayload.WireFile>> listings = new java.util.LinkedHashMap<>();

    /** The subfolders the player has opened up in a tree, by path. */
    private final java.util.Set<String> expanded = new java.util.LinkedHashSet<>();

    /** Folders still to be asked for, one at a time, since one listing is waited for at once. */
    private final java.util.ArrayDeque<String> toList = new java.util.ArrayDeque<>();

    public CodeWorkspace(final BlockPos host) {
        this.host = host;
    }

    /* What is on the disk */

    /** The folder the workspace is on. */
    public String folder() {
        return this.folder;
    }

    /** Points the workspace at another folder and reads it. */
    public void setFolder(final String dir) {
        this.folder = dir == null ? "" : dir;
        this.expanded.clear();
        this.listings.clear();
        refresh();
    }

    /** Asks the machine what the folder holds, and what every opened subfolder holds. */
    public void refresh() {
        this.toList.clear();
        this.toList.add(this.folder);
        this.toList.addAll(this.expanded);
        askNext();
    }

    private void askNext() {
        final String dir = this.toList.poll();
        if (dir == null) {
            return;
        }
        CodeFileReplies.expectListing(this, dir);
        PacketDistributor.sendToServer(new RequestDiskFilesPayload(this.host, dir));
    }

    /** Opens a subfolder up in the tree, or closes it again. */
    public void toggleFolder(final String dir) {
        if (!this.expanded.remove(dir)) {
            this.expanded.add(dir);
            if (!this.listings.containsKey(dir)) {
                this.toList.add(dir);
                if (this.toList.size() == 1) {
                    askNext();
                }
            }
        }
    }

    /** Whether a subfolder is opened up in the tree. */
    public boolean isExpanded(final String dir) {
        return this.expanded.contains(dir);
    }

    /** One row of the tree: how deep it sits, and what it is. */
    public record TreeRow(int depth, DiskFilesPayload.WireFile file) {
    }

    /**
     * The folder as a tree: its entries, subfolders first, each opened subfolder's entries indented
     * under it. Every file is listed, not only the programs, since a folder is what the player sees.
     */
    public List<TreeRow> tree() {
        final List<TreeRow> out = new ArrayList<>();
        addRows(out, this.folder, 0);
        return out;
    }

    private void addRows(final List<TreeRow> out, final String dir, final int depth) {
        final List<DiskFilesPayload.WireFile> here = this.listings.getOrDefault(dir, List.of());
        for (final DiskFilesPayload.WireFile file : here) {
            if (file.directory()) {
                out.add(new TreeRow(depth, file));
                if (this.expanded.contains(file.path())) {
                    addRows(out, file.path(), depth + 1);
                }
            }
        }
        for (final DiskFilesPayload.WireFile file : here) {
            if (!file.directory()) {
                out.add(new TreeRow(depth, file));
            }
        }
    }

    /** The programs in the folder: the files some language in the registry claims. */
    public List<DiskFilesPayload.WireFile> files() {
        return this.files;
    }

    @Override
    public void onListing(final DiskFilesPayload listing) {
        this.listings.put(listing.dir(), List.copyOf(listing.files()));
        if (listing.dir().equals(this.folder)) {
            this.files.clear();
            for (final DiskFilesPayload.WireFile file : listing.files()) {
                if (!file.directory() && languageOf(file.path()) != null) {
                    this.files.add(file);
                }
            }
        }
        askNext();
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
        CodeFileReplies.expectContent(this, path);
        PacketDistributor.sendToServer(new RequestFileContentPayload(this.host, path));
    }

    /** The files still to open, one after the other, and the one to end on. */
    private final java.util.ArrayDeque<String> toOpen = new java.util.ArrayDeque<>();
    private String endOn = "";

    /**
     * Opens several files, ending on {@code current}, or on the last when it names none of them.
     *
     * <p>The machine answers one request for a file at a time, so the files are asked for one after
     * the other: the next goes out when the one before has arrived. A file that is no longer on the
     * disk comes back empty, as a new one, which is the most a window can do about it.
     */
    public void openAll(final List<String> paths, final String current) {
        this.toOpen.clear();
        this.toOpen.addAll(paths);
        this.endOn = current == null ? "" : current;
        openNext();
    }

    private void openNext() {
        while (true) {
            final String next = this.toOpen.pollFirst();
            if (next == null) {
                break;
            }
            final int at = indexOf(next);
            if (at < 0) {
                open(next);
                return;
            }
            this.current = at;
        }
        final int end = indexOf(this.endOn);
        if (end >= 0) {
            this.current = end;
        }
        this.endOn = "";
    }

    private int indexOf(final String path) {
        for (int i = 0; i < this.docs.size(); i++) {
            if (this.docs.get(i).path.equals(path)) {
                return i;
            }
        }
        return -1;
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
        ensureSurveyed(dirOf(path));
        this.status = exists ? "Opened " + doc.name() : "New file " + doc.name();
        if (!this.toOpen.isEmpty() || !this.endOn.isEmpty()) {
            openNext();
        }
    }

    /**
     * What is open here, as a window remembers it for the machine: the folder, then every file on a
     * tab, then the one in front, a line each.
     */
    public String describeOpen() {
        final StringBuilder out = new StringBuilder(this.folder);
        for (final Doc doc : this.docs) {
            out.append('\n').append(doc.path);
        }
        final Doc current = current();
        return out.append('\n').append(current == null ? "" : current.path).toString();
    }

    /**
     * Reads what {@link #describeOpen} wrote: the files go on tabs again, one after the other, and the
     * one that was in front ends in front. The folder is the caller's to set, since each editor treats
     * its folder differently.
     */
    public void reopen(final String described) {
        final String[] lines = described.split("\n", -1);
        if (lines.length < 3) {
            return;
        }
        final List<String> paths = new ArrayList<>();
        for (int i = 1; i < lines.length - 1; i++) {
            if (!lines[i].isEmpty()) {
                paths.add(lines[i]);
            }
        }
        openAll(paths, lines[lines.length - 1]);
    }

    /** The first line of what {@link #describeOpen} wrote: the folder it was on. */
    public static String folderOf(final String described) {
        final int end = described.indexOf('\n');
        return end < 0 ? described : described.substring(0, end);
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
        FilesApps.diskChanged();
    }

    @Override
    public void onSaved(final boolean ok, final String message) {
        this.status = message;
        final Doc doc = current();
        if (ok && doc != null) {
            doc.dirty = false;
            // A save can be a file the folder did not have; the list and the survey read the disk again.
            refresh();
            if (this.surveyed.contains(dirOf(doc.path))) {
                surveyFolder(dirOf(doc.path));
            }
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

    /** The language that compiles {@code path}: the one claiming it as a source, not as its output. */
    public static IProgrammingLanguage sourceLanguageOf(final String path) {
        final int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return null;
        }
        final String ext = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        final IProgrammingLanguage language = JsCore.languages().byExtension(ext);
        return language != null && language.sourceExtensions().contains(ext) ? language : null;
    }

    /** The rows of a file, coloured by whichever language owns it. */
    private static List<List<CodeRuns.Run>> colour(final String path, final List<String> lines) {
        final IProgrammingLanguage language = languageOf(path);
        if (language == null) {
            return List.of();
        }
        if (sourceLanguageOf(path) == null) {
            return colourListing(lines);
        }
        final List<CodeRuns.Span> spans = new ArrayList<>();
        for (final IProgrammingLanguage.Token token : language.tokenize(String.join("\n", lines))) {
            spans.add(new CodeRuns.Span(token.line(), token.column(), token.length(), inkOf(token.kind())));
        }
        return CodeRuns.byLine(lines, spans);
    }

    /**
     * The colouring of a listing the compiler wrote: directives, labels, numbers and quoted text.
     *
     * <p>A listing is read, not compiled, so it is coloured by its shape rather than by a language's
     * lexer: a word starting with a dot is a directive, a word ending in a colon is a label, and the
     * rest is left as it is. It is what lets a player follow their program a line at a time.
     */
    private static List<List<CodeRuns.Run>> colourListing(final List<String> lines) {
        final List<CodeRuns.Span> spans = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            final String text = lines.get(i);
            int at = 0;
            while (at < text.length()) {
                final char c = text.charAt(at);
                if (Character.isWhitespace(c)) {
                    at++;
                    continue;
                }
                int end = at + 1;
                if (c == '"') {
                    while (end < text.length() && text.charAt(end) != '"') {
                        end++;
                    }
                    end = Math.min(text.length(), end + 1);
                    spans.add(new CodeRuns.Span(i + 1, at + 1, end - at, CodeRuns.Ink.TEXT));
                } else if (c == ';') {
                    spans.add(new CodeRuns.Span(i + 1, at + 1, text.length() - at, CodeRuns.Ink.COMMENT));
                    end = text.length();
                } else {
                    while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
                        end++;
                    }
                    final String word = text.substring(at, end);
                    final CodeRuns.Ink ink = word.startsWith(".") ? CodeRuns.Ink.KEYWORD
                            : word.endsWith(":") ? CodeRuns.Ink.NAME
                            : word.chars().allMatch(ch -> Character.isDigit(ch) || ch == '-' || ch == '.')
                            ? CodeRuns.Ink.NUMBER : null;
                    if (ink != null) {
                        spans.add(new CodeRuns.Span(i + 1, at + 1, end - at, ink));
                    }
                }
                at = end;
            }
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
        // Only a source is compiled; what the compiler wrote is read, coloured, and left alone.
        final IProgrammingLanguage language = sourceLanguageOf(doc.path);
        if (language == null) {
            doc.complaints = List.of();
            doc.area.setMarks(List.of());
            return;
        }
        /*
         * The file is read together with the others in its folder, the way the build reads them, so a
         * class that extends one written in the file beside it is not told that class does not exist.
         * Only what is said about this file goes in its margin; the others have margins of their own.
         */
        final List<IProgrammingLanguage.SourceText> sources = new ArrayList<>();
        sources.add(new IProgrammingLanguage.SourceText(doc.name(), doc.area.text()));
        sources.addAll(siblingsOf(doc, language));
        final List<IProgrammingLanguage.Complaint> all = language.compile(sources).complaints();
        final List<IProgrammingLanguage.Complaint> mine = new ArrayList<>();
        for (final IProgrammingLanguage.Complaint complaint : all) {
            if (complaint.file().isEmpty() || complaint.file().equals(doc.name())) {
                mine.add(complaint);
            }
        }
        doc.complaints = mine;
        final List<CodeArea.Mark> marks = new ArrayList<>(doc.complaints.size());
        for (final IProgrammingLanguage.Complaint complaint : doc.complaints) {
            marks.add(markOf(doc, complaint));
        }
        doc.area.setMarks(marks);
    }

    /** The text of every file on the disk the surveys have read, by path, for compiling beside an open one. */
    private final java.util.Map<String, String> folderTexts = new java.util.LinkedHashMap<>();
    /** The folders whose files have been asked for, so a folder is read once and not on every keystroke. */
    private final java.util.Set<String> surveyed = new java.util.HashSet<>();

    /**
     * The other sources in {@code doc}'s folder that its language reads, for an editor asking what the
     * program around a file declares; empty when the file is in no language the machine knows.
     */
    public List<IProgrammingLanguage.SourceText> siblingsOf(final Doc doc) {
        final IProgrammingLanguage language = sourceLanguageOf(doc.path);
        return language == null ? List.of() : this.siblingsOf(doc, language);
    }

    /**
     * The other sources in {@code doc}'s folder: an open one as it stands in its editor, any other as the
     * disk had it when the folder was last read.
     */
    private List<IProgrammingLanguage.SourceText> siblingsOf(final Doc doc, final IProgrammingLanguage language) {
        final String dir = dirOf(doc.path);
        final List<IProgrammingLanguage.SourceText> out = new ArrayList<>();
        final java.util.Set<String> seen = new java.util.HashSet<>();
        seen.add(doc.path);
        for (final Doc other : this.docs) {
            if (other != doc && dirOf(other.path).equals(dir) && sourceLanguageOf(other.path) == language
                    && seen.add(other.path)) {
                out.add(new IProgrammingLanguage.SourceText(other.name(), other.area.text()));
            }
        }
        for (final java.util.Map.Entry<String, String> entry : this.folderTexts.entrySet()) {
            if (dirOf(entry.getKey()).equals(dir) && sourceLanguageOf(entry.getKey()) == language
                    && seen.add(entry.getKey())) {
                out.add(new IProgrammingLanguage.SourceText(ProblemReport.nameOf(entry.getKey()), entry.getValue()));
            }
        }
        return out;
    }

    private static String dirOf(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(0, slash) : "";
    }

    /** Reads a folder's sources once, so the file opened from it can be compiled beside them. */
    public void ensureSurveyed(final String dir) {
        if (this.surveyed.add(dir)) {
            surveyFolder(dir);
        }
    }

    /**
     * The text of the source at {@code path} as the player sees it: the open copy when there is one,
     * else what the disk had when its folder was last read, else null when nothing has read it yet.
     */
    public String textOf(final String path) {
        for (final Doc doc : this.docs) {
            if (doc.path.equals(path)) {
                return doc.area.text();
            }
        }
        return this.folderTexts.get(path);
    }

    /** What the compiler says when a class leaves an interface's method out, with the three names in it. */
    private static final java.util.regex.Pattern MISSING_MEMBER =
            java.util.regex.Pattern.compile("'([^']+)' says it is a '([^']+)' but does not have '([^']+)'");

    /**
     * Writes into {@code doc} every method its classes promised an interface and left out, the way
     * a studio's "Implement interface" does; false when there was nothing to write.
     *
     * <p>The compiler already names each missing method; the interface's own declaration, in this
     * file or one of the others open, says what it returns and takes. The stubs go in before the
     * class's closing brace, at the class's own depth.
     */
    public boolean implementInterface(final Doc doc) {
        final java.util.Map<Integer, List<String>> stubsByClassLine = new java.util.LinkedHashMap<>();
        final java.util.Map<Integer, String> indentByClassLine = new java.util.HashMap<>();
        final TextDocument text = doc.area.document();
        for (final IProgrammingLanguage.Complaint complaint : doc.complaints) {
            if (!"C3018".equals(complaint.code())) {
                continue;
            }
            final java.util.regex.Matcher m = MISSING_MEMBER.matcher(complaint.message());
            if (!m.find()) {
                continue;
            }
            final int classLine = complaint.line() - 1;
            if (classLine < 0 || classLine >= text.lineCount()) {
                continue;
            }
            final String classText = text.line(classLine);
            final String indent = " ".repeat(classText.length() - classText.stripLeading().length());
            indentByClassLine.put(classLine, indent);
            stubsByClassLine.computeIfAbsent(classLine, k -> new ArrayList<>())
                    .add(stubFor(m.group(2), m.group(3), indent + " ".repeat(doc.area.tabSize())));
        }
        if (stubsByClassLine.isEmpty()) {
            return false;
        }
        // Later classes first, so writing into one does not move the lines of the ones above it.
        final List<Integer> lines = new ArrayList<>(stubsByClassLine.keySet());
        lines.sort(java.util.Collections.reverseOrder());
        for (final int classLine : lines) {
            final int closing = closingBraceLine(text, classLine);
            if (closing < 0) {
                continue;
            }
            final StringBuilder block = new StringBuilder();
            for (final String stub : stubsByClassLine.get(classLine)) {
                block.append('\n').append(stub);
            }
            block.append('\n');
            final String before = text.line(closing);
            final int brace = before.indexOf('}');
            text.setCursor(closing, Math.max(0, brace));
            text.insertText(block.toString());
        }
        doc.dirty = true;
        recompile(doc);
        return true;
    }

    /** The line holding the brace that closes the class declared on {@code classLine}, or -1. */
    private static int closingBraceLine(final TextDocument text, final int classLine) {
        int depth = 0;
        boolean opened = false;
        for (int i = classLine; i < text.lineCount(); i++) {
            final String line = text.line(i);
            for (int c = 0; c < line.length(); c++) {
                final char ch = line.charAt(c);
                if (ch == '{') {
                    depth++;
                    opened = true;
                } else if (ch == '}') {
                    depth--;
                    if (opened && depth == 0) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * The method a class has to write for {@code member} of {@code face}: the built-in script
     * interface is known here, and any other interface is read from the open files.
     */
    private String stubFor(final String face, final String member, final String indent) {
        final int paren = member.indexOf('(');
        final String name = paren < 0 ? member : member.substring(0, paren);
        String returnType = "void";
        String parameters = "";
        if (!face.equals("IScript")) {
            final dev.jstech.computers.cannon.ast.IDecl.MethodDecl declared = declaredMethod(face, member);
            if (declared != null) {
                returnType = declared.returnType().describe();
                final StringBuilder params = new StringBuilder();
                for (final dev.jstech.computers.cannon.ast.IDecl.Parameter p : declared.parameters()) {
                    params.append(params.isEmpty() ? "" : ", ").append(p.outward() ? "out " : "")
                            .append(p.type().describe()).append(' ').append(p.name());
                }
                parameters = params.toString();
            }
        }
        final String body = switch (returnType) {
            case "void" -> "";
            case "int", "long", "float", "double" -> "return 0;";
            case "bool" -> "return false;";
            case "string" -> "return \"\";";
            default -> "return null;";
        };
        return indent + "public " + returnType + " " + name + "(" + parameters + ") {\n"
                + (body.isEmpty() ? "" : indent + "    " + body + "\n") + indent + "}";
    }

    /** The declaration of {@code face}'s method described as {@code member}, in any open file, or null. */
    private dev.jstech.computers.cannon.ast.IDecl.MethodDecl declaredMethod(final String face, final String member) {
        for (final Doc open : this.docs) {
            final dev.jstech.computers.cannon.CannonFrontEnd.Result parsed = dev.jstech.computers.cannon.CannonFrontEnd
                    .parse(new dev.jstech.computers.cannon.SourceFile(open.name(), open.area.text()));
            for (final dev.jstech.computers.cannon.ast.IDecl.ITypeDecl type : parsed.unit().types()) {
                if (type instanceof dev.jstech.computers.cannon.ast.IDecl.InterfaceDecl declared
                        && declared.name().equals(face)) {
                    for (final dev.jstech.computers.cannon.ast.IDecl.MethodDecl method : declared.methods()) {
                        final StringBuilder described = new StringBuilder(method.name()).append('(');
                        for (int i = 0; i < method.parameters().size(); i++) {
                            described.append(i > 0 ? ", " : "").append(method.parameters().get(i).type().describe());
                        }
                        if (described.append(')').toString().equals(member)) {
                            return method;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * A complaint as a mark: on its row, under the word at its column.
     *
     * <p>The compiler says where it stopped; how much to underline is the word that starts there, so
     * a wrong name is underlined whole rather than as one letter.
     */
    private static CodeArea.Mark markOf(final Doc doc, final IProgrammingLanguage.Complaint complaint) {
        final String text = complaint.code() + ": " + complaint.message();
        final int row = complaint.line() - 1;
        if (complaint.column() <= 0 || row < 0 || row >= doc.area.document().lineCount()) {
            return new CodeArea.Mark(complaint.line(), true, text);
        }
        final String line = doc.area.document().line(row);
        final int from = Math.min(complaint.column() - 1, line.length());
        int to = from;
        while (to < line.length() && (Character.isLetterOrDigit(line.charAt(to)) || line.charAt(to) == '_')) {
            to++;
        }
        return new CodeArea.Mark(complaint.line(), complaint.column(), Math.max(1, to - from), true, text);
    }

    /* What is wrong with the whole folder, not just with what is open */

    /** What the compiler said about every program on the disk, the last time they were all read. */
    private final java.util.Map<String, List<IProgrammingLanguage.Complaint>> folderComplaints =
            new java.util.LinkedHashMap<>();

    /** Whether the next survey's outcome is announced in the status, as a survey somebody asked for is. */
    private boolean announceSurvey;

    /** Asks the machine for every program at once, to compile the lot and say how many are broken. */
    public void surveyFolder() {
        this.announceSurvey = true;
        surveyFolder(this.folder);
    }

    /** Asks the machine for every program in {@code dir} at once. */
    public void surveyFolder(final String dir) {
        this.surveyed.add(dir);
        CodeFileReplies.expectFolder(this, dir);
        PacketDistributor.sendToServer(
                new dev.jstech.computers.operation.payload.RequestFolderContentPayload(
                        this.host, dir, ".can"));
    }

    /** Saves the file being edited under another name, which then becomes the one being edited. */
    public void saveAs(final String path) {
        final Doc doc = current();
        if (doc == null || path == null || path.isBlank()) {
            return;
        }
        final Doc copy = new Doc(path);
        copy.area.setText(doc.area.text()).setPalette(this.palette).setColouring(lines -> colour(path, lines));
        copy.area.setTabSize(doc.area.tabSize());
        this.docs.add(copy);
        this.current = this.docs.size() - 1;
        recompile(copy);
        save();
    }

    /** Puts every open file that changed back on the disk, one after the other. */
    public void saveAll() {
        final int was = this.current;
        for (int i = 0; i < this.docs.size(); i++) {
            if (this.docs.get(i).dirty) {
                this.current = i;
                save();
            }
        }
        this.current = was;
    }

    /** Opens a new, empty file under {@code path} without asking the disk, as New File does. */
    public void newFile(final String path) {
        final Doc doc = new Doc(path);
        doc.area.setText("").setPalette(this.palette).setColouring(lines -> colour(path, lines));
        this.docs.add(doc);
        this.current = this.docs.size() - 1;
        doc.dirty = true;
        recompile(doc);
        this.status = "New file " + doc.name();
    }

    /** Whether any open file has changes not yet on the disk. */
    public boolean anyDirty() {
        for (final Doc doc : this.docs) {
            if (doc.dirty) {
                return true;
            }
        }
        return false;
    }

    /** Closes every open file. */
    public void closeAll() {
        this.docs.clear();
        this.current = -1;
    }

    @Override
    public void onFolder(final dev.jstech.computers.operation.payload.FolderContentPayload folder) {
        this.folderComplaints.clear();
        this.folderTexts.keySet().removeIf(path -> dirOf(path).equals(folder.dir()));
        for (final var file : folder.files()) {
            this.folderTexts.put(file.path(), file.text());
        }
        // What is open is read again beside what just arrived, so its margin agrees with the disk.
        for (final Doc doc : this.docs) {
            if (dirOf(doc.path).equals(folder.dir())) {
                recompile(doc);
            }
        }
        for (final var file : folder.files()) {
            final IProgrammingLanguage language = languageOf(file.path());
            if (language == null) {
                continue;
            }
            /*
             * The text on the disk, not what is open: a file the player is halfway through editing is
             * reported as the machine would find it, and the open copy has its own margin for that.
             */
            this.folderComplaints.put(file.path(), language.compile(
                    List.of(new IProgrammingLanguage.SourceText(
                            ProblemReport.nameOf(file.path()), file.text()))).complaints());
        }
        if (this.announceSurvey) {
            this.announceSurvey = false;
            this.status = ProblemReport.brokenFiles(this.folderComplaints) + " of "
                    + folder.files().size() + " program(s) with problems";
        }
    }

    /** Every complaint from every program on the disk, worst file first. */
    public List<ProblemReport.Row> folderProblems() {
        return ProblemReport.of(this.folderComplaints);
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
