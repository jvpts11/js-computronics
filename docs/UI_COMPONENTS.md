# UI components

The programs that run on a desktop, and the dialogs a screen opens, are built from the components in
`dev.jstech.core.client.gui.component`. A component keeps its own state (what is typed in a field, which
tab is selected, how far a list is scrolled), draws itself through the desktop's skin, and reports what
the player did to it through a callback. A program composes components instead of painting pixels and
hit-testing rectangles by hand, and it looks like whichever desktop it runs on, because the skin does the
painting.

## The model

Components are retained, layout is immediate. A program creates its components once, keeps them in a
`Panel`, and on every frame places them from the window's current size with `setBounds`, then renders the
panel. Input arrives in the same coordinates the bounds were given in, so nothing is translated twice.

```java
private final Panel root = new Panel();
private final TabStrip tabs = root.add(new TabStrip(List.of("Files", "Encoder")).setOnSelect(this::selectTab));
private final ListView<Row> list = root.add(new ListView<>(this::rows, ROW_H, this::renderRow).setOnClick(this::rowClicked));

void renderContent(GuiGraphics g, Font font, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick) {
    tabs.setBounds(x, y, width, TAB_H);
    list.setBounds(x, y + TAB_H, width, height - TAB_H);
    root.render(g, new UiContext(skin, font, mouseX, mouseY, partialTick));
}

void mouseClicked(DesktopWindow window, double mouseX, double mouseY, int button) {
    root.mouseClicked(mouseX, mouseY, button);
}
```

The panel routes a click to the frontmost child under the cursor, hands the keyboard to a focusable child
that took a click (a field, an editor, a command line) and takes it away from whoever had it, sends keys to
the focused child, drags and releases to the child that took the press, and the wheel to the child under
the cursor. A component that took a click returns `true`; a panel returns `false` when no child did, which
is how a program knows a click landed on empty space.

Every drawing call goes through the `Skin` interface: the colours (text, dim, accent, edge, backgrounds) and
the primitives (panel, button, field, tab, list row, scroll thumb, status bar, window frame). J's
Computers implements it once per operating system, so a button on Frames 95 has a bevel and the same
button on Frames 11 is flat, with no change to the program.

## The components

| Component | What it is |
|---|---|
| `Panel` | The container: children in z-order, focus routing, pressed child. |
| `ScrollPanel` | A panel whose content is taller than it: wheel scrolling, a thumb, clipping. |
| `Popup` | A modal dialog centred in a rectangle: title, layouter for its children, dims what is behind, Escape and an outside click close it. |
| `Label` | Text with a tone (text, dim, accent) or a colour, an alignment, a scale; clipped to its width. |
| `Button` | Fires on press; primary or plain; can be disabled. |
| `Checkbox` | A box with a label, read and toggled through callbacks. |
| `TextField` | One line of text: shows the committed value, edits on focus, commits on blur, Escape reverts; a placeholder, a suffix that is not edited. |
| `SearchField` | A field whose text filters something as it is typed. |
| `TextArea` | Multi-line text with a caret: Enter and Backspace split and join lines, the arrows and a click move the caret. |
| `CommandLine` | A terminal line: a prompt, Enter submits, the arrows recall history, an idle line shows the last output. |
| `TabStrip` | Tabs of equal width or fitted to their labels. |
| `ListView<T>` | A scrolling list of rows of one height, drawn by a renderer from a live supplier; a click reports the row or the empty space. |
| `ColumnHeader` | The header over a table: sortable columns with the direction, or fixed names. |
| `CellGrid` | A grid of cells: inventory wells or plain tiles, square or rectangular, scrolled by rows, with per-cell tooltips. |
| `ContextMenu` | The menu a right-click opens at the cursor, kept inside a rectangle, closed by any click. |
| `Breadcrumbs` | An address as a trail of crumbs, each a navigation target. |
| `ScrollBar` | A thin bar beside a list, driven by a maximum, a value and a callback. |
| `ProgressBar` | A bar filled by a percentage. |
| `AmountStepper` | A number with halve, double, minus and plus. |

Helpers: `FlowLayout` places components in a row or a column; `Texts` clips, trims and scales strings;
`Draw` outlines, fades a disabled control and clips to a rectangle under the current pose. The pure logic
behind the fields and the editor (`TextEditState`, `TextDocument`, `StepperMath`, `ScrollState`) lives in
`dev.jstech.core.client.gui.logic` and is covered by unit tests.

## Writing a program

- Create the components in the constructor, add them to the panel in the order they should stack (later
  children are in front), and keep a field for each one you lay out or read.
- Lay everything out in `renderContent` from the content rectangle, every frame; hide what the current
  state has no use for with `setVisible`.
- A program with a dialog overrides `modalActive` and `renderModal`, renders the open popup there with
  `renderIn`, and calls `placeIn` right after `open()`, so a click that arrives before the next frame
  already finds the dialog's controls in place.
- Give the client tests something to click: a method that returns a component's `center()` in the content's
  coordinates, so a test never hard-codes a position.
- Ship a new component with a real consumer. A component nothing uses is not finished.
