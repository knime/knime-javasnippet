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
import java.util.List;

import org.knime.base.node.jsnippet.util.JavaFieldList;
import org.knime.base.node.jsnippet.util.field.InCol;
import org.knime.base.node.jsnippet.util.field.InVar;
import org.knime.base.node.jsnippet.util.field.OutCol;
import org.knime.base.node.jsnippet.util.field.OutVar;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.config.Config;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.array.ArrayWidget;
import org.knime.node.parameters.layout.Advanced;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.persistence.array.ArrayPersistor;

/**
 * NodeParameters implementation for the Java Snippet node in the Modern UI.
 * This class defines all configuration options except the script sections (imports, fields, body),
 * which are handled by the scripting dialog framework.
 *
 * @author GitHub Copilot
 * @since 5.4
 */
@SuppressWarnings("restriction")
public final class JavaSnippetScriptingNodeParameters implements NodeParameters {

    // =================================================================================
    // Script Section Persistors
    // These handle the three editable script sections (imports, fields, body)
    // =================================================================================

    /**
     * Persistor for script imports section.
     */
    public static final class ScriptImportsPersistor implements NodeParametersPersistor<String> {
        private static final String CONFIG_KEY = "scriptImports";

        @Override
        public String load(final NodeSettingsRO settings) throws InvalidSettingsException {
            return settings.getString(CONFIG_KEY, "");
        }

