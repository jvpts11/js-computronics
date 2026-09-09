/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.operation.payload.DeleteFilePayload;
import dev.jstech.computers.operation.payload.DiskFilesPayload;
import dev.jstech.computers.operation.payload.FolderContentPayload;
import dev.jstech.computers.operation.payload.RequestFileContentPayload;
import dev.jstech.computers.operation.payload.RequestFolderContentPayload;
import dev.jstech.computers.operation.payload.SaveFilePayload;
import dev.jstech.computers.os.edit.InkPalette;
import dev.jstech.computers.os.edit.ProblemReport;
import dev.jstech.computers.os.edit.project.ProjectFile;
import dev.jstech.computers.os.edit.project.ProjectTemplate;
import dev.jstech.computers.os.edit.project.SolutionFile;
import dev.jstech.core.JsCore;
import dev.jstech.core.client.gui.component.AmountStepper;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.Checkbox;
import dev.jstech.core.client.gui.component.ContextMenu;
import dev.jstech.core.client.gui.component.Draw;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.MenuBar;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.Popup;
import dev.jstech.core.client.gui.component.TabStrip;
import dev.jstech.core.client.gui.component.TextField;
import dev.jstech.core.client.gui.component.UiContext;
import dev.jstech.core.language.IProgrammingLanguage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Virtual Studio: the whole workshop in one window, working in solutions and projects.
 *
 * <p>It opens on a Start Window. A solution is a folder with a solution file and a folder per project
 * under it; a project says what it is made of and what it builds. Create a new project picks a
 * template, filtered by language, platform and kind, names it, and opens the solution around it.
 * Build compiles every source of a project, with the libraries it references, to the listing its
 * project file names; Start builds the startup project and runs it at the terminal.
 *
 * <p>What it has that nothing else does is the price: the list of what can follow a name says what
 * each call will cost the program, before the line is written.
 */
public final class VirtualStudioApp implements IDesktopApp, CodeFileReplies.IReader {

    private static final int TOOLBAR_H = 13;
    private static final int SIDE_W = 108;
    private static final int CAPTION_H = 9;
    private static final int TAB_H = 10;
    private static final int STATUS_H = 9;
    private static final int ROW_H = 9;
    private static final int DOCK_H = 52;
    /** A template row: its title, what it is, and its tags, one under the other. */
    private static final int TEMPLATE_ROW_H = 28;
    private static final int MIN_CODE_H = 36;
    private static final String KEY = "Virtual Studio";

    private static final List<String> DOCK_TABS = List.of("ERROR LIST", "OUTPUT");
    private static final int DOCK_ERRORS = 0;
    private static final int DOCK_OUTPUT = 1;

    /** Where solutions are made unless the player says otherwise. */
    private static final String LOCATION = CodeWorkspace.HOME;

    /** The solutions opened on each machine lately, for the Start Window while the game runs. */
    private static final Map<BlockPos, Deque<String>> RECENT = new LinkedHashMap<>();
    private static final int RECENT_MAX = 5;

    /** What the window is showing. */
    private enum Page { START, SOLUTION }

    /** What a row of the Solution Explorer stands for. */
    private enum NodeKind { SOLUTION, PROJECT, DEPENDENCIES, DEPENDENCY, PROPERTIES, SOURCE, OUTPUT, FOLDER_FILE }

    /** One row of the Solution Explorer. */
    private record Node(int depth, String label, NodeKind kind, String project, String path) {
    }

    private final BlockPos host;
    private final CodeWorkspace workspace;
    private OsSkin skin = OsSkin.fallback();
    private Page page = Page.START;
    private int tabSize = 4;
    private boolean completionsOn = true;

    /* The solution */
    private SolutionFile solution;
    private String solutionDir = "";
    private final Map<String, ProjectFile> projects = new LinkedHashMap<>();
    private final Set<String> collapsed = new LinkedHashSet<>();
    private final Set<String> dependenciesOpen = new LinkedHashSet<>();
    /** Project files still to be read, in order, since one file is waited for at a time. */
    private final Deque<String> loading = new ArrayDeque<>();

    /* The build */
    private final Deque<String> buildQueue = new ArrayDeque<>();
    private String building = "";
    private boolean runAfterBuild;
    private final Deque<String> foldersToRead = new ArrayDeque<>();
    private final Map<String, String> sourceTexts = new LinkedHashMap<>();
    private final Map<String, List<IProgrammingLanguage.Complaint>> buildErrors = new LinkedHashMap<>();
    private final List<String> output = new ArrayList<>();

    /* The window */
    private final Panel root = new Panel();
    private final MenuBar menuBar = new MenuBar(96, 10);
    private final ListView<Node> explorer;
    private final TabStrip tabs;
    private final TabStrip dockTabs;
    private final ListView<ProblemReport.Row> errors;
    private final ListView<String> outputList;
    private final Button start;
    private final CodeCompletions completions = new CodeCompletions().withCosts(true);
    private final CommandPalette palette = new CommandPalette();
    private final FolderPicker picker;
    private final List<Link> links = new ArrayList<>();

    private record Link(String title, int x, int y, int width, int height, Runnable action) {
    }

    /* The New Project wizard */
    private final Popup templates = new Popup("Create a new project", 220, 150).setLayouter(this::layoutTemplates);
    private final TextField templateSearch = new TextField(32);
    private final ListView<ProjectTemplate> templateList;
    private final Button templateKind;
    private final Button templateLanguage;
    private final Button templateNext;
    private String kindFilter = "";
    private String languageFilter = "";
    private final Popup configure = new Popup("Configure your new project", 230, 92).setLayouter(this::layoutConfigure);
    private final TextField projectName = new TextField(32);
    private final TextField solutionName = new TextField(32);
    private final Label configureNote;
    private final Button create;
    private boolean sameFolder = true;
    private ProjectTemplate chosen = ProjectTemplate.CONSOLE_APP;
    private boolean addingToSolution;

    /* The small windows a command opens for one thing */
    private final Popup ask = new Popup(() -> this.askTitle, 150, 44).setLayouter(this::layoutAsk);
    private final TextField askField = new TextField(64);
    private final Button askOk;
    private String askTitle = "";
    private java.util.function.Consumer<String> askAction = value -> { };
    private final Popup properties = new Popup("Project Properties", 170, 70).setLayouter(this::layoutProperties);
    private final List<Label> propertyLines = new ArrayList<>();
    private final Button propertiesClose;
    private String propertiesOf = "";
    private final Popup options = new Popup("Options", 170, 60).setLayouter(this::layoutOptions);
    private final AmountStepper tabStepper = new AmountStepper();
    private final Checkbox completionsBox;
    private final Button optionsClose;

