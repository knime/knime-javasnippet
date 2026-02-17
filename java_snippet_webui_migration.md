# Java Snippet Node - Modern UI Migration Plan

## Overview

This document outlines the migration plan for the Java Snippet node from the legacy dialog system to the Modern UI using the scripting dialog framework (`AbstractScriptingNodeDialog`).

**Key Challenge**: Unlike typical scripting nodes that have a single script field, the Java Snippet node requires managing three separate user-editable sections (imports, fields, body) within a single Java class template. This is addressed using a composite script approach with read-only template sections separating the editable zones.

### Current State

**Files:**
- Dialog: [JavaSnippetNodeDialog.java](knime-javasnippet/org.knime.jsnippets/src/org/knime/base/node/jsnippet/JavaSnippetNodeDialog.java)
- Model: [JavaSnippetNodeModel.java](knime-javasnippet/org.knime.jsnippets/src/org/knime/base/node/jsnippet/JavaSnippetNodeModel.java)
- Settings: [JavaSnippetSettings.java](knime-javasnippet/org.knime.jsnippets/src/org/knime/base/node/jsnippet/util/JavaSnippetSettings.java)
- Factory: [JavaSnippetNodeFactory.java](knime-javasnippet/org.knime.jsnippets/src/org/knime/base/node/jsnippet/JavaSnippetNodeFactory.java)

**Current Dialog Structure:**
1. **Tab 1 - Java Snippet**: Code editor, column/flow variable lists, input/output field tables
2. **Tab 2 - Additional Libraries**: JAR file management
3. **Tab 3 - Additional Bundles**: OSGi bundle management
4. **Tab 4 - Templates**: Template management (to be dropped)

**Persistence Keys (from JavaSnippetSettings):**
- `scriptImports` - Custom Java imports
- `scriptFields` - Custom Java field declarations
- `scriptBody` - Main script code
- `jarFiles` - String[] of JAR file paths
- `bundles` - String[] of bundle specifications
- `inCols` - InColList (input columns mapped to Java fields)
- `inVars` - InVarList (input flow variables mapped to Java fields)
- `outCols` - OutColList (output columns with Java types)
- `outVars` - OutVarList (output flow variables with Java types)
- `templateUUID` - String (dropped in migration)
- `version` - String (compatibility tracking)

---

## Target Architecture

### ScriptingNodeDialog Layout

The new Modern UI will extend `AbstractScriptingNodeDialog` with a custom settings service to handle the multi-section script structure. The layout follows the standard scripting node pattern:

```
┌─────────────────────────────────────────────────────────────────┐
│ Java Snippet Node Dialog                                        │
├──────────────┬─────────────────────────────────┬────────────────┤
│              │                                 │                │
│  Left Panel  │                                 │ Right Panel    │
│              │      Code Editor (Java)         │                │
│ • Input      │                                 │ Settings from  │
│   Table      │                                 │ NodeParameters │
│              │                                 │                │
│ • Flow Vars  │                                 │ • Input Fields │
│              │                                 │ • Output Fields│
│ • Output     │                                 │                │
│   Objects    │                                 │ [Libraries &   │
│              │                                 │  Bundles...]   │
│              │                                 │  (side drawer) │
└──────────────┴─────────────────────────────────┴────────────────┘
```

**Key Features:**
- **Center**: Monaco editor with Java syntax highlighting and mixed editable/read-only sections
- **Left**: Auto-generated from InputOutputModel[] - shows inputs/outputs
- **Right**: Auto-generated from NodeParameters class



---

## NodeParameters Structure

The NodeParameters class (`JavaSnippetNodeParameters`) will organize all node settings into logical sections that appear in the right panel of the Modern UI dialog.

**Note on Script Handling**: The script is managed specially - a `displayScript` field in NodeParameters holds the composite Java class for the Monaco editor, but this field is never persisted. Instead, the custom settings service decomposes it into three separate sections (`scriptImports`, `scriptFields`, `scriptBody`) that are saved using the existing JavaSnippetSettings persistence logic.

