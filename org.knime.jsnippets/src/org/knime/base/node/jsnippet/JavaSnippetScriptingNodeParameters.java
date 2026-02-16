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

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persistor;

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

    // Note: Script sections (scriptImports, scriptFields, scriptBody) are NOT defined here.
    // They are handled by the JavaSnippetScriptingNodeDialog and persisted through
    // custom persistors that interact with the existing JavaSnippetSettings.

    /**
     * Persistor for script imports section.
     * Reads from and writes to the "scriptImports" config key.
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
     * Reads from and writes to the "scriptFields" config key.
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
     * Reads from and writes to the "scriptBody" config key.
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

    // Script sections persisted through custom persistors (not displayed as widgets)
    @Persistor(ScriptImportsPersistor.class)
    String m_scriptImports = "";

    @Persistor(ScriptFieldsPersistor.class)
    String m_scriptFields = "";

    @Persistor(ScriptBodyPersistor.class)
    String m_scriptBody = "";

    // TODO: Add Input Fields section with ArrayWidgets for input columns and flow variables
    // TODO: Add Output Fields section with ArrayWidgets for output columns and flow variables
    // TODO: Add Libraries & Bundles side drawer section
    // TODO: Add custom persistors for all field arrays (InColList, InVarList, OutColList, OutVarList)
    // TODO: Add custom persistors for JAR files and bundles arrays
    // TODO: Add inner classes for field types (InputColumnField, etc.)
    // TODO: Add ChoicesProviders for Java types and OSGi bundles
}
