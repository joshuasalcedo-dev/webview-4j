# The Java UI is not written like Swing yet

**Labels:** `api` `ui` `breaking-change`  ·  **Found by:** writing the component explorer (`explorer/`), 2026-11-27

## The goal

A webview-4j UI is written **like Java Swing**: components are objects you make (`new Sidebar()`), keep in fields,
put in containers (`sidebar.add(group)`), listen to (`button.addActionListener(e -> ...)`), and read and change
through typed getters and setters (`select.getSelectedItem()`, `tabs.setSelectedItem(Page.CODE)`). The app starts
from `WebviewApplication`, and a `Window` holds the components. Nothing is a hard-coded string, a `Map<String,
String>`, or a whole tree rebuilt from factories on every change.

```java
public final class Explorer extends WebviewApplication {
    @Override public void start(App app) { new ExplorerWindow().open(app); }
    public static void main(String[] args) { launch(args); }
}

final class ExplorerWindow extends Window {
    private final Sidebar sidebar = new Sidebar();
    private final SidebarGroup components = new SidebarGroup("Components");
    private final Tabs<Page> tabs = new Tabs<>(Page.values(), Page::title);   // typed, no "preview"/"code" strings

    ExplorerWindow() {
        super("Components");
        for (Category category : Category.values()) {                       // an enum, not package-name strings
            SidebarMenuButton heading = new SidebarMenuButton(category.title());
            for (ComponentDoc doc : category.components()) {
                SidebarMenuButton item = new SidebarMenuButton(doc.name());
                item.addActionListener(e -> show(doc));
                heading.add(item);
            }
            components.add(heading);
        }
        sidebar.add(components);
        tabs.addChangeListener(e -> showPage(e.getNewValue()));
        setContentPane(new BorderPane(page).left(sidebar));
    }
}
```

**None of that compiles today.** The explorer had to fall back to strings, maps and a `render()` that rebuilds
everything, which is exactly what this API is meant to avoid.

## What is missing, in general

1. **Containers cannot be added to.** `Component.add(...)` is `protected`, so every container that is not a `Pane`
   takes its children only in its constructor or a builder `with(...)`: `Sidebar`, `SidebarGroup`, `SidebarSection`,
   `ListView`, `Surface`, `Toolbar`, `StatusBar`, `TitleBar`, `Dialog`, `Accordion`, `Carousel`, `Inspector`,
   `InspectorSection`, `InfiniteScroll`, `TitledPane`, `MdiWindow`, `Settings`, `DocumentViewerSidebar`.
   `SidebarMenuButton` takes its sub-items only through `items(...)`.
   *Wanted:* public `add(Component...)`, `add(Component, int)`, `remove(Component)`, `removeAll()`,
   `getComponents()` on every container, as `Pane` already has.
2. **Events are lambdas, not listeners.** 66 components have only `onX(Runnable / Consumer)`, which *replaces* the
   one handler. Swing has `addActionListener` / `addChangeListener` / `addListSelectionListener`: as many as you
   want, removable, with an event that says what happened and to whom (`getSource()`). Only `ActionEvent` and
   `ChangeEvent` exist in `ui.event`. *Wanted:* `add/remove*Listener` everywhere an `on*` is. Add the event types they
   need: selection (lists, tables, trees, files), open/close (popover, sheet, dialog, inspector), expansion
   (accordion, titled pane, tree), paging, sorting, and editing. The `on*` builders can stay as shorthands.
3. **State cannot be read back.** Components that change as the user acts have no getters: which sidebar item is
   selected, which tab is open, what a `CodeEditor`, `RichTextEditor`, `TokenField` or `Composer` holds, a table's
   selection, sort, filter or page, whether a `Sidebar` is collapsed, a `Carousel`'s index, a `SplitPane`'s divider
   positions. *Wanted:* typed `get*` / `is*` / `set*` for all of it, which also redraw.
