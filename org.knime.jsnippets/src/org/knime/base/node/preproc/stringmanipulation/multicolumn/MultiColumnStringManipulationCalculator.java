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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.knime.core.data.container.AbstractCellFactory;
import org.knime.core.data.container.CellFactory;
import org.knime.core.data.container.ColumnRearranger;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
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
import org.knime.ext.sun.nodes.script.settings.JavaSnippetType;
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
@SuppressWarnings("javadoc")
public class MultiColumnStringManipulationCalculator extends AbstractCellFactory implements AutoCloseable {

    /**
     * A wrapper for all auxiliary objects around an instantiated expression.
     *
     * @author Carl Witt, KNIME AG, Zurich, Switzerland
     */
    static class ManagedExpression {

        private final Expression m_expression;
        private final ExpressionInstance m_expressionInstance;
        private final Map<InputField, Object> m_expressionContext;
        private final Map<InputField, ColumnAccessor> m_usedInputFields;
        private final Function<Object, DataCell> m_cellConstructor;

        /**
         * @param expression This reference is used to close the expression's open resources, see
         *            {@link MultiColumnStringManipulationCalculator#close()}.
         * @param expressionInstance The instance is created from
         *            {@link MultiColumnStringManipulationCalculator#m_expression} and used to evaluate the expression
         *            and compute cell values.
         * @param expressionContext Used to pass data to the expression, specifically the currently iterated column's
         *            value and flow variable values.
         * @param usedInputFields Maps the expressions input fields (as used in {@link #m_expressionContext}) that refer
         *            to columns, e.g., $column1$ to column accessors for retrieving their values.
         * @param cellConstructor Used to convert the object produced by the evaluation of the java expression to a data
         *            cell.
         */
        private ManagedExpression(final Expression expression, final ExpressionInstance expressionInstance,
            final Map<InputField, Object> expressionContext, final Map<InputField, ColumnAccessor> usedInputFields,
            final Function<Object, DataCell> cellConstructor) {
            super();
            m_expression = expression;
            m_expressionInstance = expressionInstance;
            m_expressionContext = expressionContext;
            m_usedInputFields = usedInputFields;
            m_cellConstructor = cellConstructor;
        }


    }

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
     * Instantiated expressions for different column types, e.g., String, Integer, or Double.
     * Compiling an expression for each distinct column type across the iterated columns allows us to avoid casting
     * everything to string.
     */
    private final Map<DataType, ManagedExpression> m_expressionsByType;

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
        // one per iterated column type: one for String columns, one for Integer columns, etc.
        final Map<DataType, JavaScriptingSettings> javaScriptingSettingsByType = getJavaScriptingSettings(transformer);
        final Map<DataType, ManagedExpression> managedExpressions = new HashMap<>();

        for (DataType columnType : javaScriptingSettingsByType.keySet()) {

            JavaScriptingSettings javaScriptingSettings = javaScriptingSettingsByType.get(columnType);

            // compile only for execution
            javaScriptingSettings.setInputAndCompile(transformer.getSpec());

            // this is closed in MultiColumnCalculator#close()
            Expression compiledExpression = javaScriptingSettings.getCompiledExpression();
            if (compiledExpression == null) {
                throw new InstantiationException("Cannot compile expression.");
            }

            // instantiate a wrapper object for the compiled expression
            final Expression expression = compiledExpression;
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
            expressionContext.put(new InputField(Expression.ROWCOUNT, FieldType.TableConstant),
                castRowCountToInt(rowCount));

            // Find statically referenced columns in the expression. The values of these columns are accessed via
            // the created ColumnAccessor and passed to the expression context of the expression for every row.
            final Map<InputField, ColumnAccessor> usedInputFields =
                transformer.getSpec().stream().map(columnSpec -> new InputField(columnSpec.getName(), FieldType.Column))
                    .filter(expressionInstance::needsInputField)
                    .collect(Collectors.toMap(Function.identity(), inputField -> {
                        final String columnName = inputField.getColOrVarName();
                        try {
                            return new ColumnAccessor(transformer.getSpec(), columnName);
                        } catch (InvalidSettingsException e1) {
                            throw new RuntimeException(MessageFormat.format(
                                "Can not iterate over column {0}, it is not present in the input data table.",
                                columnName));
                        }
                    }));

            // create a function that converts the objects returned by expression evaluation to DataCells for use in
            // the cell factory's compute method (getCells)
            final Function<Object, DataCell> cellConstructor = (final Object o) -> o == null ? DataType.getMissingCell()
                : transformer.getReturnJavaSnippetType().asKNIMECell(o);

            managedExpressions.put(columnType, new ManagedExpression(expression, expressionInstance, expressionContext,
                usedInputFields, cellConstructor));

        }

