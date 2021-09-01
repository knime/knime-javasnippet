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

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.text.MessageFormat;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import org.fife.ui.autocomplete.BasicCompletion;
import org.knime.base.node.preproc.stringmanipulation.StringManipulatorProvider;
import org.knime.base.node.util.JSnippetPanel;
import org.knime.base.node.util.JavaScriptingCompletionProvider;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.DialogComponentButtonGroup;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnFilter2;
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.core.node.util.filter.column.DataColumnSpecFilterPanel;
import org.knime.ext.sun.nodes.script.expression.Expression;
import org.knime.rsyntaxtextarea.KnimeCompletionProvider;

/**
 * Mostly binds settings models from {@link MultiColumnStringManipulationSettings} to dialog components. However, the
 * expression from the {@link JSnippetPanel} needs to be synchronized with {@link MultiColumnStringManipulationSettings}
 * manually.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
final class MultiColumnStringManipulationNodeDialog extends NodeDialogPane {

    /** One settings object to group all settings models. */
    private final MultiColumnStringManipulationSettings m_settings = new MultiColumnStringManipulationSettings();

    /**
     * Dialog component to select column to iterate over. I tried {@link DialogComponentColumnFilter2} but it had some
     * glitches (disappearing labels on click) and was more difficult to use.
     */
    private final DataColumnSpecFilterPanel m_dialogCompColFilter;

    /** Expression editor. */
    private final JSnippetPanel m_snippetPanel;

    /** Dialog component to choose between replacing or append columns. */
    private final DialogComponentButtonGroup m_appendOrReplace;

    /** User enters a suffix to form the names of the new columns to append. */
    private final DialogComponentString m_newColSuffixField;

    /** Check box to abort execution on evaluation errors. */
    private final DialogComponentBoolean m_failOnEvaluationException;

    /** Check box to select whether to evaluate expressions when input values are missing (insert them as null) or not
     * (return missing value without evaluation).
     */
    private final DialogComponentBoolean m_evaluateWithMissingValues;

    /**
     * Create the dialog components. Build the user interface and add to the dialog.
     */
    MultiColumnStringManipulationNodeDialog() {

        // columns to iterate over selection
        m_dialogCompColFilter = new DataColumnSpecFilterPanel();
        m_dialogCompColFilter.setBorder(BorderFactory.createTitledBorder(
            MessageFormat.format("Selected Columns - assigned one by one to occurrences of {0} in the expression",
                MultiColumnStringManipulationSettings.getCurrentColumnReference())));

        // java expression editor
        m_snippetPanel =
            new JSnippetPanel(StringManipulatorProvider.getDefault(), createCompletionProvider(), true, true);

        // choose between replacing input columns or appending new ones
        m_appendOrReplace = new DialogComponentButtonGroup(m_settings.getAppendOrReplaceSettingsModel(),
            "Result columns", true, new String[]{"Append as new columns", "Replace selected input columns"},
            new String[]{MultiColumnStringManipulationSettings.APPEND_ACTION,
                MultiColumnStringManipulationSettings.REPLACE_ACTION});

        // input for new column suffix
        m_newColSuffixField =
            new DialogComponentString(m_settings.getAppendedColumnsSuffixSettingsModel(), "Suffix for new columns");
        // enable new column suffix text field only when append radio button is selected
        final ActionListener actionListener = e -> m_newColSuffixField.getModel()
            .setEnabled(m_appendOrReplace.getButton(MultiColumnStringManipulationSettings.APPEND_ACTION).isSelected());
        m_appendOrReplace.getButton(MultiColumnStringManipulationSettings.APPEND_ACTION)
            .addActionListener(actionListener);
        m_appendOrReplace.getButton(MultiColumnStringManipulationSettings.REPLACE_ACTION)
            .addActionListener(actionListener);

        // choose expression evaluation failure handling
        m_failOnEvaluationException = new DialogComponentBoolean(m_settings.getFailOnEvaluationExceptionSettingsModel(),
            "Fail if expression can not be evaluated");
        m_failOnEvaluationException.setToolTipText(
            "Stop the execution on problems. If unselected, errors during calculation (e.g., due to missing input"
                + " values or impossible type conversions) will simply produce missing values.");

        m_evaluateWithMissingValues = new DialogComponentBoolean(m_settings.getEvaluateWithMissingValuesSettingsModel(),
                "Insert missing values as null");
        m_evaluateWithMissingValues.setToolTipText(
                "If checked, missing values will be inserted as null into the expression. If unchecked, the expression "
                + "is not evaluated if any of its inputs is missing. Instead, a missing value is returned.");

        addTab("String Manipulation", getDialogComponents());

    }

    // ------------------------------------------------------------------------------------------------
    // Panel
    // ------------------------------------------------------------------------------------------------

    /**
     * Base dialog layout: column selection on top, expression editor in center, output handling on bottom.
     */
    private JPanel getDialogComponents() {

        // panel for checkboxes for missing value and error handling
        final JPanel errorHandling = new JPanel(new GridLayout(2, 0));
        errorHandling.add(m_evaluateWithMissingValues.getComponentPanel());
        errorHandling.add(m_failOnEvaluationException.getComponentPanel());

        // bottom for configuring result and error handling
        // three column layout:
        // radio button group, text field for suffix, missing value check box
        final JPanel replaceOrAppend = new JPanel(new GridLayout(0, 3));
        replaceOrAppend.add(m_appendOrReplace.getComponentPanel());
        replaceOrAppend.add(m_newColSuffixField.getComponentPanel());
        replaceOrAppend.add(errorHandling);

        // three row layout:
        // column selection, expression editor, configuration
        final JPanel p = new JPanel(new BorderLayout());
        p.add(m_dialogCompColFilter, BorderLayout.NORTH);
        p.add(m_snippetPanel, BorderLayout.CENTER);
        p.add(replaceOrAppend, BorderLayout.SOUTH);

        return p;

    }

    // ------------------------------------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------------------------------------

    /**
     * @return special expressions to add to the java editors column list, e.g., the currently iterated column reference
     */
    static private String[] getExpressionsForSnippetPanel() {
        return new String[]{
            // the special column reference for the currently iterated column
            MultiColumnStringManipulationSettings.getCurrentColumnReferenceName(),
            // basic expressions
            Expression.ROWID, Expression.ROWINDEX, Expression.ROWCOUNT};
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final DataTableSpec[] specs)
        throws NotConfigurableException {

        final DataTableSpec spec = specs[0];

        try {
            // the dialog components and settings share the same settings models and are thus implicitly updated here
            m_settings.loadSettingsInDialog(settings, spec);
        } catch (InvalidSettingsException e) {
            throw new NotConfigurableException(e.getMessage());
        }

        // the column filter component doesn't listen to changes in its setting model, so update manually
        m_dialogCompColFilter.loadConfiguration(m_settings.getColumnFilterConfigurationSettingsModel(), spec);

        // load expression into expression editor
        m_snippetPanel.update(
            // set the editor content
            m_settings.getExpression(),
            // provide the input table spec for compilation
            spec,
            // add available flow variables to panel
            getAvailableFlowVariables(MultiColumnStringManipulationNodeModel.SUPPORTED_FLOW_VARIABLE_TYPES),
            // add the current column reference to the list of columns
            getExpressionsForSnippetPanel());

        // invoke when about to display
        m_snippetPanel.installAutoCompletion();

    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {

        // save configuration to settings, then save settings
        m_dialogCompColFilter.saveConfiguration(m_settings.getColumnFilterConfigurationSettingsModel());

        // save expression to settings, then save settings
        m_settings.setExpression(m_snippetPanel.getExpression());

        // delegate to settings object, it shares settings models with this dialog's components so
        // we don't have to pass around the dialog's state manually
        m_settings.saveSettingsTo(settings);

    }

    /**
     * Create a simple provider that adds the current column reference.
     *
     * @return The completion provider.
     */
    private static KnimeCompletionProvider createCompletionProvider() {
        final KnimeCompletionProvider cp = new JavaScriptingCompletionProvider();

        final String autocomplete = MultiColumnStringManipulationSettings.getCurrentColumnReference();
        final String shortDesc = "String";
        final String summary = MessageFormat.format("The selected columns are assigned one by one to {0}. "
            + "Prior to assignment, the source columns value is converted to string. ", autocomplete);
        cp.addCompletion(new BasicCompletion(cp, autocomplete, shortDesc, summary));

        return cp;
    }

}