### Visual Structure

```
Right Panel (Settings)
├── Section: Input Fields
│   ├── Input Columns (ArrayWidget)
│   │   Each entry: Column | Field | Type
│   └── Input Flow Variables (ArrayWidget)
│       Each entry: Variable | Field | Type
│
└── Section: Output Fields
    ├── Output Columns (ArrayWidget)
    │   Each entry: Column | Field | Type
    │               Replace? | Collection?
    └── Output Flow Variables (ArrayWidget)
        Each entry: Variable | Field | Type

[Configure Libraries & Bundles... button] ─────> Opens Side Drawer
                                                       │
                             ┌─────────────────────────┘
                             ▼
                    Side Drawer (Advanced)
                    ├── JAR Files (ArrayWidget)
                    │   Each entry: Path/URL
                    ├── Active Bundles (ArrayWidget)
                    │   Each entry: Bundle specification
                    └── Custom Type Bundles (Read-only)
                        Multi-line text showing auto-detected bundles
```

### Section 1: Input Fields

**Input Columns** (ArrayWidget):
- Fields per entry: Column Name (dropdown), Java Field Name (text), Java Type (dropdown)
- Custom persistor: Array ↔ legacy `InColList`

**Input Flow Variables** (ArrayWidget):
- Fields per entry: Flow Variable (dropdown), Java Field Name (text), Java Type (dropdown)
- Custom persistor: Array ↔ legacy `InVarList`

> **Note:** In both cases the "Java Type" dropdown should provide all options that are provided through
>           Java type converters, as seen in InFieldsTable.java:219
> ```java
>        final Optional<DataCellToJavaConverterFactory<?, ?>> first =
>            ConverterUtil.getFactoriesForSourceType(colSpec.getType()).stream()
>                .filter(factory -> JavaSnippet.getBuildPathFromCache(factory.getIdentifier()) != null).findFirst();
> ```

### Section 2: Output Fields

**Output Columns** (ArrayWidget):
- Fields per entry: Column Name (text), Java Field (text), Java Type (dropdown), Replace Existing (checkbox), Is Collection (checkbox)
- Custom persistor: Array ↔ legacy `OutColList`

**Output Flow Variables** (ArrayWidget):
- Fields per entry: Flow Variable Name (text), Java Field (text), Java Type (dropdown)
- Custom persistor: Array ↔ legacy `OutVarList`

> **Note:** The Java Type dropdown should be populated with all types that are supported, just as in AddOutFieldDialog.java:358-378,
> ```java
> ConverterUtil.getAllDestinationDataTypes()
> // and 
> ConverterUtil.getFactoriesForDestinationType(type).stream()
> ```

### Section 3: Libraries & Bundles (Side Drawer)

**What are bundles vs JARs (=libraries)?**
- **JARs**: External libraries not part of KNIME installation
- **Bundles**: OSGi bundles already installed in the KNIME platform that aren't automatically on the classpath

| Aspect | JAR Files | OSGi Bundles |
|--------|-----------|--------------|
| **Location** | External to KNIME (user-provided) | Already in KNIME installation |
| **Use Case** | Add custom libraries | Access KNIME platform APIs |
| **User Knowledge** | File path or URL | Bundle symbolic name |
| **Visibility** | Side drawer (advanced) | Side drawer (advanced) |
| **Typical Users** | Advanced users needing external libs | Power users, developers |
| **Example** | `/home/user/mylib.jar` | `org.apache.commons.lang3` |
| **Persistence** | `jarFiles` String[] | `bundles` String[] |

#### Side Drawer
**Side Drawer Behavior**:
- Button: "Configure Libraries & Bundles..." (secondary styling)
- Opens side drawer from the right
- Contains: JAR Files, Active Bundles, Custom Type Bundles
- Changes persisted on close

**Side Drawer Implementation**: Use `@Section(title = "...", sideDrawer = true)` on the section interface. This creates a separate sub-panel with transition animation, accessed via a button in the main panel. 

