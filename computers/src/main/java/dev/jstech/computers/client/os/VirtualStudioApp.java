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
    private static final int RECENT_ROW_H = 19;
    private static final int MIN_CODE_H = 36;
    private static final String KEY = "Virtual Studio";

    private static final List<String> DOCK_TABS = List.of("ERROR LIST", "OUTPUT", "TERMINAL");
    private static final int DOCK_ERRORS = 0;
    private static final int DOCK_OUTPUT = 1;
    private static final int DOCK_TERMINAL = 2;
    /** How close to a divider a click has to land to take hold of it. */
    private static final int GRIP = 3;

    /* The two dividers the player can drag: how wide the Solution Explorer is, how tall the dock is. */
    private int sideW = SIDE_W;
    private int dockH = DOCK_H;
    /** Where the dividers were drawn last, so a click can find them. */
    private int dividerX;
    private int dividerY;
    private int bodyTop;
    private int bodyBottom;
    /** Which divider the mouse is holding: 0 none, 1 the explorer's, 2 the dock's. */
    private int holding;

    /** The machine's own console in the dock, where Start runs what was built. */
    private final ShellView terminal;
    /** Whether typing goes to the terminal rather than to the code. */
    private boolean typingInTerminal;
    /** Lines still to be typed at the terminal, the next once the machine has answered the one before. */
    private final Deque<String> typing = new ArrayDeque<>();

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
    /** The system's file window, for everything the studio opens or saves by choosing on the disk. */
    private final FileDialog dialog;
    private final List<Link> links = new ArrayList<>();

    private record Link(String title, int x, int y, int width, int height, Runnable action) {
    }

    /* The New Project wizard */
    private final Popup templates = new Popup("Create a new project", 302, 172).setLayouter(this::layoutTemplates);
    private final TextField templateSearch = new TextField(32);
    private final ListView<ProjectTemplate> templateList;
    private final ListView<ProjectTemplate> recentList;
    private final Button templateKind;
    private final Button templateLanguage;
    private final Button templatePlatform;
    private final Button templateNext;
    private final Button templateBack;
    private String kindFilter = "";
    private String languageFilter = "";
    private String platformFilter = "";
    /** The templates used lately on each machine, newest first, for the wizard's left pane. */
    private static final Map<BlockPos, Deque<ProjectTemplate>> RECENT_TEMPLATES = new LinkedHashMap<>();
    private final Popup configure = new Popup("Configure your new project", 240, 124).setLayouter(this::layoutConfigure);
    private final TextField projectName = new TextField(32);
    private final TextField locationField = new TextField(64);
    private final Button browse;
    private final TextField solutionName = new TextField(32);
    private final Label configureNote;
    private final Button create;
    private boolean sameFolder = true;
    private ProjectTemplate chosen = ProjectTemplate.CONSOLE_APP;
    private boolean addingToSolution;
    /** Where the wizard puts the solution: the machine's program folder until the player picks another. */
    private String location = LOCATION;

    /* The small windows a command opens for one thing */
    private final Popup ask = new Popup(() -> this.askTitle, 150, 44).setLayouter(this::layoutAsk);
    /** The question a closing tab with changes asks. */
    private final Popup askClose = new Popup(() -> "Save changes to " + closingName() + "?", 176, 40)
            .setLayouter(this::layoutAskClose);
    /** The question the tree asks before a file goes, and the row it is about. */
    private final Popup askDelete = new Popup(() -> "Delete " + deletingName() + "?", 150, 40)
            .setLayouter(this::layoutAskDelete);
    private Node deleting;
    /** The right-button menu of the Solution Explorer. */
    private final ContextMenu treeContext = new ContextMenu(124, 11);
    /** Where the window was last drawn, for a menu opened from a row of the tree to stay inside it. */
    private int winX;
    private int winY;
    private int winW;
    private int winH;
    /** The tab being closed while the question is up. */
    private int closing = -1;
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
        this.dialog = new FileDialog(host, this);
        this.explorer = this.root.add(new ListView<>(this::nodes, ROW_H, this::drawNode)).setOnClick(this::onNode);
        this.tabs = this.root.add(new TabStrip(this.workspace::tabLabels).fitToLabels(10).setUnderline(false));
        this.tabs.setOnSelect(this.workspace::setCurrent);
        this.dockTabs = this.root.add(new TabStrip(DOCK_TABS).fitToLabels(12).setUnderline(true));
        this.errors = this.root.add(new ListView<>(this::errorRows, ROW_H, this::drawErrorRow)).setOnClick(this::onError);
        this.outputList = this.root.add(new ListView<>(() -> this.output, ROW_H, this::drawOutputRow));
        this.terminal = this.root.add(new ShellView(host, false, false));
        this.terminal.setOnIdle(this::typeNext);
        this.tabs.setCloseable(this::closeTab);
        this.askClose.add(new Button("Save", () -> {
            this.askClose.close();
            this.workspace.setCurrent(this.closing);
            this.workspace.save();
            this.workspace.close(this.closing);
        }).setPrimary(true));
        this.askClose.add(new Button("Don't Save", () -> {
            this.askClose.close();
            this.workspace.close(this.closing);
        }));
        this.askClose.add(new Button("Cancel", this.askClose::close));
        this.askDelete.add(new Button("Delete", () -> {
            this.askDelete.close();
            deleteNode();
        }).setPrimary(true));
        this.askDelete.add(new Button("Cancel", this.askDelete::close));
        this.start = this.root.add(new Button("Start", this::startProgram).setPrimary(true));
        this.root.add(this.menuBar);
        this.menuBar.add("File", this::fileMenu).add("Edit", this::editMenu).add("View", this::viewMenu)
                .add("Project", this::projectMenu).add("Build", this::buildMenu).add("Debug", this::debugMenu)
                .add("Tools", this::toolsMenu).add("Help", this::helpMenu);

        /*
         * The wizard's first page: the templates used lately on the left, and on the right the whole
         * list, narrowed by what is typed and by language, platform and kind.
         */
        this.templates.add(new Label("Recent templates", Label.Tone.DIM));
        this.recentList = this.templates.add(new ListView<>(this::recentTemplates, RECENT_ROW_H, this::drawRecent)
                .setOnClick((index, button, mx, my) -> pickRecent(index)));
        this.templates.add(this.templateSearch.setPlaceholder("Search for templates"));
        // Three filters side by side in the small text, the way a row of drop-downs would sit.
        this.templateLanguage = this.templates.add(new Button("All languages", this::cycleLanguage).setLabelScale(0.75f));
        this.templatePlatform = this.templates.add(new Button("All platforms", this::cyclePlatform).setLabelScale(0.75f));
        this.templateKind = this.templates.add(new Button("All types", this::cycleKind).setLabelScale(0.75f));
        this.templateList = this.templates.add(new ListView<>(this::matchingTemplates, TEMPLATE_ROW_H, this::drawTemplate)
                .setOnClick((index, button, mx, my) -> pickTemplate(index)));
        this.templateBack = this.templates.add(new Button("Back", () -> { }));
        this.templateBack.setEnabled(false);
        this.templateNext = this.templates.add(new Button("Next", this::toConfigure).setPrimary(true));
        this.templates.add(new Button("Cancel", this.templates::close));
        // The second page: the template chosen, the names, and where it all goes.
        this.configure.add(new Label(() -> this.chosen.title()));
        this.configure.add(new Label(() -> String.join("  ", this.chosen.tags()), Label.Tone.DIM));
        this.configure.add(new Label("Project name", Label.Tone.DIM));
        this.configure.add(this.projectName);
        this.configure.add(new Label("Location", Label.Tone.DIM));
        this.configure.add(this.locationField);
        this.browse = this.configure.add(new Button("...", this::browseLocation));
        this.configure.add(new Label("Solution name", Label.Tone.DIM));
        this.configure.add(this.solutionName);
        this.configure.add(new Checkbox(() -> "Same folder for solution and project",
                () -> this.sameFolder, () -> this.sameFolder = !this.sameFolder));
        this.configureNote = this.configure.add(new Label(this::configureNoteText, Label.Tone.DIM));
        this.configure.add(new Button("Back", () -> {
            this.configure.close();
            this.templates.open();
        }));
        this.create = this.configure.add(new Button("Create", this::createProject).setPrimary(true));
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
            final List<String> paths = new ArrayList<>(this.openWhenLoaded);
            this.openWhenLoaded.clear();
            this.workspace.openAll(paths, this.currentWhenLoaded);
            this.currentWhenLoaded = "";
        }
    }

    /**
     * The solution and the tabs, for the machine to hand back after the game itself was closed: which
     * kind of thing is open, where, then the workspace's own account of its files.
     */
    @Override
    public String saveState() {
        if (this.page != Page.SOLUTION || this.solutionDir.isEmpty()) {
            return "";
        }
        return (this.solution != null ? "solution" : "folder") + "\n" + this.solutionDir + "\n"
                + this.workspace.describeOpen().substring(this.workspace.folder().length() + 1);
    }

    @Override
    public void restoreState(final String state) {
        final int firstBreak = state.indexOf('\n');
        if (firstBreak < 0) {
            return;
        }
        final String kind = state.substring(0, firstBreak);
        final String rest = state.substring(firstBreak + 1);
        final String dir = CodeWorkspace.folderOf(rest);
        if (dir.isEmpty()) {
            return;
        }
        if ("solution".equals(kind)) {
            // The files wait for the solution: the machine answers one file at a time, and the solution goes first.
            final String[] lines = rest.split("\n", -1);
            this.openWhenLoaded.clear();
            for (int i = 1; i < lines.length - 1; i++) {
                if (!lines[i].isEmpty()) {
                    this.openWhenLoaded.add(lines[i]);
                }
            }
            this.currentWhenLoaded = lines.length > 2 ? lines[lines.length - 1] : "";
            openSolutionFolder(dir);
            return;
        }
        openFolder(dir);
        this.workspace.reopen(rest);
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
        final int iconX = x + 2 + node.depth() * 5;
        FileIcons.draw(g, iconX, y, iconOf(node));
        final int textX = iconX + 12;
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(node.label(), x + width - 2 - textX), textX, y + 1,
                color, false);
    }

    /** The icon beside a row of the tree: what the explorer gives the same file, and a few of the tree's own. */
    private FileIcons.Kind iconOf(final Node node) {
        return switch (node.kind()) {
            case SOLUTION -> FileIcons.Kind.BUNDLE;
            case PROJECT -> FileIcons.Kind.PROGRAM;
            case DEPENDENCIES -> FileIcons.Kind.FOLDER;
            case DEPENDENCY -> FileIcons.Kind.BIN;
            case PROPERTIES -> FileIcons.Kind.CFG;
            case SOURCE, OUTPUT -> FileIcons.kindOfPath(node.path(), false);
            case FOLDER_FILE -> {
                final DiskFilesPayload.WireFile file = fileAt(node.path());
                yield FileIcons.kindOfPath(node.path(), file != null && file.directory());
            }
        };
    }

    private void onNode(final int index, final int button, final double mx, final double my) {
        final List<Node> all = nodes();
        if (index < 0 || index >= all.size()) {
            return;
        }
        final Node node = all.get(index);
        if (button == 1) {
            final List<ContextMenu.Item> items = treeMenu(node);
            if (!items.isEmpty()) {
                this.treeContext.open(items, (int) mx, (int) my, this.winX, this.winY, this.winW, this.winH);
            }
            return;
        }
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
        // Code, description, file and line, each in its column, the way the real error list lays them out.
        final int fileX = x + width - COL_LINE_W - COL_FILE_W;
        final int descriptionW = fileX - (x + 3 + COL_CODE_W) - 4;
        g.drawString(ctx.font(), row.complaint().code(), x + 3, y + 1, 0xFFC0392B, false);
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(row.complaint().message(), descriptionW),
                x + 3 + COL_CODE_W, y + 1, ctx.skin().listRowText(selected), false);
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(row.name(), COL_FILE_W - 4), fileX, y + 1,
                ctx.skin().dim(), false);
        g.drawString(ctx.font(), String.valueOf(row.complaint().line()), x + width - COL_LINE_W, y + 1,
                ctx.skin().dim(), false);
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
        this.workspace.say("Build failed");
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
                runListing(doc.path().replaceAll("\\.[^.]+$", "") + ".asm");
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
            runListing(join(projectDir(project.name()), project.entry()));
        }
    }

    /**
     * Runs a listing at the dock's terminal.
     *
     * <p>The terminal is a shell of its own and may have been moved anywhere; the listing is named
     * from the root so it is found wherever the shell stands.
     */
    private void runListing(final String path) {
        runInTerminal(List.of("cd \\", "cannon run \"" + path.replace('/', '\\') + "\""));
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
        runInTerminal(List.of(
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
        this.platformFilter = "";
        this.templateLanguage.setLabel("All languages");
        this.templatePlatform.setLabel("All platforms");
        this.templateKind.setLabel("All types");
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
        this.templateLanguage.setLabel(this.languageFilter.isEmpty() ? "All languages" : this.languageFilter);
    }

    private void cycleKind() {
        this.kindFilter = switch (this.kindFilter) {
            case "" -> "Console";
            case "Console" -> "Script";
            case "Script" -> "Library";
            default -> "";
        };
        this.templateKind.setLabel(this.kindFilter.isEmpty() ? "All types" : this.kindFilter);
    }

    /** The icon a template shows in the wizard: the page its first file would have. */
    private static FileIcons.Kind iconOf(final ProjectTemplate template) {
        return switch (template) {
            case CONSOLE_APP -> FileIcons.Kind.PROGRAM;
            case SCRIPT -> FileIcons.Kind.SOURCE;
            case CLASS_LIBRARY -> FileIcons.Kind.BUNDLE;
            case EMPTY_PROJECT -> FileIcons.Kind.FOLDER;
        };
    }

    private void drawRecent(final GuiGraphics g, final UiContext ctx, final ProjectTemplate template,
                            final int index, final int x, final int y, final int width, final int height,
                            final boolean hovered, final boolean selected) {
        ctx.skin().listRow(g, x, y, width, height, hovered, selected);
        FileIcons.draw(g, x + 2, y + 1, iconOf(template));
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(template.title(), width - 16), x + 14, y + 1,
                ctx.skin().listRowText(selected), false);
        final String kind = template.tags().isEmpty() ? "" : template.tags().getLast();
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth("Cannon  " + kind, width - 16), x + 14, y + 10,
                ctx.skin().dim(), false);
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
            if (!this.platformFilter.isEmpty() && !template.tags().contains(this.platformFilter)) {
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
        FileIcons.draw(g, x + 3, y + 2, iconOf(template));
        final int textX = x + 16;
        final int textW = width - 19;
        g.drawString(ctx.font(), template.title(), textX, y + 1, ctx.skin().listRowText(selected), false);
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(template.description(), textW), textX, y + 9,
                ctx.skin().dim(), false);
        final String tags = String.join("  ", template.tags());
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(tags, textW), textX, y + 17,
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
        this.locationField.set(shownLocation(this.location));
        this.locationField.setEnabled(!this.addingToSolution);
        this.browse.setEnabled(!this.addingToSolution);
        this.solutionName.set(this.addingToSolution ? this.solution.name() : name);
        this.solutionName.setEnabled(!this.addingToSolution);
        this.configure.open();
        this.configure.focus(this.projectName);
    }

    /** The location the way the wizard shows it: a drive letter, backslashes, a trailing one. */
    private static String shownLocation(final String dir) {
        return "C:\\" + dir.replace('/', '\\') + (dir.isEmpty() ? "" : "\\");
    }

    /** The location typed in the wizard, back to a path on the drive: no drive letter, forward slashes. */
    private static String typedLocation(final String shown) {
        String dir = shown.trim().replace('\\', '/');
        if (dir.regionMatches(true, 0, "C:", 0, 2)) {
            dir = dir.substring(2);
        }
        while (dir.startsWith("/")) {
            dir = dir.substring(1);
        }
        while (dir.endsWith("/")) {
            dir = dir.substring(0, dir.length() - 1);
        }
        return dir;
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
                : join(typedLocation(this.locationField.edit()), this.sameFolder || sol.isEmpty() ? project : sol);
        return "Project will be created in " + shownLocation(join(where, project));
    }

    /** Create, from the wizard's second page: what was typed there. */
    private void createProject() {
        final String project = this.projectName.edit().trim();
        final String typedSolution = this.solutionName.edit().trim();
        final String sol = this.addingToSolution ? this.solution.name()
                : (typedSolution.isEmpty() || this.sameFolder ? project : typedSolution);
        if (!this.addingToSolution) {
            this.location = typedLocation(this.locationField.edit());
        }
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
        rememberTemplate(template);
        this.addingToSolution = intoSolution && this.solution != null;
        final String dir = this.addingToSolution ? this.solutionDir : join(this.location, sol);
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
        this.openWhenLoaded.clear();
        if (!first.isEmpty()) {
            this.openWhenLoaded.add(join(join(dir, project), first));
        }
        openSolutionFolder(dir);
    }

    /** A source to open once the solution being read is in, or empty. */
    /** The files to put on tabs once the solution being read is in, and the one to end on. */
    private final List<String> openWhenLoaded = new ArrayList<>();
    private String currentWhenLoaded = "";

    private void layoutTemplates(final Popup p) {
        final List<dev.jstech.core.client.gui.component.UiComponent> c = p.children();
        final int x = p.x() + 4;
        final int top = p.contentTop() + 2;
        final int bottom = p.bottom() - 16;
        // The left pane: what was used lately.
        final int leftW = 96;
        c.get(0).setBounds(x, top, leftW, 9);
        this.recentList.setBounds(x, top + 10, leftW, bottom - top - 10);
        // The right pane: search, the three filters in equal thirds, the list.
        final int rx = x + leftW + 6;
        final int rw = p.right() - 4 - rx;
        int y = top;
        this.templateSearch.setBounds(rx, y, rw, 11);
        y += 13;
        final int third = (rw - 8) / 3;
        this.templateLanguage.setBounds(rx, y, third, 11);
        this.templatePlatform.setBounds(rx + third + 4, y, third, 11);
        this.templateKind.setBounds(rx + 2 * (third + 4), y, rw - 2 * (third + 4), 11);
        y += 13;
        this.templateList.setBounds(rx, y, rw, bottom - y);
        final int by = p.bottom() - 14;
        c.get(c.size() - 1).setBounds(x, by, 40, 11);
        this.templateBack.setBounds(p.right() - 82, by, 36, 11);
        this.templateNext.setBounds(p.right() - 42, by, 38, 11);
    }

    private void layoutConfigure(final Popup p) {
        final int x = p.x() + 4;
        int y = p.contentTop() + 1;
        final int w = p.width() - 8;
        final List<dev.jstech.core.client.gui.component.UiComponent> c = p.children();
        // The template's name and tags, the way the page is headed.
        c.get(0).setBounds(x, y, w, 9);
        y += 9;
        c.get(1).setBounds(x, y, w, 9);
        y += 11;
        final int labelW = 72;
        c.get(2).setBounds(x, y + 1, labelW, 9);
        this.projectName.setBounds(x + labelW + 2, y, w - labelW - 2, 11);
        y += 13;
        c.get(4).setBounds(x, y + 1, labelW, 9);
        this.locationField.setBounds(x + labelW + 2, y, w - labelW - 2 - 22, 11);
        this.browse.setBounds(p.right() - 4 - 20, y, 20, 11);
        y += 13;
        c.get(7).setBounds(x, y + 1, labelW, 9);
        this.solutionName.setBounds(x + labelW + 2, y, w - labelW - 2, 11);
        y += 13;
        c.get(9).setBounds(x, y, w, 9);
        y += 11;
        this.configureNote.setBounds(x, y, w, 9);
        final int by = p.bottom() - 14;
        c.get(11).setBounds(p.right() - 82, by, 36, 11);
        this.create.setBounds(p.right() - 42, by, 38, 11);
    }

    /** The templates used lately on this machine, for the wizard's left pane. */
    private List<ProjectTemplate> recentTemplates() {
        return new ArrayList<>(RECENT_TEMPLATES.getOrDefault(this.host, new ArrayDeque<>()));
    }

    private void pickRecent(final int index) {
        final List<ProjectTemplate> recent = recentTemplates();
        if (index >= 0 && index < recent.size()) {
            this.chosen = recent.get(index);
            openConfigure();
        }
    }

    private void rememberTemplate(final ProjectTemplate template) {
        final Deque<ProjectTemplate> recent = RECENT_TEMPLATES.computeIfAbsent(this.host, h -> new ArrayDeque<>());
        recent.remove(template);
        recent.addFirst(template);
        while (recent.size() > 3) {
            recent.removeLast();
        }
    }

    private void cyclePlatform() {
        final List<String> platforms = ProjectTemplate.PLATFORMS;
        if (this.platformFilter.isEmpty()) {
            this.platformFilter = platforms.isEmpty() ? "" : platforms.get(0);
        } else {
            final int at = platforms.indexOf(this.platformFilter);
            this.platformFilter = at + 1 < platforms.size() ? platforms.get(at + 1) : "";
        }
        this.templatePlatform.setLabel(this.platformFilter.isEmpty() ? "All platforms" : this.platformFilter);
    }

    /** Opens the folder picker for where the new solution goes. */
    private void browseLocation() {
        this.dialog.openFolder("Project Location", typedLocation(this.locationField.edit()), dir -> {
            this.location = dir;
            this.locationField.set(shownLocation(dir));
        });
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
        // A file name opens with the caret before its extension, which is the part that gets typed over.
        final int dot = initial.lastIndexOf('.');
        this.askField.setCaret(dot > 0 ? dot : initial.length());
        this.ask.open();
        this.ask.focus(this.askField);
    }

    private void layoutAsk(final Popup p) {
        this.askField.setBounds(p.x() + 4, p.contentTop() + 3, p.width() - 8, 11);
        this.askOk.setBounds(p.right() - 38, p.bottom() - 15, 34, 11);
    }

    private String closingName() {
        final List<CodeWorkspace.Doc> docs = this.workspace.docs();
        return this.closing >= 0 && this.closing < docs.size() ? docs.get(this.closing).name() : "";
    }

    private void layoutAskClose(final Popup p) {
        final List<dev.jstech.core.client.gui.component.UiComponent> c = p.children();
        final int y = p.bottom() - 15;
        c.get(0).setBounds(p.x() + 4, y, 40, 11);
        c.get(1).setBounds(p.x() + 48, y, 60, 11);
        c.get(2).setBounds(p.right() - 44, y, 40, 11);
    }

    /** Closes a tab, asking first when it has changes the disk does not. */
    private void closeTab(final int index) {
        final List<CodeWorkspace.Doc> docs = this.workspace.docs();
        if (index < 0 || index >= docs.size()) {
            return;
        }
        if (docs.get(index).dirty()) {
            this.closing = index;
            this.askClose.open();
            return;
        }
        this.workspace.close(index);
    }

    /** Types lines at the dock's terminal, one after the other as the machine answers each. */
    private void runInTerminal(final List<String> lines) {
        this.dockTabs.setSelected(DOCK_TERMINAL);
        this.typingInTerminal = true;
        this.typing.addAll(lines);
        if (!this.terminal.busy()) {
            typeNext();
        }
    }

    private void typeNext() {
        final String next = this.typing.poll();
        if (next != null) {
            this.terminal.run(next);
        }
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
                item("Terminal", true, () -> {
                    this.dockTabs.setSelected(DOCK_TERMINAL);
                    this.typingInTerminal = true;
                }),
                item("Assembly", hasSolution() && this.projects.containsKey(currentProject()), this::openAssembly),
                ContextMenu.Item.separator(),
                item("Zoom In", hasDoc(), () -> zoomEditor(1)),
                item("Zoom Out", hasDoc(), () -> zoomEditor(-1)),
                item("Reset Zoom", hasDoc(), () -> setEditorScale(1.0f)),
                ContextMenu.Item.separator(),
                item("Start Window", true, this::closeSolution));
    }

    /**
     * The menu the right button opens on the code: the clipboard, then what the studio can do to the
     * code where the caret is, the way a studio keeps its refactorings a click away from the code.
     */
    private List<ContextMenu.Item> editorMenu() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        final boolean selected = doc != null && doc.area().document().hasSelection();
        return List.of(
                item("Cut", selected, () -> pressInEditor(GLFW.GLFW_KEY_X)),
                item("Copy", hasDoc(), () -> pressInEditor(GLFW.GLFW_KEY_C)),
                item("Paste", hasDoc(), () -> pressInEditor(GLFW.GLFW_KEY_V)),
                item("Select All", hasDoc(), () -> pressInEditor(GLFW.GLFW_KEY_A)),
                ContextMenu.Item.separator(),
                item("Toggle Line Comment", hasDoc(), this::toggleComment),
                ContextMenu.Item.submenu("Quick Actions and Refactorings", List.of(
                        item("Implement Interface", hasDoc(), this::implementInterface))),
                ContextMenu.Item.separator(),
                item("Go To Line...", hasDoc(), this::goToLine));
    }

    /** Sends a Ctrl key to the code area, which is where the clipboard commands live. */
    private void pressInEditor(final int key) {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null) {
            doc.area().keyPressed(key, 0, GLFW.GLFW_MOD_CONTROL);
        }
    }

    /** The size the code is drawn at, shared by every open file, as the status bar shows it. */
    private float editorScale = 1.0f;

    private void zoomEditor(final int steps) {
        setEditorScale(this.editorScale + steps * 0.25f);
    }

    private void setEditorScale(final float value) {
        this.editorScale = Math.max(0.75f, Math.min(2.0f, value));
        for (final CodeWorkspace.Doc doc : this.workspace.docs()) {
            doc.area().setScale(this.editorScale);
        }
    }

    /** The menu the right button opens on the code. */
    private final ContextMenu editorContext = new ContextMenu(110, 11);

    /** The desktop-local centre of the code area, where a test right-clicks the code; null with no file open. */
    public int[] editorCenter() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null) {
            return null;
        }
        final CodeArea area = doc.area();
        return new int[] {area.x() + area.width() / 2, area.y() + area.height() / 2};
    }

    /** Whether the right-button menu on the code is up. */
    public boolean editorMenuOpen() {
        return this.editorContext.isOpen();
    }

    /** Escape closes whatever menu or window is up before it means anything to the desktop. */
    @Override
    public boolean wantsEscape() {
        return this.editorContext.isOpen() || this.treeContext.isOpen() || this.menuBar.isOpen()
                || this.palette.isOpen() || openPopup() != null;
    }

    /** The labels of the code's right-button menu, so a test can read what it offers. */
    public List<String> editorMenuLabels() {
        final List<String> out = new ArrayList<>();
        for (final ContextMenu.Item entry : this.editorContext.items()) {
            out.add(entry.label());
        }
        return out;
    }

    /** The size the code is drawn at, as the status bar shows it. */
    public int zoomPercent() {
        return Math.round(this.editorScale * 100);
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
                item("Stop", this.terminal.busy(), this.terminal::interrupt));
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

    /** The kinds the Open Project/Solution window offers: what the studio opens as a solution, then everything. */
    private static final List<FileDialog.Filter> SOLUTION_FILTERS = List.of(
            FileDialog.Filter.of("Solutions", SolutionFile.EXTENSION, ProjectFile.EXTENSION),
            FileDialog.Filter.ALL);

    private void openSolutionByPicker() {
        this.dialog.openFile("Open Project/Solution", LOCATION, SOLUTION_FILTERS, this::openFile);
    }

    private void openFolderByPicker() {
        this.dialog.openFolder("Open Folder", LOCATION, this::openFolder);
    }

    /** Shows the Open Project/Solution window, as the File menu does; a test drives it from here. */
    public void showOpenSolutionDialog() {
        openSolutionByPicker();
    }

    /** The system's file window this studio opens, for a test to drive. */
    public FileDialog dialog() {
        return this.dialog;
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
        addNewItem(currentProject());
    }

    private void addNewItem(final String name) {
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
        addExistingItem(currentProject());
    }

    private void addExistingItem(final String name) {
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
        addReference(currentProject());
    }

    private void addReference(final String name) {
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
        setStartup(currentProject());
    }

    private void setStartup(final String name) {
        if (this.solution != null && this.projects.containsKey(name)) {
            this.solution = this.solution.withStartup(name);
            saveSolution();
        }
    }

    /* The tree's right-button menu */

    /** What the right button offers on a row of the Solution Explorer, by what the row is. */
    private List<ContextMenu.Item> treeMenu(final Node node) {
        final boolean several = this.projects.size() > 1;
        return switch (node.kind()) {
            case SOLUTION -> this.solution == null
                    ? List.of(item("Refresh", true, this.workspace::refresh))
                    : List.of(item("Build Solution", true, this::buildSolution),
                            item("Clean Solution", true, this::cleanSolution),
                            ContextMenu.Item.separator(),
                            item("Add New Project...", true, () -> newProject(true)));
            case PROJECT -> List.of(
                    item("Build", true, () -> buildProject(node.project())),
                    item("Set as Startup Project", true, () -> setStartup(node.project())),
                    ContextMenu.Item.separator(),
                    item("Add New Item...", true, () -> addNewItem(node.project())),
                    item("Add Existing Item...", true, () -> addExistingItem(node.project())),
                    item("Add Project Reference...", several, () -> addReference(node.project())),
                    ContextMenu.Item.separator(),
                    item("Properties", true, () -> showProperties(node.project())));
            case SOURCE -> List.of(
                    item("Open", true, () -> this.workspace.open(node.path())),
                    ContextMenu.Item.separator(),
                    item("Exclude From Project", true, () -> excludeSource(node)),
                    item("Delete", true, () -> askDelete(node)));
            case OUTPUT -> List.of(
                    item("Open", true, () -> this.workspace.open(node.path())),
                    ContextMenu.Item.separator(),
                    item("Delete", true, () -> askDelete(node)));
            case FOLDER_FILE -> {
                final DiskFilesPayload.WireFile file = fileAt(node.path());
                yield file == null || file.directory()
                        ? List.of(item("Open", true, () -> this.workspace.toggleFolder(node.path())))
                        : List.of(item("Open", true, () -> this.workspace.open(node.path())),
                                ContextMenu.Item.separator(),
                                item("Delete", true, () -> askDelete(node)));
            }
            default -> List.of();
        };
    }

    /** Takes the source out of its project's file, leaving the file itself on the disk. */
    private void excludeSource(final Node node) {
        final ProjectFile project = this.projects.get(node.project());
        if (project != null) {
            saveProject(project.withoutSource(relativeSource(project, node.path())));
        }
    }

    /** A source's name as the project file lists it: its path inside the project's folder. */
    private String relativeSource(final ProjectFile project, final String path) {
        final String prefix = projectDir(project.name()) + "/";
        return path.startsWith(prefix) ? path.substring(prefix.length()) : shortName(path);
    }

    /** Asks before a file goes: deleting is the one thing the tree does that cannot be undone. */
    private void askDelete(final Node node) {
        this.deleting = node;
        this.askDelete.open();
    }

    private String deletingName() {
        return this.deleting == null ? "" : shortName(this.deleting.path());
    }

    private void layoutAskDelete(final Popup p) {
        final List<dev.jstech.core.client.gui.component.UiComponent> c = p.children();
        final int y = p.bottom() - 15;
        c.get(0).setBounds(p.x() + 4, y, 44, 11);
        c.get(1).setBounds(p.right() - 44, y, 40, 11);
    }

    /**
     * Deletes the file the question was about: off the disk, off its tab if it was open, and out of
     * its project's file when the project listed it.
     */
    private void deleteNode() {
        final Node node = this.deleting;
        this.deleting = null;
        if (node == null || node.path().isEmpty()) {
            return;
        }
        final List<CodeWorkspace.Doc> docs = this.workspace.docs();
        for (int i = 0; i < docs.size(); i++) {
            if (docs.get(i).path().equals(node.path())) {
                this.workspace.close(i);
                break;
            }
        }
        PacketDistributor.sendToServer(new DeleteFilePayload(this.host, node.path()));
        final ProjectFile project = this.projects.get(node.project());
        if (node.kind() == NodeKind.SOURCE && project != null) {
            saveProject(project.withoutSource(relativeSource(project, node.path())));
        }
        this.output.add("Deleted " + shortName(node.path()));
        FilesApps.diskChanged();
        this.workspace.refresh();
    }

    /** The labels of the tree's rows, top to bottom, so a test can find one. */
    public List<String> explorerLabels() {
        final List<String> out = new ArrayList<>();
        for (final Node node : nodes()) {
            out.add(node.label());
        }
        return out;
    }

    /** The middle of the tree row with that label, window-relative, or null when there is none. */
    public int[] explorerRowPoint(final String label) {
        final int index = explorerLabels().indexOf(label);
        return index < 0 ? null : this.explorer.rowCenter(index);
    }

    /** Whether the tree's right-button menu is up. */
    public boolean treeMenuOpen() {
        return this.treeContext.isOpen();
    }

    /** The labels of the tree's right-button menu, so a test can read what it offers. */
    public List<String> treeMenuLabels() {
        final List<String> out = new ArrayList<>();
        for (final ContextMenu.Item entry : this.treeContext.items()) {
            out.add(entry.label());
        }
        return out;
    }

    /** The middle of the tree menu's entry with that label, or null. */
    public int[] treeMenuPoint(final String label) {
        final int index = treeMenuLabels().indexOf(label);
        return index < 0 ? null : this.treeContext.itemCenter(index);
    }

    /** Whether the question before a delete is up. */
    public boolean deleteQuestionOpen() {
        return this.askDelete.isOpen();
    }

    /** Whether a question asking for a name is up. */
    public boolean askOpen() {
        return this.ask.isOpen();
    }

    /** Answers the name question up with {@code name}, as typing it and pressing OK does. */
    public void answerAsk(final String name) {
        if (this.ask.isOpen()) {
            this.askField.set(name);
            this.ask.close();
            this.askAction.accept(name.trim());
        }
    }

    /** Answers the question before a delete, as its Delete button does. */
    public void confirmDelete() {
        if (this.askDelete.isOpen()) {
            this.askDelete.close();
            deleteNode();
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

    /** Writes the methods the class under the caret promised its interface and left out. */
    private void implementInterface() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null) {
            this.workspace.say(this.workspace.implementInterface(doc) ? "Interface implemented"
                    : "Nothing to implement here");
        }
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
        this.terminal.setSkin(osSkin);
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
        this.dialog.release();
        this.terminal.release();
        CodeFileReplies.forget(this);
    }

    /** What the dock's terminal has printed, one line after another. */
    public String terminalText() {
        return this.terminal.scrollbackText();
    }

    @Override
    public boolean modalActive() {
        // The file window is a window of its own over this one; the desktop holds this one while it is up.
        return this.templates.isOpen() || this.configure.isOpen() || this.ask.isOpen()
                || this.properties.isOpen() || this.options.isOpen() || this.askClose.isOpen()
                || this.askDelete.isOpen();
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
        this.winX = x;
        this.winY = y;
        this.winW = width;
        this.winH = height;
        final int top = y + MenuBar.HEIGHT;

        final CodeWorkspace.Doc doc = this.workspace.current();
        // What the Start Window does not show is hidden outright: a list with no room still has rows to draw.
        final boolean start = this.page == Page.START;
        for (final dev.jstech.core.client.gui.component.UiComponent part
                : List.of(this.start, this.explorer, this.tabs, this.dockTabs, this.errors, this.outputList,
                        this.terminal)) {
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
            // The dividers hold their places between frames, within what the window can afford.
            this.sideW = Math.max(60, Math.min(width - 120, this.sideW));
            final int codeW = width - this.sideW;
            final int sideX = x + codeW;
            this.skin.panel(g, sideX, bodyY, this.sideW, bodyH);
            g.drawString(font, "SOLUTION EXPLORER", sideX + 3, bodyY + 1, this.skin.dim(), false);
            this.explorer.setBounds(sideX, bodyY + CAPTION_H, this.sideW, bodyH - CAPTION_H);
            this.skin.panel(g, codeX, bodyY, codeW, TAB_H);
            this.tabs.setBounds(codeX, bodyY, codeW, TAB_H);
            this.tabs.setSelected(this.workspace.currentIndex());
            final int paneY = bodyY + TAB_H;
            final int paneH = bodyH - TAB_H;
            this.dockH = Math.max(TAB_H + ROW_H * 2, Math.min(paneH - MIN_CODE_H, this.dockH));
            final boolean dockShown = paneH - this.dockH >= MIN_CODE_H;
            final int codeH = dockShown ? paneH - this.dockH : paneH;
            if (doc != null) {
                doc.area().setBounds(codeX, paneY, codeW, codeH);
            }
            layoutDock(codeX, paneY + codeH, codeW, dockShown ? this.dockH : 0);
            if (dockShown && this.dockTabs.selected() == DOCK_ERRORS) {
                drawErrorHeader(g, font, codeX, paneY + codeH + TAB_H, codeW);
            }
            if (doc == null) {
                drawEmpty(g, font, codeX, paneY, codeW, codeH);
            }
            this.dividerX = sideX;
            this.dividerY = paneY + codeH;
            this.bodyTop = bodyY;
            this.bodyBottom = bodyY + bodyH;
        }
        this.root.render(g, ctx);
        if (doc != null && this.page == Page.SOLUTION) {
            doc.area().render(g, ctx);
        }
        drawStatus(g, font, x, y + height - STATUS_H, width, doc);
        this.completions.render(g, ctx);
        this.palette.render(g, ctx, x, top, width);
        for (final Popup popup : List.of(this.templates, this.configure, this.ask, this.properties, this.options, this.askClose, this.askDelete)) {
            if (popup.isOpen()) {
                popup.renderIn(g, ctx, x, y, width, height);
            }
        }
        this.editorContext.render(g, ctx);
        this.treeContext.render(g, ctx);
        this.menuBar.render(g, ctx);
    }

    private void layoutDock(final int x, final int y, final int width, final int height) {
        final boolean shown = height > 0;
        final int tab = this.dockTabs.selected();
        this.dockTabs.setVisible(shown);
        this.errors.setVisible(shown && tab == DOCK_ERRORS);
        this.outputList.setVisible(shown && tab == DOCK_OUTPUT);
        this.terminal.setVisible(shown && tab == DOCK_TERMINAL);
        this.dockTabs.setBounds(x, y, width, TAB_H);
        // The error list sits under its column headings; the others fill the dock.
        this.errors.setBounds(x, y + TAB_H + CAPTION_H, width, Math.max(0, height - TAB_H - CAPTION_H));
        this.outputList.setBounds(x, y + TAB_H, width, Math.max(0, height - TAB_H));
        this.terminal.setBounds(x, y + TAB_H, width, Math.max(0, height - TAB_H));
    }

    /* The Error List's columns: where each starts, as a share of the dock's width. */
    private static final int COL_CODE_W = 34;
    private static final int COL_FILE_W = 58;
    private static final int COL_LINE_W = 22;

    /** The headings over the Error List, in the columns the rows use. */
    private void drawErrorHeader(final GuiGraphics g, final Font font, final int x, final int y, final int width) {
        this.skin.panel(g, x, y, width, CAPTION_H);
        final int fileX = x + width - COL_LINE_W - COL_FILE_W;
        g.drawString(font, "Code", x + 3, y + 1, this.skin.dim(), false);
        g.drawString(font, "Description", x + 3 + COL_CODE_W, y + 1, this.skin.dim(), false);
        g.drawString(font, "File", fileX, y + 1, this.skin.dim(), false);
        g.drawString(font, "Line", x + width - COL_LINE_W, y + 1, this.skin.dim(), false);
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
        this.dialog.openFile("Open File", LOCATION, FileDialog.Filter.sources(), this::openFile);
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
        /*
         * The right-hand pieces are laid out first, so the message on the left is cut to the room
         * left before them: a saved path can be longer than the bar, and it stops rather than runs
         * under the zoom.
         */
        final int right = drawStatusRight(g, font, x, y, width, doc);
        if (!this.workspace.status().isEmpty()) {
            final int from = x + 5 + font.width(left) + 6;
            final int room = right - 6 - from;
            if (room > 8) {
                g.drawString(font, font.plainSubstrByWidth(this.workspace.status(), room), from, y + 1,
                        this.skin.dim(), false);
            }
        }
    }

    /** Draws what the status bar keeps on its right, and says where that begins. */
    private int drawStatusRight(final GuiGraphics g, final Font font, final int x, final int y,
                                final int width, final CodeWorkspace.Doc doc) {
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
            right -= 8;
            /*
             * The zoom, the way the studio keeps it at the bottom of the editor: the size in percent
             * with a minus and a plus either side, so a bigger or smaller text is one click away.
             */
            final String percent = Math.round(this.editorScale * 100) + "%";
            final int plusX = right - font.width("+") - 1;
            final int percentX = plusX - 3 - font.width(percent);
            final int minusX = percentX - 3 - font.width("-");
            g.drawString(font, "-", minusX, y + 1, this.skin.text(), false);
            g.drawString(font, percent, percentX, y + 1, this.skin.dim(), false);
            g.drawString(font, "+", plusX, y + 1, this.skin.text(), false);
            this.zoomMinus = new int[] {minusX - 2, y, font.width("-") + 4, STATUS_H};
            this.zoomPlus = new int[] {plusX - 2, y, font.width("+") + 4, STATUS_H};
            right = minusX - 2;
        } else {
            this.zoomMinus = null;
            this.zoomPlus = null;
        }
        return right;
    }

    /* Where the status bar's zoom minus and plus were drawn, so a click on them is known for what it is. */
    private int[] zoomMinus;
    private int[] zoomPlus;

    private boolean clickZoomControl(final double mx, final double my) {
        if (inRect(this.zoomMinus, mx, my)) {
            zoomEditor(-1);
            return true;
        }
        if (inRect(this.zoomPlus, mx, my)) {
            zoomEditor(1);
            return true;
        }
        return false;
    }

    private static boolean inRect(final int[] rect, final double mx, final double my) {
        return rect != null && mx >= rect[0] && mx < rect[0] + rect[2] && my >= rect[1] && my < rect[1] + rect[3];
    }

    /* Input */

    private Popup openPopup() {
        for (final Popup popup : List.of(this.templates, this.configure, this.ask, this.properties, this.options, this.askClose, this.askDelete)) {
            if (popup.isOpen()) {
                return popup;
            }
        }
        return null;
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        final Popup popup = openPopup();
        if (popup != null) {
            popup.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (this.palette.isOpen()) {
            this.palette.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (this.editorContext.isOpen()) {
            this.editorContext.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (this.treeContext.isOpen()) {
            this.treeContext.mouseClicked(mouseX, mouseY, button);
            return;
        }
        final CodeWorkspace.Doc onCode = this.workspace.current();
        if (button == 1 && onCode != null && this.page == Page.SOLUTION && onCode.area().contains(mouseX, mouseY)) {
            this.editorContext.open(editorMenu(), (int) mouseX, (int) mouseY, window.x(), window.y(),
                    window.width(), window.height());
            return;
        }
        if (clickZoomControl(mouseX, mouseY)) {
            return;
        }
        // An open menu gets the click first: on one of its items, on another title, or outside to close.
        if (this.menuBar.mouseClicked(mouseX, mouseY, button)) {
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
        if (this.page == Page.SOLUTION) {
            // A click on a divider takes hold of it; the drag that follows moves it.
            if (Math.abs(mouseX - this.dividerX) <= GRIP && mouseY >= this.bodyTop && mouseY < this.bodyBottom) {
                this.holding = 1;
                return;
            }
            if (Math.abs(mouseY - this.dividerY) <= GRIP && mouseX < this.dividerX && this.dockTabs.visible()) {
                this.holding = 2;
                return;
            }
        }
        if (this.terminal.visible() && this.terminal.contains(mouseX, mouseY)) {
            this.typingInTerminal = true;
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null && this.page == Page.SOLUTION && doc.area().contains(mouseX, mouseY)) {
            this.typingInTerminal = false;
            doc.area().mouseClicked(mouseX, mouseY, button);
            return;
        }
        this.root.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseDragged(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (this.holding == 1) {
            this.sideW = (int) (this.dividerX + this.sideW - mouseX);
            return;
        }
        if (this.holding == 2) {
            this.dockH = (int) (this.dividerY + this.dockH - mouseY);
            return;
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null && this.page == Page.SOLUTION && !modalActive()) {
            doc.area().mouseDragged(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseReleased(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        this.holding = 0;
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null) {
            doc.area().mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public boolean charTyped(final char c) {
        final Popup popup = openPopup();
        if (popup != null) {
            return popup.charTyped(c);
        }
        if (this.palette.isOpen()) {
            return this.palette.charTyped(c);
        }
        if (this.typingInTerminal && this.terminal.visible()) {
            return this.terminal.charTyped(c);
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
        final Popup popup = openPopup();
        if (popup != null) {
            return popup.keyPressed(key, scanCode, modifiers);
        }
        if (this.palette.isOpen()) {
            return this.palette.keyPressed(key, scanCode, modifiers);
        }
        if (this.editorContext.isOpen()) {
            return this.editorContext.keyPressed(key, scanCode, modifiers);
        }
        if (this.treeContext.isOpen()) {
            return this.treeContext.keyPressed(key, scanCode, modifiers);
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
        if (ctrl && key == GLFW.GLFW_KEY_W) {
            closeTab(this.workspace.currentIndex());
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_PERIOD) {
            implementInterface();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_GRAVE_ACCENT) {
            this.dockTabs.setSelected(DOCK_TERMINAL);
            this.typingInTerminal = true;
            return true;
        }
        if (this.typingInTerminal && this.terminal.visible()) {
            return this.terminal.keyPressed(key, scanCode, modifiers);
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
        this.completions.offer(area, doc.path(), sourcesAround(doc),
                new int[] {area.x(), area.y(), area.width(), area.height()});
    }

    /**
     * The sources a completion list reads beside the open file: the rest of its folder, and the sources
     * of every library its project references, so a type a library declares is offered like the file's
     * own. A library folder nobody has read yet is asked for, and is there the next time the list opens.
     */
    private List<IProgrammingLanguage.SourceText> sourcesAround(final CodeWorkspace.Doc doc) {
        final List<IProgrammingLanguage.SourceText> out = new ArrayList<>(this.workspace.siblingsOf(doc));
        final String own = projectOf(doc.path());
        if (own == null) {
            return out;
        }
        final Deque<String> order = new ArrayDeque<>();
        collectOrder(own, order, new LinkedHashSet<>());
        for (final String each : order) {
            if (each.equals(own)) {
                continue;
            }
            this.workspace.ensureSurveyed(projectDir(each));
            for (final String source : this.projects.get(each).sources()) {
                final String text = this.workspace.textOf(join(projectDir(each), source));
                if (text != null) {
                    out.add(new IProgrammingLanguage.SourceText(each + "/" + source, text));
                }
            }
        }
        return out;
    }

    /** The project whose folder holds {@code path}, or null when it is in none of the solution's. */
    private String projectOf(final String path) {
        for (final String name : this.projects.keySet()) {
            if (path.startsWith(projectDir(name) + "/")) {
                return name;
            }
        }
        return null;
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
        if (this.page != Page.SOLUTION) {
            return;
        }
        // A row of the Error List shows the whole of what was said, which the column cannot always fit.
        if (this.errors.visible() && this.errors.contains(mouseX, mouseY)) {
            final int index = this.errors.rowAt(mouseX, mouseY);
            final List<ProblemReport.Row> rows = errorRows();
            if (index >= 0 && index < rows.size()) {
                final ProblemReport.Row row = rows.get(index);
                g.renderTooltip(font, Component.literal(row.complaint().code() + ": " + row.complaint().message()
                        + "  (" + row.name() + ", line " + row.complaint().line() + ")"), mouseX, mouseY);
                return;
            }
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null) {
            return;
        }
        final String message = doc.area().messageAt(mouseX, mouseY);
        if (!message.isEmpty()) {
            g.renderTooltip(font, Component.literal(message), mouseX, mouseY);
        }
    }
}
