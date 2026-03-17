/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Created on Feb 16, 2026 by GitHub Copilot
 */
package org.knime.base.node.jsnippet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.knime.base.node.jsnippet.type.ConverterUtil;
import org.knime.base.node.jsnippet.type.TypeProvider;
import org.knime.base.node.jsnippet.type.flowvar.TypeConverter;
import org.knime.base.node.jsnippet.util.JavaFieldList;
import org.knime.base.node.jsnippet.util.JavaFieldList.InColList;
import org.knime.base.node.jsnippet.util.JavaFieldList.InVarList;
import org.knime.base.node.jsnippet.util.JavaSnippetSettings;
import org.knime.base.node.jsnippet.util.field.InCol;
import org.knime.base.node.jsnippet.util.field.InVar;
import org.knime.base.node.jsnippet.util.field.JavaField;
import org.knime.base.node.jsnippet.util.field.OutCol;
import org.knime.base.node.jsnippet.util.field.OutVar;
import org.knime.core.data.DataType;
import org.knime.core.data.convert.datacell.JavaToDataCellConverterFactory;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.VariableTypeRegistry;
import org.knime.core.webui.node.dialog.defaultdialog.internal.persistence.ArrayPersistor;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.FileReaderWidget;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.FileSelection;
import org.knime.filehandling.core.connections.FSCategory;
import org.knime.filehandling.core.connections.FSLocation;
import org.knime.core.webui.node.dialog.defaultdialog.internal.persistence.ElementFieldPersistor;
import org.knime.core.webui.node.dialog.defaultdialog.internal.persistence.PersistArray;
import org.knime.core.webui.node.dialog.defaultdialog.internal.persistence.PersistArrayElement;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.node.parameters.Advanced;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.array.ArrayWidget;
import org.knime.node.parameters.layout.After;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.DataTypeChoicesProvider;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.StringChoice;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.choices.util.AllColumnsProvider;
import org.knime.node.parameters.widget.choices.util.AllFlowVariablesProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

/**
 * NodeParameters implementation for the Java Snippet node in the Modern UI. This class defines all configuration
 * options except the script sections (imports, fields, body), which are handled by the scripting dialog framework.
 *
 * @author GitHub Copilot
 * @author Carsten Haubold, KNIME GmbH, Konstanz, Germany
 * @since 5.11
 */
@SuppressWarnings("restriction")
public final class JavaSnippetScriptingNodeParameters implements NodeParameters {

    // Script sections (not displayed as widgets - handled by dialog)
    String m_scriptImports = "";

    String m_scriptFields = "";

    String m_scriptBody = "";

    // These are internal metadata fields not shown in the dialog (no @Widget annotation).
    @Persist(configKey = "version")
    String m_version = JavaSnippet.VERSION_1_X;

    @Persist(configKey = "templateUUID")
    String m_templateUUID = null;

    @Persist(configKey = "runOnExecute")
    boolean m_runOnExecute = false;

    // =================================================================================
    // Field Type Inner Classes
    // These represent individual entries in the ArrayWidgets.
    // =================================================================================

    /**
     * Represents an input flow variable field definition. As it represents an {@link InVar} which extends a
     * {@link JavaField} we need to serialize all InVar fields.
     */
    public static final class InputFlowVariableField implements NodeParameters {
        @Widget(title = "Flow Variable", description = "The input flow variable to use")
        @ChoicesProvider(AllFlowVariablesProvider.class)
        @ValueReference(KnimeNameRef.class)
        @PersistArrayElement(InputFlowVariableKnimeNamePersistor.class)
        String m_knimeName = "";

        static final class KnimeNameRef implements ParameterReference<String> {
        }

        @Widget(title = "Java Field Name", description = "Java variable name for this flow variable")
        @PersistArrayElement(InputFlowVariableJavaNamePersistor.class)
        String m_javaName = "";

        @Widget(title = "Java Type", description = "Java type for conversion")
        @ChoicesProvider(InputFlowVariableJavaTypeChoicesProvider.class)
        @PersistArrayElement(InputFlowVariableJavaTypePersistor.class)
        @ValueProvider(InputFlowVariableJavaTypeProvider.class)
        @ValueReference(JavaTypeRef.class)
        String m_javaType = "";

        static final class JavaTypeRef implements ParameterReference<String> {
        }

        // Internal field: tracks the preferred Java type for the selected variable so that
        // InputFlowVariableJavaTypeProvider can preserve the user's selection. Populated by
        // InputFlowVariableKnimeTypeProvider and loaded from the legacy FlowVariable.Type enum name on startup.
        @PersistArrayElement(InputFlowVariableKnimeTypePersistor.class)
        @ValueProvider(InputFlowVariableKnimeTypeProvider.class)
        @ValueReference(KnimeTypeRef.class)
        String m_knimeType = "";

        static final class KnimeTypeRef implements ParameterReference<String> {
        }

        // bundle and converter factory ID are not used for variable fields, ignored here

        /**
         * Provides the preferred Java type simple name (e.g. "String", "Double") for the selected flow variable.
         * This is used internally to initialise {@code m_knimeType} and to drive
         * {@link InputFlowVariableJavaTypeProvider}.
         */
        static final class InputFlowVariableKnimeTypeProvider implements StateProvider<String> {
            private Supplier<String> m_knimeNameProvider;

            @Override
            public void init(final StateProviderInitializer initializer) {
                initializer.computeBeforeOpenDialog();
                m_knimeNameProvider = initializer.computeFromValueSupplier(KnimeNameRef.class);
            }

            @Override
            public String computeState(final NodeParametersInput context) throws StateComputationFailureException {
                String selectedVariableName = m_knimeNameProvider.get();

                var flowVariables =
                    context.getAvailableInputFlowVariables(VariableTypeRegistry.getInstance().getAllTypes());

                if (flowVariables.isEmpty() || !flowVariables.containsKey(selectedVariableName)) {
                    throw new StateComputationFailureException();
                }

                var flowVariable = flowVariables.get(selectedVariableName);
                TypeConverter typeConversion = TypeProvider.getDefault().getTypeConverter(flowVariable.getType());
                return typeConversion.getPreferredJavaType().getSimpleName();
            }
        }

        /**
         * Provides the Flow Variable converter options for the selected combination of flow variable and its KNIME type
         */
        static final class InputFlowVariableJavaTypeProvider implements StateProvider<String> {
            private Supplier<String> m_knimeNameProvider;

            private Supplier<String> m_knimeTypeProvider;

            private Supplier<String> m_javaTypeProvider;

            @Override
            public void init(final StateProviderInitializer initializer) {
                initializer.computeBeforeOpenDialog();
                m_knimeNameProvider = initializer.computeFromValueSupplier(KnimeNameRef.class);
                m_knimeTypeProvider = initializer.computeFromValueSupplier(KnimeTypeRef.class);
                m_javaTypeProvider = initializer.getValueSupplier(JavaTypeRef.class);
            }

            @Override
            public String computeState(final NodeParametersInput context) throws StateComputationFailureException {
                String selectedVariableName = m_knimeNameProvider.get();
                String selectedVariableType = m_knimeTypeProvider.get();

                var flowVariables =
                    context.getAvailableInputFlowVariables(VariableTypeRegistry.getInstance().getAllTypes());

                if (!flowVariables.isEmpty() && flowVariables.containsKey(selectedVariableName)) {
                    var flowVariable = flowVariables.get(selectedVariableName);
                    TypeConverter typeConversion = TypeProvider.getDefault().getTypeConverter(flowVariable.getType());
                    var matchingType = Arrays.stream(typeConversion.canProvideJavaTypes())
                        .filter(type -> type.getSimpleName().equals(selectedVariableType)).findFirst();

                    if (matchingType.isPresent()) {
                        return matchingType.get().getSimpleName();
                    }
                }

                // Fallback: convert the persisted value (which may be a fully qualified class name) to a simple name.
                String current = m_javaTypeProvider.get();
                if (current != null && !current.isEmpty()) {
                    return toSimpleName(current);
                }
                throw new StateComputationFailureException();
            }
        }