Optional parameters:
- `sideDrawerSetText`: Custom button text (default: "Set")
- `description`: Description shown in the side drawer
- Often combined with `@Advanced` annotation

Example from DB Connector:
```java
@Section(title = "Input Type Mapping", sideDrawer = true)
@Advanced
interface InputMappingSection { }
```

#### JAR Files (ArrayWidget)

**Configuration:**
- Each entry: text input for file path or KNIME URL
  - File system paths: `/path/to/library.jar`
  - KNIME URLs: `knime://knime.workflow/lib/mylib.jar`
- Custom persistor: array of strings ↔ `String[]` in `jarFiles` config key
- Backwards compatible with existing format
- Validate that all JARs are found, otherwise mark them as missing by showing a TextWidget in the respective array entry (if that works).

#### OSGi Bundles

**Active Bundles** (ArrayWidget):
- Fields per entry: Bundle Selection (dropdown with OSGi bundles, including the version in the form "NAME - VERSION")
- Custom persistor: Array ↔ `String[]` in `bundles` config key
- Custom choices provider queries OSGi framework

**Custom Type Bundles** (Read-only ArrayWidget):
- Shows bundles auto-detected from custom data types used in input/output fields
- System-managed (users cannot edit)
- Persisted to `customTypeBundles` config key
- Provides transparency about dependencies from type choices

**Why separate Active vs Custom Type Bundles?**
- Active Bundles: User-controlled
- Custom Type Bundles: System-managed for type converter dependencies
- Prevents accidental removal of required dependencies

---

## Custom Persistors - Backwards Compatibility Bridge

Custom persistors translate between legacy serialization format and new NodeParameters structure, ensuring workflows work in both old and new KNIME versions.

**Workflow**:
- Load: Legacy format → NodeParameters → UI widgets
- Save: UI widgets → NodeParameters → Legacy format (same config keys)

### Required Persistors

All persistors maintain backwards compatibility by converting between legacy formats and new NodeParameters structure.

**1. InColListPersistor** (Input Columns)
- Legacy: `InColList` → New: Array of `InputColumnField` objects

**2. InVarListPersistor** (Input Flow Variables)
- Legacy: `InVarList` → New: Array of `InputFlowVariableField` objects

**3. OutColListPersistor** (Output Columns)
- Legacy: `OutColList` → New: Array of `OutputColumnField` objects
- Preserves `replaceExisting` and `isArray` flags

**4. OutVarListPersistor** (Output Flow Variables)
- Legacy: `OutVarList` → New: Array of `OutputFlowVariableField` objects

**5. JarFilesPersistor** (JAR Files)
- Legacy: `String[]` → New: Array of `JarFileEntry` objects
- Note: ArrayWidget requires NodeParameters-implementing objects

**6. BundlesPersistor** (OSGi Bundles)
- Legacy: `String[]` → New: Array of `BundleEntry` objects
- Preserves bundle symbolic names with version specifications

## Script Persistence Handling

The script itself (imports, fields, body) is **NOT** persisted through NodeParameters. Instead:

1. **In NodeParameters**: Script field should NOT have `@Persist` annotation
2. **In JavaSnippetSettings**: Keep existing save/load logic for:
   - `scriptImports`
   - `scriptFields`
   - `scriptBody`
3. **ScriptingNodeDialog**: Manages all three script sections in a single Monaco editor

**Why?** The ScriptingNodeDialog framework handles script persistence separately from NodeParameters to allow proper integration with the code editor UI component.

### Multi-Section Script Editor Strategy

The Java Snippet node requires three editable sections (imports, fields, body) separated by read-only template code that forms a complete Java class.

**Example Structure**:
- Editable: Imports section
- Read-only: Class declaration
- Editable: Fields section  
- Read-only: Method header
- Editable: Body section
- Read-only: Method/class footer

#### Implementation Approach Custom Frontend with Constrained Monaco Plugin