        @Override
        public void save(final String param, final NodeSettingsWO settings) {
            settings.addString(CONFIG_KEY, param != null ? param : "");
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{CONFIG_KEY}};
        }
    }

    /**
     * Persistor for script fields section.
     */
    public static final class ScriptFieldsPersistor implements NodeParametersPersistor<String> {
        private static final String CONFIG_KEY = "scriptFields";

        @Override
        public String load(final NodeSettingsRO settings) throws InvalidSettingsException {
            return settings.getString(CONFIG_KEY, "");
        }

        @Override
        public void save(final String param, final NodeSettingsWO settings) {
            settings.addString(CONFIG_KEY, param != null ? param : "");
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{CONFIG_KEY}};
        }
    }

    /**
     * Persistor for script body section.
     */
    public static final class ScriptBodyPersistor implements NodeParametersPersistor<String> {
        private static final String CONFIG_KEY = "scriptBody";

        @Override
        public String load(final NodeSettingsRO settings) throws InvalidSettingsException {
            return settings.getString(CONFIG_KEY, "");
        }

        @Override
        public void save(final String param, final NodeSettingsWO settings) {
            settings.addString(CONFIG_KEY, param != null ? param : "");
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{CONFIG_KEY}};
        }
    }

    // Script sections (not displayed as widgets - handled by dialog)
    @Persistor(ScriptImportsPersistor.class)
    String m_scriptImports = "";

    @Persistor(ScriptFieldsPersistor.class)
    String m_scriptFields = "";

    @Persistor(ScriptBodyPersistor.class)
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
        @Persist(configKey = "columnName")
        String m_columnName = "";

        @Widget(title = "Java Field Name", description = "Java variable name for this column")
        @Persist(configKey = "javaFieldName")
        String m_javaFieldName = "";

        @Widget(title = "Java Type", description = "Java type for conversion")
        @Persist(configKey = "javaType")
        String m_javaType = "";

        // TODO: Add converter factory ID persistence
    }

    /**
     * Represents an input flow variable field definition.
     */
    public static final class InputFlowVariableField implements NodeParameters {
        @Widget(title = "Flow Variable", description = "The input flow variable to use")
        @Persist(configKey = "variableName")
        String m_variableName = "";

        @Widget(title = "Java Field Name", description = "Java variable name for this flow variable")
        @Persist(configKey = "javaFieldName")  
        String m_javaFieldName = "";

        @Widget(title = "Java Type", description = "Java type for conversion")
        @Persist(configKey = "javaType")
        String m_javaType = "";
    }

    /**
     * Represents an output column field definition.
     */
    public static final class OutputColumnField implements NodeParameters {
        @Widget(title = "Column Name", description = "The output column name")
        @Persist(configKey = "columnName")
        String m_columnName = "";

        @Widget(title = "Java Field", description = "Java field name that provides the value")
        @Persist(configKey = "javaFieldName")
        String m_javaFieldName = "";

        @Widget(title = "Java Type", description = "Java type to convert from")
        @Persist(configKey = "javaType")
        String m_javaType = "";

        @Widget(title = "Replace Existing", description = "Replace existing column if present")
        @Persist(configKey = "replaceExisting")
        boolean m_replaceExisting = false;

        @Widget(title = "Is Collection", description = "Output is a collection type")
        @Persist(configKey = "isArray")
        boolean m_isArray = false;
    }

    /**
     * Represents an output flow variable field definition.
     */
    public static final class OutputFlowVariableField implements NodeParameters {
        @Widget(title = "Flow Variable Name", description = "The output flow variable name")
        @Persist(configKey = "variableName")
        String m_variableName = "";

        @Widget(title = "Java Field", description = "Java field name that provides the value")
        @Persist(configKey = "javaFieldName")
        String m_javaFieldName = "";

        @Widget(title = "Java Type", description = "Java type to convert from")
        @Persist(configKey = "javaType")
        String m_javaType = "";
    }

    /**
     * Represents a JAR file entry.
     */
    public static final class JarFileEntry implements NodeParameters {
        @Widget(title = "JAR Path/URL", description = "File path or KNIME URL to JAR file")
        @Persist(configKey = "path")
        String m_path = "";
    }

    /**
     * Represents an OSGi bundle entry.
     */
    public static final class BundleEntry implements NodeParameters {
        @Widget(title = "Bundle", description = "OSGi bundle symbolic name with version")
        @Persist(configKey = "bundle")
        String m_bundle = "";
    }

    // =================================================================================
    // Array Persistors
    // These convert between the new array format and legacy list formats
    // =================================================================================

    /**
     * Persistor for input columns (InColList <-> InputColumnField[]).
     */
    public static final class InColListPersistor implements ArrayPersistor<InputColumnField> {
        private static final String CONFIG_KEY = "inCols";

        @Override
        public List<InputColumnField> load(final NodeSettingsRO settings) throws InvalidSettingsException {
            var legacy = new JavaFieldList.InColList();
            if (settings.containsKey(CONFIG_KEY)) {
                legacy.loadSettings(settings.getConfig(CONFIG_KEY));
            }
            
            List<InputColumnField> result = new ArrayList<>();
            for (InCol inCol : legacy) {
                var field = new InputColumnField();
                field.m_columnName = inCol.getKnimeName();
                field.m_javaFieldName = inCol.getJavaName();
                field.m_javaType = inCol.getJavaType() != null ? inCol.getJavaType().getName() : "";
                result.add(field);
            }
            return result;
        }

        @Override
        public void save(final List<InputColumnField> param, final NodeSettingsWO settings) {
            var legacy = new JavaFieldList.InColList();
            if (param != null) {
                for (InputColumnField field : param) {
                    var inCol = new InCol();
                    inCol.setKnimeName(field.m_columnName);
                    inCol.setJavaName(field.m_javaFieldName);
                    // TODO: Set java type and converter factory
                    legacy.add(inCol);
                }
            }
            legacy.saveSettings(settings.addConfig(CONFIG_KEY));
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{CONFIG_KEY}};
        }
    }

    /**
     * Persistor for JAR files (String[] <-> JarFileEntry[]).
     */
    public static final class JarFilesPersistor implements ArrayPersistor<JarFileEntry> {
        private static final String CONFIG_KEY = "jarFiles";

        @Override
        public List<JarFileEntry> load(final NodeSettingsRO settings) throws InvalidSettingsException {
            String[] jarFiles = settings.getStringArray(CONFIG_KEY, new String[0]);
            List<JarFileEntry> result = new ArrayList<>();
            for (String path : jarFiles) {
                var entry = new JarFileEntry();
                entry.m_path = path;
                result.add(entry);
            }
            return result;
        }

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
        public String[][] getConfigPaths() {
            return new String[][]{{CONFIG_KEY}};
        }
    }

    /**
     * Persistor for bundles (String[] <-> BundleEntry[]).
     */
    public static final class BundlesPersistor implements ArrayPersistor<BundleEntry> {
        private static final String CONFIG_KEY = "bundles";

        @Override
        public List<BundleEntry> load(final NodeSettingsRO settings) throws InvalidSettingsException {
            String[] bundles = settings.getStringArray(CONFIG_KEY, new String[0]);
            List<BundleEntry> result = new ArrayList<>();
            for (String bundle : bundles) {
                var entry = new BundleEntry();
                entry.m_bundle = bundle;
                result.add(entry);
            }
            return result;
        }

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
        public String[][] getConfigPaths() {
            return new String[][]{{CONFIG_KEY}};
        }
    }

    // =================================================================================
    // Input Fields Section
    // =================================================================================

    @Section(title = "Input Fields")
    interface InputFieldsSection {
        @Widget(title = "Input Columns", 
                description = "Define which input columns to use and their Java field names")
        @ArrayWidget(element = InputColumnField.class)
        @Persistor(InColListPersistor.class)
        InputColumnField[] getInputColumns();

        // TODO: Add input flow variables
        // @Widget(title = "Input Flow Variables",
        //         description = "Define which flow variables to use and their Java field names")
        // @ArrayWidget(element = InputFlowVariableField.class)
        // @Persistor(InVarListPersistor.class)
        // InputFlowVariableField[] getInputFlowVariables();
    }

    // =================================================================================
    // Output Fields Section (Placeholder)
    // =================================================================================

    // TODO: Add Output Fields section similar to Input Fields
    // TODO: Add Libraries & Bundles side drawer section

    @Section(title = "Libraries & Bundles", sideDrawer = true)
    @Advanced
    interface LibrariesAndBundlesSection {
        @Widget(title = "JAR Files",
                description = "External JAR files to include on the classpath")
        @ArrayWidget(element = JarFileEntry.class)
        @Persistor(JarFilesPersistor.class)
        JarFileEntry[] getJarFiles();

        @Widget(title = "OSGi Bundles",
                description = "OSGi bundles to add to the classpath")
        @ArrayWidget(element = BundleEntry.class)
        @Persistor(BundlesPersistor.class)
        BundleEntry[] getBundles();
    }
}