    public VirtualStudioApp(final BlockPos host) {
        this.host = host;
        this.workspace = new CodeWorkspace(host);
        this.picker = new FolderPicker(host, "Open");
        this.explorer = this.root.add(new ListView<>(this::nodes, ROW_H, this::drawNode)).setOnClick(this::onNode);
        this.tabs = this.root.add(new TabStrip(this.workspace::tabLabels).fitToLabels(10).setUnderline(false));
        this.tabs.setOnSelect(this.workspace::setCurrent);
        this.dockTabs = this.root.add(new TabStrip(DOCK_TABS).fitToLabels(12).setUnderline(true));
        this.errors = this.root.add(new ListView<>(this::errorRows, ROW_H, this::drawErrorRow)).setOnClick(this::onError);
        this.outputList = this.root.add(new ListView<>(() -> this.output, ROW_H, this::drawOutputRow));
        this.start = this.root.add(new Button("Start", this::startProgram).setPrimary(true));
        this.root.add(this.menuBar);
        this.menuBar.add("File", this::fileMenu).add("Edit", this::editMenu).add("View", this::viewMenu)
                .add("Project", this::projectMenu).add("Build", this::buildMenu).add("Debug", this::debugMenu)
                .add("Tools", this::toolsMenu).add("Help", this::helpMenu);

        // The wizard's first page: the templates, with what narrows them.
        this.templates.add(this.templateSearch.setPlaceholder("Search for templates"));
        this.templateLanguage = this.templates.add(new Button("All languages", this::cycleLanguage));
        this.templateKind = this.templates.add(new Button("All types", this::cycleKind));
        this.templateList = this.templates.add(new ListView<>(this::matchingTemplates, TEMPLATE_ROW_H, this::drawTemplate)
                .setOnClick((index, button, mx, my) -> pickTemplate(index)));
        this.templateNext = this.templates.add(new Button("Next", this::toConfigure).setPrimary(true));
        this.templates.add(new Button("Cancel", this.templates::close));
        // The second page: the names.
        this.configure.add(new Label("Project name", Label.Tone.DIM));
        this.configure.add(this.projectName);
        this.configure.add(new Label("Solution name", Label.Tone.DIM));
        this.configure.add(this.solutionName);
        this.configure.add(new Checkbox(() -> "Put solution and project together",
                () -> this.sameFolder, () -> this.sameFolder = !this.sameFolder));
        this.configureNote = this.configure.add(new Label(this::configureNoteText, Label.Tone.DIM));
        this.create = this.configure.add(new Button("Create", this::createProject).setPrimary(true));
        this.configure.add(new Button("Back", () -> {
            this.configure.close();
            this.templates.open();
        }));
        this.projectName.setOnEdit(() -> {
            if (!this.addingToSolution) {
                this.solutionName.set(this.projectName.edit());
            }
        });

        this.ask.add(this.askField);
        this.askOk = this.ask.add(new Button("OK", () -> {
            this.ask.close();
            this.askAction.accept(this.askField.edit().trim());
        }).setPrimary(true));
        for (int i = 0; i < 5; i++) {
            final int line = i;
            this.propertyLines.add(this.properties.add(new Label(() -> propertyText(line))));
        }
        this.propertiesClose = this.properties.add(new Button("Close", this.properties::close).setPrimary(true));
        this.options.add(new Label("Tab size", Label.Tone.DIM));
        this.options.add(this.tabStepper.setRange(2, 8).setAmount(4).setOnChange(v -> setTabSize((int) v)));
        this.completionsBox = this.options.add(new Checkbox(() -> "Suggest what can follow a name",
                () -> this.completionsOn, () -> this.completionsOn = !this.completionsOn));
        this.optionsClose = this.options.add(new Button("Close", this.options::close).setPrimary(true));
    }

    /* What a test, or "Open with", asks of it */

