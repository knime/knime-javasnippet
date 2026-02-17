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
import java.util.List;

import org.knime.base.node.jsnippet.type.ConverterUtil;
import org.knime.base.node.jsnippet.type.TypeProvider;
import org.knime.base.node.jsnippet.util.JavaFieldList;
import org.knime.base.node.jsnippet.util.field.InCol;
import org.knime.base.node.jsnippet.util.field.InVar;
import org.knime.base.node.jsnippet.util.field.OutCol;
import org.knime.base.node.jsnippet.util.field.OutVar;
import org.knime.core.data.DataType;
import org.knime.core.data.DataTypeRegistry;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.webui.node.dialog.defaultdialog.internal.persistence.ArrayPersistor;
import org.knime.core.webui.node.dialog.defaultdialog.internal.persistence.ElementFieldPersistor;
import org.knime.core.webui.node.dialog.defaultdialog.internal.persistence.PersistArray;
import org.knime.core.webui.node.dialog.defaultdialog.internal.persistence.PersistArrayElement;
import org.knime.node.parameters.Advanced;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.array.ArrayWidget;
import org.knime.node.parameters.layout.After;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.node.parameters.widget.choices.util.AllColumnsProvider;
import org.knime.node.parameters.widget.choices.util.AllFlowVariablesProvider;

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

    // =================================================================================
    // Field Type Inner Classes
    // These represent individual entries in the ArrayWidgets
    // =================================================================================

    /**
     * Represents an input column field definition.
     */
    public static final class InputColumnField implements NodeParameters {
        @Widget(title = "Column Name", description = "The input column to use")
        @ChoicesProvider(AllColumnsProvider.class)
        @PersistArrayElement(InputColumnNamePersistor.class)
        String m_columnName = "";

        @Widget(title = "Java Field Name", description = "Java variable name for this column")
        @PersistArrayElement(InputColumnJavaNamePersistor.class)
        String m_javaFieldName = "";

        @Widget(title = "Java Type", description = "Java type for conversion")
        @ChoicesProvider(InputColumnJavaTypeChoicesProvider.class)
        @PersistArrayElement(InputColumnJavaTypePersistor.class)
        String m_javaType = "";

        // Hidden field: converter factory ID
        @PersistArrayElement(InputColumnConverterFactoryPersistor.class)
        String m_converterFactoryId = "";

        // Hidden field: data type (KNIME column type)
        @PersistArrayElement(InputColumnDataTypePersistor.class)
        String m_dataTypeName = "";
    }

    /**
     * Represents an input flow variable field definition.
     */
    public static final class InputFlowVariableField implements NodeParameters {
        @Widget(title = "Flow Variable", description = "The input flow variable to use")
        @ChoicesProvider(AllFlowVariablesProvider.class)
        @PersistArrayElement(InputFlowVariableNamePersistor.class)
        String m_variableName = "";

        @Widget(title = "Java Field Name", description = "Java variable name for this flow variable")
        @PersistArrayElement(InputFlowVariableJavaNamePersistor.class)
        String m_javaFieldName = "";

        @Widget(title = "Java Type", description = "Java type for conversion")
        @ChoicesProvider(FlowVariableJavaTypeChoicesProvider.class)
        @PersistArrayElement(InputFlowVariableJavaTypePersistor.class)
        String m_javaType = "";

        // Hidden field: flow variable type
        @PersistArrayElement(InputFlowVariableTypePersistor.class)
        String m_flowVarType = "";
    }

    private static abstract class InVarListElementPersistor
        implements ElementFieldPersistor<String, Integer, InputFlowVariableField> {
        private final String m_configKey;

        InVarListElementPersistor(final String configKey) {
            m_configKey = configKey;
        }

        protected abstract String getFieldFromItem(final InVar inVar);

        @Override
        public String load(final NodeSettingsRO settings, final Integer loadContext) throws InvalidSettingsException {
            var inVarList = new JavaFieldList.InVarList();
            if (settings.containsKey(m_configKey)) {
                inVarList.loadSettings(settings.getConfig(m_configKey));
                return loadContext < inVarList.size() ? getFieldFromItem(inVarList.get(loadContext)) : null;
            }
            return null;
        }

        @Override
        public void save(final String param, final InputFlowVariableField saveDTO) {
            saveDTO.m_variableName = param;
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{m_configKey}};
        }

    }

    private static final class InputFlowVariableNamePersistor extends InVarListElementPersistor {
        InputFlowVariableNamePersistor() {
            super("variableName");
        }

        @Override
        protected String getFieldFromItem(final InVar inVar) {
            return inVar.getKnimeName();
        }
    }

    private static final class InputFlowVariableJavaNamePersistor extends InVarListElementPersistor {
        InputFlowVariableJavaNamePersistor() {
            super("javaFieldName");
        }

        @Override
        protected String getFieldFromItem(final InVar inVar) {
            return inVar.getJavaName();
        }
    }

    private static final class InputFlowVariableJavaTypePersistor extends InVarListElementPersistor {
        InputFlowVariableJavaTypePersistor() {
            super("javaType");
        }

        @Override
        protected String getFieldFromItem(final InVar inVar) {
            return inVar.getJavaType() != null ? inVar.getJavaType().getName() : "";
        }
    }

    private static final class InputFlowVariableTypePersistor extends InVarListElementPersistor {
        InputFlowVariableTypePersistor() {
            super("inVars");
        }

        @Override
        protected String getFieldFromItem(final InVar inVar) {
            return inVar.getFlowVarType() != null ? inVar.getFlowVarType().toString() : "";
        }
    }

    /**
     * Provides Java type choices for flow variables.
     */
    static final class FlowVariableJavaTypeChoicesProvider implements StringChoicesProvider {
        @Override
        public List<String> choices(final NodeParametersInput context) {
            // Return all common Java types that converters might support
            TypeProvider typeProvider = TypeProvider.getDefault();
            List<String> types = new ArrayList<>();
            for (FlowVariable.Type type : typeProvider.getTypes()) {
                types.add(type.name());
            }
            return types;
        }
    }

    private static abstract class InColListElementPersistor
        implements ElementFieldPersistor<String, Integer, InputColumnField> {
        private final String m_configKey;

        InColListElementPersistor(final String configKey) {
            m_configKey = configKey;
        }

        protected abstract String getFieldFromItem(final InCol inCol);

        @Override
        public String load(final NodeSettingsRO settings, final Integer loadContext) throws InvalidSettingsException {
            var inColList = new JavaFieldList.InColList();
            if (settings.containsKey(m_configKey)) {
                inColList.loadSettings(settings.getConfig(m_configKey));
                return loadContext < inColList.size() ? getFieldFromItem(inColList.get(loadContext)) : null;
            }
            return null;
        }

        @Override
        public void save(final String param, final InputColumnField saveDTO) {
            // Field saved by individual persistors
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{m_configKey}};
        }
    }

    private static final class InputColumnNamePersistor extends InColListElementPersistor {
        InputColumnNamePersistor() {
            super("inCols");
        }

        @Override
        protected String getFieldFromItem(final InCol inCol) {
            return inCol.getKnimeName();
        }
    }

    private static final class InputColumnJavaNamePersistor extends InColListElementPersistor {
        InputColumnJavaNamePersistor() {
            super("inCols");
        }

        @Override
        protected String getFieldFromItem(final InCol inCol) {
            return inCol.getJavaName();
        }
    }

    private static final class InputColumnJavaTypePersistor extends InColListElementPersistor {
        InputColumnJavaTypePersistor() {
            super("inCols");
        }

        @Override
        protected String getFieldFromItem(final InCol inCol) {
            return inCol.getJavaType() != null ? inCol.getJavaType().getName() : "";
        }
    }

    private static final class InputColumnConverterFactoryPersistor extends InColListElementPersistor {
        InputColumnConverterFactoryPersistor() {
            super("inCols");
        }

        @Override
        protected String getFieldFromItem(final InCol inCol) {
            return inCol.getConverterFactoryId();
        }
    }

    private static final class InputColumnDataTypePersistor extends InColListElementPersistor {
        InputColumnDataTypePersistor() {
            super("inCols");
        }

        @Override
        protected String getFieldFromItem(final InCol inCol) {
            return inCol.getDataType() != null ? inCol.getDataType().toString() : "";
        }
    }

    /**
     * Provides Java type choices for input columns based on available converters for the selected column's data type.
     */
    static final class InputColumnJavaTypeChoicesProvider implements StringChoicesProvider {
        @Override
        public List<String> choices(final NodeParametersInput context) {
            // Return all common Java types that converters might support
            // In real usage, this would be dynamically determined based on the selected column
            return List.of("String", "Integer", "Double", "Boolean");
        }
    }

    /**
     * Represents an output column field definition.
     */
    public static final class OutputColumnField implements NodeParameters {
        @Widget(title = "Column Name", description = "The output column name")
        @PersistArrayElement(OutputColumnNamePersistor.class)
        String m_columnName = "";

        @Widget(title = "Java Field", description = "Java field name that provides the value")
        @PersistArrayElement(OutputColumnJavaNamePersistor.class)
        String m_javaFieldName = "";

        @Widget(title = "Java Type", description = "Java type to convert from")
        @ChoicesProvider(OutputColumnJavaTypeChoicesProvider.class)
        @PersistArrayElement(OutputColumnJavaTypePersistor.class)
        String m_javaType = "";

        @Widget(title = "Replace Existing", description = "Replace existing column if present")
        @PersistArrayElement(OutputColumnReplaceExistingPersistor.class)
        boolean m_replaceExisting = false;

        @Widget(title = "Is Collection", description = "Output is a collection type")
        @PersistArrayElement(OutputColumnIsArrayPersistor.class)
        boolean m_isArray = false;

        // Hidden field: converter factory ID
        @PersistArrayElement(OutputColumnConverterFactoryPersistor.class)
        String m_converterFactoryId = "";

        // Hidden field: data type
        @PersistArrayElement(OutputColumnDataTypePersistor.class)
        String m_dataTypeName = "";
    }

    private static abstract class OutColListElementPersistor<T>
        implements ElementFieldPersistor<T, Integer, OutputColumnField> {
        private final String m_configKey;

        OutColListElementPersistor(final String configKey) {
            m_configKey = configKey;
        }

        protected abstract T getFieldFromItem(final OutCol outCol);

        @Override
        public T load(final NodeSettingsRO settings, final Integer loadContext) throws InvalidSettingsException {
            var outColList = new JavaFieldList.OutColList();
            if (settings.containsKey(m_configKey)) {
                outColList.loadSettings(settings.getConfig(m_configKey));
                return loadContext < outColList.size() ? getFieldFromItem(outColList.get(loadContext)) : null;
            }
            return null;
        }

        @Override
        public void save(final T param, final OutputColumnField saveDTO) {
            // Field saved by individual persistors
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{m_configKey}};
        }
    }

    private static final class OutputColumnNamePersistor extends OutColListElementPersistor<String> {
        OutputColumnNamePersistor() {
            super("outCols");
        }

        @Override
        protected String getFieldFromItem(final OutCol outCol) {
            return outCol.getKnimeName();
        }
    }

    private static final class OutputColumnJavaNamePersistor extends OutColListElementPersistor<String> {
        OutputColumnJavaNamePersistor() {
            super("outCols");
        }

        @Override
        protected String getFieldFromItem(final OutCol outCol) {
            return outCol.getJavaName();
        }
    }

    private static final class OutputColumnJavaTypePersistor extends OutColListElementPersistor<String> {
        OutputColumnJavaTypePersistor() {
            super("outCols");
        }

        @Override
        protected String getFieldFromItem(final OutCol outCol) {
            return outCol.getJavaType() != null ? outCol.getJavaType().getName() : "";
        }
    }

    private static final class OutputColumnReplaceExistingPersistor extends OutColListElementPersistor<Boolean> {
        OutputColumnReplaceExistingPersistor() {
            super("outCols");
        }

        @Override
        protected Boolean getFieldFromItem(final OutCol outCol) {
            return outCol.getReplaceExisting();
        }
    }

    private static final class OutputColumnIsArrayPersistor extends OutColListElementPersistor<Boolean> {
        OutputColumnIsArrayPersistor() {
            super("outCols");
        }

        @Override
        protected Boolean getFieldFromItem(final OutCol outCol) {
            return outCol.getDataType() != null && outCol.getDataType().isCollectionType();
        }
    }

    private static final class OutputColumnConverterFactoryPersistor extends OutColListElementPersistor<String> {
        OutputColumnConverterFactoryPersistor() {
            super("outCols");
        }

        @Override
        protected String getFieldFromItem(final OutCol outCol) {
            return outCol.getConverterFactoryId();
        }
    }

    private static final class OutputColumnDataTypePersistor extends OutColListElementPersistor<String> {
        OutputColumnDataTypePersistor() {
            super("outCols");
        }

        @Override
        protected String getFieldFromItem(final OutCol outCol) {
            return outCol.getDataType() != null ? outCol.getDataType().toString() : "";
        }
    }

    /**
     * Provides Java type choices for output columns based on available converters.
     */
    static final class OutputColumnJavaTypeChoicesProvider implements StringChoicesProvider {
        @Override
        public List<String> choices(final NodeParametersInput context) {
            List<String> types = new ArrayList<>();

            for (final DataType type : ConverterUtil.getAllDestinationDataTypes()) {
                if (ConverterUtil.getFactoriesForDestinationType(type).stream()
                    .filter(factory -> JavaSnippet.getBuildPathFromCache(factory.getIdentifier()) != null).findAny()
                    .isPresent()) {
                    types.add(type.getName());
                }
            }
            return types;
        }
    }

    /**
     * Represents an output flow variable field definition.
     */
    public static final class OutputFlowVariableField implements NodeParameters {
        @Widget(title = "Flow Variable Name", description = "The output flow variable name")
        @PersistArrayElement(OutputFlowVariableNamePersistor.class)
        String m_variableName = "";

        @Widget(title = "Java Field", description = "Java field name that provides the value")
        @PersistArrayElement(OutputFlowVariableJavaNamePersistor.class)
        String m_javaFieldName = "";

        @Widget(title = "Java Type", description = "Java type to convert from")
        @ChoicesProvider(FlowVariableJavaTypeChoicesProvider.class)
        @PersistArrayElement(OutputFlowVariableJavaTypePersistor.class)
        String m_javaType = "";

        // Hidden field: flow variable type
        @PersistArrayElement(OutputFlowVariableTypePersistor.class)
        String m_flowVarType = "";
    }

    private static abstract class OutVarListElementPersistor
        implements ElementFieldPersistor<String, Integer, OutputFlowVariableField> {
        private final String m_configKey;

        OutVarListElementPersistor(final String configKey) {
            m_configKey = configKey;
        }

        protected abstract String getFieldFromItem(final OutVar outVar);

        @Override
        public String load(final NodeSettingsRO settings, final Integer loadContext) throws InvalidSettingsException {
            var outVarList = new JavaFieldList.OutVarList();
            if (settings.containsKey(m_configKey)) {
                outVarList.loadSettings(settings.getConfig(m_configKey));
                return loadContext < outVarList.size() ? getFieldFromItem(outVarList.get(loadContext)) : null;
            }
            return null;
        }

        @Override
        public void save(final String param, final OutputFlowVariableField saveDTO) {
            // Field saved by individual persistors
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{m_configKey}};
        }
    }

    private static final class OutputFlowVariableNamePersistor extends OutVarListElementPersistor {
        OutputFlowVariableNamePersistor() {
            super("outVars");
        }

        @Override
        protected String getFieldFromItem(final OutVar outVar) {
            return outVar.getKnimeName();
        }
    }

    private static final class OutputFlowVariableJavaNamePersistor extends OutVarListElementPersistor {
        OutputFlowVariableJavaNamePersistor() {
            super("outVars");
        }

        @Override
        protected String getFieldFromItem(final OutVar outVar) {
            return outVar.getJavaName();
        }
    }

    private static final class OutputFlowVariableJavaTypePersistor extends OutVarListElementPersistor {
        OutputFlowVariableJavaTypePersistor() {
            super("outVars");
        }

        @Override
        protected String getFieldFromItem(final OutVar outVar) {
            return outVar.getJavaType() != null ? outVar.getJavaType().getName() : "";
        }
    }

    private static final class OutputFlowVariableTypePersistor extends OutVarListElementPersistor {
        OutputFlowVariableTypePersistor() {
            super("outVars");
        }

        @Override
        protected String getFieldFromItem(final OutVar outVar) {
            return outVar.getFlowVarType() != null ? outVar.getFlowVarType().toString() : "";
        }
    }

    /**
     * Represents a JAR file entry.
     */
    public static final class JarFileEntry implements NodeParameters {
        // TODO: make this a file browser
        @Widget(title = "JAR Path/URL", description = "File path or KNIME URL to JAR file")
        @PersistArrayElement(JarFilePathPersistor.class)
        String m_path = "";
    }

    private static final class JarFilePathPersistor implements ElementFieldPersistor<String, Integer, JarFileEntry> {
        private static final String CONFIG_KEY = "jarFiles";

        @Override
        public String load(final NodeSettingsRO settings, final Integer loadContext) throws InvalidSettingsException {
            String[] jarFiles = settings.getStringArray(CONFIG_KEY, new String[0]);
            return loadContext < jarFiles.length ? jarFiles[loadContext] : null;
        }

        @Override
        public void save(final String param, final JarFileEntry saveDTO) {
            saveDTO.m_path = param;
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{CONFIG_KEY}};
        }
    }

    /**
     * Represents an OSGi bundle entry.
     */
    public static final class BundleEntry implements NodeParameters {
        // TODO: make this a choices provider with dropdown of all available bundles
        @Widget(title = "Bundle", description = "OSGi bundle symbolic name with version")
        @PersistArrayElement(BundleNamePersistor.class)
        String m_bundle = "";
    }

    private static final class BundleNamePersistor implements ElementFieldPersistor<String, Integer, BundleEntry> {
        private static final String CONFIG_KEY = "bundles";

        @Override
        public String load(final NodeSettingsRO settings, final Integer loadContext) throws InvalidSettingsException {
            String[] bundles = settings.getStringArray(CONFIG_KEY, new String[0]);
            return loadContext < bundles.length ? bundles[loadContext] : null;
        }

        @Override
        public void save(final String param, final BundleEntry saveDTO) {
            saveDTO.m_bundle = param;
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{CONFIG_KEY}};
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
        private static final String CONFIG_KEY = "inVars";

        @Override
        public void save(final List<InputFlowVariableField> param, final NodeSettingsWO settings) {
            var inVarList = new JavaFieldList.InVarList();
            if (param != null) {
                for (InputFlowVariableField field : param) {
                    var inVar = new InVar();
                    inVar.setKnimeName(field.m_variableName);
                    inVar.setJavaName(field.m_javaFieldName);

                    // Set flow var type from string
                    if (field.m_flowVarType != null && !field.m_flowVarType.isEmpty()) {
                        try {
                            inVar.setFlowVarType(
                                org.knime.core.node.workflow.FlowVariable.Type.valueOf(field.m_flowVarType));
                        } catch (IllegalArgumentException e) {
                            // Fallback to STRING if parsing fails
                            inVar.setFlowVarType(org.knime.core.node.workflow.FlowVariable.Type.STRING);
                        }
                    }

                    // Set java type from String class name
                    if (field.m_javaType != null && !field.m_javaType.isEmpty()) {
                        try {
                            Class<?> javaClass = Class.forName("java.lang." + field.m_javaType);
                            inVar.setJavaType(javaClass);
                        } catch (ClassNotFoundException e) {
                            // Fallback to String if class not found
                            inVar.setJavaType(String.class);
                        }
                    }

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
     * Persistor for output columns (OutColList <-> OutputColumnField[]).
     */
    public static final class OutColListPersistor implements ArrayPersistor<Integer, OutputColumnField> {
        private static final String CONFIG_KEY = "outCols";

        @Override
        public void save(final List<OutputColumnField> param, final NodeSettingsWO settings) {
            var outColList = new JavaFieldList.OutColList();
            if (param != null) {
                for (OutputColumnField field : param) {
                    var outCol = new OutCol();
                    outCol.setKnimeName(field.m_columnName);
                    outCol.setJavaName(field.m_javaFieldName);
                    outCol.setReplaceExisting(field.m_replaceExisting);

                    // Set java type from String class name
                    if (field.m_javaType != null && !field.m_javaType.isEmpty()) {
                        try {
                            Class<?> javaClass = Class.forName("java.lang." + field.m_javaType);

                            // Try to find a converter factory for this type
                            if (field.m_converterFactoryId != null && !field.m_converterFactoryId.isEmpty()) {
                                var factory =
                                    ConverterUtil.getJavaToDataCellConverterFactory(field.m_converterFactoryId);
                                if (factory.isPresent()) {
                                    outCol.setConverterFactory(factory.get());
                                }
                            } else {
                                // Try to infer data type - use String as fallback
                                DataType dataType = StringCell.TYPE;
                                var factory = ConverterUtil.getConverterFactory(javaClass, dataType);
                                if (factory.isPresent()) {
                                    outCol.setConverterFactory(factory.get());
                                }
                            }
                        } catch (ClassNotFoundException e) {
                            // Fallback - leave unset
                        }
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
        private static final String CONFIG_KEY = "outVars";

        @Override
        public void save(final List<OutputFlowVariableField> param, final NodeSettingsWO settings) {
            var outVarList = new JavaFieldList.OutVarList();
            if (param != null) {
                for (OutputFlowVariableField field : param) {
                    var outVar = new OutVar();
                    outVar.setKnimeName(field.m_variableName);
                    outVar.setJavaName(field.m_javaFieldName);

                    // Set flow var type from string
                    if (field.m_flowVarType != null && !field.m_flowVarType.isEmpty()) {
                        try {
                            outVar.setFlowVarType(
                                org.knime.core.node.workflow.FlowVariable.Type.valueOf(field.m_flowVarType));
                        } catch (IllegalArgumentException e) {
                            // Fallback to STRING if parsing fails
                            outVar.setFlowVarType(org.knime.core.node.workflow.FlowVariable.Type.STRING);
                        }
                    }

                    // Set java type from String class name
                    if (field.m_javaType != null && !field.m_javaType.isEmpty()) {
                        try {
                            Class<?> javaClass = Class.forName("java.lang." + field.m_javaType);
                            outVar.setJavaType(javaClass);
                        } catch (ClassNotFoundException e) {
                            // Fallback to String if class not found
                            outVar.setJavaType(String.class);
                        }
                    }

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
        private static final String CONFIG_KEY = "inCols";

        @Override
        public void save(final List<InputColumnField> param, final NodeSettingsWO settings) {
            var inColList = new JavaFieldList.InColList();
            if (param != null) {
                for (InputColumnField field : param) {
                    var inCol = new InCol();
                    inCol.setKnimeName(field.m_columnName);
                    inCol.setJavaName(field.m_javaFieldName);

                    // Set converter factory if both data type and factory ID are available
                    if (field.m_dataTypeName != null && !field.m_dataTypeName.isEmpty()
                        && field.m_converterFactoryId != null && !field.m_converterFactoryId.isEmpty()) {
                        try {
                            // Parse the data type from string
                            var dataCellClass = DataTypeRegistry.getInstance().getCellClass(field.m_dataTypeName);
                            if (dataCellClass.isPresent()) {
                                DataType dataType = DataType.getType(dataCellClass.get());

                                // Get the converter factory
                                var factory =
                                    ConverterUtil.getDataCellToJavaConverterFactory(field.m_converterFactoryId);
                                if (factory.isPresent()) {
                                    inCol.setConverterFactory(dataType, factory.get());
                                }
                            }
                        } catch (Exception e) {
                            // Leave unset if data type or factory cannot be loaded
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
        private static final String CONFIG_KEY = "jarFiles";

        @Override
        public void save(final List<JarFileEntry> param, final NodeSettingsWO settings) {
            if (param == null || param.isEmpty()) {
                settings.addStringArray(CONFIG_KEY, new String[0]);
            } else {
                String[] paths = param.stream().map(e -> e.m_path).toArray(String[]::new);
                settings.addStringArray(CONFIG_KEY, paths);
            }
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
        private static final String CONFIG_KEY = "bundles";

        @Override
        public void save(final List<BundleEntry> param, final NodeSettingsWO settings) {
            if (param == null || param.isEmpty()) {
                settings.addStringArray(CONFIG_KEY, new String[0]);
            } else {
                String[] bundles = param.stream().map(e -> e.m_bundle).toArray(String[]::new);
                settings.addStringArray(CONFIG_KEY, bundles);
            }
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


    @Section(title="Input Columns")
    interface InputColumns {}

    @Section(title="Input Variables")
    @After(InputColumns.class)
    interface InputVariables {}


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

    @Section(title="Output Columns")
    @After(InputVariables.class)
    interface OutputColumns {}

    @Section(title="Output Variables")
    @After(OutputColumns.class)
    interface OutputVariables {}

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
        @Section(title="JAR Files")
        interface JARFiles {}

        @Section(title="OSGi Bundles")
        interface OSGIBundles {}
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