4. **Choices are strings and maps, not types.** `Select(String, Map<String, String>)`, `Segmented(String,
   Map<String, String>)`, `Tabs.tab(String value, String label)` and `value(String)`, and
   `RadioGroup` / `Combobox` keyed by `String`. `CountrySelect` / `CurrencySelect` / `LanguageSelect` take
   `options(Map<String, String>)`. *Wanted (decided: replace the map versions):* `Select<T>`,
   `Segmented<T>`, `Tabs<T>`, `RadioGroup<T>` and `Combobox<T>`, each with typed items and a label function
   (`new Select<>(Tone.values(), Tone::label)`, or a `ListModel<T>` / `ComboBoxModel<T>` as in Swing). They would
   have `getSelectedItem()`, `setSelectedItem(T)` and `ChangeListener<T>`. The value objects' selects take `Country`,
   `Currency` and `Language`.
5. **Other stringly-typed values**, each wanting its type or an enum:
   - `Calendar.value/min/max/month(String)`: `LocalDate` / `YearMonth`.
   - `Dialog.kind(String)`, `MessageBubble.state(String)` (a `DeliveryState` enum exists), `ChatHeader.status(String)`,
     `FileChip.status(String)`, `Swipeable.left/right(String tone)` (a `Tone` enum exists).
   - `CodeBlock(code, String language)`, `CodeEditor.language(String)`, `Ui.code(String, String language)`,
     `MediaPlayer.captions(String language)`: a `CodeLanguage` enum (or `Locale` for captions).
   - `EmptyState.icon(String)` and `CommandPalette.command(..., String icon)`: no `Icon` overload, as `Button` and
     the others now have.
   - `Frame.accent(String color)`: a color type.
   - `HoverCard` / `Popover(String trigger)`: fine as a label, but keep the `Component` trigger first-class.
6. **The sidebar is the worst hit.** `Sidebar`, `SidebarGroup`, `SidebarSection`, `SidebarItem` and
   `SidebarMenuButton` have no `add`/`remove`, no listeners (`onClick` / `onSelect` only), no getters, and no
   selection model. *Wanted:*
   - `Sidebar.add(SidebarGroup)`.
   - `SidebarGroup.add(SidebarMenuButton)`.
   - `SidebarMenuButton.add(SidebarMenuButton)` for sub-items, `addActionListener`, `isSelected()` / `setSelected()`,
     and `getText()` / `setText()`.
   - One selection across the sidebar (a `SidebarSelectionModel`, or a `ButtonGroup`-like group), so picking an
     item deselects the last one without the app tracking it.
   - `SidebarInput.getText()` and `addChangeListener`.
   - `Sidebar.isCollapsed()` / `setCollapsed()`.
7. **Components made of components are one opaque node in Java.** In React the kit reuses its own components. In Java
   the same component is a single node with props, and its parts are not Java components: you cannot listen to them,
   style them, read them, or swap one.

   | Java class | One node in Java (`super(...)` + props) | Made in React of |
   |---|---|---|
   | `SignInForm` | `signInForm`; its providers are `Map.of("id", …, "label", …)` | `TextField`, `PasswordField`, `Checkbox`, `Button` |
   | `SessionExpired` | `sessionExpired` | `Dialog` |
   | `AccountMenu` | `accountMenu` | `Avatar`, the context menu |
   | `UpdatePrompt` | `updatePrompt` | `Button`, `ProgressBar`, `Changelog` |
   | `Changelog` | `changelog` | `Markdown` |
   | `DataTable` | `dataTable` | `SearchField`, `Checkbox`, `Button`, `Pill`, `Money`, the context menu |
   | `DataList` | `dataList` | `SearchField`, `List`/`ListItem`, `Avatar`, `Pill`, `InfiniteScroll` |
   | `DataGrid` | `dataGrid` | `Avatar`, `Pill` |
   | `DataMap` | `dataMap` | `Button` |
   | `DataTree` | `dataTree` | `Checkbox`, `Spinner` |
   | `EmptyState` / `Pages` (collection states) | one node each | `Button` |
   | `CountrySelect`, `CurrencySelect`, `LanguageSelect`, `PhoneField`, `MoneyField`… (value fields) | one node each | `Select`, `TextField`, `NumberField` |
   | `FileExplorer` / `FileList` / `FileGrid` | one node each | `Breadcrumb`, `FileTree`, `FileIcon` |
   | `ConversationList`, `ChatHeader`, chat details | one node each | `Avatar`, `Pill`, `PillGroup`, `FileChip`, `Inspector`, `Segmented` |

   Only `plugin.settings.Settings` is built the Swing way, from Java components (`new Button(...)`, `new
   TextField(...)`).

   *Wanted:* a composite exposes its parts as the Java components they are, the way a Swing `JFileChooser` or
   `JOptionPane` is made of `JButton`s and `JTextField`s you can reach:
   - `signInForm.getUsernameField()` returns a `TextField`, `getSubmitButton()` a `Button`, and
     `getRememberBox()` a `Checkbox`, with their own listeners and styles.
   - `dataTable.getSearchField()` returns a `SearchField`.
   - `SessionExpired` *is* a `Dialog` (extends it, or holds one).
   - Providers, entries and columns are typed objects (`new SignInProvider(Provider.GOOGLE)`), not maps.

   Two ways, to decide: **(a)** compose in Java: the composite is a container that adds real Java children, as
   `Settings` does, and the React side draws them. **(b)** Keep one React component, but give Java a typed handle to
   each named part, through the generator: listeners, getters, setters and style parts of the inner component,
   routed to that part. (a) is Swing's way and needs the parts to stand alone. (b) keeps the kit's layout in React.

