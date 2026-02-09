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
 *   18 Mar 2020 (carlwitt): created
 */
package org.knime.base.node.preproc.stringmanipulation.multicolumn;

import org.knime.base.node.util.JSnippetPanel;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.IntValue;
import org.knime.core.data.StringValue;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.util.filter.InputFilter;
import org.knime.core.node.util.filter.column.DataColumnSpecFilterConfiguration;
import org.knime.ext.sun.nodes.script.expression.Expression;

/**
 * Configuration for the Multi Column String Manipulation node. There are getters for the settings models too, e.g.,
 * {@link #getAppendedColumnsSuffixSettingsModel()} because they can be used in dialog components, e.g., a
 * {@link DialogComponentString}. The model however will use getters for the values stored in the settings models for
 * convenience, e.g., {@link #getAppendedColumnsSuffix()}.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
class MultiColumnStringManipulationSettings {

    final static String EXPRESSION = "EXPRESSION";

    /** Signifies that calculated columns should be appended to the input data table to create the output table. */
    final static String APPEND_ACTION = "APPEND_COLUMNS";

    /** Signifies that calculated columns should replace the selected (iterated) input columns. */
    final static String REPLACE_ACTION = "REPLACE_COLUMNS";

    /**
     * The column selection configuration (not the selected columns; obtain these by applying the configuration to a
     * data table specification). Supports only integer, double, and string, because {@link Expression} only supports
     * these flow variable types (and this is how data from an iterated column is passed into the expression).
     */
    private final DataColumnSpecFilterConfiguration m_columnFilterConfiguration =
        new DataColumnSpecFilterConfiguration("column_selection", new InputFilter<DataColumnSpec>() {
            @Override
            public boolean include(final DataColumnSpec name) {
                    return name.getType().isCompatible(StringValue.class)
                        || name.getType().isCompatible(DoubleValue.class)
                        || name.getType().isCompatible(IntValue.class);
            }
        });

    /**
     * The expression to be evaluated, e.g., "toInt($$CURRENTCOLUMN$$)" entered via {@link JSnippetPanel} and passed for
     * compilation to {@link Expression}.
     */
    private final SettingsModelString m_expression = new SettingsModelString(EXPRESSION, getCurrentColumnReference());

    /** This is a string because the button group dialog component uses action strings to represent selection. */
    private final SettingsModelString m_appendOrReplace = new SettingsModelString("APPEND_OR_REPLACE", APPEND_ACTION);

    /** This is the content of the text box for the suffix to append to newly created columns. */
    private final SettingsModelString m_appendedColumnsSuffix =
        new SettingsModelString("APPEND_COLUMN_SUFFIX", "_transformed");

    /**
     * Whether to abort execution when encountering problems in expression evaluation. If false, the output will contain
     * missing cells where problems occurred.
     */
    private final SettingsModelBoolean m_failOnEvaluationException =
        new SettingsModelBoolean("Abort execution on evaluation errors", true);

    private final SettingsModelBoolean m_evaluateWithMissingValues =
            new SettingsModelBoolean("Insert missing values as null", true);

    /**
     * @return Never change this. This would break all nodes in previous workflows that use $$CURRENTCOLUMN$$ in their
     *         expressions. the name of the special identifier that signifies the currently iterated column in an
     *         expression.
     */
    static String getCurrentColumnReferenceName() {
        return "CURRENTCOLUMN";
    }

    /**
     * @return Never change this. This would break all nodes in previous workflows that use $$CURRENTCOLUMN$$ in their
     *         expressions. This special identifier refers to the currently iterated column in an expression. Needed by
     *         the dialog (e.g., to add to java snippet editor autocompletion) and the model (e.g., to bind data to the
     *         variable in the expression). Depending on the reference name this could be for instance
     *         <code>$$CURRENTCOLUMN$$</code>.
     */
    static String getCurrentColumnReference() {
        return "$$" + getCurrentColumnReferenceName() + "$$";
    }

    void validateSettingsInModel(final NodeSettingsRO settings) throws InvalidSettingsException {

        // check that the settings models' keys exist in the settings object
        getExpressionSettingsModel().validateSettings(settings);
        getAppendOrReplaceSettingsModel().validateSettings(settings);
        getAppendedColumnsSuffixSettingsModel().validateSettings(settings);
        getFailOnEvaluationExceptionSettingsModel().validateSettings(settings);
        getEvaluateWithMissingValuesSettingsModel().validateSettings(settings);

    }

    /**
     * Common load settings code in dialog and model.
     *
     * @param settings
     * @throws InvalidSettingsException
     */
    private void loadSettingsCommon(final NodeSettingsRO settings) throws InvalidSettingsException {
        getExpressionSettingsModel().loadSettingsFrom(settings);

        getAppendOrReplaceSettingsModel().loadSettingsFrom(settings);
        getAppendedColumnsSuffixSettingsModel().loadSettingsFrom(settings);

        getFailOnEvaluationExceptionSettingsModel().loadSettingsFrom(settings);
        getEvaluateWithMissingValuesSettingsModel().loadSettingsFrom(settings);
    }

    void loadSettingsInDialog(final NodeSettingsRO settings, final DataTableSpec spec) throws InvalidSettingsException {

        getColumnFilterConfigurationSettingsModel().loadConfigurationInDialog(settings, spec);
        loadSettingsCommon(settings);

    }

    void loadSettingsInModel(final NodeSettingsRO settings) throws InvalidSettingsException {

        getColumnFilterConfigurationSettingsModel().loadConfigurationInModel(settings);
        loadSettingsCommon(settings);

    }

    void saveSettingsTo(final NodeSettingsWO settings) {

        getColumnFilterConfigurationSettingsModel().saveConfiguration(settings);

        getExpressionSettingsModel().saveSettingsTo(settings);

        getAppendOrReplaceSettingsModel().saveSettingsTo(settings);
        getAppendedColumnsSuffixSettingsModel().saveSettingsTo(settings);

        getFailOnEvaluationExceptionSettingsModel().saveSettingsTo(settings);
        getEvaluateWithMissingValuesSettingsModel().saveSettingsTo(settings);

    }

    String getExpression() {
        return getExpressionSettingsModel().getStringValue();
    }

    void setExpression(final String expression) {
        m_expression.setStringValue(expression);
    }

    public SettingsModelString getExpressionSettingsModel() {
        return m_expression;
    }

    boolean isReplace() {
        return m_appendOrReplace.getStringValue().equals(REPLACE_ACTION);
    }

    String getAppendedColumnsSuffix() {
        return getAppendedColumnsSuffixSettingsModel().getStringValue();
    }

    void setAppendedColumnsSuffix(final String appendedColumnsSuffix) {
        m_appendedColumnsSuffix.setStringValue(appendedColumnsSuffix);
    }

    SettingsModelString getAppendedColumnsSuffixSettingsModel() {
        return m_appendedColumnsSuffix;
    }

    boolean isFailOnEvaluationException() {
        return getFailOnEvaluationExceptionSettingsModel().getBooleanValue();
    }

    void setFailOnEvaluationException(final boolean failOnEvaluationException) {
        getFailOnEvaluationExceptionSettingsModel().setBooleanValue(failOnEvaluationException);
    }

    SettingsModelBoolean getFailOnEvaluationExceptionSettingsModel() {
        return m_failOnEvaluationException;
    }

    SettingsModelBoolean getEvaluateWithMissingValuesSettingsModel() {
        return m_evaluateWithMissingValues;
    }

    boolean isEvaluateWithMissingValues() {
        return getEvaluateWithMissingValuesSettingsModel().getBooleanValue();
    }

    void setEvaluateWithMissingValues(final boolean evaluateWithMissingValues) {
        getEvaluateWithMissingValuesSettingsModel().setBooleanValue(evaluateWithMissingValues);
    }

    SettingsModelString getAppendOrReplaceSettingsModel() {
        return m_appendOrReplace;
    }

    DataColumnSpecFilterConfiguration getColumnFilterConfigurationSettingsModel() {
        return m_columnFilterConfiguration;
    }

}