        return new MultiColumnStringManipulationCalculator(managedExpressions, failOnEvaluationProblems, evaluateWithMissingValues,
            transformer);

    }

    /**
     * Maybe used to consistently change how row counts larger than the maximum int value are handled. Called in
     * {@link #create(MultiColumnStringManipulationConfigurator, long, Function, boolean)} when putting the row count to
     * the expression context map and in {@link #getCells(DataRow)} when putting the current row index in the expression
     * context map. Since row index < row count, an overflow can usually already be detected in
     * {@link #create(MultiColumnStringManipulationConfigurator, long, Function, boolean)}, however in streaming this
     * might not be the case.
     *
     * TODO: [row count long] the expression API allows row count to be int only, update.
     *
     * @return the row count cast to an int.
     * @throws AssertionError when the row count is larger than the maximum integer value.
     */
    private static int castRowCountToInt(final long rowCount) {
        if (rowCount > Integer.MAX_VALUE) {
            throw new ArithmeticException(
                "The number of rows to process is too large to be stored in the $$ROWCOUNT$$ variable.");
        }
        return (int)rowCount;
    }

    /**
     * @param managedExpressions
     * @param failOnEvaluationProblems
     * @param evaluateWithMissingValues
     * @param transformer
     */
    public MultiColumnStringManipulationCalculator(final Map<DataType, ManagedExpression> managedExpressions,
        final boolean failOnEvaluationProblems, final boolean evaluateWithMissingValues,
        final MultiColumnStringManipulationConfigurator transformer) {
        super(transformer.getEvaluatedColumnSpecs());
        m_expressionsByType = managedExpressions;
        m_evaluateWithMissingValues = evaluateWithMissingValues;
        m_failOnEvaluationProblems = failOnEvaluationProblems;
        m_transformer = transformer;
    }

    private static final Pattern CURRENT_COLUMN_PATTERN =
        Pattern.compile(Pattern.quote(MultiColumnStringManipulationSettings.getCurrentColumnReference()));

    /**
     * Prepare the compilation of the expression by configuring a JavaScriptingSettings objects.
     *
     * @return the settings required to compile the expression for each distinct column type
     * @throws InvalidSettingsException the return type determined in {@link #determineOutputColumnSpecs()} can not be
     *             used to set the return type of JavaScriptingSettings object.
     */
    private static Map<DataType, JavaScriptingSettings> getJavaScriptingSettings(
        final MultiColumnStringManipulationConfigurator transformer) throws InvalidSettingsException {

        // TODO this would be nicer to have in one place
        final Map<Class<?>, String> typeStrings = Map.of(
            String.class, "S",
            Integer.class, "I",
            Double.class, "D"
        );

        Map<DataType, JavaScriptingSettings> result = new HashMap<>();

        for(DataType columnType : transformer.getDistinctIteratedColumnTypes()) {

            final JavaScriptingSettings s = new JavaScriptingSettings(null);

            // inform about deduced return type of the expression
            s.setReturnType(transformer.getReturnTypeClass().getName());

            s.setArrayReturn(false);

            //
            // replace occurrences of the current column reference with a virtual flow variable
            //

            // replace $$CURRENTCOLUMN$$ with a virtual flow variable $${SCURRENTCOLUMN}$$ (see class documentation)
            // e.g., search for $$CURRENTCOLUMN$$ and replace with $${SCURRENTCOLUMN}$$
            final String expressionString = transformer.getExpressionString();
            final Matcher matcher = CURRENT_COLUMN_PATTERN.matcher(expressionString);

            final String expressionWithVariable;
            if (matcher.find()) {
                // the current column is actually being used
                final Class<?> javaClass = JavaSnippetType.findType(columnType).getJavaClass(false);
                final String typeString = typeStrings.get(javaClass);
                if (typeString == null) {
                    throw new InvalidSettingsException(
                        "Column type %s maps to Java type %s, this node only supports Integer, Double or String"
                            .formatted(columnType, javaClass.getSimpleName()));
                }

                final String currentColumnRef = MultiColumnStringManipulationSettings.getCurrentColumnReferenceName();

                // escape $ since it is not a regex group reference
                expressionWithVariable = matcher.replaceAll("\\$\\${" + typeString + currentColumnRef + "}\\$\\$");
            } else {
                // nothing to do
                expressionWithVariable = expressionString;
            }

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
                throw new IllegalStateException(
                    "Cannot locate necessary libraries due to I/O problem: " + e.getMessage(), e);
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

            result.put(columnType, s);
        }
        return result;
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

        final DataCell[] result = new DataCell[m_transformer.getEvaluatedColumnSpecs().length];

        // update static columns & constants for the compiled expression for each type
        for (Map.Entry<DataType, ManagedExpression> e : m_expressionsByType.entrySet()) {

            ManagedExpression me = e.getValue();

            // update the input fields of the compiled expression
            me.m_expressionContext.put(ROW_INDEX_INPUT_FIELD, castRowCountToInt(m_lastProcessedRow));
            me.m_expressionContext.put(ROW_ID_INPUT_FIELD, row.getKey().getString());

            // static column references
            // pass current row's cell values from statically referenced columns to expression context
            // each input field represents a column and is mapped to its column index in the input table
            for (Map.Entry<InputField, ColumnAccessor> inputFieldToColumnIdx : me.m_usedInputFields.entrySet()) {

                // put the row's cell value into the expression context
                // statically referenced column values are unboxed back to java types
                Object cellContentsObject = inputFieldToColumnIdx.getValue().getCellContents(row);

                // if any of the statically referenced columns has a missing value, the expression has a missing value
                // for every iterated column. If evaluation with missing values is off, return missing values for
                // every iterated column.
                if (!m_evaluateWithMissingValues && cellContentsObject == null) {
                    Arrays.fill(result, DataType.getMissingCell());
                    return result;
                }

                me.m_expressionContext.put(inputFieldToColumnIdx.getKey(), cellContentsObject);
            }
        }

        // evaluate the expression for all dynamically referenced columns
        ColumnAccessor[] iteratedInputColumns = m_transformer.getIteratedInputColumns();
        for (int i = 0; i < iteratedInputColumns.length; i++) {
            ColumnAccessor accessor = iteratedInputColumns[i];
            result[i] = evaluate(row, m_expressionsByType.get(accessor.getColumnType()), accessor);
        }

        // update the row index of the last processed row
        m_lastProcessedRow += 1;
        return result;
    }

    /**
     * Compute the result of applying the expression to a single cell of a given row.
     * @param row the row containing the cell
     * @param me the compiled expression to evaluate
     * @param accessor used to get the cell contents of the desired column
     * @return the data cell that goes into the output table
     */
    private DataCell evaluate(final DataRow row, final ManagedExpression me, final ColumnAccessor accessor) {

        // bind the current column's value into the expression context
        final Object cellContents = accessor.getCellContents(row);

        // if the currently iterated column's value is missing and evaluation with missing values if off,
        // output a missing cell
        if( ! m_evaluateWithMissingValues && cellContents == null) {
            return DataType.getMissingCell();
        }

        me.m_expressionContext.put(CURRENT_COLUMN_INPUT_FIELD, cellContents);

        // compute cell content
        // code adapted from ColumnCalculator
        Object evaluationResult = null;
        try {
            // bind variables in expression through context mapping
            me.m_expressionInstance.set(me.m_expressionContext);
            // run
            evaluationResult = me.m_expressionInstance.evaluate();

        } catch (Abort ee) {
            final String message =
                "Calculation aborted: " + (ee.getMessage() == null ? "<no details>" : ee.getMessage());
            failOrContinue(row, message, ee);
        } catch (EvaluationFailedException ee) {
            String message;
            try { message = ((InvocationTargetException)ee.getCause()).getCause().getMessage(); }
            catch (ClassCastException e) { message = ee.getMessage(); }
            failOrContinue(row, message, ee);
        } catch (IllegalPropertyException ipe) {
            failOrContinue(row, ipe.getMessage(), ipe);
        }

        // construct the data cell using the generated function for the expressions return type
        return me.m_cellConstructor.apply(evaluationResult);
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
        m_expressionsByType.forEach((type, me)->{
            try {
                me.m_expression.close();
            } catch (IOException e) {
                NodeLogger.getLogger(MultiColumnStringManipulationCalculator.class)
                    .debug("Can't clean up compiled expression for type " + type + "\n" + e.getMessage());
            }
        });
    }

}