## Every component, as audited

Made by reading each component's public methods (core, `ui.components*` and `plugin.*`, not the generated `Html.*`
elements). 149 components; 49 have no gap. A blank cell means no gap there. "only `onX`" means it has that builder but
no `add*Listener`.

| Area | Component | Listeners (`on*` only, no `add*Listener`) | Public `add`/`remove` | Getters | Strings / maps instead of types |
|---|---|---|---|---|---|
| plugin.plugin | `AccountMenu` | only onSignOut, onSwitch |  | missing |  |
| plugin.plugin | `BoundingBoxCitations` | only onActiveChange, onExpectedChange |  | missing | `expected(Object expected)`, `of(Object value)` |
| plugin.plugin | `Changelog` | only onLink |  | missing |  |
| plugin.plugin | `ChatComposer` | only onCancelEdit, onCancelReply, onEditLast, onSend, onTyping |  | missing |  |
| plugin.plugin | `ChatHeader` | only onCall, onInfo, onOpenInfo, onSearch, onVideoCall |  | missing | `status(String status)` |
| plugin.plugin | `CodeField` | only onChange, onComplete |  | missing |  |
| plugin.plugin | `CollectionView` | only onQueryChange, onRetry, onSelectionChange |  | missing |  |
| plugin.plugin | `DataGrid` | only onOpen |  | missing |  |
| plugin.plugin | `DataList` | only onLoadMore, onOpen |  | missing |  |
| plugin.plugin | `DataMap` | only onAdd, onEdit, onRemove |  | missing |  |
| plugin.plugin | `DataTable` | only onCellEdit, onFilterChange, onPageChange, onRowOpen, onSortChange |  | missing |  |
| plugin.plugin | `DataTree` | only onExpand, onExpandedChange, onRowOpen |  | missing |  |
| plugin.plugin | `DocumentSplits` | only onChange, onSelectPage |  | missing |  |
| plugin.plugin | `DocumentViewerSidebar` | only onSelect | missing | missing |  |
| plugin.plugin | `DocxAnnotationCard` | only onClick |  | missing |  |
| plugin.plugin | `DocxDocument` | only onDownload, onError, onPageChange, onUpload, onZoomChange |  | missing |  |
| plugin.plugin | `DocxLayoutBlocks` | only onError, onFocus, onSelect |  | missing |  |
| plugin.plugin | `FileThumbnail` | only onLoad |  | missing |  |
| plugin.plugin | `Mdi` | only onLayoutChange |  | missing |  |
| plugin.plugin | `MdiWindow` | only onClose | missing | missing |  |
| plugin.plugin | `MessageBubble` |  |  |  | `state(String state)` |
| plugin.plugin | `MessageList` | only onDelete, onEdit, onForward, onLoadOlder, onOpenAttachment, onPin, onReact, onReply, onReport, onRetry, onUnlock, onUnsend |  | missing | `people(Map<String`, `ChatPerson>)` |
| plugin.plugin | `PdfViewer` | only onDownload, onError, onLoad, onOpen, onPageChange |  | missing |  |
| plugin.plugin | `SessionExpired` | only onSignIn, onSignOut |  | missing |  |
| plugin.plugin | `Settings` |  | missing |  |  |
| plugin.plugin | `SignInForm` | only onForgot, onProvider, onRememberChange, onSubmit |  | missing |  |
| plugin.plugin | `UpdatePrompt` | only onInstall, onLater, onSkip, onUpdate |  | missing |  |
| ui.components | `Accordion` | only onExpandedChange | missing | missing |  |
| ui.components | `Calendar` |  |  |  | `max(String max)`, `min(String min)`, `month(String month)`, `setValue(String value)`, `value(String value)` |
| ui.components | `Carousel` | only onIndexChange | missing | missing |  |
| ui.components | `Chart` |  |  |  | `unit(String unit)` |
| ui.components | `CodeBlock` |  |  |  | `CodeBlock(String language)` |
| ui.components | `CodeEditor` | only onChange |  | missing | `language(String language)` |
| ui.components | `Combobox` |  |  |  | `setValue(String value)`, `value(String value)` |
| ui.components | `CommandPalette` | only onOpenChange |  | missing | `command(String icon)` |
| ui.components | `Composer` | only onSend |  | missing |  |
| ui.components | `Dialog` |  | missing |  | `kind(String kind)` |
| ui.components | `EmptyState` |  |  |  | `icon(String icon)` |
| ui.components | `FileChip` | only onOpen, onRemove |  | missing | `status(String status)` |
| ui.components | `FileExplorer` | only onDelete, onOpen, onRename, onSelectionChange |  | missing |  |
| ui.components | `FileGrid` | only onDelete, onOpen, onRename, onSelectionChange |  | missing |  |
| ui.components | `FileList` | only onDelete, onOpen, onRename, onSelectionChange |  | missing |  |
| ui.components | `FileTree` | only onDelete, onOpen, onRename, onSelectionChange |  | missing |  |
| ui.components | `Frame` |  |  |  | `accent(String color)` |
| ui.components | `GrammarTextArea` | only onChange, onMistakes |  | missing |  |
| ui.components | `HoverCard` |  |  |  | `HoverCard(String trigger)`, `trigger(String trigger)` |
| ui.components | `InfiniteScroll` | only onLoadMore | missing | missing |  |
| ui.components | `Inspector` | only onOpenChange | missing | missing |  |
| ui.components | `InspectorSection` |  | missing |  |  |
| ui.components | `Link` | only onClick |  | missing |  |
| ui.components | `ListItem` | only onDoubleClick, onSelect |  | missing |  |
| ui.components | `ListView` |  | missing |  |  |
| ui.components | `Markdown` | only onLink |  | missing |  |
| ui.components | `MediaPlayer` | only onEnded, onError, onPause, onPlay, onSeek |  | missing | `captions(String language)` |
| ui.components | `MoneyField` | only onChange, onCurrencyChange |  | missing |  |
| ui.components | `NavigationMenu` | only onNavigate |  | missing |  |
| ui.components | `Pages` | only onPage |  | missing |  |
| ui.components | `Popover` |  |  |  | `Popover(String trigger)`, `trigger(String trigger)` |
| ui.components | `QuantityField` | only onChange, onUnitChange |  | missing |  |
| ui.components | `RadioGroup` |  |  |  | `setValue(String value)`, `value(String value)` |
| ui.components | `RichTextEditor` | only onChange |  | missing |  |
| ui.components | `Segmented` |  |  |  | `Segmented(Map<String`, `String>)`, `setSelectedValue(String value)`, `value(String value)` |
| ui.components | `Select` |  |  |  | `Select(Map<String`, `String>)`, `setOptions(Map<String`, `String>)`, `setSelectedValue(String value)`, `value(String value)` |
| ui.components | `Sidebar` | only onCollapsedChange | missing | missing |  |
| ui.components | `SidebarGroup` |  | missing |  |  |
| ui.components | `SidebarInput` | only onChange |  | missing |  |
| ui.components | `SidebarItem` | only onSelect |  | missing |  |
| ui.components | `SidebarMenuButton` | only onClick |  | missing |  |
| ui.components | `SidebarSection` |  | missing |  |  |
| ui.components | `StatusBar` |  | missing |  |  |
| ui.components | `Surface` |  | missing |  |  |
| ui.components | `Swipeable` |  |  |  | `left(String tone)`, `right(String tone)` |
| ui.components | `Tabs` | only onAdd, onChange, onClose |  | missing | `tab(String value)`, `value(String value)` |
| ui.components | `TitleBar` |  | missing |  |  |
| ui.components | `TitledPane` | only onExpandedChange | missing | missing |  |
| ui.components | `TokenField` | only onChange |  | missing |  |
| ui.components | `Toolbar` |  | missing |  |  |
| ui.components | `ToolbarButton` | only onClick |  | missing |  |
| ui.components.layout | `SplitPane` | only onDividerPositionsChange |  | missing |  |
| ui.components.valueobject | `CardNumberField` | only onChange |  | missing |  |
| ui.components.valueobject | `CountrySelect` | only onChange |  | missing | `options(Map<String`, `String>)` |
| ui.components.valueobject | `CurrencySelect` | only onChange |  | missing | `options(Map<String`, `String>)` |
| ui.components.valueobject | `DateField` | only onChange |  | missing |  |
| ui.components.valueobject | `EmailField` | only onChange |  | missing |  |
| ui.components.valueobject | `IbanField` | only onChange |  | missing |  |
| ui.components.valueobject | `LanguageSelect` | only onChange |  | missing | `options(Map<String`, `String>)` |
| ui.components.valueobject | `PercentField` | only onChange |  | missing |  |
| ui.components.valueobject | `PhoneField` | only onChange |  | missing |  |
| ui.components.valueobject | `UrlField` | only onChange |  | missing |  |