Implement a custom frontend using the `constrained-monaco` plugin, which allows to set readOnly regions in the code/script.
We will extend the options allowed for getInitialData() to accept "readOnlyRegions" to make the frontend as flexible as possible.

1. Use `constrained-monaco` in the default script editor (knime-core-ui/js-src/packages/scripting-editor/app/App.vue) to create an editor with multiple editable or read-only regions
2. In getInitialData(), instead of `mainScriptConfigKey`, pass a list of config keys and strings to the frontend app.
    - The frontend should read the contents of the settings keys, determine the length and set that as editable regions
    - Strings in the list become read-only regions in the editor.


#### Backwards Compatibility

- Old workflows: load three sections from legacy keys (`scriptImports`, `scriptFields`, `scriptBody`)
- New workflows: use the same three config fields
- No changes to `JavaSnippetSettings` persistence logic


#### Flow Variable Interoperability

**Open question:** 
How can we allow users to control the fields `scriptImports`, `scriptFields` and `scriptBody` independently by flow variables?
The model accepts existing flow variable configurations, so this is not a backwards compatiblity issue. Instead, it's a question
of UX: how can the user configure this in the modern UI? We need to keep that in mind when enabling the Flow Variable button for script editors in general.

---

## Testing Strategy

### Unit Tests
- Custom persistor tests
  - Old format → new format → old format (round-trip)
  - Edge cases: empty arrays, special characters, null values
- Custom settings service tests
  - Script composition: Combine three sections + template → displayScript
  - Script decomposition: Parse displayScript → extract three sections
  - Edge cases: empty sections, special characters in code, malformed scripts
  - Verify section boundaries are correctly identified
  
### Integration Tests
- Legacy workflow loading
  - Complex snippets with multiple inputs/outputs
  - Snippets with JARs and bundles
  - Verify all three script sections (imports, fields, body) load correctly
  - Verify node executes correctly with legacy settings
- Script section persistence
  - Create snippet with all three sections
  - Save and reload - verify sections preserved exactly
  - Verify no cross-contamination between sections
  
### UI Tests
- Dialog opens correctly
- Code editor displays composite script with template and user sections
- Read-only template sections are non-editable (Monaco enforces restrictions)
- Editable sections accept user input correctly
- Section boundaries are visually clear
- Left panel shows inputs
- Right panel shows settings
- Side drawer for libraries and bundles opens correctly
- Save/load settings preserves all three script sections
- Round-trip test: open → edit imports/fields/body → save → close → reopen

## Verification Instructions

### Critical Path Testing

**1. Backwards Compatibility (Must Pass)**
- [ ] Open existing workflows with Java Snippet nodes - all three script sections load correctly
- [ ] Execute legacy snippets with custom types, JARs, and bundles - output matches expected results
- [ ] Empty sections and large snippets (>100 lines) work without errors

**2. Multi-Section Script Editor (Core Feature)**
- [ ] Template structure visible with clear visual distinction between editable/read-only regions
- [ ] Cannot edit read-only sections (class declaration, method headers, footers)
- [ ] Can edit all three user sections (imports, fields, body)
- [ ] Monaco syntax highlighting and autocomplete work for Java

**3. Input/Output Configuration**
- [ ] Add input columns and flow variables with type selection
- [ ] Add output columns with Replace/Collection options; output flow variables work
- [ ] Common types (String, Integer, Boolean, dates) work correctly
- [ ] Custom types (e.g. Geo Value, JSON) also work

**4. Side Drawer (Libraries & Bundles)**
- [ ] Opens with animation, contains JAR files and OSGi bundles sections
- [ ] Can add JARs (paths/URLs) and bundles (with version specs)
- [ ] Custom Type Bundles section is read-only and shows auto-detected bundles

**5. Round-Trip & Persistence**
- [ ] Create snippet with all features → Save → Close → Reopen → All settings preserved
- [ ] Execute node → Output correct
- [ ] Save workflow → Close KNIME → Reopen → Still works

