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
 */
package org.knime.base.node.preproc.stringmanipulation.multicolumn;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.knime.base.node.preproc.stringmanipulation.StringManipulatorProvider;
import org.knime.base.node.preproc.stringmanipulation.manipulator.Manipulator;
import org.knime.base.node.preproc.stringmanipulation.multicolumn.MultiColumnStringManipulationConfigurator.ColumnAccessor;
import org.knime.base.node.util.ManipulatorProvider;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataType;
import org.knime.core.data.collection.ListCell;
import org.knime.core.data.container.AbstractCellFactory;
import org.knime.core.data.container.CellFactory;
import org.knime.core.data.container.ColumnRearranger;
import org.knime.core.node.InvalidSettingsException;
import org.knime.ext.sun.nodes.script.calculator.ColumnCalculator;
import org.knime.ext.sun.nodes.script.compile.CompilationFailedException;
import org.knime.ext.sun.nodes.script.expression.Abort;
import org.knime.ext.sun.nodes.script.expression.EvaluationFailedException;
import org.knime.ext.sun.nodes.script.expression.Expression;
import org.knime.ext.sun.nodes.script.expression.Expression.ExpressionField;
import org.knime.ext.sun.nodes.script.expression.Expression.FieldType;
import org.knime.ext.sun.nodes.script.expression.Expression.InputField;
import org.knime.ext.sun.nodes.script.expression.ExpressionInstance;
import org.knime.ext.sun.nodes.script.expression.IllegalPropertyException;
import org.knime.ext.sun.nodes.script.settings.JavaScriptingSettings;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Compiles an expression and evaluates it for every column in a set of input columns in turn. Every result is used to
 * populate an output column. The {@link #m_transformer} determines how these output columns are named and whether they
 * replace input columns or whether they are appended to the output table. It also helps in accessing the data in an
 * input table to process. The logic used in {@link MultiColumnStringManipulationConfigurator} is used both to configure
 * a node (determine output table specifications) and during execute. Since it does not compile the expression, it is
 * lightweight and was thus factored out of the {@link MultiColumnStringManipulationCalculator}. This allows node
 * configuration to use only {@link MultiColumnStringManipulationConfigurator} and only the actual execution to cause
 * compilation.
 *
 * The calculation is similar to how the {@link ColumnCalculator} is implemented, but generalized to multiple columns.
 * The idea is that the java code snippet is the same for every column and thus has to be compiled only once, which
 * saves a lot of time on many columns. <br/>
 * <br/>
 *
 * This class relies on {@link JavaScriptingSettings} to compile expressions, in a similar way the
 * {@link ColumnCalculator} does. Unfortunately, JavaScriptingSettings are also designed for a single column (e.g.,
 * {@link JavaScriptingSettings#getColName()}). Determining the output type of an expression is done without compilation
 * and was thus moved to {@link MultiColumnStringManipulationConfigurator#determineReturnType(String)}.
 *
 * In an expression, references to specific columns, e.g., <code>$column1$</code> are referred to as static references.
 * On the other hand, columns referenced through $$CURRENTCOLUMN$$ are referred to as dynamic references, because the
 * column the reference points to depends on the selected input columns and which one of it is currently processed. The
 * static references are collected in {{@link #m_compiledExpression.m_usedInputFields}. The dynamic references are
 * listed in {{@link #m_iteratedInputColumns}, and the order in which they are listed corresponds to the order of the
 * computed output cells in {{@link #getCells(DataRow)}. The static and dynamic references may overlap (a statically
 * referenced field may also be iterated over) in which case there will be two accessors, one in
 * {@link #m_compiledExpression.m_usedInputFields} and a separate one in {@link #m_iteratedInputColumns}. Since the
 * number of static references is typically small, this doesn't hurt.
 *
 * One tricky aspect in reusing expressions for multiple columns is to pass the value of the currently iterated column
 * into the expression. The easiest way is to use the flow variable syntax in an expression
 * ($${Ssome.string.variable}$$). When evaluating the expression, the value of the flow variable will be looked up in a
 * map (I call it the expression context) which can easily be used to bind the value of the variable (without actually
 * creating a flow variable). <br/>
 * Due to the way Expressions are parsed, this was the simplest alternative I found. For instance, $$ROWCOUNT$$ is a
 * special identifier that's the expression parsers tokenizer is looking for and handling in a special way. Adding new
 * ones seemed dangerous. Using column references (e.g., mapping new InputField("$$CURRENTCOLUMN$$, FieldType.Column) to
 * the current iteration's column value won't work because the evaluation nevertheless looks up the value of the column
 * (which doesn't exist). <br/>
 * <br/>
 *
 * Note on caching: it is assumed that a new MultiColumnCalculator is created every time the expression changes. In
 * principle, it would be possible to reuse the compiled expression if only the source and target columns change, but
 * since the expression is compiled only once that's probably not necessary. <br/>
 * <br/>
 *
 * As a CellFactory, an instance of this class is typically passed to a {@link ColumnRearranger}.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public class MultiColumnStringManipulationCalculator extends AbstractCellFactory implements AutoCloseable {

    /**
     * Reusable key for the expression context map {@link #m_expressionContext}. Used to pass the currently iterated
     * column's value into the java snippet expression.
     */
    private static final InputField CURRENT_COLUMN_INPUT_FIELD =
        new InputField(MultiColumnStringManipulationSettings.getCurrentColumnReferenceName(), FieldType.Variable);

    /**
     * Reusable key for the expression context map {@link #m_expressionContext} Used to pass the id of the currently
     * processed row to the expression.
     */
    private static final InputField ROW_ID_INPUT_FIELD = new InputField(Expression.ROWID, FieldType.TableConstant);

    /**
     * Reusable key for the expression context map {@link #m_compiledExpression.m_expressionContext} Used to pass the
     * index/offset of the currently processed row to the expression.
     */
    private static final InputField ROW_INDEX_INPUT_FIELD =
        new InputField(Expression.ROWINDEX, FieldType.TableConstant);

    private final MultiColumnStringManipulationConfigurator m_transformer;

    /**
     * This reference is used to close the expression's open resources, see {@link #close()}.
     */
    private final Expression m_expression;

    /**
     * The instance is created from {@link #m_expression} and used to evaluate the expression and compute cell values.
     */
    private final ExpressionInstance m_expressionInstance;

    /**
     * Used to pass data to the expression, specifically the currently iterated column's value and flow variable values.
     */
    private final Map<InputField, Object> m_expressionContext;

    /**
     * Maps the expressions input fields (as used in {@link #m_expressionContext}) that refer to columns, e.g.,
     * $column1$ to column accessors for retrieving their values.
     */
    private final Map<InputField, ColumnAccessor> m_usedInputFields;

    /**
     * Used to convert the object produced by the evaluation of the java expression to a data cell.
     *
     * TODO [array cell types] returns are to be supported, this function would have possibly to return a
     * {@link ListCell}.
     */
    private final Function<Object, DataCell> m_cellConstructor;

    /**
     * Whether to stop evaluation on expression evaluation errors.
     */
    private final boolean m_failOnEvaluationProblems;

    /**
     * Whether to evaluate an expression if one of the columns has a missing value.
     * If true, the missing values will be set to null.
     * If false, evaluation is skipped and a missing value is returned for the corresponding cell.
     */
    private final boolean m_evaluateWithMissingValues;

    /**
     * The index/offset of the currently processed row.
     */
    private long m_lastProcessedRow = 0;

    /**
     * To avoid jamming the logs, only report a single warning upon cell evaluation exceptions and suppress all warnings
     * after the first has been logged.
     */
    private boolean m_aWarningHasBeenLogged = false;

    /**
     * Factory method to compile the expression specified in the given {@link MultiColumnStringManipulationConfigurator}
     * and initialize auxiliary data structures, using the private constructor. <br/>
     * Throws an exception if the expression could not be compiled. Create a new
     * {@link MultiColumnStringManipulationConfigurator} in this case with updated settings and recompile. Attempting to
     * use a {@link MultiColumnStringManipulationConfigurator} that threw errors during compile for calculation will
     * likely result in null pointer exceptions.
     *
     * @param transformer which columns to iterate over and whether computed columns should replace or be appended
     * @param rowCount number of rows in the table to process
     * @param flowVarProvider provides access to the values of flow variables appearing in the expression
     * @param failOnEvaluationProblems whether to stop evaluation on exceptions or carry on
     * @param evaluateWithMissingValues whether to evaluate with null for missing values or skip and return null
     * @return a cell factory that can be provided to a {@link ColumnRearranger}
     * @throws InstantiationException if the expression can not be instantiated
     * @throws CompilationFailedException if the expression can not be compiled
     * @throws InvalidSettingsException if the expression can not be created or uses non-existent flow variables
     */
    static MultiColumnStringManipulationCalculator create(final MultiColumnStringManipulationConfigurator transformer,
        final long rowCount, final Function<String, Optional<Object>> flowVarProvider,
        final boolean failOnEvaluationProblems, final boolean evaluateWithMissingValues)
        throws InstantiationException, CompilationFailedException, InvalidSettingsException {

        // create settings (handles framework bundle, string manipulator code imports)
        final JavaScriptingSettings javaScriptingSettings = getJavaScriptingSettings(transformer);

        // compile only for execution
        javaScriptingSettings.setInputAndCompile(transformer.getSpec());

        if (javaScriptingSettings.getCompiledExpression() == null) {
            throw new InstantiationException("Cannot compile expression.");
        }

        // instantiate a wrapper object for the compiled expression
        final Expression expression = javaScriptingSettings.getCompiledExpression();
        final ExpressionInstance expressionInstance = expression.getInstance();

        // prepare basic expression context to add variables and constants to
        // (variable: currently iterated column's value, constants: current row index, etc.)
        final HashMap<InputField, Object> expressionContext = new HashMap<InputField, Object>();
        for (Map.Entry<InputField, ExpressionField> e : expression.getFieldMap().entrySet()) {
            final InputField flowVarInputField = e.getKey();
            if (flowVarInputField.getFieldType().equals(FieldType.Variable)) {

                // of all used variables, don't try to look up the value of $${SCURRENTCOLUMN} because
                // that's bound dynamically in getCell
                if (MultiColumnStringManipulationSettings.getCurrentColumnReferenceName()
                    .equals(flowVarInputField.getColOrVarName())) {
                    continue;
                }

                // look up value of flow variable and store in expression context
                final Object flowVariableValue = flowVarProvider.apply(flowVarInputField.getColOrVarName())
                    .orElseThrow(() -> new InvalidSettingsException(
                        String.format("The expression uses the flow variable %s, which does not exist.",
                            flowVarInputField.getColOrVarName())));
                expressionContext.put(flowVarInputField, flowVariableValue);
            }
        }

        // row count of input table is constant, add only once to expression context
        // but it might not be set, e.g., when compiling for validating the expression
        assert rowCount <= Integer.MAX_VALUE : "The number of rows to process is too large "
            + "to be stored in the $$ROWCOUNT$$ variable.";
        // TODO: [row count long] the expression API allows row count to be int only, update.
        int rowCountInt = (int)rowCount;
        expressionContext.put(new InputField(Expression.ROWCOUNT, FieldType.TableConstant), rowCountInt);

        // Find statically referenced columns in the expression. The values of these columns are accessed via
        // the created ColumnAccessor and passed to the expression context of the expression for every row.
        final Map<InputField, ColumnAccessor> usedInputFields = transformer.getSpec().stream()
            .map(columnSpec -> new InputField(columnSpec.getName(), FieldType.Column))
            .filter(expressionInstance::needsInputField).collect(Collectors.toMap(Function.identity(), inputField -> {
                final String columnName = inputField.getColOrVarName();
                try {
                    return new ColumnAccessor(transformer.getSpec(), columnName);
                } catch (InvalidSettingsException e1) {
                    throw new RuntimeException(MessageFormat.format(
                        "Can not iterate over column {0}, it is not present in the input data table.", columnName));
                }
            }));

        // create a function that converts the objects returned by expression evaluation to DataCells for use in
        // the cell factory's compute method (getCells)
        // TODO [array cell support] if array return type would be supported, something like this was necessary:
        // t.asKNIMEListCell((Object[])o);
        final Function<Object, DataCell> cellConstructor = (final Object o) -> o == null ? DataType.getMissingCell()
            : transformer.getReturnJavaSnippetType().asKNIMECell(o);

        return new MultiColumnStringManipulationCalculator(expression, expressionInstance, expressionContext,
            usedInputFields, cellConstructor, failOnEvaluationProblems, evaluateWithMissingValues, transformer);

    }

    private MultiColumnStringManipulationCalculator(final Expression expression,
        final ExpressionInstance expressionInstance, final Map<InputField, Object> expressionContext,
        final Map<InputField, ColumnAccessor> usedInputFields, final Function<Object, DataCell> cellConstructor,
        final boolean failOnEvaluationProblems, final boolean evaluateWithMissingValues,
        final MultiColumnStringManipulationConfigurator transformer) {
        super(transformer.getEvaluatedColumnSpecs());
        m_expression = expression;
        m_expressionInstance = expressionInstance;
        m_expressionContext = expressionContext;
        m_usedInputFields = usedInputFields;
        m_cellConstructor = cellConstructor;
        m_evaluateWithMissingValues = evaluateWithMissingValues;
        m_failOnEvaluationProblems = failOnEvaluationProblems;
        m_transformer = transformer;
    }

    /**
     * Prepare the compilation of the expression by configuring a JavaScriptingSettings objects.
     *
     * @return
     * @throws InvalidSettingsException the return type determined in {@link #determineOutputColumnSpecs()} can not be
     *             used to set the return type of JavaScriptingSettings object.
     */
    private static JavaScriptingSettings getJavaScriptingSettings(
        final MultiColumnStringManipulationConfigurator transformer) throws InvalidSettingsException {

        final JavaScriptingSettings s = new JavaScriptingSettings(null);

        // inform about deduced return type of the expression
        s.setReturnType(transformer.getReturnTypeClass().getName());

        // TODO [array output cell support] this should be determined in
        // determineOutputColumnSpecs and passed to this line of code
        s.setArrayReturn(false);

        //
        // replace occurrences of the current column reference with a virtual flow variable
        //

        final String searchFor = MultiColumnStringManipulationSettings.getCurrentColumnReference();
        final String currentColumnReferenceName = MultiColumnStringManipulationSettings.getCurrentColumnReferenceName();

        // replace $$CURRENTCOLUMN$$ with a virtual flow variable $${SCURRENTCOLUMN}$$ (see class documentation)
        final String expressionWithVariable = transformer.getExpressionString().replaceAll(
            // e.g., search for $$CURRENTCOLUMN$$
            java.util.regex.Pattern.quote(searchFor),
            // ...and replace with $${SCURRENTCOLUMN}$$
            // escape $ since it is not a regex group reference
            "\\$\\${S" + currentColumnReferenceName + "}\\$\\$");

        s.setExpression("return " + expressionWithVariable + ";");

        //
        // reused code from ColumnCalculator
        //

        s.setExpressionVersion(Expression.VERSION_2X);
        s.setHeader("");

        final Bundle bundle = FrameworkUtil.getBundle(MultiColumnStringManipulationCalculator.class);
        try {
            final List<String> includes = new ArrayList<>();
            final URL snippetIncURL = FileLocator.find(bundle, new Path("/lib/snippet_inc"), null);
            final File includeDir = new File(FileLocator.toFileURL(snippetIncURL).getPath());
            for (File includeJar : includeDir.listFiles()) {
                if (includeJar.isFile() && includeJar.getName().endsWith(".jar")) {
                    includes.add(includeJar.getPath());
                    MultiColumnStringManipulationConfigurator.LOGGER.debugWithFormat("Include jar file: %s",
                        includeJar.getPath());
                }
            }
            final StringManipulatorProvider provider = StringManipulatorProvider.getDefault();
            includes.add(provider.getJarFile().getAbsolutePath());
            includes.add(FileLocator.getBundleFile(FrameworkUtil.getBundle(StringUtils.class)).getAbsolutePath());
            s.setJarFiles(includes.toArray(new String[includes.size()]));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot locate necessary libraries due to I/O problem: " + e.getMessage(),
                e);
        }
        s.setReplace(transformer.isReplace());

        // imports
        final List<String> imports = new ArrayList<>();
        // Use defaults imports
        imports.addAll(Arrays.asList(Expression.getDefaultImports()));
        final StringManipulatorProvider provider = StringManipulatorProvider.getDefault();
        // Add StringManipulators to the imports
        Collection<Manipulator> manipulators = provider.getManipulators(ManipulatorProvider.ALL_CATEGORY);
        for (Manipulator manipulator : manipulators) {
            String toImport = manipulator.getClass().getName();
            imports.add("static " + toImport + ".*");
        }
        s.setImports(imports.toArray(new String[imports.size()]));

        return s;
    }

    /**
     * @return the new data cells in the order of
     *         {@link MultiColumnStringManipulationConfigurator#getIteratedInputColumns()}.
     */
    @Override
    public DataCell[] getCells(final DataRow row) {

        // don't produce data without inputs
        if (m_transformer.getIteratedInputColumns().length == 0) {
            return new DataCell[0];
        }

        // update the input fields of the compiled expression
        m_expressionContext.put(ROW_INDEX_INPUT_FIELD, m_lastProcessedRow++);
        m_expressionContext.put(ROW_ID_INPUT_FIELD, row.getKey().getString());

        final DataCell[] result = new DataCell[m_transformer.getEvaluatedColumnSpecs().length];

        // pass current row's cell values to expression context
        // each input field represents a column and is mapped to its column index in the input table
        for (Map.Entry<InputField, ColumnAccessor> inputFieldToColumnIdx : m_usedInputFields.entrySet()) {

            // put the row's cell value into the expression context
            // statically referenced column values are unboxed back to java types
            Object cellContentsObject = inputFieldToColumnIdx.getValue().getCellContents(row);

            // if any of the statically referenced columns has a missing value, the expression has a missing value for
            // every iterated column. If evaluation with missing values is off, return missing values for
            // every iterated column.
            if (! m_evaluateWithMissingValues && cellContentsObject == null) {
                Arrays.fill(result, DataType.getMissingCell());
                return result;
            }

            m_expressionContext.put(inputFieldToColumnIdx.getKey(), cellContentsObject);

            // TODO [array output cell support] would require something like
            // boolean isArray = cellType.isCollectionType();
            // if (isArray) {
            //     cellType = cellType.getCollectionElementType();
            // }
            // if (isArray) {
            //     cellVal = t.asJavaArray((CollectionDataValue)cell);
            // }
        }

        // iterate over selected columns, binding their values to $$CURRENTCOLUMN$$ variable,
        // each iteration generates one result DataCell
        // if a problem occurs, the failOrContinue either swallows exceptions or escalates them as an
        // IllegalArgumentException (since the interface doesn't allow us to throw a checked exception here).
        for (int i = 0; i < m_transformer.getIteratedInputColumns().length; i++) {

            // bind the current column's value into the expression context
            // convert the content of a dynamically referenced column to string (since this a string manipulation node)
            // also better than allowing only string columns as input columns to iterate over
            final String cellContentsString = m_transformer.getIteratedInputColumns()[i].getCellContentsString(row);

            // if the currently iterated column's value is missing and evaluation with missing values if off,
            // output a missing cell
            if( ! m_evaluateWithMissingValues && cellContentsString == null) {
                result[i] = DataType.getMissingCell();
                continue;
            }

            m_expressionContext.put(CURRENT_COLUMN_INPUT_FIELD, cellContentsString);

            // compute cell content
            // code adopted from ColumnCalculator
            Object o = null;
            try {
                // bind variables in expression through context mapping
                m_expressionInstance.set(m_expressionContext);
                // run
                o = m_expressionInstance.evaluate();

            } catch (Abort ee) {
                final String message =
                    "Calculation aborted: " + (ee.getMessage() == null ? "<no details>" : ee.getMessage());
                failOrContinue(row, message, ee);
            } catch (EvaluationFailedException ee) {
                String message;
                try {
                    message = ((InvocationTargetException)ee.getCause()).getCause().getMessage();
                } catch (ClassCastException e) {
                    message = ee.getMessage();
                }
                failOrContinue(row, message, ee);
            } catch (IllegalPropertyException ipe) {
                failOrContinue(row, ipe.getMessage(), ipe);
            }

            // construct the data cell using the generated function for the expressions return type
            result[i] = m_cellConstructor.apply(o);
        }

        return result;
    }

    /**
     * Handle errors during expression evaluation when computing the values of cells. Swallows exceptions if
     * {@link #m_failOnEvaluationProblems} is false. Escalates exceptions otherwise. Uses unchecked exception because of
     * API restrictions {@link CellFactory#getCells(DataRow)} doesn't allow for exceptions). Logs at most one warning,
     * swallows subsequent warnings.
     *
     * @param row The data row that was processed when the problem occured.
     * @param message What to log (if enabled)
     */
    private void failOrContinue(final DataRow row, final String message, final Exception exception) {
        final String messageWithRowContext =
            MessageFormat.format("Evaluation of expression failed for row \"{0}\": {1}", row.getKey(), message);
        if (!m_aWarningHasBeenLogged) {
            MultiColumnStringManipulationConfigurator.LOGGER.warn(messageWithRowContext);
            m_aWarningHasBeenLogged = true;
        }
        // stop the execution if problems should not be ignored
        if (m_failOnEvaluationProblems) {
            throw new RuntimeException("Execution stopped: " + messageWithRowContext, exception);
        }
    }

    @Override
    public void close() throws IOException {
        m_expression.close();
    }

}
