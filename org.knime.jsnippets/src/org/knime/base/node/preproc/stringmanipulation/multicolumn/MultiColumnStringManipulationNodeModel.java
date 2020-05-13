
/*
 * ------------------------------------------------------------------------

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
 * -------------------------------------------------------------------
 *
 */
package org.knime.base.node.preproc.stringmanipulation.multicolumn;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.knime.base.node.jsnippet.AbstractConditionalStreamingNodeModel;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.container.ColumnRearranger;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.streamable.PartitionInfo;
import org.knime.core.node.streamable.PortInput;
import org.knime.core.node.streamable.PortOutput;
import org.knime.core.node.streamable.StreamableOperator;
import org.knime.core.node.streamable.StreamableOperatorInternals;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.VariableType;
import org.knime.core.node.workflow.VariableType.DoubleType;
import org.knime.core.node.workflow.VariableType.IntType;
import org.knime.core.node.workflow.VariableType.StringType;
import org.knime.ext.sun.nodes.script.calculator.FlowVariableProvider;
import org.knime.ext.sun.nodes.script.compile.CompilationFailedException;
import org.knime.ext.sun.nodes.script.expression.Expression;

/**
 * Most of the execution logic is delegated to the custom cell factory
 * {@link MultiColumnStringManipulationConfigurator}. Extracts settings from
 * {@link MultiColumnStringManipulationSettings} to create a {@link MultiColumnStringManipulationConfigurator}.
 *
 * Configures the allowed flow variable types {@link #SUPPORTED_FLOW_VARIABLE_TYPES} and provides their values to the
 * java expression by implementing {@link FlowVariableProvider}.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
@SuppressWarnings("javadoc")
class MultiColumnStringManipulationNodeModel extends AbstractConditionalStreamingNodeModel {

    /**
     * The supported flow variable types show up in the expression editor. However, this concerns the model, because it
     * is responsible for providing the values of these flow variables {@link FlowVariableProvider}.
     *
     * Non-string variables are supported because the user might want to convert them to string or even use them in a
     * ternary expression. Some string manipulators have varargs methods so string arrays make sense, but even though
     * converting arrays to string isn't likely, accessing their fields might be?
     */
    final static VariableType<?>[] SUPPORTED_FLOW_VARIABLE_TYPES =
        new VariableType<?>[]{StringType.INSTANCE, IntType.INSTANCE, DoubleType.INSTANCE};
    //TODO [Extended flow variable type support] use VariableTypeRegistry.getInstance().getAllTypes();

    /** The port index that receives the input data table. */
    private static final int IN_PORT = 0;

    private final MultiColumnStringManipulationSettings m_settings;

    private final Function<String, Optional<Object>> m_flowVariableProvider;

    /**
     * Stored during configure to be able to validate the expression (through compiling it). The validateSettings does
     * not pass the table specification around.
     */
    private DataTableSpec m_inputSpecification;

    /**
     * Created during configure and execute to determine output table specifications and access input table in the way
     * the java {@link Expression} needs it.
     */
    private MultiColumnStringManipulationConfigurator m_configurator;

    /**
     * This cell factory is created for execution and used later to release its resources. Although a
     * {@link MultiColumnStringManipulationCalculator} implements {@link AutoCloseable}, we can use a try-with-resources
     * block only in batch execution, not in streaming. The reason is that in streaming, the calculator is created and
     * passed to a {@link ColumnRearranger}. The node model then has to wait until the execution context is done. See
     * {@link #createStreamableOperator(PartitionInfo, PortObjectSpec[])} for further info on releasing resources.
     */
    private MultiColumnStringManipulationCalculator m_multiCalculatorCompiled;

    /**
     * One input table, one output table.
     *
     * @param settings The settings object shared with the dialog.
     */
    MultiColumnStringManipulationNodeModel(final MultiColumnStringManipulationSettings settings) {
        m_settings = settings;

        // capture getAvailableVariables from node model to access flow variables values in expression
        m_flowVariableProvider = (final String name) -> {
            for (VariableType<?> varType : SUPPORTED_FLOW_VARIABLE_TYPES) {
                final Map<String, FlowVariable> varsOfType = getAvailableFlowVariables(varType);
                if (varsOfType.containsKey(name)) {
                    return Optional.of(varsOfType.get(name).getValue(varType));
                }
            }
            return Optional.empty();
        };

    }

    /**
     * Use the settings object to create an appropriate {@link ColumnRearranger}, e.g., reflecting whether calculated
     * columns should be appended or replace their source columns.
     *
     * @param spec the specifications of the data table to process
     * @return a column rearranger, e.g., for computing the output specs and performing the actual computation.
     * @throws InvalidSettingsException if no expression has been set.
     * @throws CompilationFailedException if the expression can not be compiled.
     */
    /**
     * @return the input table spec if no columns are selected for iteration, otherwise generate output specifications
     *         using a {@link ColumnRearranger} with a {@link MultiColumnStringManipulationConfigurator} as cell
     *         factory.
     */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException {

        m_inputSpecification = inSpecs[IN_PORT];

        m_configurator = new MultiColumnStringManipulationConfigurator(m_settings, m_inputSpecification);

        if (m_configurator.isPassThrough()) {
            warnPassThrough();
            return inSpecs;
        }

        return new DataTableSpec[]{new DataTableSpec(m_configurator.getOutputColumnSpecs())};
    }

    /**
     * Computes the result using a {@link ColumnRearranger} with a {@link MultiColumnStringManipulationConfigurator} as
     * cell factory.
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec)
        throws Exception {

        final DataTableSpec dataTableSpec = inData[0].getDataTableSpec();

        exec.setMessage(() -> "Compiling expression.");


        if (m_configurator == null) {
            throw new InvalidSettingsException("Node can not be executed, since it has not been properly configured.");
        }

        if (m_configurator.isPassThrough()) {
            warnPassThrough();
            return inData;
        }

        final long rowCount = inData[IN_PORT].size();

        try (final MultiColumnStringManipulationCalculator cellFactory = MultiColumnStringManipulationCalculator
            .create(m_configurator,
                rowCount,
                m_flowVariableProvider,
                m_settings.isFailOnEvaluationException(),
                m_settings.isEvaluateWithMissingValues())) {

            final ColumnRearranger rearranger = m_configurator.createColumnRearranger(dataTableSpec, cellFactory);
            final BufferedDataTable o = exec.createColumnRearrangeTable(inData[0], rearranger, exec);

            return new BufferedDataTable[]{o};
        }

    }

    /**
     * Create new MultiColumnStringManipulationSettings and validate them by compiling an expression.
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {

        // create blank settings object to load into
        final MultiColumnStringManipulationSettings validationSettings = new MultiColumnStringManipulationSettings();
        // validate key existence
        validationSettings.validateSettingsInModel(settings);
        // load settings to validate them
        validationSettings.loadSettingsInModel(settings);

        CheckUtils.checkSetting(
            validationSettings.getExpression() != null && validationSettings.getExpression().length() > 0,
            "No expression has been set.");

        if (m_inputSpecification != null) {

            MultiColumnStringManipulationConfigurator validationConfigurator =
                new MultiColumnStringManipulationConfigurator(validationSettings, m_inputSpecification);

            if (!validationConfigurator.isPassThrough()) {

                try (final MultiColumnStringManipulationCalculator validationCellFactory =
                    MultiColumnStringManipulationCalculator.create(validationConfigurator,
                        -1,
                        m_flowVariableProvider,
                        validationSettings.isFailOnEvaluationException(),
                        m_settings.isEvaluateWithMissingValues())) {
                } catch (IOException e) {
                    warnIOException(e);
                } catch (CompilationFailedException | InstantiationException e1) {
                    throw new InvalidSettingsException(e1);
                }
            }

        }

    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_settings.loadSettingsInModel(settings);
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_settings.saveSettingsTo(settings);
    }

    @Override
    protected void reset() {
        // called for instance after changing something and confirming in the node dialog
        // or if something goes wrong in the node execution
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
    }

    /**
     * Only for streaming. <br/>
     * <br/>
     * Called by during the construction of the streamable operator in
     * {@link AbstractConditionalStreamingNodeModel#createStreamableOperator(PartitionInfo, PortObjectSpec[])}. The
     * {@link MultiColumnStringManipulationCalculator} created here is destroyed in
     * {@link #createStreamableOperator(PartitionInfo, PortObjectSpec[])}.
     */
    @Override
    protected ColumnRearranger createColumnRearranger(final DataTableSpec spec, final long rowCount)
        throws InvalidSettingsException {

        // if no columns are selected, don't do anything
        if (m_configurator.isPassThrough()) {
            warnPassThrough();
            return new ColumnRearranger(spec);
        }

        try {
            m_multiCalculatorCompiled = MultiColumnStringManipulationCalculator.create(
                m_configurator,
                rowCount,
                m_flowVariableProvider,
                m_settings.isFailOnEvaluationException(),
                m_settings.isEvaluateWithMissingValues());
        } catch (InstantiationException | CompilationFailedException e) {
            throw new InvalidSettingsException(e);
        }

        return m_configurator.createColumnRearranger(spec, m_multiCalculatorCompiled);
    }

    @Override
    protected boolean usesRowCount() {
        boolean uses = m_settings.getExpression().contains(Expression.ROWCOUNT);
        if (uses) {
            getLogger()
                .warn("The ROWCOUNT field is used in the expression. Manipulations cannot be done in streamed manner!");
        }
        return uses;
    }

    @Override
    protected boolean usesRowIndex() {
        boolean uses = m_settings.getExpression().contains(Expression.ROWINDEX);
        if (uses) {
            getLogger().warn(
                "The ROWINDEX field is used in the expression. Manipulations cannot be done in distributed manner!");
        }
        return uses;
    }

    /**
     * Creates a streamable operator that makes sure the resources by the compiled expression in
     * {@link MultiColumnStringManipulationCalculator} are released. Delegates functionality to the streamable operator
     * created by {@link AbstractConditionalStreamingNodeModel}'s default implementation but makes sure that runFinal
     * eventually releases the compiled resources.
     */
    @Override
    public StreamableOperator createStreamableOperator(final PartitionInfo partitionInfo,
        final PortObjectSpec[] inSpecs) throws InvalidSettingsException {

        StreamableOperator op = super.createStreamableOperator(partitionInfo, inSpecs);

        return new StreamableOperator() {

            @Override
            public void loadInternals(final StreamableOperatorInternals internals) {
                op.loadInternals(internals);
            }

            @Override
            public void runIntermediate(final PortInput[] inputs, final ExecutionContext exec) throws Exception {
                op.runIntermediate(inputs, exec);
            }

            @Override
            public StreamableOperatorInternals saveInternals() {
                return op.saveInternals();
            }

            @Override
            public void runFinal(final PortInput[] inputs, final PortOutput[] outputs, final ExecutionContext exec)
                throws Exception {
                try {
                    // leads to a call to createColumnRearranger, which sets m_multiCalculatorCompiled if not in
                    // pass-through mode
                    op.runFinal(inputs, outputs, exec);
                } finally {
                    // release the resources held by the compiled expression
                    // could be null if the node is in pass-through mode and skips compilation.
                    if (m_multiCalculatorCompiled != null) {
                        try {
                            m_multiCalculatorCompiled.close();
                        } catch (IOException e) {
                            warnIOException(e);
                        }
                    }
                }
            }

        };
    }

    private void warnIOException(final IOException e) {
        getLogger().warn(MessageFormat.format(
            "Could not release the resources held by the compiled java snippet expression:\n{0}", e.getMessage()));
    }

    /**
     * Display a visual warning indicator on the node that no columns are selected.
     * Trumps the warning that an empty output table has been created.
     */
    private void warnPassThrough() {
        setWarningMessage("No columns are selected. Will have no effect.");
    }

}