**6. Error Handling**
- [ ] Invalid Java syntax shows helpful errors
- [ ] Duplicate field names prevented by validation
- [ ] Invalid JAR paths and bundle specs handled gracefully

**7. Performance**
- [ ] Dialog opens quickly (<2 seconds)
- [ ] Large scripts (>500 lines) remain responsive
- [ ] Array widgets with 20+ entries perform well

**8. Final Checks**
- [ ] Run static code analysis (if available) - no violations
- [ ] All automated tests pass (unit, integration, UI)
- [ ] Code review completed
- [ ] Backwards compatibility confirmed with representative test workflows

---

## Open Questions & Decisions

### 1. Input/Output Field Management
**Decision**: Field definitions in NodeParameters (right panel). Optional: drag & drop from left panel auto-adds entry to right side.

### 2. Template Migration
**Decision**: DROP template functionality (rarely used, complex to maintain).

### 3. Read-only regions in Monaco
**Decision**: Add the constrained-monaco plugin as dependency in the default scripting editor app in `knime-core-ui`, and extend the app
such that it allows that the user provides a list of config keys or strings with read-only template code as initial data.


## References

### Key Files to Study
- [StringManipulationScriptingNodeDialog.java](knime-javasnippet/org.knime.jsnippets/src/org/knime/base/node/preproc/stringmanipulation/StringManipulationScriptingNodeDialog.java) - Example scripting dialog
- [AbstractScriptingNodeDialog.java](knime-core-ui/org.knime.core.ui/src/eclipse/org/knime/core/webui/node/dialog/scripting/AbstractScriptingNodeDialog.java) - Base class for custom scripting dialogs
- [AbstractDefaultScriptingNodeDialog.java](knime-core-ui/org.knime.core.ui/src/eclipse/org/knime/core/webui/node/dialog/scripting/AbstractDefaultScriptingNodeDialog.java) - Reference for standard scripting dialog
- [DefaultScriptingNodeSettingsService.java](knime-core-ui/org.knime.core.ui/src/eclipse/org/knime/core/webui/node/dialog/scripting/DefaultScriptingNodeSettingsService.java) - Base for custom settings service
- [ScriptingService.java](knime-core-ui/org.knime.core.ui/src/eclipse/org/knime/core/webui/node/dialog/scripting/ScriptingService.java) - Scripting service interface
- [RecursiveLoopEndDynamicNodeParameters.java](knime-base/org.knime.base/src/org/knime/base/node/meta/looper/recursive/RecursiveLoopEndDynamicNodeParameters.java) - ArrayWidget example
- [DBConnectorNodeSettings.java](knime-database/org.knime.database.nodes/src/org/knime/database/node/connector/generic/DBConnectorNodeSettings.java) - Side drawer example (see `@Section` with `sideDrawer = true`)
- [GuardedSection.java](knime-core/org.knime.rsyntaxtextarea/src/org/knime/rsyntaxtextarea/guarded/GuardedSection.java) - Legacy example of read-only sections
- [Section.java](knime-core-ui/org.knime.core.ui/src/eclipse/org/knime/node/parameters/layout/Section.java) - Side drawer annotation reference

### API Documentation
- NodeParameters annotations and widgets
- ScriptingNodeDialog framework
- Custom persistor patterns
- ArrayWidget usage
- Side drawers: `@Section(sideDrawer = true)` creates a separate sub-panel with button access

---

## Summary

This migration plan transforms the Java Snippet node from a legacy 4-tab dialog to a modern scripting interface with:
- **Center**: Java code editor with multi-section support (editable imports/fields/body sections separated by read-only template code)
- **Left**: Input table and flow variable display
- **Right**: Settings for input/output fields with type selection
- **Side Drawer**: Advanced configuration for JAR files and OSGi bundles

**Key Technical Decisions**:
- Leverage "constrained Monaco"'s read-only ranges instead of multiple editors
- Maintain full backwards compatibility through custom persistors

The migration preserves full backwards compatibility while providing a significantly improved user experience aligned with the Modern UI framework.