## The explorer's own hard-coding (to fix once the above lands)

These are mistakes in `explorer/`, made to work around the gaps above or by not following the API. They will be
fixed in the same change:

- It starts from `WebviewApplication`, but the window overrides `render()` and rebuilds the whole tree from `Ui.*`
  factories on every change, instead of keeping its components in fields and changing them.
- Its own live reload (`Compiler`, file polling) duplicates `WebviewApplication`'s `LiveReload` (dev mode). The
  stories should reload through that.
- Its categories are a `Map<String, String>` of package names, and Components / Plugins is a second map. It wants an
  enum.
- It has string keys (`"category/" + name`, `type + "/tabs/" + i`), tab values `"preview"` / `"code"`, and
  `Map<String, String>` options for an enum's select. These are typed `Tabs<Page>` and `Select<E>` once item 4 lands.
- The API Reference is a hand-built markdown table string. It wants a typed table component (`DataTable` of a
  record).
- The pretty-docs window is told where to go with a JavaScript string (`location.hash = ...`). It wants a typed
  navigation on `View`, or a route type.

## Done when

- [ ] Every container listed in 1 has public `add` / `remove` / `removeAll` / `getComponents`.
- [ ] Every `on*` in the table has an `add*Listener` / `remove*Listener` with a typed event that has `getSource()`.
- [ ] Every component whose state the user changes has typed getters and setters, and setting redraws.
- [ ] `Select`, `Segmented`, `Tabs`, `RadioGroup` and `Combobox` are generic over their items, and the
      `Map<String, String>` constructors are gone (callers updated).
- [ ] The stringly values in 5 take their types.
- [ ] The sidebar parts work as in 6, and the example at the top compiles and runs.
- [ ] Composite components (7) expose their parts as typed Java components; no `Map` for their providers, entries
      or columns.
- [ ] `java-gen` (the generator of the Java components from the React props) makes the above for generated
      components, so they do not drift back.
- [ ] The explorer is rewritten Swing-style on it, with no strings or maps, reloading through `WebviewApplication`.