    /** The file being edited, or empty when none is. */
    public String openFile() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        return doc == null ? "" : doc.path();
    }

    /** The text of the file being edited. */
    public String text() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        return doc == null ? "" : doc.area().text();
    }

    /** The name of the open solution, or empty on the Start Window. */
    public String solutionName() {
        return this.solution == null ? "" : this.solution.name();
    }

    /** The names of the projects in the open solution. */
    public List<String> projectNames() {
        return new ArrayList<>(this.projects.keySet());
    }

    /** What the Output pane says, one line after another. */
    public List<String> outputLines() {
        return List.copyOf(this.output);
    }

    /** Whether the window is on its Start Window. */
    public boolean onStartWindow() {
        return this.page == Page.START;
    }

    /** Opens a file, as picking this program with "Open with" does; a solution file opens its solution. */
    @Override
    public void openFile(final String path) {
        if (path.endsWith("." + SolutionFile.EXTENSION)) {
            final int slash = path.lastIndexOf('/');
            openSolutionFolder(slash > 0 ? path.substring(0, slash) : "");
            return;
        }
        if (path.endsWith("." + ProjectFile.EXTENSION)) {
            final int slash = path.lastIndexOf('/');
            final String projectDir = slash > 0 ? path.substring(0, slash) : "";
            final int up = projectDir.lastIndexOf('/');
            openSolutionFolder(up > 0 ? projectDir.substring(0, up) : "");
            return;
        }
        if (this.page == Page.START) {
            final int slash = path.lastIndexOf('/');
            openFolder(slash > 0 ? path.substring(0, slash) : "");
        }
        this.workspace.open(path);
    }

    /* Solutions */

    private void remember(final String dir) {
        final Deque<String> recent = RECENT.computeIfAbsent(this.host, h -> new ArrayDeque<>());
        recent.remove(dir);
        recent.addFirst(dir);
        while (recent.size() > RECENT_MAX) {
            recent.removeLast();
        }
    }

    private List<String> recent() {
        return new ArrayList<>(RECENT.getOrDefault(this.host, new ArrayDeque<>()));
    }

    private static String shortName(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
    }

    private static String join(final String dir, final String name) {
        return dir.isEmpty() ? name : dir + "/" + name;
    }

    /** Opens the solution kept in {@code dir}: its file, then each project's, then the folders. */
    public void openSolutionFolder(final String dir) {
        this.solutionDir = dir;
        this.solution = null;
        this.projects.clear();
        this.loading.clear();
        final String file = join(dir, SolutionFile.fileName(shortName(dir)));
        CodeFileReplies.expectContent(this, file);
        PacketDistributor.sendToServer(new RequestFileContentPayload(this.host, file));
    }

    /** Opens a plain folder, with no solution around it: the files are the tree. */
    public void openFolder(final String dir) {
        this.solution = null;
        this.solutionDir = dir;
        this.projects.clear();
        this.workspace.setFolder(dir);
        this.page = Page.SOLUTION;
        remember(dir);
    }

    @Override
    public void onContent(final String path, final String content, final boolean exists) {
        if (path.endsWith("." + SolutionFile.EXTENSION)) {
            if (!exists) {
                this.workspace.say("No solution in " + shortName(this.solutionDir) + "; opened as a folder");
                openFolder(this.solutionDir);
                return;
            }
            this.solution = SolutionFile.read(content);
            for (final String project : this.solution.projects()) {
                this.loading.add(join(this.solutionDir, project));
            }
            readNextProject();
            return;
        }
        if (path.endsWith("." + ProjectFile.EXTENSION)) {
            if (exists) {
                final ProjectFile project = ProjectFile.read(content);
                this.projects.put(project.name(), project);
            }
            readNextProject();
        }
    }

    private void readNextProject() {
        final String next = this.loading.poll();
        if (next != null) {
            CodeFileReplies.expectContent(this, next);
            PacketDistributor.sendToServer(new RequestFileContentPayload(this.host, next));
            return;
        }
        // Everything is read: the tree can be built, and the folders are asked for their outputs.
        this.workspace.setFolder(this.solutionDir);
        for (final String name : this.projects.keySet()) {
            this.workspace.toggleFolder(join(join(this.solutionDir, name), "build"));
        }
        this.page = Page.SOLUTION;
        remember(this.solutionDir);
        this.workspace.say("Opened " + this.solution.name());
        if (!this.openWhenLoaded.isEmpty()) {
            final String path = this.openWhenLoaded;
            this.openWhenLoaded = "";
            this.workspace.open(path);
        }
    }

    private String projectDir(final String name) {
        return join(this.solutionDir, name);
    }

    private void saveSolution() {
        if (this.solution == null) {
            return;
        }
        PacketDistributor.sendToServer(new SaveFilePayload(this.host,
                join(this.solutionDir, SolutionFile.fileName(this.solution.name())), this.solution.write()));
        FilesApps.diskChanged();
    }

    private void saveProject(final ProjectFile project) {
        this.projects.put(project.name(), project);
        PacketDistributor.sendToServer(new SaveFilePayload(this.host,
                join(projectDir(project.name()), ProjectFile.fileName(project.name())), project.write()));
        FilesApps.diskChanged();
    }

    private void closeSolution() {
        this.solution = null;
        this.projects.clear();
        this.solutionDir = "";
        this.workspace.closeAll();
        this.buildErrors.clear();
        this.output.clear();
        this.page = Page.START;
    }

    /* The Solution Explorer */

    private List<Node> nodes() {
        final List<Node> out = new ArrayList<>();
        if (this.solution == null) {
            // A plain folder: what is in it, the way an explorer shows it.
            out.add(new Node(0, shortName(this.solutionDir).isEmpty() ? "C:\\" : shortName(this.solutionDir),
                    NodeKind.SOLUTION, "", this.solutionDir));
            for (final CodeWorkspace.TreeRow row : this.workspace.tree()) {
                final String mark = row.file().directory()
                        ? (this.workspace.isExpanded(row.file().path()) ? "v " : "> ") : "";
                out.add(new Node(row.depth() + 1, mark + shortName(row.file().path()), NodeKind.FOLDER_FILE, "",
                        row.file().path()));
            }
            return out;
        }
        out.add(new Node(0, "Solution '" + this.solution.name() + "' (" + this.projects.size() + ")",
                NodeKind.SOLUTION, "", this.solutionDir));
        for (final ProjectFile project : this.projects.values()) {
            final boolean open = !this.collapsed.contains(project.name());
            final boolean startup = project.name().equals(startupName());
            out.add(new Node(1, (open ? "v " : "> ") + project.name() + (startup ? " *" : ""), NodeKind.PROJECT,
                    project.name(), projectDir(project.name())));
            if (!open) {
                continue;
            }
            final boolean deps = this.dependenciesOpen.contains(project.name());
            out.add(new Node(2, (deps ? "v " : "> ") + "Dependencies", NodeKind.DEPENDENCIES, project.name(), ""));
            if (deps) {
                out.add(new Node(3, languageName(project.language()), NodeKind.DEPENDENCY, project.name(), ""));
                for (final String reference : project.references()) {
                    out.add(new Node(3, reference, NodeKind.DEPENDENCY, project.name(), ""));
                }
            }
            out.add(new Node(2, "Properties", NodeKind.PROPERTIES, project.name(), ""));
            for (final String source : project.sources()) {
                out.add(new Node(2, source, NodeKind.SOURCE, project.name(), join(projectDir(project.name()), source)));
            }
            final String buildDir = join(projectDir(project.name()), "build");
            for (final CodeWorkspace.TreeRow row : this.workspace.tree()) {
                if (!row.file().directory() && row.file().path().startsWith(buildDir + "/")) {
                    out.add(new Node(2, "build/" + shortName(row.file().path()), NodeKind.OUTPUT, project.name(),
                            row.file().path()));
                }
            }
        }
        return out;
    }

    private String languageName(final String id) {
        final IProgrammingLanguage language = id.contains(":")
                ? JsCore.languages().get(net.minecraft.resources.ResourceLocation.tryParse(id)) : null;
        return language == null ? id : language.displayName() + " 1.0";
    }

    private String startupName() {
        if (this.solution == null) {
            return "";
        }
        if (!this.solution.startup().isEmpty()) {
            return this.solution.startup();
        }
        for (final ProjectFile project : this.projects.values()) {
            if (project.buildsAListing()) {
                return project.name();
            }
        }
        return "";
    }

    private void drawNode(final GuiGraphics g, final UiContext ctx, final Node node, final int index,
                          final int x, final int y, final int width, final int height,
                          final boolean hovered, final boolean selected) {
        ctx.skin().listRow(g, x, y, width, height, hovered, selected);
        final int color = node.kind() == NodeKind.DEPENDENCY || node.kind() == NodeKind.OUTPUT
                ? ctx.skin().dim() : ctx.skin().listRowText(selected);
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(node.label(), width - 2 - node.depth() * 5),
                x + 2 + node.depth() * 5, y + 1, color, false);
    }

    private void onNode(final int index, final int button, final double mx, final double my) {
        final List<Node> all = nodes();
        if (index < 0 || index >= all.size()) {
            return;
        }
        final Node node = all.get(index);
        switch (node.kind()) {
            case PROJECT -> {
                if (!this.collapsed.remove(node.project())) {
                    this.collapsed.add(node.project());
                }
            }
            case DEPENDENCIES -> {
                if (!this.dependenciesOpen.remove(node.project())) {
                    this.dependenciesOpen.add(node.project());
                }
            }
            case PROPERTIES -> showProperties(node.project());
            case SOURCE, OUTPUT -> this.workspace.open(node.path());
            case FOLDER_FILE -> {
                final DiskFilesPayload.WireFile file = fileAt(node.path());
                if (file != null && file.directory()) {
                    this.workspace.toggleFolder(node.path());
                } else {
                    this.workspace.open(node.path());
                }
            }
            default -> { }
        }
    }

    private DiskFilesPayload.WireFile fileAt(final String path) {
        for (final CodeWorkspace.TreeRow row : this.workspace.tree()) {
            if (row.file().path().equals(path)) {
                return row.file();
            }
        }
        return null;
    }

    /* Errors and output */

    private List<ProblemReport.Row> errorRows() {
        if (!this.buildErrors.isEmpty()) {
            return ProblemReport.of(this.buildErrors);
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null) {
            return List.of();
        }
        final Map<String, List<IProgrammingLanguage.Complaint>> live = new LinkedHashMap<>();
        live.put(doc.path(), doc.complaints());
        return ProblemReport.of(live);
    }

    private void drawErrorRow(final GuiGraphics g, final UiContext ctx, final ProblemReport.Row row,
                              final int index, final int x, final int y, final int width, final int height,
                              final boolean hovered, final boolean selected) {
        ctx.skin().listRow(g, x, y, width, height, hovered, selected);
        final String where = row.name() + " " + row.complaint().line();
        final int whereW = Math.min(width / 3, ctx.font().width(where));
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(where, whereW), x + 2, y + 1, ctx.skin().dim(), false);
        final int textX = x + 2 + whereW + 4;
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(row.complaint().code() + " "
                + row.complaint().message(), width - (textX - x) - 2), textX, y + 1, 0xFFC0392B, false);
    }

    private void onError(final int index, final int button, final double mx, final double my) {
        final List<ProblemReport.Row> rows = errorRows();
        if (index < 0 || index >= rows.size()) {
            return;
        }
        final ProblemReport.Row row = rows.get(index);
        this.workspace.open(row.path());
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null && doc.path().equals(row.path())) {
            doc.area().document().setCursor(row.complaint().line() - 1, Math.max(0, row.complaint().column() - 1));
        }
    }

    private void drawOutputRow(final GuiGraphics g, final UiContext ctx, final String line, final int index,
                               final int x, final int y, final int width, final int height,
                               final boolean hovered, final boolean selected) {
        ctx.skin().listRow(g, x, y, width, height, hovered, selected);
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(line, width - 4), x + 2, y + 1,
                line.startsWith("Build succeeded") ? 0xFF1E8449 : line.contains("error") ? 0xFFC0392B
                        : ctx.skin().text(), false);
    }

    /* Building */

    /** Builds every project that makes a listing, in the order the solution lists them. */
    public void buildSolution() {
        if (this.solution == null) {
            buildOpenFile();
            return;
        }
        this.output.clear();
        this.buildErrors.clear();
        this.output.add("Build started: " + this.solution.name());
        this.buildQueue.clear();
        for (final ProjectFile project : this.projects.values()) {
            if (project.buildsAListing()) {
                this.buildQueue.add(project.name());
            }
        }
        this.dockTabs.setSelected(DOCK_OUTPUT);
        buildNext();
    }

    private void buildProject(final String name) {
        this.output.clear();
        this.buildErrors.clear();
        this.output.add("Build started: " + name);
        this.buildQueue.clear();
        this.buildQueue.add(name);
        this.dockTabs.setSelected(DOCK_OUTPUT);
        buildNext();
    }

    /** With no solution, Build is the open file on its own, as the light editor does it. */
    private void buildOpenFile() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        this.output.clear();
        this.buildErrors.clear();
        if (doc == null) {
            this.output.add("Nothing to build");
            return;
        }
        final IProgrammingLanguage language = CodeWorkspace.languageOf(doc.path());
        if (language == null) {
            this.output.add(doc.name() + ": no language claims this file");
            return;
        }
        final IProgrammingLanguage.CompileResult result = language.compile(
                List.of(new IProgrammingLanguage.SourceText(doc.name(), doc.area().text())));
        finishBuild(doc.name(), doc.path().replaceAll("\\.[^.]+$", "") + ".asm", result, Map.of(doc.path(), doc.name()));
    }

    private void buildNext() {
        final String name = this.buildQueue.poll();
        if (name == null) {
            if (this.runAfterBuild) {
                this.runAfterBuild = false;
                runStartup();
            }
            return;
        }
        this.building = name;
        this.sourceTexts.clear();
        this.foldersToRead.clear();
        final ProjectFile project = this.projects.get(name);
        if (project == null) {
            buildNext();
            return;
        }
        // The project's own folder and every library it references, which may reference more.
        final Deque<String> pending = new ArrayDeque<>(List.of(name));
        final Set<String> seen = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            final String each = pending.poll();
            if (!seen.add(each) || !this.projects.containsKey(each)) {
                continue;
            }
            this.foldersToRead.add(projectDir(each));
            pending.addAll(this.projects.get(each).references());
        }
        readNextFolder();
    }

    private void readNextFolder() {
        final String dir = this.foldersToRead.poll();
        if (dir != null) {
            CodeFileReplies.expectFolder(this, dir);
            PacketDistributor.sendToServer(new RequestFolderContentPayload(this.host, dir, ".can"));
            return;
        }
        compileBuilding();
    }

    @Override
    public void onFolder(final FolderContentPayload folder) {
        for (final FolderContentPayload.WireFile file : folder.files()) {
            this.sourceTexts.put(file.path(), file.text());
        }
        readNextFolder();
    }

    /** Compiles what was read for the project being built, and writes the listing when it built. */
    private void compileBuilding() {
        final ProjectFile project = this.projects.get(this.building);
        if (project == null) {
            buildNext();
            return;
        }
        final IProgrammingLanguage language = JsCore.languages().get(
                net.minecraft.resources.ResourceLocation.tryParse(project.language()));
        if (language == null) {
            this.output.add(project.name() + ": no language called " + project.language());
            buildNext();
            return;
        }
        /*
         * What is open and changed counts over what the disk holds, so a build sees what the player
         * sees; a library's sources come in first, so its types are known when the project's are read.
         */
        final List<IProgrammingLanguage.SourceText> sources = new ArrayList<>();
        final Map<String, String> names = new LinkedHashMap<>();
        final Deque<String> order = new ArrayDeque<>();
        collectOrder(project.name(), order, new LinkedHashSet<>());
        for (final String each : order) {
            final ProjectFile part = this.projects.get(each);
            for (final String source : part.sources()) {
                final String path = join(projectDir(each), source);
                final String text = openText(path, this.sourceTexts.get(path));
                if (text != null) {
                    names.put(path, each + "/" + source);
                    sources.add(new IProgrammingLanguage.SourceText(each + "/" + source, text));
                }
            }
        }
        if (sources.isEmpty()) {
            this.output.add(project.name() + ": no sources to build");
            buildNext();
            return;
        }
        final IProgrammingLanguage.CompileResult result = language.compile(sources);
        finishBuild(project.name(), join(projectDir(project.name()), project.entry()), result, names);
        buildNext();
    }

    /** The order to read a project's parts in: its libraries first, itself last. */
    private void collectOrder(final String name, final Deque<String> order, final Set<String> seen) {
        if (!seen.add(name) || !this.projects.containsKey(name)) {
            return;
        }
        for (final String reference : this.projects.get(name).references()) {
            collectOrder(reference, order, seen);
        }
        order.add(name);
    }

    /** The text of a source as the player sees it: the open buffer when there is one, else the disk's. */
    private String openText(final String path, final String fromDisk) {
        for (final CodeWorkspace.Doc doc : this.workspace.docs()) {
            if (doc.path().equals(path)) {
                return doc.area().text();
            }
        }
        return fromDisk;
    }

    private void finishBuild(final String what, final String outputPath, final IProgrammingLanguage.CompileResult result,
                             final Map<String, String> names) {
        if (result.ok()) {
            final int lines = result.binary().split("\n", -1).length;
            this.output.add(what + " -> " + outputPath + "  (" + lines + " lines)");
            this.output.add("Build succeeded: " + what);
            PacketDistributor.sendToServer(new SaveFilePayload(this.host, outputPath, result.binary()));
            FilesApps.diskChanged();
            this.workspace.say("Build succeeded");
            return;
        }
        for (final IProgrammingLanguage.Complaint complaint : result.complaints()) {
            this.output.add(what + ": " + complaint.format());
            // The complaint names the source as the compiler saw it; the row needs the path on the disk.
            String path = complaint.file();
            for (final Map.Entry<String, String> entry : names.entrySet()) {
                if (entry.getValue().equals(complaint.file())) {
                    path = entry.getKey();
                }
            }
            this.buildErrors.computeIfAbsent(path, p -> new ArrayList<>()).add(complaint);
        }
        this.output.add("Build failed: " + what + ", " + result.complaints().size() + " error(s)");
        this.workspace.say(result.complaints().size() + " error(s)");
        this.dockTabs.setSelected(DOCK_ERRORS);
        this.buildQueue.clear();
        this.runAfterBuild = false;
    }

    /** Start: builds the startup project and, when it built, runs it at the terminal. */
    public void startProgram() {
        if (this.solution == null) {
            buildOpenFile();
            final CodeWorkspace.Doc doc = this.workspace.current();
            if (doc != null && this.buildErrors.isEmpty()) {
                DesktopScreen.requestRunAtTerminal(doc.path().replaceAll("\\.[^.]+$", "") + ".asm");
            }
            return;
        }
        final String startup = startupName();
        if (startup.isEmpty()) {
            this.output.add("No project to start: none builds a listing");
            return;
        }
        this.runAfterBuild = true;
        buildProject(startup);
    }

    private void runStartup() {
        final ProjectFile project = this.projects.get(startupName());
        if (project != null && project.buildsAListing() && this.buildErrors.isEmpty()) {
            DesktopScreen.requestRunAtTerminal(join(projectDir(project.name()), project.entry()));
        }
    }

    /** Clean: the listings every project built are deleted, and the tree stops showing them. */
    private void cleanSolution() {
        this.output.clear();
        for (final ProjectFile project : this.projects.values()) {
            if (project.buildsAListing()) {
                PacketDistributor.sendToServer(new DeleteFilePayload(this.host,
                        join(projectDir(project.name()), project.entry())));
                this.output.add("Deleted " + project.entry() + " of " + project.name());
            }
        }
        FilesApps.diskChanged();
        this.workspace.refresh();
    }

    /** Package: the prompt's canpack does it, in the project's folder, at the terminal. */
    private void packageStartup() {
        final ProjectFile project = this.projects.get(startupName());
        if (project == null) {
            this.output.add("No project to package");
            return;
        }
        DesktopScreen.requestTypeAtTerminal(List.of(
                "cd \\" + projectDir(project.name()).replace('/', '\\'),
                "canpack init " + project.name(),
                "canpack build"));
    }

    /* The New Project wizard */

    private void newProject(final boolean intoSolution) {
        this.addingToSolution = intoSolution && this.solution != null;
        this.templateSearch.set("");
        this.kindFilter = "";
        this.languageFilter = "";
        this.templateList.setSelected(0);
        this.chosen = ProjectTemplate.CONSOLE_APP;
        this.templates.open();
    }

    /** The languages the machine knows, which is what the language filter cycles through. */
    private List<String> languageNames() {
        final List<String> out = new ArrayList<>();
        for (final IProgrammingLanguage language : JsCore.languages().all()) {
            out.add(language.displayName());
        }
        return out;
    }

    private void cycleLanguage() {
        final List<String> names = languageNames();
        if (this.languageFilter.isEmpty()) {
            this.languageFilter = names.isEmpty() ? "" : names.get(0);
        } else {
            final int at = names.indexOf(this.languageFilter);
            this.languageFilter = at + 1 < names.size() ? names.get(at + 1) : "";
        }
    }

    private void cycleKind() {
        this.kindFilter = switch (this.kindFilter) {
            case "" -> "Console";
            case "Console" -> "Script";
            case "Script" -> "Library";
            default -> "";
        };
    }

    /** The templates that fit what was typed and the two filters. */
    private List<ProjectTemplate> matchingTemplates() {
        final List<ProjectTemplate> out = new ArrayList<>();
        final String typed = this.templateSearch.edit().toLowerCase(java.util.Locale.ROOT).trim();
        for (final ProjectTemplate template : ProjectTemplate.values()) {
            if (!template.isKind(this.kindFilter)) {
                continue;
            }
            if (!this.languageFilter.isEmpty() && !template.tags().contains(this.languageFilter)) {
                continue;
            }
            if (!typed.isEmpty() && !template.title().toLowerCase(java.util.Locale.ROOT).contains(typed)
                    && !template.description().toLowerCase(java.util.Locale.ROOT).contains(typed)) {
                continue;
            }
            out.add(template);
        }
        return out;
    }

    private void drawTemplate(final GuiGraphics g, final UiContext ctx, final ProjectTemplate template,
                              final int index, final int x, final int y, final int width, final int height,
                              final boolean hovered, final boolean selected) {
        ctx.skin().listRow(g, x, y, width, height, hovered, selected);
        g.drawString(ctx.font(), template.title(), x + 3, y + 1, ctx.skin().listRowText(selected), false);
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(template.description(), width - 6), x + 3, y + 9,
                ctx.skin().dim(), false);
        final String tags = String.join("  ", template.tags());
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(tags, width - 6), x + 3, y + 17,
                ctx.skin().dim(), false);
    }

    private void pickTemplate(final int index) {
        final List<ProjectTemplate> shown = matchingTemplates();
        if (index >= 0 && index < shown.size()) {
            this.chosen = shown.get(index);
            this.templateList.setSelected(index);
        }
    }

    private void toConfigure() {
        pickTemplate(this.templateList.selected() < 0 ? 0 : this.templateList.selected());
        openConfigure();
    }

    private void openConfigure() {
        this.templates.close();
        final String name = uniqueProjectName(this.chosen == ProjectTemplate.CLASS_LIBRARY ? "Library" : "App");
        this.projectName.set(name);
        this.solutionName.set(this.addingToSolution ? this.solution.name() : name);
        this.solutionName.setEnabled(!this.addingToSolution);
        this.configure.open();
        this.configure.focus(this.projectName);
    }

    private String uniqueProjectName(final String base) {
        String name = base;
        int n = 1;
        while (this.projects.containsKey(name)) {
            name = base + (++n);
        }
        return name;
    }

    private String configureNoteText() {
        final String project = this.projectName.edit().trim();
        final String sol = this.solutionName.edit().trim();
        final String where = this.addingToSolution ? this.solutionDir
                : join(LOCATION, this.sameFolder || sol.isEmpty() ? project : sol);
        return "Project will be created in C:\\" + join(where, project).replace('/', '\\') + "\\";
    }

    /** Create, from the wizard's second page: what was typed there. */
    private void createProject() {
        final String project = this.projectName.edit().trim();
        final String typedSolution = this.solutionName.edit().trim();
        final String sol = this.addingToSolution ? this.solution.name()
                : (typedSolution.isEmpty() || this.sameFolder ? project : typedSolution);
        create(this.chosen, project, sol, this.addingToSolution);
    }

    /**
     * Creates a new solution around one project of {@code template}, as the wizard's Create does with
     * the same directory for both, and opens it.
     */
    public void createProject(final ProjectTemplate template, final String name) {
        create(template, name, name, false);
    }

    /** The point on the Start Window that opens {@code title}, once drawn, or null when it is not up. */
    public int[] startLinkCenter(final String title) {
        for (final Link link : this.links) {
            if (link.title().equals(title)) {
                return new int[] {link.x() + link.width() / 2, link.y() + link.height() / 2};
            }
        }
        return null;
    }

    /** Whether the New Project wizard is up, on either of its pages. */
    public boolean wizardOpen() {
        return this.templates.isOpen() || this.configure.isOpen();
    }

    /** Takes the wizard from its template page to its names page with {@code template} chosen. */
    public void chooseTemplate(final ProjectTemplate template) {
        this.chosen = template;
        openConfigure();
    }

    /** The solution file, the project file and the first source, then the solution opens. */
    private void create(final ProjectTemplate template, final String project, final String sol,
                        final boolean intoSolution) {
        if (project.isEmpty() || project.contains("/") || project.contains("\\") || project.contains(" ")) {
            this.workspace.say("A project name is one word, without slashes");
            return;
        }
        this.configure.close();
        this.chosen = template;
        this.addingToSolution = intoSolution && this.solution != null;
        final String dir = this.addingToSolution ? this.solutionDir : join(LOCATION, sol);
        final ProjectFile file = this.chosen.project(project);
        final SolutionFile solutionFile = (this.addingToSolution ? this.solution : new SolutionFile(sol, List.of(), ""))
                .withProject(SolutionFile.projectPath(project));
        PacketDistributor.sendToServer(new SaveFilePayload(this.host, join(dir, SolutionFile.fileName(sol)),
                solutionFile.write()));
        PacketDistributor.sendToServer(new SaveFilePayload(this.host,
                join(join(dir, project), ProjectFile.fileName(project)), file.write()));
        final String first = this.chosen.firstSource(project);
        if (!first.isEmpty()) {
            PacketDistributor.sendToServer(new SaveFilePayload(this.host, join(join(dir, project), first),
                    this.chosen.source(project)));
        }
        FilesApps.diskChanged();
        /*
         * The files are on their way; asking for the solution now reads them once they have landed. The
         * first source waits for the solution to be in, since one file is waited for at a time.
         */
        this.openWhenLoaded = first.isEmpty() ? "" : join(join(dir, project), first);
        openSolutionFolder(dir);
    }

    /** A source to open once the solution being read is in, or empty. */
    private String openWhenLoaded = "";

    private void layoutTemplates(final Popup p) {
        final int x = p.x() + 4;
        int y = p.contentTop() + 2;
        final int w = p.width() - 8;
        this.templateSearch.setBounds(x, y, w, 11);
        y += 13;
        this.templateLanguage.setBounds(x, y, 76, 11);
        this.templateKind.setBounds(x + 80, y, 60, 11);
        y += 13;
        this.templateList.setBounds(x, y, w, p.bottom() - y - 16);
        this.templateNext.setBounds(p.right() - 40, p.bottom() - 14, 36, 11);
        p.children().get(p.children().size() - 1).setBounds(p.right() - 80, p.bottom() - 14, 36, 11);
    }

    private void layoutConfigure(final Popup p) {
        final int x = p.x() + 4;
        int y = p.contentTop() + 1;
        final int w = p.width() - 8;
        final List<dev.jstech.core.client.gui.component.UiComponent> c = p.children();
        c.get(0).setBounds(x, y + 1, 74, 9);
        this.projectName.setBounds(x + 76, y, w - 76, 11);
        y += 13;
        c.get(2).setBounds(x, y + 1, 74, 9);
        this.solutionName.setBounds(x + 76, y, w - 76, 11);
        y += 13;
        c.get(4).setBounds(x, y, w, 9);
        y += 11;
        this.configureNote.setBounds(x, y, w, 9);
        this.create.setBounds(p.right() - 40, p.bottom() - 14, 36, 11);
        c.get(7).setBounds(p.right() - 80, p.bottom() - 14, 36, 11);
    }

    /* Properties, options, and the one-thing windows */

    private void showProperties(final String name) {
        this.propertiesOf = name;
        this.properties.open();
    }

    private String propertyText(final int line) {
        final ProjectFile project = this.projects.get(this.propertiesOf);
        if (project == null) {
            return "";
        }
        return switch (line) {
            case 0 -> "Name: " + project.name();
            case 1 -> "Kind: " + project.kind().key();
            case 2 -> "Language: " + languageName(project.language());
            case 3 -> "Entry: " + (project.entry().isEmpty() ? "none, a library" : project.entry());
            default -> "Sources: " + project.sources().size() + ", references: " + project.references().size();
        };
    }

    private void layoutProperties(final Popup p) {
        int y = p.contentTop() + 2;
        for (final Label label : this.propertyLines) {
            label.setBounds(p.x() + 4, y, p.width() - 8, 9);
            y += 9;
        }
        this.propertiesClose.setBounds(p.right() - 38, p.bottom() - 14, 34, 11);
    }

    private void layoutOptions(final Popup p) {
        final List<dev.jstech.core.client.gui.component.UiComponent> c = p.children();
        c.get(0).setBounds(p.x() + 4, p.contentTop() + 4, 50, 9);
        this.tabStepper.setBounds(p.x() + 56, p.contentTop() + 2, 96, 12);
        this.completionsBox.setBounds(p.x() + 4, p.contentTop() + 18, p.width() - 8, 9);
        this.optionsClose.setBounds(p.right() - 38, p.bottom() - 14, 34, 11);
    }

    private void setTabSize(final int value) {
        this.tabSize = value;
        for (final CodeWorkspace.Doc doc : this.workspace.docs()) {
            doc.area().setTabSize(value);
        }
    }

    private void ask(final String title, final String initial, final java.util.function.Consumer<String> action) {
        this.askTitle = title;
        this.askAction = action;
        this.askField.set(initial);
        this.ask.open();
        this.ask.focus(this.askField);
    }

    private void layoutAsk(final Popup p) {
        this.askField.setBounds(p.x() + 4, p.contentTop() + 3, p.width() - 8, 11);
        this.askOk.setBounds(p.right() - 38, p.bottom() - 15, 34, 11);
    }

    /* The menus */

    private ContextMenu.Item item(final String label, final boolean enabled, final Runnable action) {
        return new ContextMenu.Item(label, enabled, action);
    }

    private boolean hasDoc() {
        return this.workspace.current() != null;
    }

    private boolean hasSolution() {
        return this.solution != null;
    }

    private String currentProject() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null) {
            for (final String name : this.projects.keySet()) {
                if (doc.path().startsWith(projectDir(name) + "/")) {
                    return name;
                }
            }
        }
        return startupName();
    }

    private List<ContextMenu.Item> fileMenu() {
        final List<ContextMenu.Item> items = new ArrayList<>(List.of(
                item("New Project...", true, () -> newProject(false)),
                item("New File", this.page == Page.SOLUTION, this::newFile),
                ContextMenu.Item.separator(),
                item("Open Project/Solution...", true, this::openSolutionByPicker),
                item("Open Folder...", true, this::openFolderByPicker),
                item("Open File...", this.page == Page.SOLUTION, this::goToFile)));
        for (final String dir : recent()) {
            items.add(item("Recent: " + shortName(dir), true, () -> openSolutionFolder(dir)));
        }
        items.add(ContextMenu.Item.separator());
        items.add(item("Save", hasDoc(), this.workspace::save));
        items.add(item("Save All", this.workspace.anyDirty(), this.workspace::saveAll));
        items.add(ContextMenu.Item.separator());
        items.add(item("Close Solution", this.page == Page.SOLUTION, this::closeSolution));
        items.add(item("Exit", true, () -> DesktopScreen.requestClose(KEY)));
        return items;
    }

    private List<ContextMenu.Item> editMenu() {
        return List.of(
                item("Find...", hasDoc(), this::find),
                item("Go To Line...", hasDoc(), this::goToLine),
                item("Toggle Line Comment", hasDoc(), this::toggleComment));
    }

    private List<ContextMenu.Item> viewMenu() {
        return List.of(
                item("Error List", true, () -> this.dockTabs.setSelected(DOCK_ERRORS)),
                item("Output", true, () -> this.dockTabs.setSelected(DOCK_OUTPUT)),
                item("Assembly", hasSolution() && this.projects.containsKey(currentProject()), this::openAssembly),
                item("Start Window", true, this::closeSolution));
    }

    private List<ContextMenu.Item> projectMenu() {
        final boolean has = hasSolution() && this.projects.containsKey(currentProject());
        return List.of(
                item("Add New Item...", has, this::addNewItem),
                item("Add Existing Item...", has, this::addExistingItem),
                item("Add Project Reference...", has && this.projects.size() > 1, this::addReference),
                item("Add New Project...", hasSolution(), () -> newProject(true)),
                ContextMenu.Item.separator(),
                item("Set as Startup Project", has, this::setStartup),
                item("Properties", has, () -> showProperties(currentProject())));
    }

    private List<ContextMenu.Item> buildMenu() {
        final String name = currentProject();
        return List.of(
                item("Build Solution", this.page == Page.SOLUTION, this::buildSolution),
                item("Rebuild Solution", this.page == Page.SOLUTION, this::buildSolution),
                item("Clean Solution", hasSolution(), this::cleanSolution),
                item("Build " + (name.isEmpty() ? "Project" : name), hasSolution() && !name.isEmpty(),
                        () -> buildProject(name)),
                ContextMenu.Item.separator(),
                item("Package", hasSolution() && !startupName().isEmpty(), this::packageStartup));
    }

    private List<ContextMenu.Item> debugMenu() {
        return List.of(
                item("Start", this.page == Page.SOLUTION, this::startProgram),
                item("Stop", true, () -> DesktopScreen.requestTypeAtTerminal(List.of("cannon stop"))));
    }

    private List<ContextMenu.Item> toolsMenu() {
        return List.of(item("Options...", true, () -> {
            this.tabStepper.setAmount(this.tabSize);
            this.options.open();
        }));
    }

    private List<ContextMenu.Item> helpMenu() {
        return List.of(item("About Virtual Studio", true,
                () -> this.workspace.say("Virtual Studio, by Midsoft. Cannon 1.0.")));
    }

    /* What the menus do */

    private void openSolutionByPicker() {
        this.picker.open(LOCATION, this::openSolutionFolder);
    }

    private void openFolderByPicker() {
        this.picker.open(LOCATION, this::openFolder);
    }

    private void goToFile() {
        final List<CommandPalette.Entry> entries = new ArrayList<>();
        for (final CodeWorkspace.TreeRow row : this.workspace.tree()) {
            if (!row.file().directory()) {
                final String path = row.file().path();
                entries.add(new CommandPalette.Entry(shortName(path), "", () -> this.workspace.open(path)));
            }
        }
        for (final ProjectFile project : this.projects.values()) {
            for (final String source : project.sources()) {
                final String path = join(projectDir(project.name()), source);
                entries.add(new CommandPalette.Entry(project.name() + "/" + source, "", () -> this.workspace.open(path)));
            }
        }
        this.palette.open(entries, "");
    }

    private void newFile() {
        final String project = currentProject();
        ask("New File", "untitled.can", name -> {
            if (name.isEmpty()) {
                return;
            }
            final String dir = this.projects.containsKey(project) ? projectDir(project) : this.solutionDir;
            this.workspace.newFile(join(dir, name));
            setTabSize(this.tabSize);
        });
    }

    private void addNewItem() {
        final String name = currentProject();
        final ProjectFile project = this.projects.get(name);
        if (project == null) {
            return;
        }
        ask("Add New Item", "Class1.can", file -> {
            if (file.isEmpty()) {
                return;
            }
            this.workspace.newFile(join(projectDir(name), file));
            saveProject(project.withSource(file));
        });
    }

    private void addExistingItem() {
        final String name = currentProject();
        final ProjectFile project = this.projects.get(name);
        if (project == null) {
            return;
        }
        final List<CommandPalette.Entry> entries = new ArrayList<>();
        for (final CodeWorkspace.TreeRow row : this.workspace.tree()) {
            final String path = row.file().path();
            if (!row.file().directory() && path.startsWith(projectDir(name) + "/") && path.endsWith(".can")) {
                final String relative = path.substring(projectDir(name).length() + 1);
                if (!project.sources().contains(relative)) {
                    entries.add(new CommandPalette.Entry(relative, "", () -> saveProject(project.withSource(relative))));
                }
            }
        }
        this.palette.open(entries, "");
    }

    private void addReference() {
        final String name = currentProject();
        final ProjectFile project = this.projects.get(name);
        if (project == null) {
            return;
        }
        final List<CommandPalette.Entry> entries = new ArrayList<>();
        for (final String other : this.projects.keySet()) {
            if (!other.equals(name) && !project.references().contains(other)) {
                entries.add(new CommandPalette.Entry(other, "", () -> saveProject(project.withReference(other))));
            }
        }
        this.palette.open(entries, "");
    }

    private void setStartup() {
        if (this.solution != null) {
            this.solution = this.solution.withStartup(currentProject());
            saveSolution();
        }
    }

    private void openAssembly() {
        final ProjectFile project = this.projects.get(currentProject());
        if (project != null && project.buildsAListing()) {
            this.workspace.open(join(projectDir(project.name()), project.entry()));
        }
    }

    private void goToLine() {
        ask("Go To Line", "", value -> {
            final CodeWorkspace.Doc doc = this.workspace.current();
            try {
                if (doc != null) {
                    doc.area().document().setCursor(Integer.parseInt(value) - 1, 0);
                }
            } catch (final NumberFormatException ignored) {
                this.workspace.say("Not a line number: " + value);
            }
        });
    }

    private void find() {
        ask("Find", "", needle -> {
            final CodeWorkspace.Doc doc = this.workspace.current();
            if (doc != null && !doc.area().document().find(needle)) {
                this.workspace.say("No results for '" + needle + "'");
            }
        });
    }

    private void toggleComment() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null) {
            doc.area().document().toggleLinePrefix("// ");
            this.workspace.edited();
        }
    }

    /* The window */

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        this.workspace.setPalette(InkPalette.forGround(osSkin.isDark()));
    }

    @Override
    public String title() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        final String where = this.solution != null ? this.solution.name()
                : this.page == Page.SOLUTION ? shortName(this.solutionDir) : "";
        if (doc == null) {
            return where.isEmpty() ? "Virtual Studio" : where + " - Virtual Studio";
        }
        return doc.name() + (doc.dirty() ? " *" : "") + " - " + (where.isEmpty() ? "" : where + " - ") + "Virtual Studio";
    }

    @Override
    public int defaultWidth() {
        return 320;
    }

    @Override
    public int defaultHeight() {
        return 200;
    }

    @Override
    public int minWidth() {
        return 240;
    }

    @Override
    public int minHeight() {
        return 130;
    }

    @Override
    public void onRestored() {
        if (this.page == Page.SOLUTION) {
            this.workspace.refresh();
        }
    }

    @Override
    public void onClosed() {
        this.workspace.release();
        this.picker.release();
        CodeFileReplies.forget(this);
    }

    @Override
    public boolean modalActive() {
        return this.picker.isOpen() || this.templates.isOpen() || this.configure.isOpen() || this.ask.isOpen()
                || this.properties.isOpen() || this.options.isOpen();
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        final UiContext ctx = new UiContext(this.skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, this.skin.windowBg());
        this.root.setBounds(x, y, width, height);
        this.menuBar.setBounds(x, y, width, MenuBar.HEIGHT);
        this.menuBar.setWindow(x, y, width, height);
        final int top = y + MenuBar.HEIGHT;

        final CodeWorkspace.Doc doc = this.workspace.current();
        // What the Start Window does not show is hidden outright: a list with no room still has rows to draw.
        final boolean start = this.page == Page.START;
        for (final dev.jstech.core.client.gui.component.UiComponent part
                : List.of(this.start, this.explorer, this.tabs, this.dockTabs, this.errors, this.outputList)) {
            part.setVisible(!start);
        }
        if (start) {
            drawStartWindow(g, font, x, top, width, height - MenuBar.HEIGHT - STATUS_H);
        } else {
            this.skin.panel(g, x, top, width, TOOLBAR_H);
            this.start.setBounds(x + 3, top + 2, 34, TOOLBAR_H - 4);
            final String config = "Release";
            this.skin.field(g, x + 42, top + 2, 40, TOOLBAR_H - 4, false);
            g.drawString(font, config, x + 45, top + 3, this.skin.text(), false);
            final int bodyY = top + TOOLBAR_H;
            final int bodyH = height - MenuBar.HEIGHT - TOOLBAR_H - STATUS_H;
            final int codeX = x;
            final int codeW = width - SIDE_W;
            final int sideX = x + codeW;
            this.skin.panel(g, sideX, bodyY, SIDE_W, bodyH);
            g.drawString(font, "SOLUTION EXPLORER", sideX + 3, bodyY + 1, this.skin.dim(), false);
            this.explorer.setBounds(sideX, bodyY + CAPTION_H, SIDE_W, bodyH - CAPTION_H);
            this.skin.panel(g, codeX, bodyY, codeW, TAB_H);
            this.tabs.setBounds(codeX, bodyY, codeW, TAB_H);
            this.tabs.setSelected(this.workspace.currentIndex());
            final int paneY = bodyY + TAB_H;
            final int paneH = bodyH - TAB_H;
            final boolean dockShown = paneH - DOCK_H >= MIN_CODE_H;
            final int codeH = dockShown ? paneH - DOCK_H : paneH;
            if (doc != null) {
                doc.area().setBounds(codeX, paneY, codeW, codeH);
            }
            layoutDock(codeX, paneY + codeH, codeW, dockShown ? DOCK_H : 0);
            if (doc == null) {
                drawEmpty(g, font, codeX, paneY, codeW, codeH);
            }
        }
        this.root.render(g, ctx);
        if (doc != null && this.page == Page.SOLUTION) {
            doc.area().render(g, ctx);
        }
        drawStatus(g, font, x, y + height - STATUS_H, width, doc);
        this.completions.render(g, ctx);
        this.palette.render(g, ctx, x, top, width);
        this.picker.render(g, ctx, x, y, width, height);
        for (final Popup popup : List.of(this.templates, this.configure, this.ask, this.properties, this.options)) {
            if (popup.isOpen()) {
                popup.renderIn(g, ctx, x, y, width, height);
            }
        }
        this.menuBar.render(g, ctx);
    }

    private void layoutDock(final int x, final int y, final int width, final int height) {
        final boolean shown = height > 0;
        final boolean showErrors = this.dockTabs.selected() == DOCK_ERRORS;
        this.dockTabs.setVisible(shown);
        this.errors.setVisible(shown && showErrors);
        this.outputList.setVisible(shown && !showErrors);
        this.dockTabs.setBounds(x, y, width, TAB_H);
        this.errors.setBounds(x, y + TAB_H, width, Math.max(0, height - TAB_H));
        this.outputList.setBounds(x, y + TAB_H, width, Math.max(0, height - TAB_H));
    }

    /** The Start Window: what was opened lately on the left, the ways to begin on the right. */
    private void drawStartWindow(final GuiGraphics g, final Font font, final int x, final int y,
                                 final int width, final int height) {
        final InkPalette palette = InkPalette.forGround(this.skin.isDark());
        this.links.clear();
        // The recent list needs less room than the cards, whose titles are whole sentences.
        final int half = width * 2 / 5;
        this.skin.panel(g, x, y, half, height);
        g.fill(x + half, y, x + width, y + height, palette.ground());
        Draw.pushScissor(g, x, y, x + width, y + height);
        int ly = y + 6;
        g.drawString(font, "Open recent", x + 6, ly, this.skin.text(), false);
        ly += 12;
        final List<String> recent = recent();
        if (recent.isEmpty()) {
            g.drawString(font, "Nothing yet", x + 6, ly, this.skin.dim(), false);
        }
        for (final String dir : recent) {
            final String label = SolutionFile.fileName(shortName(dir));
            g.drawString(font, label, x + 6, ly, this.skin.accent(), false);
            g.drawString(font, font.plainSubstrByWidth("C:\\" + dir.replace('/', '\\'), half - 12), x + 6, ly + 9,
                    this.skin.dim(), false);
            this.links.add(new Link(label, x + 6, ly - 1, half - 12, 18, () -> openSolutionFolder(dir)));
            ly += 20;
        }
        int ry = y + 6;
        final int rx = x + half + 8;
        g.drawString(font, "Get started", rx, ry, palette.plain(), false);
        ry += 12;
        final int cardW = width - half - 16;
        ry = card(g, font, rx, ry, cardW, "Open a project or solution", "A .sln on this machine",
                this::openSolutionByPicker, palette);
        ry = card(g, font, rx, ry, cardW, "Open a local folder", "Any folder of programs",
                this::openFolderByPicker, palette);
        ry = card(g, font, rx, ry, cardW, "Open a file", "One .can, no project",
                this::openFileFromStart, palette);
        card(g, font, rx, ry, cardW, "Create a new project", "Start from a template",
                () -> newProject(false), palette);
        Draw.popScissor(g);
    }

    private int card(final GuiGraphics g, final Font font, final int x, final int y, final int width,
                     final String title, final String sub, final Runnable action, final InkPalette palette) {
        g.fill(x, y, x + width, y + 20, palette.gutter());
        g.drawString(font, font.plainSubstrByWidth(title, width - 6), x + 3, y + 2, palette.plain(), false);
        g.drawString(font, font.plainSubstrByWidth(sub, width - 6), x + 3, y + 11, palette.gutterText(), false);
        this.links.add(new Link(title, x, y, width, 20, action));
        return y + 23;
    }

    /** Open a file from the Start Window: pick its folder, then the file. */
    private void openFileFromStart() {
        this.picker.open(LOCATION, dir -> {
            openFolder(dir);
            goToFile();
        });
    }

    private void drawEmpty(final GuiGraphics g, final Font font, final int x, final int y,
                           final int width, final int height) {
        final InkPalette palette = InkPalette.forGround(this.skin.isDark());
        g.fill(x, y, x + width, y + height, palette.ground());
        Draw.pushScissor(g, x, y, x + width, y + height);
        g.drawString(font, this.solution == null ? "Open a file from the folder"
                : "Open a source from the Solution Explorer", x + 6, y + 6, palette.gutterText(), false);
        Draw.popScissor(g);
    }

    private void drawStatus(final GuiGraphics g, final Font font, final int x, final int y,
                            final int width, final CodeWorkspace.Doc doc) {
        this.skin.statusBar(g, x, y, width, STATUS_H);
        final int errorCount = errorRows().size();
        final String left = errorCount == 0 ? "Ready" : errorCount + " error(s)";
        g.drawString(font, left, x + 3, y + 1, this.skin.dim(), false);
        if (!this.workspace.status().isEmpty()) {
            g.drawString(font, this.workspace.status(), x + 5 + font.width(left) + 6, y + 1, this.skin.dim(), false);
        }
        int right = x + width - 3;
        if (this.solution != null) {
            right -= font.width(this.solution.name());
            g.drawString(font, this.solution.name(), right, y + 1, this.skin.dim(), false);
            right -= 8;
        }
        if (doc != null) {
            final String where = "Ln " + (doc.area().document().cursorLine() + 1)
                    + ", Col " + (doc.area().document().cursorCol() + 1);
            right -= font.width(where);
            g.drawString(font, where, right, y + 1, this.skin.dim(), false);
        }
    }

    /* Input */

    private Popup openPopup() {
        for (final Popup popup : List.of(this.templates, this.configure, this.ask, this.properties, this.options)) {
            if (popup.isOpen()) {
                return popup;
            }
        }
        return null;
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (this.picker.isOpen()) {
            this.picker.mouseClicked(mouseX, mouseY, button);
            return;
        }
        final Popup popup = openPopup();
        if (popup != null) {
            popup.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (this.palette.isOpen()) {
            this.palette.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (this.menuBar.isOpen() || this.menuBar.mouseClicked(mouseX, mouseY, button)) {
            return;
        }
        if (this.completions.mouseClicked(mouseX, mouseY, button)) {
            this.workspace.edited();
            return;
        }
        this.completions.close();
        for (final Link link : this.links) {
            if (this.page == Page.START && mouseX >= link.x() && mouseX < link.x() + link.width()
                    && mouseY >= link.y() && mouseY < link.y() + link.height()) {
                link.action().run();
                return;
            }
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null && this.page == Page.SOLUTION && doc.area().contains(mouseX, mouseY)) {
            doc.area().mouseClicked(mouseX, mouseY, button);
            return;
        }
        this.root.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(final char c) {
        if (this.picker.isOpen()) {
            return this.picker.charTyped(c);
        }
        final Popup popup = openPopup();
        if (popup != null) {
            return popup.charTyped(c);
        }
        if (this.palette.isOpen()) {
            return this.palette.charTyped(c);
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null || this.page != Page.SOLUTION || !doc.area().charTyped(c)) {
            return false;
        }
        this.workspace.edited();
        if (this.completionsOn && (c == '.' || this.completions.isOpen())) {
            offerCompletions(doc);
        }
        return true;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        final boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        final boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (this.picker.isOpen()) {
            return this.picker.keyPressed(key, scanCode, modifiers);
        }
        final Popup popup = openPopup();
        if (popup != null) {
            return popup.keyPressed(key, scanCode, modifiers);
        }
        if (this.palette.isOpen()) {
            return this.palette.keyPressed(key, scanCode, modifiers);
        }
        if (this.menuBar.keyPressed(key, scanCode, modifiers)) {
            return true;
        }
        if (ctrl && shift && key == GLFW.GLFW_KEY_N) {
            newProject(false);
            return true;
        }
        if (ctrl && shift && key == GLFW.GLFW_KEY_B) {
            buildSolution();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_S) {
            this.workspace.save();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_F) {
            find();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_G) {
            goToLine();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F5) {
            startProgram();
            return true;
        }
        if (this.completions.keyPressed(key, scanCode, modifiers)) {
            this.workspace.edited();
            return true;
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null || this.page != Page.SOLUTION) {
            return false;
        }
        if (ctrl && key == GLFW.GLFW_KEY_SPACE && this.completionsOn) {
            offerCompletions(doc);
            return true;
        }
        if (doc.area().keyPressed(key, scanCode, modifiers)) {
            this.workspace.edited();
            if (this.completions.isOpen()) {
                offerCompletions(doc);
            }
            return true;
        }
        return false;
    }

    private void offerCompletions(final CodeWorkspace.Doc doc) {
        final CodeArea area = doc.area();
        this.completions.offer(area, doc.path(), new int[] {area.x(), area.y(), area.width(), area.height()});
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        if (this.palette.isOpen()) {
            return this.palette.mouseScrolled(0, 0, delta);
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null || this.page != Page.SOLUTION) {
            return this.explorer.mouseScrolled(this.explorer.x(), this.explorer.y(), delta);
        }
        return doc.area().mouseScrolled(doc.area().x(), doc.area().y(), delta);
    }

    @Override
    public void renderTooltip(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY) {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null || this.page != Page.SOLUTION) {
            return;
        }
        final String message = doc.area().messageAt(mouseX, mouseY);
        if (!message.isEmpty()) {
            g.renderTooltip(font, Component.literal(message), mouseX, mouseY);
        }
    }
}
