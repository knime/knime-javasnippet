# knime-javasnippet — Repository Overview

## Purpose

This repository provides the **Java Snippet** family of KNIME nodes, which allow users to write arbitrary Java code inside a KNIME workflow. The code runs per-row (for the table variant) or against flow variables (for the variable variant).

## Key Bundles

| Bundle | Description |
|---|---|
| `org.knime.jsnippets` | Main bundle containing all Java Snippet nodes |
| `org.knime.ext.sun` | Java compilation helpers (Eclipse JDT-based) |

## Main Packages and Entry Points

### `org.knime.base.node.jsnippet`

Core of the Java Snippet node:

- **`JavaSnippet`** — The execution engine. Compiles and runs user-written Java code via Eclipse JDT. Manages the snippet JAR (`jSnippetJar`), classpath, and `JSnippet.java` source file construction. Key methods: `getClassPath()`, `getRuntimeClassPath()`, `getSystemImports()`, `execute()`, `validateSettings()`.
- **`JavaSnippetNodeFactory`** — Legacy (Swing) factory; also implements `NodeDialogFactory` to provide the WebUI dialog when the feature flag is active.
- **`JavaSnippetModel`** — `NodeModel` implementation. Delegates compilation and per-row execution to `JavaSnippet` and `JavaSnippetCellFactory`.
- **`JavaSnippetCellFactory`** — `GenericRowInput`-based cell factory that injects field values into an `AbstractJSnippet` instance and calls `snippet()` per row.
- **`JavaSnippetScriptingNodeDialog`** — **Modern UI (WebUI) dialog** using the scripting editor framework. Provides a multi-section code editor (imports, fields, body) and dynamic autocompletion via `JavaSnippetCompletionService`.
- **`JavaSnippetCompletionService`** — Provides dynamic autocompletion items for the WebUI dialog:
  - *P0 (always available)*: row metadata (`ROWID`, `ROWINDEX`, `ROWCOUNT`), input columns, flow variables, `AbstractJSnippet` methods, Java keywords.
  - *P1 (dot-triggered)*: reflection-based type resolution for the identifier before `.`, returning matching public methods and fields.
- **`JavaSnippetScriptingNodeParameters`** — `NodeParameters` (WebUI settings) class for the Modern UI dialog.
- **`JavaSnippetSettings`** — Legacy settings container (XML serialization via `NodeSettings`).

### `org.knime.base.node.jsnippet.expression`

- **`AbstractJSnippet`** — Base class for all generated snippet classes. Exposes `getCell`, `isMissing`, `isType`, `getFlowVariable`, `logWarn/Info/Error/Fatal`, etc. to the user snippet body.
- **`Type`**, **`Cell`**, **`Abort`**, **`TypeException`**, **`ColumnException`** — Expression support types available in the snippet scope.

### `org.knime.base.node.jsnippet.util`

- **`JSnippet`** (interface) — Contract for snippet objects: `getClassPath()`, `getCompilationUnits()`, etc.
- **`JavaSnippetCompiler`** — Compiles the generated `JSnippet.java` source using Eclipse JDT in-process.
- **`FlowVariableRepository`** — Adapter that exposes `FlowObjectStack` variables to the snippet.
- **`JDK11ClasspathLibraryInfo`** — Provides JDK 11 module-aware classpath entries.

### `org.knime.base.node.util`

- **`WebUIDialogUtils`** — Shared utilities for building WebUI scripting dialogs: `getFirstInputTableModel`, `getFlowVariablesInputOutputModel`, `getCompletionItems`, `getFlowVariableTypePrefix`, flow variable and column alias templates, supported variable types.

## Modern UI Dialog Pattern

The WebUI dialog is activated when the system property `org.knime.scripting.ui.mode=js` is set (or when running headless for remote workflow editing). The factory extends `AbstractFallbackScriptingNodeFactory` which routes to either the legacy Swing dialog or the WebUI dialog.

The dialog (`JavaSnippetScriptingNodeDialog`) extends `AbstractDefaultScriptingNodeDialog` and:
1. Provides `getInitialData` with multi-section script sections, input/output models, and `enableDynamicCompletion(".")`.
2. Overrides `getDataServiceBuilder` to attach `JavaSnippetScriptingService`, a custom `ScriptingService` whose `RpcService.getCompletions` delegates to `JavaSnippetCompletionService`.

## Flow Variable Syntax

Flow variables in the Java Snippet are referenced using the inline syntax `$${TypeCharVarName}$$`, where `TypeChar` is one of:
- `D` — `Double`
- `I` — `Integer`
- `S` — `String`

Column values are accessed via `$columnName$`.

## Conventions

- The snippet source (`JSnippet.java`) is generated at runtime and compiled to a temp directory. It extends `AbstractJSnippet` and overrides the `snippet()` method.
- System imports (auto-prepended) include: `AbstractJSnippet`, `Abort`, `Cell`, `ColumnException`, `TypeException`, `static Type.*`, `java.util.Date`, `java.util.Calendar`, `org.w3c.dom.Document`.
- All WebUI classes from `org.knime.core.webui` are restricted API; code using them is annotated with `@SuppressWarnings("restriction")`.
- Do NOT modify the `JavaSnippetModel` or `JavaSnippetSettings` classes when migrating dialog changes — only the dialog and parameters classes should change.
