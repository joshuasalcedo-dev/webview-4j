# java-gen: what it does, and what it cannot do

Analysis for [#1](https://github.com/joshuasalcedo-dev/webview-4j/issues/1) (the Java UI is not written like Swing yet).
`core/src/main/ui/scripts/java-gen.ts` (1002 lines) with `java-gen.json`, read 2026-11-27.

## What it does

For each component named in `java-gen.json`, the script reads the React component's props with TypeScript's own type
checker and writes two things:

1. `ui/components/<Java>.java` (or `plugin.<name>`), headed "Generated … do not edit".
2. Its case in `src/java-ui.generated.tsx`, the renderer's side.

`bun run java-gen:check` (the build's `uiJavaGenCheck`) fails when either file differs from what the props give.

How props become Java:

| Prop in TypeScript | Java it writes |
|---|---|
| `string` | `name(String)` builder; an `icon` prop also gets `icon(Icon)` |
| `number` | `name(double)` |
| `boolean` | `name(boolean)` and `name()` |
| string literal union (`"a" \| "b"`) | a nested `enum` and `name(Enum)` |
| `string \| number…` | one overload for each |
| `ReactNode` | `name(String)` and `name(Component<?>)` (a slot) |
| object, array, union of objects | generated `record`s, `enum`s and `sealed interface`s, then `name(Record / List<Record>)` |
| callback | `onX(Runnable)`, or `onX(Consumer<String / Double / Boolean / Object / List<Object>>)` |
| `children` | `with(...)`, `add(...)`, `remove(...)`, `removeAll()` (Swing-style, public) |
| required props | constructor parameters |
| **a value with its change event** (`value` + `onChange`, `open` + `onOpenChange`, `x` + `onXChange`) when the value is a `string`, `number` or `boolean` | Swing's `getX()` / `isX()`, `setX(...)`, `addChangeListener(ChangeListener<T>)` / `removeChangeListener`, and it keeps what the user changed (`keep`) |

It leaves out what has no JSON form (a ref, a function inside an object, `File[]`) and says so in the class's javadoc.

## Its reach

**Only 23 components go through it** (the manifest), and they make 39 generated files with their records and enums.
**Core has 152 component classes, so about 113 are hand-written**, and the generator never sees them. The worst gaps in
#1 are all among them: `Sidebar`, `SidebarGroup`, `SidebarMenuButton`, `SidebarItem`, `SidebarInput`, `Select`,
`Segmented`, `Tabs`, `Button`, `TextField`, `ListView`, `Surface`, `Toolbar`, `StatusBar`, `Dialog`, and every
plugin class.

So **the first problem is not the script but what it is not run on.** Hand-written classes follow no rule, so each
one has whatever API was written for it.

## What it cannot do, even on the components it reads

1. **No action listeners.** A callback that is not a value's change event (`onClick`, `onSelect`, `onOpen`,
   `onSubmit`, `onClose`, `onLoadMore`…) becomes only `onX(Runnable / Consumer)`, which *replaces* the one handler.
   There is no `addActionListener` / `addXListener`, no `removeXListener`, and no event object with
   `getSource()`. Only the value/change pair gets Swing's listener.
2. **Change listeners only for `string`, `number` and `boolean` values.** A value that is a literal union (a tab, a
   size), an array (`TokenField`'s `string[]`), a date, or an object gets no getter, setter or listener. Neither
   does a change event named another way (`onExpandedChange` with a number index works; `onSelectionChange` of a list
   does not).
3. **It cannot invent types the TypeScript does not have.** A prop typed `string` in React stays `String` in Java:
   `Calendar.value/min/max` (dates), colors, code languages, `status` / `state` / `kind` written as plain `string`. An
   enum appears only where the React prop is a literal union. The stringly-typed API in #1 starts in the props.
4. **It cannot make generic choices.** `Select` / `Combobox` / `RadioGroup` / `Tabs` hold `{ value: string, label:
   string }` items and a `value: string`, because the page only ever sees strings. A typed `Select<T>` needs
   Java-side code that the props cannot describe: items of `T`, a label function, and a map from each item to a key
   the page sends back. The script has no rule for "options + value + onChange", so it writes
   `List<ComboboxOption>` and `value(String)`.
5. **No getters or setters for plain properties.** A prop gets only a builder (`label(String)`). Swing's
   `getLabel()` / `setLabel(...)` exist only for the value/change pair above. Swing has `getText`/`setText`,
   `isEnabled`/`setEnabled` and the rest for every property.
6. **Children are untyped, and compound components are out of reach.** `children` becomes `add(Component<?>...)`:
   any component, anywhere. The script reads one component's props, so it cannot express the kit's compound parts
   (shadcn's `Sidebar` › `SidebarGroup` › `SidebarMenu` › `SidebarMenuItem` › `SidebarMenuButton` /
   `SidebarMenuSub`). It cannot write `Sidebar.add(SidebarGroup)` or `SidebarMenuButton.add(SidebarMenuButton)`
   for sub-items.
7. **No state shared across components.** Swing's `ButtonGroup` (one selected among many sidebar buttons, toggle
   buttons, radios) and selection models (`ListSelectionModel`, `SingleSelectionModel`) belong to no single
   component's props, so nothing generates them.
8. **No typed slots.** A `ReactNode` prop becomes `name(Component<?>)`. Where the kit expects a specific part (a
   `trigger`, a `footer`), any component is accepted.
9. **No collection models.** Data props become `List<Record>` set whole (`items(List)`). There is no model to add,
   remove or update one row and redraw (Swing's `ListModel` / `TableModel` with their events).
10. **Callbacks with several or partial arguments are loosely typed.** They become `Consumer<List<Object>>` or
    `Consumer<Object>` (`arg: "args"`, `"json"`), so the Java handler casts.
11. **It cannot run on hand-written classes.** It refuses a file without its header. Moving a component to the
    generator means deleting the hand-written class, and losing anything added there by hand (Swing methods,
    helpers), unless the generator learns to make it.

## What would close #1 through the generator

- Put every kit component in `java-gen.json`, or generate by default and list exceptions, and retire the
  hand-written classes.
- For every callback, generate `add/remove<Event>Listener` and a typed event (`getSource()`, its value) beside the
  `onX` shorthand.
- Generate getters and setters for every property, not only value/change pairs, plus listeners for enum, array and
  object values too.
- A rule for choice components (`options` + `value` + `onChange`): a generic `<T>` class with items, a label
  function, `getSelectedItem` / `setSelectedItem`, and `ChangeListener<T>`, replacing the map/string forms.
- Compound parts declared in the manifest (`"children": ["SidebarGroup"]`, `"subItems": "SidebarMenuButton"`), so
  that `add` is typed to its parts. Selection groups declared there too (`"selectionGroup": true`).
- Type the React props first, where they are `string` but mean a date, color, language or status. The generator
  then follows.

## Every generator, and what it covers

| Generator | Makes | Run by | Covers |
|---|---|---|---|
| `java-gen.ts` | component classes, their records and enums, and the renderer's cases | `bun run java-gen` by hand, checked by the build | **23 of ~150 components** |
| `style-gen.ts` | `<Component>Part` enums (typed CSS classes per component) | Gradle `uiStyleGen`, every build | 79 components in `style-gen.json` |
| `css-gen.ts` | `<Name>Style` enums for each `src/styles/*.css`, with custom properties as methods | **nothing**: it is not in `package.json` or Gradle, and `ui.styles` does not exist | none (dead) |
| `html-gen.ts` | `Html.<Element>`, every HTML element, typed from React's types | by hand, checked by the build | all elements |
| `icon-gen.ts` | the `Icon` enum (all of Lucide) | Gradle `uiIconGen`, every build | all icons |
| `WebViewProcessor` (Java annotation processor) | `<Controller>_Routes`, and a typed TypeScript client (`api.ts`) for `@WebViewController`s | `javac` | Java → page **commands** (the HTTP-like API) |

**Types, styles, components:** partly. Styles and icons are complete. Components are generated for 23 of ~150, and
their types only where the React props are typed. css-gen is written but never run.

**IPC for every action:** no, and not per action. Every component event goes over **one** channel,
`webview4j.ui.event`, carrying `{ handler, value }`. `handler` is a key the renderer got with the tree, and Java
looks up the handler registered with `on(...)`. It is IPC (wry's `ipc.postMessage`, see #1's discussion). But it
is not a typed command per action, and there is no listener API on the Java side (see 1 above). Commands typed both
ways exist only for `@WebViewController`s, through the processor. Nothing generates Java for the kit's
`@webview4j/api` bridge calls.

**Hooks:** none. Nothing reads the kit's React hooks or context (theme, media queries, the sidebar's state,
toasts…) to give Java an equivalent. What a page knows only through a hook cannot be reached from Java except where a
component was written by hand to send it.