        /**
         * Provides Java type choices for flow variables.
         */
        static final class InputFlowVariableJavaTypeChoicesProvider implements StringChoicesProvider {
            private Supplier<String> m_knimeNameProvider;

            @Override
            public void init(final StateProviderInitializer initializer) {
                initializer.computeBeforeOpenDialog();
                m_knimeNameProvider = initializer.computeFromValueSupplier(KnimeNameRef.class);
            }

            @Override
            public List<String> choices(final NodeParametersInput context) {
                String selectedVariableName = m_knimeNameProvider.get();

                var flowVariables =
                    context.getAvailableInputFlowVariables(VariableTypeRegistry.getInstance().getAllTypes());

                if (flowVariables.isEmpty() || !flowVariables.containsKey(selectedVariableName)) {
                    return Collections.emptyList();
                }

                var flowVariable = flowVariables.get(selectedVariableName);
                TypeConverter typeConversion = TypeProvider.getDefault().getTypeConverter(flowVariable.getType());
                // Note: most flow variable types expose only a single Java type
                return Arrays.stream(typeConversion.canProvideJavaTypes()).map(clazz -> clazz.getSimpleName()).toList();
            }
        }
    }

    private abstract static class JavaListElementPersistor<F extends NodeParameters, FieldType extends JavaField>
        implements ElementFieldPersistor<String, Integer, F> {
        private final String m_configKey;

        JavaListElementPersistor(final String configKey) {
            m_configKey = configKey;
        }

        protected abstract String getFieldFromItem(final FieldType inVar);

        protected abstract JavaFieldList<FieldType> createList();

        @Override
        public String load(final NodeSettingsRO settings, final Integer loadContext) throws InvalidSettingsException {
            var javaFieldList = createList();
            if (settings.containsKey(m_configKey)) {
                javaFieldList.loadSettings(settings.getConfig(m_configKey));
                return loadContext < javaFieldList.size() ? getFieldFromItem(javaFieldList.get(loadContext)) : null;
            }
            return null;
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{m_configKey}};
        }

    }

    private abstract static class InVarListElementPersistor
        extends JavaListElementPersistor<InputFlowVariableField, InVar> {

        InVarListElementPersistor() {
            super(JavaSnippetSettings.IN_VARS);
        }

        @Override
        protected JavaFieldList<InVar> createList() {
            return new InVarList();
        }
    }

    private static final class InputFlowVariableKnimeNamePersistor extends InVarListElementPersistor {
        @Override
        protected String getFieldFromItem(final InVar inVar) {
            return inVar.getKnimeName();
        }

        @Override
        public void save(final String param, final InputFlowVariableField saveDTO) {
            saveDTO.m_knimeName = param;
        }
    }

    private static final class InputFlowVariableJavaNamePersistor extends InVarListElementPersistor {
        @Override
        protected String getFieldFromItem(final InVar inVar) {
            return inVar.getJavaName();
        }

        @Override
        public void save(final String param, final InputFlowVariableField saveDTO) {
            saveDTO.m_javaName = param;
        }
    }

    private static final class InputFlowVariableJavaTypePersistor extends InVarListElementPersistor {
        @Override
        protected String getFieldFromItem(final InVar inVar) {
            // Load as simple name to match what InputFlowVariableJavaTypeProvider returns
            // (computeBeforeOpenDialog). Using FQN here would cause the framework to detect a
            // change on dialog open (FQN -> simple name) and mark settings as dirty.
            return inVar.getJavaType() != null ? inVar.getJavaType().getSimpleName() : "";
        }

        @Override
        public void save(final String param, final InputFlowVariableField saveDTO) {
            saveDTO.m_javaType = param;
        }
    }

    private static final class InputFlowVariableKnimeTypePersistor extends InVarListElementPersistor {
        @Override
        protected String getFieldFromItem(final InVar inVar) {
            // Load as the preferred Java type simple name to match what
            // InputFlowVariableKnimeTypeProvider.computeState returns. Using the
            // FlowVariable.Type enum name (e.g. "STRING") would differ from the
            // ValueProvider output (e.g. "String") and cause false dirty state.
            if (inVar.getFlowVarType() != null) {
                TypeConverter typeConversion =
                    TypeProvider.getDefault().getTypeConverter(inVar.getFlowVarType());
                return typeConversion.getPreferredJavaType().getSimpleName();
            }
            return "";
        }

        @Override
        public void save(final String param, final InputFlowVariableField saveDTO) {
            saveDTO.m_knimeType = param;
        }
    }

    // ---------------------------------------------------------------------------------
    // Input Columns
    // ---------------------------------------------------------------------------------

    /**
     * Represents an input column field definition.
     */
    public static final class InputColumnField implements NodeParameters {
        @Widget(title = "Column Name", description = "The input column to use")
        @ChoicesProvider(AllColumnsProvider.class)
        @ValueReference(SelectedKnimeNameRef.class)
        @PersistArrayElement(InputColumnKnimeNamePersistor.class)
        String m_knimeName = "";

        static final class SelectedKnimeNameRef implements ParameterReference<String> {
        }

        @Widget(title = "Java Field Name", description = "Java variable name for this column")
        @PersistArrayElement(InputColumnJavaNamePersistor.class)
        String m_javaName = "";

        @Widget(title = "Java Type", description = "Java type for conversion")
        @ChoicesProvider(InputColumnJavaTypeChoicesProvider.class)
        @PersistArrayElement(InputColumnJavaTypePersistor.class)
        @ValueProvider(InputColumnJavaTypeProvider.class)
        @ValueReference(SelectedJavaTypeRef.class)
        String m_javaType = "";

        static final class SelectedJavaTypeRef implements ParameterReference<String> {
        }

        // Internal field: the converter factory ID for the selected column/type combination,
        // computed by ConverterIDValueProvider and used when saving back to NodeSettings.
        @PersistArrayElement(InputColumnConverterFactoryPersistor.class)
        @ValueProvider(ConverterIDValueProvider.class)
        String m_converterFactoryId = "";

        // Internal field: the KNIME DataType of the selected column, used when saving back to
        // NodeSettings to reconstruct the full DataType including element types for collections.
        @PersistArrayElement(InputColumnKnimeTypePersistor.class)
        DataType m_knimeType = null;

        /**
         * Provides Java type choices for input columns based on available converters for the selected column's data
         * type.
         */
        static final class InputColumnJavaTypeChoicesProvider implements StringChoicesProvider {
            private Supplier<String> m_selectedColumnSupplier;

            @Override
            public void init(final StateProviderInitializer initializer) {
                initializer.computeBeforeOpenDialog();
                m_selectedColumnSupplier = initializer.computeFromValueSupplier(SelectedKnimeNameRef.class);
            }

            @Override
            public List<String> choices(final NodeParametersInput context) {
                var selectedColumnName = m_selectedColumnSupplier.get();
                if (context.getInTableSpec(0).isEmpty()) {
                    // No input spec -> no type selection
                    return Collections.emptyList();
                }

                var tableSpec = context.getInTableSpec(0).get();
                var selectedColumnSpec = tableSpec.getColumnSpec(selectedColumnName);

                if (selectedColumnSpec == null) {
                    // Selected column is not present in input table
                    return Collections.emptyList();
                }

                return ConverterUtil.getFactoriesForSourceType(selectedColumnSpec.getType()).stream()
                    .filter(factory -> JavaSnippet.getBuildPathFromCache(factory.getIdentifier()) != null) //
                    .map(converter -> converter.getDestinationType().getSimpleName()).toList();
            }
        }

        /**
         * Provides the currently selected Java type (simple name) for the selected input column.
         * Preserves the stored value (which may be a fully qualified class name from legacy settings)
         * if it still matches one of the available choices. Only falls back to the first available
         * type when the stored value is absent or no longer valid.
         */
        static final class InputColumnJavaTypeProvider implements StateProvider<String> {
            private Supplier<String> m_selectedColumnSupplier;

            private Supplier<String> m_javaTypeSupplier;

            @Override
            public void init(final StateProviderInitializer initializer) {
                initializer.computeBeforeOpenDialog();
                m_selectedColumnSupplier = initializer.computeFromValueSupplier(SelectedKnimeNameRef.class);
                // Read the currently persisted value so we can preserve the user's selection.
                m_javaTypeSupplier = initializer.getValueSupplier(SelectedJavaTypeRef.class);
            }

            @Override
            public String computeState(final NodeParametersInput context) throws StateComputationFailureException {
                String selectedColumnName = m_selectedColumnSupplier.get();

                if (context.getInTableSpec(0).isEmpty()) {
                    throw new StateComputationFailureException();
                }

                var tableSpec = context.getInTableSpec(0).get();
                var selectedColumnSpec = tableSpec.getColumnSpec(selectedColumnName);

                if (selectedColumnSpec == null) {
                    throw new StateComputationFailureException();
                }

                var availableTypes = ConverterUtil.getFactoriesForSourceType(selectedColumnSpec.getType()).stream()
                    .filter(factory -> JavaSnippet.getBuildPathFromCache(factory.getIdentifier()) != null) //
                    .map(converter -> converter.getDestinationType().getSimpleName()) //
                    .toList();

                // Preserve the stored type selection if it is still valid. The persisted value may be a
                // fully qualified class name (FQN) from legacy settings – extract the simple name first.
                String stored = m_javaTypeSupplier.get();
                if (stored != null && !stored.isEmpty()) {
                    String storedSimple = toSimpleName(stored);
                    if (availableTypes.contains(storedSimple)) {
                        return storedSimple;
                    }
                }

                if (availableTypes.isEmpty()) {
                    throw new StateComputationFailureException();
                }

                return availableTypes.get(0);
            }
        }

        /**
         * Provides the Converter Factory ID for the selected combination of input column type and java variable type
         */
        static final class ConverterIDValueProvider implements StateProvider<String> {
            private Supplier<String> m_selectedColumnSupplier;

            private Supplier<String> m_selectedJavaTypeSupplier;

            @Override
            public void init(final StateProviderInitializer initializer) {
                initializer.computeBeforeOpenDialog();
                m_selectedColumnSupplier = initializer.computeFromValueSupplier(SelectedKnimeNameRef.class);
                m_selectedJavaTypeSupplier = initializer.computeFromValueSupplier(SelectedJavaTypeRef.class);
            }

            @Override
            public String computeState(final NodeParametersInput context) throws StateComputationFailureException {
                String selectedColumnName = m_selectedColumnSupplier.get();
                String selectedJavaType = m_selectedJavaTypeSupplier.get();

                if (context.getInTableSpec(0).isEmpty()) {
                    throw new StateComputationFailureException();
                }

                var tableSpec = context.getInTableSpec(0).get();
                var selectedColumnSpec = tableSpec.getColumnSpec(selectedColumnName);

                if (selectedColumnSpec == null) {
                    throw new StateComputationFailureException();
                }

                var converter = ConverterUtil.getFactoriesForSourceType(selectedColumnSpec.getType()).stream()
                    .filter(factory -> JavaSnippet.getBuildPathFromCache(factory.getIdentifier()) != null) //
                    .filter(factory -> factory.getDestinationType().getSimpleName().equals(selectedJavaType)) //
                    .findFirst();

                if (converter.isEmpty()) {
                    throw new StateComputationFailureException();
                }

                return converter.get().getIdentifier();
            }
        }
    }

    private abstract static class InColListElementPersistor extends JavaListElementPersistor<InputColumnField, InCol> {
        InColListElementPersistor() {
            super(JavaSnippetSettings.IN_COLS);
        }

        @Override
        protected JavaFieldList<InCol> createList() {
            return new InColList();
        }
    }

    private static final class InputColumnKnimeNamePersistor extends InColListElementPersistor {
        @Override
        protected String getFieldFromItem(final InCol inCol) {
            return inCol.getKnimeName();
        }

        @Override
        public void save(final String param, final InputColumnField saveDTO) {
            saveDTO.m_knimeName = param;
        }
    }

    private static final class InputColumnJavaNamePersistor extends InColListElementPersistor {
        @Override
        protected String getFieldFromItem(final InCol inCol) {
            return inCol.getJavaName();
        }

        @Override
        public void save(final String param, final InputColumnField saveDTO) {
            saveDTO.m_javaName = param;
        }
    }

    private static final class InputColumnJavaTypePersistor extends InColListElementPersistor {
        @Override
        protected String getFieldFromItem(final InCol inCol) {
            // Load as simple name to match what InputColumnJavaTypeProvider returns
            // (computeBeforeOpenDialog). Using FQN here would cause the framework to detect a
            // change on dialog open (FQN -> simple name) and mark settings as dirty.
            return inCol.getJavaType() != null ? inCol.getJavaType().getSimpleName() : "";
        }

        @Override
        public void save(final String param, final InputColumnField saveDTO) {
            saveDTO.m_javaType = param;
        }
    }

    private static final class InputColumnConverterFactoryPersistor extends InColListElementPersistor {
        @Override
        protected String getFieldFromItem(final InCol inCol) {
            return inCol.getConverterFactoryId();
        }

        @Override
        public void save(final String param, final InputColumnField saveDTO) {
            saveDTO.m_converterFactoryId = param;
        }
    }

    /**
     * Persistor for the KNIME DataType of an input column. Stores the full {@link DataType} object
     * (not just the cell-class FQN) so that collection types such as {@code ListCell<XMLCell>}
     * round-trip correctly.  The previous String-based approach lost element-type information for
     * collection DataTypes.
     */
    private static final class InputColumnKnimeTypePersistor
        implements ElementFieldPersistor<DataType, Integer, InputColumnField> {

        @Override
        public DataType load(final NodeSettingsRO settings, final Integer loadContext)
            throws InvalidSettingsException {
            var inColList = new InColList();
            if (settings.containsKey(JavaSnippetSettings.IN_COLS)) {
                inColList.loadSettings(settings.getConfig(JavaSnippetSettings.IN_COLS));
                if (loadContext < inColList.size()) {
                    return inColList.get(loadContext).getDataType();
                }
            }
            return null;
        }

        @Override
        public void save(final DataType param, final InputColumnField saveDTO) {
            saveDTO.m_knimeType = param;
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{JavaSnippetSettings.IN_COLS}};
        }
    }

    // ---------------------------------------------------------------------------------
    // Output Columns
    // ---------------------------------------------------------------------------------

    /**
     * Represents an output column field definition.
     */
    public static final class OutputColumnField implements NodeParameters {

        /**
         * Controls whether the Java snippet output is appended as a new column or replaces an existing one.
         */
        enum ColumnMode {
                @Label(value = "Append", description = "Append a new column with the given name.")
                APPEND, //
                @Label(value = "Replace", description = "Replace an existing column with the produced value.")
                REPLACE;

            interface Ref extends ParameterReference<ColumnMode> {
            }

            static final class IsAppend implements EffectPredicateProvider {
                @Override
                public EffectPredicate init(final PredicateInitializer i) {
                    return i.getEnum(Ref.class).isOneOf(APPEND);
                }
            }

            static final class IsReplace implements EffectPredicateProvider {
                @Override
                public EffectPredicate init(final PredicateInitializer i) {
                    return i.getEnum(Ref.class).isOneOf(REPLACE);
                }
            }
        }

        @Widget(title = "Column Mode",
            description = "Whether to append a new column or replace an existing one with the produced value.")
        @ValueSwitchWidget
        @ValueReference(ColumnMode.Ref.class)
        @PersistArrayElement(OutputColumnModePersistor.class)
        ColumnMode m_columnMode = ColumnMode.APPEND;

        @Widget(title = "New Column Name", description = "Name of the new output column to append.")
        @Effect(predicate = ColumnMode.IsAppend.class, type = EffectType.SHOW)
        @PersistArrayElement(OutputColumnNamePersistor.class)
        String m_knimeName = "";

        @Widget(title = "Replace Column",
            description = "The existing column to replace with the value produced by the Java field.")
        @Effect(predicate = ColumnMode.IsReplace.class, type = EffectType.SHOW)
        @ChoicesProvider(AllColumnsProvider.class)
        @PersistArrayElement(OutputColumnReplaceColumnPersistor.class)
        String m_replaceColumn = "";

        @Widget(title = "Java Field Name", description = "Java field name that provides the value")
        @PersistArrayElement(OutputColumnJavaNamePersistor.class)
        String m_javaName = "";

        @Widget(title = "KNIME Type",
            description = "The KNIME data type of the output column. The available Java types are determined by the"
                + " converters registered for this type.")
        @ChoicesProvider(OutputColumnKnimeTypeChoicesProvider.class)
        @ValueReference(SelectedKnimeTypeRef.class)
        @PersistArrayElement(OutputColumnDataTypePersistor.class)
        DataType m_knimeType = null;

        static final class SelectedKnimeTypeRef implements ParameterReference<DataType> {
        }

        @Widget(title = "Java Type",
            description = "The Java type of the value provided by the Java field. The converter factory"
                + " for the selected KNIME type and Java type combination is used for conversion.")
        @ChoicesProvider(OutputColumnJavaTypeChoicesProvider.class)
        @ValueProvider(OutputColumnJavaTypeProvider.class)
        @ValueReference(SelectedJavaTypeRef.class)
        @PersistArrayElement(OutputColumnJavaTypePersistor.class)
        String m_javaType = "";

        static final class SelectedJavaTypeRef implements ParameterReference<String> {
        }

        @Widget(title = "Is Collection", description = "Output is a collection type")
        @PersistArrayElement(OutputColumnIsArrayPersistor.class)
        boolean m_isArray = false;

        // Internal field: converter factory ID derived from the selected KNIME type and Java type combination.
        @PersistArrayElement(OutputColumnConverterFactoryPersistor.class)
        @ValueProvider(OutputColumnConverterFactoryIdProvider.class)
        String m_converterFactoryId = "";

        /**
         * Provides all KNIME data types for which at least one converter factory with a resolvable build path exists.
         */
        static final class OutputColumnKnimeTypeChoicesProvider implements DataTypeChoicesProvider {
            @Override
            public List<DataType> choices(final NodeParametersInput context) {
                return ConverterUtil.getAllDestinationDataTypes().stream()
                    .filter(type -> ConverterUtil.getFactoriesForDestinationType(type).stream()
                        .anyMatch(f -> JavaSnippet.getBuildPathFromCache(f.getIdentifier()) != null))
                    .toList();
            }
        }

        /**
         * Provides the Java source type simple names for converters that produce the currently selected KNIME type.
         */
        static final class OutputColumnJavaTypeChoicesProvider implements StringChoicesProvider {
            private Supplier<DataType> m_knimeTypeSupplier;

            @Override
            public void init(final StateProviderInitializer initializer) {
                initializer.computeBeforeOpenDialog();
                m_knimeTypeSupplier = initializer.computeFromValueSupplier(SelectedKnimeTypeRef.class);
            }

            @Override
            public List<String> choices(final NodeParametersInput context) {
                var knimeType = m_knimeTypeSupplier.get();
                if (knimeType == null) {
                    return Collections.emptyList();
                }
                return ConverterUtil.getFactoriesForDestinationType(knimeType).stream()
                    .filter(f -> JavaSnippet.getBuildPathFromCache(f.getIdentifier()) != null)
                    .map(f -> f.getSourceType().getSimpleName())
                    .toList();
            }
        }

        /**
         * Provides the currently selected Java type (simple name) for the selected KNIME output type.
         * Preserves the stored value (which may be a fully qualified class name from legacy settings)
         * if it still matches one of the available choices for the currently selected KNIME type.
         * Falls back to the first available type when the KNIME type changes and the stored Java type
         * is no longer valid, or when the stored value is absent.
         */
        static final class OutputColumnJavaTypeProvider implements StateProvider<String> {
            private Supplier<DataType> m_knimeTypeSupplier;

            private Supplier<String> m_javaTypeSupplier;

            @Override
            public void init(final StateProviderInitializer initializer) {
                initializer.computeBeforeOpenDialog();
                m_knimeTypeSupplier = initializer.computeFromValueSupplier(SelectedKnimeTypeRef.class);
                // Read the currently persisted value so we can preserve the user's selection.
                m_javaTypeSupplier = initializer.getValueSupplier(SelectedJavaTypeRef.class);
            }

            @Override
            public String computeState(final NodeParametersInput context) throws StateComputationFailureException {
                var knimeType = m_knimeTypeSupplier.get();
                if (knimeType == null) {
                    throw new StateComputationFailureException();
                }

                var availableTypes = ConverterUtil.getFactoriesForDestinationType(knimeType).stream()
                    .filter(f -> JavaSnippet.getBuildPathFromCache(f.getIdentifier()) != null)
                    .map(f -> f.getSourceType().getSimpleName())
                    .toList();

                // Preserve the stored type selection if it is still valid for the current KNIME type.
                // The persisted value may be a FQN (legacy) – extract the simple name first.
                String stored = m_javaTypeSupplier.get();
                if (stored != null && !stored.isEmpty()) {
                    String storedSimple = toSimpleName(stored);
                    if (availableTypes.contains(storedSimple)) {
                        return storedSimple;
                    }
                }

                if (availableTypes.isEmpty()) {
                    throw new StateComputationFailureException();
                }

                return availableTypes.get(0);
            }
        }

        /**
         * Computes the converter factory ID for the selected KNIME type and Java type combination.
         */
        static final class OutputColumnConverterFactoryIdProvider implements StateProvider<String> {
            private Supplier<DataType> m_knimeTypeSupplier;

            private Supplier<String> m_javaTypeSupplier;

            @Override
            public void init(final StateProviderInitializer initializer) {
                initializer.computeBeforeOpenDialog();
                m_knimeTypeSupplier = initializer.computeFromValueSupplier(SelectedKnimeTypeRef.class);
                m_javaTypeSupplier = initializer.computeFromValueSupplier(SelectedJavaTypeRef.class);
            }

            @Override
            public String computeState(final NodeParametersInput context) throws StateComputationFailureException {
                var knimeType = m_knimeTypeSupplier.get();
                var javaType = m_javaTypeSupplier.get();
                if (knimeType == null || javaType == null || javaType.isEmpty()) {
                    throw new StateComputationFailureException();
                }
                return ConverterUtil.getFactoriesForDestinationType(knimeType).stream()
                    .filter(f -> JavaSnippet.getBuildPathFromCache(f.getIdentifier()) != null)
                    .filter(f -> f.getSourceType().getSimpleName().equals(javaType))
                    .map(f -> f.getIdentifier())
                    .findFirst()
                    .orElseThrow(StateComputationFailureException::new);
            }
        }
    }

    private abstract static class OutColListElementPersistor<T>
        implements ElementFieldPersistor<T, Integer, OutputColumnField> {

        protected abstract T getFieldFromItem(final OutCol outCol);

        @Override
        public T load(final NodeSettingsRO settings, final Integer loadContext) throws InvalidSettingsException {
            var outColList = new JavaFieldList.OutColList();
            if (settings.containsKey(JavaSnippetSettings.OUT_COLS)) {
                outColList.loadSettings(settings.getConfig(JavaSnippetSettings.OUT_COLS));
                return loadContext < outColList.size() ? getFieldFromItem(outColList.get(loadContext)) : null;
            }
            return null;
        }

        @Override
        public abstract void save(final T param, final OutputColumnField saveDTO);

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{JavaSnippetSettings.OUT_COLS}};
        }
    }

    private static final class OutputColumnNamePersistor extends OutColListElementPersistor<String> {
        @Override
        protected String getFieldFromItem(final OutCol outCol) {
            return outCol.getKnimeName();
        }

        @Override
        public void save(final String param, final OutputColumnField saveDTO) {
            saveDTO.m_knimeName = param;
        }
    }

    private static final class OutputColumnJavaNamePersistor extends OutColListElementPersistor<String> {
        @Override
        protected String getFieldFromItem(final OutCol outCol) {
            return outCol.getJavaName();
        }

        @Override
        public void save(final String param, final OutputColumnField saveDTO) {
            saveDTO.m_javaName = param;
        }
    }

    private static final class OutputColumnJavaTypePersistor extends OutColListElementPersistor<String> {
        @Override
        protected String getFieldFromItem(final OutCol outCol) {
            // Load as simple name to match what OutputColumnJavaTypeProvider returns
            // (computeBeforeOpenDialog). Using FQN here would cause the framework to detect a
            // change on dialog open (FQN -> simple name) and mark settings as dirty.
            if (outCol.getJavaType() != null) {
                return outCol.getJavaType().getSimpleName();
            }
            return "";
        }

        @Override
        public void save(final String param, final OutputColumnField saveDTO) {
            saveDTO.m_javaType = param;
        }
    }

    private static final class OutputColumnModePersistor extends OutColListElementPersistor<OutputColumnField.ColumnMode> {
        @Override
        protected OutputColumnField.ColumnMode getFieldFromItem(final OutCol outCol) {
            return outCol.getReplaceExisting() ? OutputColumnField.ColumnMode.REPLACE
                : OutputColumnField.ColumnMode.APPEND;
        }

        @Override
        public void save(final OutputColumnField.ColumnMode param, final OutputColumnField saveDTO) {
            saveDTO.m_columnMode = param != null ? param : OutputColumnField.ColumnMode.APPEND;
        }
    }

    private static final class OutputColumnReplaceColumnPersistor extends OutColListElementPersistor<String> {
        @Override
        protected String getFieldFromItem(final OutCol outCol) {
            // When replacing an existing column, knimeName holds the name of the column to replace.
            return outCol.getReplaceExisting() ? outCol.getKnimeName() : "";
        }

        @Override
        public void save(final String param, final OutputColumnField saveDTO) {
            saveDTO.m_replaceColumn = param;
        }
    }

    private static final class OutputColumnIsArrayPersistor extends OutColListElementPersistor<Boolean> {
        @Override
        protected Boolean getFieldFromItem(final OutCol outCol) {
            return outCol.getDataType() != null && outCol.getDataType().isCollectionType();
        }

        @Override
        public void save(final Boolean param, final OutputColumnField saveDTO) {
            saveDTO.m_isArray = param;
        }
    }

    private static final class OutputColumnConverterFactoryPersistor extends OutColListElementPersistor<String> {
        @Override
        protected String getFieldFromItem(final OutCol outCol) {
            return outCol.getConverterFactoryId();
        }

        @Override
        public void save(final String param, final OutputColumnField saveDTO) {
            saveDTO.m_converterFactoryId = param;
        }
    }

    private static final class OutputColumnDataTypePersistor extends OutColListElementPersistor<DataType> {
        @Override
        protected DataType getFieldFromItem(final OutCol outCol) {
            return outCol.getDataType();
        }

        @Override
        public void save(final DataType param, final OutputColumnField saveDTO) {
            saveDTO.m_knimeType = param;
        }
    }

    // ---------------------------------------------------------------------------------
    // Output Variables
    // ---------------------------------------------------------------------------------

    /**
     * Represents an output flow variable field definition.
     */
    public static final class OutputFlowVariableField implements NodeParameters {
        @Widget(title = "Flow Variable Name", description = "The output flow variable name")
        @PersistArrayElement(OutputFlowVariableNamePersistor.class)
        String m_knimeName = "";

        @Widget(title = "Java Field Name", description = "Java field name that provides the value")
        @PersistArrayElement(OutputFlowVariableJavaNamePersistor.class)
        String m_javaName = "";

        @Widget(title = "Java Type", description = "Java type to convert from")
        @ChoicesProvider(FlowVariableJavaTypeChoicesProvider.class)
        @PersistArrayElement(OutputFlowVariableJavaTypePersistor.class)
        String m_javaType = "";

        // Hidden field: flow variable type
        @PersistArrayElement(OutputFlowVariableKnimeTypePersistor.class)
        String m_flowVarType = "";

        /**
         * Provides Java type choices for flow variables.
         */
        static final class FlowVariableJavaTypeChoicesProvider implements StringChoicesProvider {
            @Override
            public List<String> choices(final NodeParametersInput context) {
                TypeProvider typeProvider = TypeProvider.getDefault();
                Set<String> types = new LinkedHashSet<>();
                for (FlowVariable.Type type : typeProvider.getTypes()) {
                    TypeConverter converter = typeProvider.getTypeConverter(type);
                    for (Class<?> javaType : converter.canProvideJavaTypes()) {
                        types.add(javaType.getSimpleName());
                    }
                }
                return new ArrayList<>(types);
            }
        }
    }

    private abstract static class OutVarListElementPersistor
        extends JavaListElementPersistor<OutputFlowVariableField, OutVar> {

        OutVarListElementPersistor() {
            super(JavaSnippetSettings.OUT_VARS);
        }

        @Override
        protected JavaFieldList<OutVar> createList() {
            return new JavaFieldList.OutVarList();
        }
    }

    private static final class OutputFlowVariableNamePersistor extends OutVarListElementPersistor {
        @Override
        protected String getFieldFromItem(final OutVar outVar) {
            return outVar.getKnimeName();
        }

        @Override
        public void save(final String param, final OutputFlowVariableField saveDTO) {
            saveDTO.m_knimeName = param;
        }
    }

    private static final class OutputFlowVariableJavaNamePersistor extends OutVarListElementPersistor {
        @Override
        protected String getFieldFromItem(final OutVar outVar) {
            return outVar.getJavaName();
        }

        @Override
        public void save(final String param, final OutputFlowVariableField saveDTO) {
            saveDTO.m_javaName = param;
        }
    }

    private static final class OutputFlowVariableJavaTypePersistor extends OutVarListElementPersistor {
        @Override
        protected String getFieldFromItem(final OutVar outVar) {
            return outVar.getJavaType() != null ? outVar.getJavaType().getSimpleName() : "";
        }

        @Override
        public void save(final String param, final OutputFlowVariableField saveDTO) {
            saveDTO.m_javaType = param;
        }
    }

    private static final class OutputFlowVariableKnimeTypePersistor extends OutVarListElementPersistor {
        @Override
        protected String getFieldFromItem(final OutVar outVar) {
            return outVar.getFlowVarType() != null ? outVar.getFlowVarType().toString() : "";
        }

        @Override
        public void save(final String param, final OutputFlowVariableField saveDTO) {
            saveDTO.m_flowVarType = param;
        }
    }

    /**
     * Represents a JAR file entry.
     */
    public static final class JarFileEntry implements NodeParameters {
        @Widget(title = "JAR Path/URL", description = "File path or KNIME URL to JAR file")
        @FileReaderWidget(fileExtensions = {"jar"})
        @PersistArrayElement(JarFilePathPersistor.class)
        FileSelection m_path = new FileSelection();
    }

    private static final class JarFilePathPersistor implements ElementFieldPersistor<FileSelection, Integer, JarFileEntry> {
        @Override
        public FileSelection load(final NodeSettingsRO settings, final Integer loadContext) throws InvalidSettingsException {
            String[] jarFiles = settings.getStringArray(JavaSnippetSettings.JAR_FILES, new String[0]);
            if (loadContext >= jarFiles.length) {
                return null;
            }
            final String path = jarFiles[loadContext];
            // Detect KNIME URLs vs local paths
            final FSCategory category = path.startsWith("knime://") ? FSCategory.CUSTOM_URL : FSCategory.LOCAL;
            return new FileSelection(new FSLocation(category, path));
        }

        @Override
        public void save(final FileSelection param, final JarFileEntry saveDTO) {
            saveDTO.m_path = param;
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{JavaSnippetSettings.JAR_FILES}};
        }
    }

    /**
     * Represents an OSGi bundle entry.
     */
    public static final class BundleEntry implements NodeParameters {
        @Widget(title = "Bundle", description = "OSGi bundle symbolic name with version")
        @ChoicesProvider(InstalledBundlesChoicesProvider.class)
        @PersistArrayElement(BundleNamePersistor.class)
        String m_bundle = "";
    }

    /**
     * Provides a list of all installed OSGi bundles as choices for the bundle dropdown.
     */
    static final class InstalledBundlesChoicesProvider implements StringChoicesProvider {
        @Override
        public List<String> choices(final NodeParametersInput context) {
            final var bundle = FrameworkUtil.getBundle(InstalledBundlesChoicesProvider.class);
            if (bundle == null) {
                return Collections.emptyList();
            }
            final BundleContext ctx = bundle.getBundleContext();
            if (ctx == null) {
                return Collections.emptyList();
            }
            return Arrays.stream(ctx.getBundles())
                .filter(b -> b.getSymbolicName() != null)
                .map(b -> b.getSymbolicName() + " " + b.getVersion().toString())
                .sorted()
                .toList();
        }
    }

    private static final class BundleNamePersistor implements ElementFieldPersistor<String, Integer, BundleEntry> {
        @Override
        public String load(final NodeSettingsRO settings, final Integer loadContext) throws InvalidSettingsException {
            String[] bundles = settings.getStringArray(JavaSnippetSettings.BUNDLES, new String[0]);
            return loadContext < bundles.length ? bundles[loadContext] : null;
        }

        @Override
        public void save(final String param, final BundleEntry saveDTO) {
            saveDTO.m_bundle = param;
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{JavaSnippetSettings.BUNDLES}};
        }
    }

    // =================================================================================
    // Array Persistors
    // These convert between the new array format and legacy list formats
    // =================================================================================

    /**
     * Persistor for input flow variables (InVarList <-> InputFlowVariableField[]).
     */
    public static final class InVarListPersistor implements ArrayPersistor<Integer, InputFlowVariableField> {
        private static final String CONFIG_KEY = JavaSnippetSettings.IN_VARS;

        @Override
        public void save(final List<InputFlowVariableField> param, final NodeSettingsWO settings) {
            var inVarList = new JavaFieldList.InVarList();
            if (param != null) {
                for (InputFlowVariableField field : param) {
                    var inVar = new InVar();
                    inVar.setKnimeName(field.m_knimeName);
                    inVar.setJavaName(field.m_javaName);

                    // Set flow var type: m_knimeType stores the FlowVariable.Type enum name when loaded from
                    // legacy settings (e.g. "STRING"). Fall back to deriving the type from m_javaType when
                    // m_knimeType is absent or does not match an enum constant (e.g. for newly added entries).
                    if (field.m_knimeType != null && !field.m_knimeType.isEmpty()) {
                        try {
                            inVar.setFlowVarType(FlowVariable.Type.valueOf(field.m_knimeType));
                        } catch (IllegalArgumentException e) {
                            inVar.setFlowVarType(deriveFlowVarTypeFromJavaType(field.m_javaType));
                        }
                    } else {
                        inVar.setFlowVarType(deriveFlowVarTypeFromJavaType(field.m_javaType));
                    }

                    // Always set java type (required by JavaField.saveSettings)
                    Class<?> inVarJavaClass = (field.m_javaType != null && !field.m_javaType.isEmpty())
                        ? resolveJavaTypeBySimpleName(field.m_javaType) : String.class;
                    inVar.setJavaType(inVarJavaClass);

                    inVarList.add(inVar);
                }
            }
            inVarList.saveSettings(settings.addConfig(CONFIG_KEY));
        }

        @Override
        public int getArrayLength(final NodeSettingsRO settings) throws InvalidSettingsException {
            if (settings.containsKey(CONFIG_KEY)) {
                var inVarList = new JavaFieldList.InVarList();
                inVarList.loadSettings(settings.getConfig(CONFIG_KEY));
                return inVarList.size();
            }
            return 0;
        }

        @Override
        public Integer createElementLoadContext(final int index) {
            return index;
        }

        @Override
        public InputFlowVariableField createElementSaveDTO(final int index) {
            return new InputFlowVariableField();
        }
    }

    /**
     * Maps a Java type simple name (e.g. "Integer", "Double") to the corresponding {@link FlowVariable.Type}.
     * Defaults to {@link FlowVariable.Type#STRING} for unknown types.
     */
    /**
     * Extracts the simple name from a potentially fully qualified class name.
     * E.g. "java.lang.String" → "String", "String" → "String".
     */
    private static String toSimpleName(final String potentiallyQualifiedName) {
        if (potentiallyQualifiedName == null || potentiallyQualifiedName.isEmpty()) {
            return potentiallyQualifiedName;
        }
        int lastDot = potentiallyQualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? potentiallyQualifiedName.substring(lastDot + 1) : potentiallyQualifiedName;
    }

    private static FlowVariable.Type deriveFlowVarTypeFromJavaType(final String javaTypeSimpleName) {
        if (javaTypeSimpleName == null) {
            return FlowVariable.Type.STRING;
        }
        return switch (javaTypeSimpleName) {
            case "Integer", "int" -> FlowVariable.Type.INTEGER;
            case "Double", "double" -> FlowVariable.Type.DOUBLE;
            default -> FlowVariable.Type.STRING;
        };
    }

    /**
     * Resolves a simple Java type name (e.g. "String") to the corresponding Class by searching all known converter
     * source types.
     */
    private static Class<?> resolveJavaTypeBySimpleName(final String simpleName) {
        // Check all destination types from converters for a matching simple name
        for (final DataType destType : ConverterUtil.getAllDestinationDataTypes()) {
            for (var factory : ConverterUtil.getFactoriesForDestinationType(destType)) {
                if (factory.getSourceType().getSimpleName().equals(simpleName)) {
                    return factory.getSourceType();
                }
            }
        }
        // Fallback: try common packages
        for (String pkg : new String[]{"java.lang.", "java.util.", ""}) {
            try {
                return Class.forName(pkg + simpleName);
            } catch (ClassNotFoundException e) {
                // try next
            }
        }
        return String.class;
    }

    /**
     * Persistor for output columns (OutColList <-> OutputColumnField[]).
     */
    public static final class OutColListPersistor implements ArrayPersistor<Integer, OutputColumnField> {
        private static final String CONFIG_KEY = JavaSnippetSettings.OUT_COLS;

        @Override
        public void save(final List<OutputColumnField> param, final NodeSettingsWO settings) {
            var outColList = new JavaFieldList.OutColList();
            if (param != null) {
                for (OutputColumnField field : param) {
                    var outCol = new OutCol();
                    outCol.setKnimeName(field.m_columnMode == OutputColumnField.ColumnMode.REPLACE
                        ? field.m_replaceColumn : field.m_knimeName);
                    outCol.setJavaName(field.m_javaName);
                    outCol.setReplaceExisting(field.m_columnMode == OutputColumnField.ColumnMode.REPLACE);

                    // Resolve converter factory. The factory ID is computed by OutputColumnConverterFactoryIdProvider,
                    // but as a fallback we also try to match by KNIME type + java source type.
                    Optional<JavaToDataCellConverterFactory<?>> outFactory = Optional.empty();
                    if (field.m_converterFactoryId != null && !field.m_converterFactoryId.isEmpty()) {
                        outFactory = ConverterUtil.getJavaToDataCellConverterFactory(field.m_converterFactoryId);
                    }
                    if (outFactory.isEmpty() && field.m_knimeType != null
                        && field.m_javaType != null && !field.m_javaType.isEmpty()) {
                        // m_javaType may be either a simple name (after ValueProvider ran) or a fully
                        // qualified class name (persisted by the legacy persistor before the dialog was
                        // opened).  Normalise to simple name for the comparison so both cases work.
                        final String javaSimpleName = toSimpleName(field.m_javaType);
                        outFactory = ConverterUtil.getFactoriesForDestinationType(field.m_knimeType).stream()
                            .filter(f -> JavaSnippet.getBuildPathFromCache(f.getIdentifier()) != null)
                            .filter(f -> f.getSourceType().getSimpleName().equals(javaSimpleName))
                            .findFirst();
                    }
                    // Last resort: search all factories matching the java source class.
                    // Prefer a factory whose destination type equals the expected KNIME type so
                    // that OutCol.setConverterFactory() sets the correct m_knimeType and the nested
                    // 'Type' config written by JavaColumnField.saveSettings() matches the original.
                    // Falling back to any factory (without KNIME-type constraint) is kept as a
                    // safety net, but may write a different DataType if the plugin is not installed.
                    if (outFactory.isEmpty()) {
                        Class<?> outJavaClass = (field.m_javaType != null && !field.m_javaType.isEmpty())
                            ? resolveJavaTypeBySimpleName(field.m_javaType) : String.class;
                        var matchingFactories = ConverterUtil.getAllJavaToDataCellConverterFactories().stream()
                            .filter(f -> f.getSourceType().equals(outJavaClass))
                            .toList();
                        // Prefer factory whose destination equals the expected KNIME type
                        if (field.m_knimeType != null) {
                            outFactory = matchingFactories.stream()
                                .filter(f -> f.getDestinationType().equals(field.m_knimeType))
                                .findFirst();
                        }
                        // Fall back to any matching factory
                        if (outFactory.isEmpty()) {
                            outFactory = matchingFactories.stream().findFirst();
                        }
                    }
                    if (outFactory.isPresent()) {
                        // setConverterFactory also sets m_knimeType (= factory.getDestinationType()),
                        // ensuring the 'Type' config is written with the correct cell class and
                        // 'replaceExisting' is preserved in the final OutCol.saveSettings() call.
                        outCol.setConverterFactory(outFactory.get());
                    } else {
                        // Absolute last resort: no factory found at all (e.g. plugin not installed).
                        // setJavaType() leaves m_knimeType as null which means the 'Type' config is
                        // written with is_null=true – this is unavoidable without a factory.
                        outCol.setJavaType(resolveJavaTypeBySimpleName(
                            field.m_javaType != null && !field.m_javaType.isEmpty() ? field.m_javaType : "String"));
                    }

                    outColList.add(outCol);
                }
            }
            outColList.saveSettings(settings.addConfig(CONFIG_KEY));
        }

        @Override
        public int getArrayLength(final NodeSettingsRO settings) throws InvalidSettingsException {
            if (settings.containsKey(CONFIG_KEY)) {
                var outColList = new JavaFieldList.OutColList();
                outColList.loadSettings(settings.getConfig(CONFIG_KEY));
                return outColList.size();
            }
            return 0;
        }

        @Override
        public Integer createElementLoadContext(final int index) {
            return index;
        }

        @Override
        public OutputColumnField createElementSaveDTO(final int index) {
            return new OutputColumnField();
        }
    }

    /**
     * Persistor for output flow variables (OutVarList <-> OutputFlowVariableField[]).
     */
    public static final class OutVarListPersistor implements ArrayPersistor<Integer, OutputFlowVariableField> {
        private static final String CONFIG_KEY = JavaSnippetSettings.OUT_VARS;

        @Override
        public void save(final List<OutputFlowVariableField> param, final NodeSettingsWO settings) {
            var outVarList = new JavaFieldList.OutVarList();
            if (param != null) {
                for (OutputFlowVariableField field : param) {
                    var outVar = new OutVar();
                    outVar.setKnimeName(field.m_knimeName);
                    outVar.setJavaName(field.m_javaName);

                    // Resolve simple name back to fully qualified class
                    if (field.m_javaType != null && !field.m_javaType.isEmpty()) {
                        Class<?> javaClass = resolveJavaTypeBySimpleName(field.m_javaType);
                        outVar.setJavaType(javaClass);
                    } else {
                        outVar.setJavaType(String.class);
                    }

                    // Set flow variable type - must be set before saveSettings()
                    if (field.m_flowVarType != null && !field.m_flowVarType.isEmpty()) {
                        try {
                            outVar.setFlowVarType(FlowVariable.Type.valueOf(field.m_flowVarType));
                        } catch (IllegalArgumentException e) {
                            outVar.setFlowVarType(deriveFlowVarTypeFromJavaType(field.m_javaType));
                        }
                    } else {
                        outVar.setFlowVarType(deriveFlowVarTypeFromJavaType(field.m_javaType));
                    }

                    outVar.setReplaceExisting(false);
                    outVarList.add(outVar);
                }
            }
            outVarList.saveSettings(settings.addConfig(CONFIG_KEY));
        }

        @Override
        public int getArrayLength(final NodeSettingsRO settings) throws InvalidSettingsException {
            if (settings.containsKey(CONFIG_KEY)) {
                var outVarList = new JavaFieldList.OutVarList();
                outVarList.loadSettings(settings.getConfig(CONFIG_KEY));
                return outVarList.size();
            }
            return 0;
        }

        @Override
        public Integer createElementLoadContext(final int index) {
            return index;
        }

        @Override
        public OutputFlowVariableField createElementSaveDTO(final int index) {
            return new OutputFlowVariableField();
        }
    }

    /**
     * Persistor for input columns (InColList <-> InputColumnField[]).
     */
    public static final class InColListPersistor implements ArrayPersistor<Integer, InputColumnField> {
        private static final String CONFIG_KEY = JavaSnippetSettings.IN_COLS;

        @Override
        public void save(final List<InputColumnField> param, final NodeSettingsWO settings) {
            var inColList = new JavaFieldList.InColList();
            if (param != null) {
                for (InputColumnField field : param) {
                    var inCol = new InCol();
                    inCol.setKnimeName(field.m_knimeName);
                    inCol.setJavaName(field.m_javaName);

                    // Always set java type as baseline (required by JavaField.saveSettings)
                    Class<?> inJavaClass = (field.m_javaType != null && !field.m_javaType.isEmpty())
                        ? resolveJavaTypeBySimpleName(field.m_javaType) : String.class;
                    inCol.setJavaType(inJavaClass);

                    // Additionally set converter factory if the full DataType and factory ID are both
                    // available. Using the DataType object directly (instead of reconstructing from a
                    // cell-class FQN string) preserves element-type information for collection types.
                    if (field.m_knimeType != null && field.m_converterFactoryId != null
                        && !field.m_converterFactoryId.isEmpty()) {
                        try {
                            var factory =
                                ConverterUtil.getDataCellToJavaConverterFactory(field.m_converterFactoryId);
                            if (factory.isPresent()) {
                                inCol.setConverterFactory(field.m_knimeType, factory.get());
                            }
                        } catch (Exception e) {
                            // Leave java type set above as fallback
                        }
                    }

                    inColList.add(inCol);
                }
            }

            inColList.saveSettings(settings.addConfig(CONFIG_KEY));
        }

        @Override
        public int getArrayLength(final NodeSettingsRO settings) throws InvalidSettingsException {
            if (settings.containsKey(CONFIG_KEY)) {
                var inColList = new JavaFieldList.InColList();
                inColList.loadSettings(settings.getConfig(CONFIG_KEY));
                return inColList.size();
            }
            return 0;
        }

        @Override
        public Integer createElementLoadContext(final int index) {
            return index;
        }

        @Override
        public InputColumnField createElementSaveDTO(final int index) {
            return new InputColumnField();
        }
    }

    /**
     * Persistor for JAR files (String[] <-> JarFileEntry[]).
     */
    public static final class JarFilesPersistor implements ArrayPersistor<Integer, JarFileEntry> {
        private static final String CONFIG_KEY = JavaSnippetSettings.JAR_FILES;

        @Override
        public void save(final List<JarFileEntry> param, final NodeSettingsWO settings) {
            // NodeSettings.addStringArray writes a config/array-size sub-config which is the same
            // format used by the legacy JavaSnippetSettings.saveSettings() / loadSettings(), so
            // the persisted representation is preserved on a load/save round-trip.
            String[] paths =
                (param == null) ? new String[0] : param.stream().map(e -> e.m_path.getFSLocation().getPath()).toArray(String[]::new);
            settings.addStringArray(CONFIG_KEY, paths);
        }

        @Override
        public int getArrayLength(final NodeSettingsRO settings) throws InvalidSettingsException {
            String[] jarFiles = settings.getStringArray(CONFIG_KEY, new String[0]);
            return jarFiles.length;
        }

        @Override
        public Integer createElementLoadContext(final int index) {
            return index;
        }

        @Override
        public JarFileEntry createElementSaveDTO(final int index) {
            return new JarFileEntry();
        }
    }

    /**
     * Persistor for bundles (String[] <-> BundleEntry[]).
     */
    public static final class BundlesPersistor implements ArrayPersistor<Integer, BundleEntry> {
        private static final String CONFIG_KEY = JavaSnippetSettings.BUNDLES;

        @Override
        public void save(final List<BundleEntry> param, final NodeSettingsWO settings) {
            // NodeSettings.addStringArray writes a config/array-size sub-config matching the format
            // of JavaSnippetSettings.saveSettings()/loadSettings() (added in KNIME 3.6).
            String[] bundles =
                (param == null) ? new String[0] : param.stream().map(e -> e.m_bundle).toArray(String[]::new);
            settings.addStringArray(CONFIG_KEY, bundles);
        }

        @Override
        public int getArrayLength(final NodeSettingsRO settings) throws InvalidSettingsException {
            String[] bundles = settings.getStringArray(CONFIG_KEY, new String[0]);
            return bundles.length;
        }

        @Override
        public Integer createElementLoadContext(final int index) {
            return index;
        }

        @Override
        public BundleEntry createElementSaveDTO(final int index) {
            return new BundleEntry();
        }
    }

    // =================================================================================
    // Input Fields Section
    // =================================================================================

    @Section(title = "Input Columns")
    interface InputColumns {
    }

    @Section(title = "Input Variables")
    @After(InputColumns.class)
    interface InputVariables {
    }

    @Widget(title = "Input Columns", description = "Define which input columns to use and their Java field names")
    @Layout(InputColumns.class)
    @ArrayWidget(elementTitle = "Input column", addButtonText = "Add input column")
    @PersistArray(InColListPersistor.class)
    InputColumnField[] m_inputColumns;

    @Widget(title = "Input Flow Variables",
        description = "Define which flow variables to use and their Java field names")
    @Layout(InputVariables.class)
    @ArrayWidget(elementTitle = "Input flow variable", addButtonText = "Add input flow variable")
    @PersistArray(InVarListPersistor.class)
    InputFlowVariableField[] m_inputFlowVariables;

    // =================================================================================
    // Output Fields Section
    // =================================================================================

    @Section(title = "Output Columns")
    @After(InputVariables.class)
    interface OutputColumns {
    }

    @Section(title = "Output Variables")
    @After(OutputColumns.class)
    interface OutputVariables {
    }

    @Widget(title = "Output Columns", description = "Define output columns and their Java source fields")
    @Layout(OutputColumns.class)
    @ArrayWidget(elementTitle = "Output column", addButtonText = "Add output column")
    @PersistArray(OutColListPersistor.class)
    OutputColumnField[] m_outputColumns;

    @Widget(title = "Output Flow Variables", description = "Define output flow variables and their Java source fields")
    @Layout(OutputVariables.class)
    @ArrayWidget(elementTitle = "Output flow variable", addButtonText = "Add output flow variable")
    @PersistArray(OutVarListPersistor.class)
    OutputFlowVariableField[] m_outputFlowVariables;

    // =================================================================================
    // Libraries & Bundles Section (Placeholder)
    // =================================================================================

    @Section(title = "Libraries & Bundles", sideDrawer = true)
    @After(OutputVariables.class)
    @Advanced
    interface LibrariesAndBundlesSection {
        @Section(title = "JAR Files")
        interface JARFiles {
        }

        @Section(title = "OSGi Bundles")
        interface OSGIBundles {
        }
    }

    @Widget(title = "JAR Files", description = "External JAR files to include on the classpath")
    @Layout(LibrariesAndBundlesSection.JARFiles.class)
    @ArrayWidget(elementTitle = "JAR file", addButtonText = "Add JAR file")
    @PersistArray(JarFilesPersistor.class)
    JarFileEntry[] m_jarFiles;

    @Widget(title = "OSGi Bundles", description = "OSGi bundles to add to the classpath")
    @Layout(LibrariesAndBundlesSection.OSGIBundles.class)
    @ArrayWidget(elementTitle = "OSGi bundle", addButtonText = "Add OSGi bundle")
    @PersistArray(BundlesPersistor.class)
    BundleEntry[] m_bundles;
}
