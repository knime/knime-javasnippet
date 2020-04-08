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

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.knime.base.node.preproc.stringmanipulation.StringManipulatorProvider;
import org.knime.base.node.preproc.stringmanipulation.manipulator.Manipulator;
import org.knime.base.node.preproc.stringmanipulation.manipulator.StringManipulator;
import org.knime.base.node.util.ManipulatorProvider;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTable;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.container.CellFactory;
import org.knime.core.data.container.ColumnRearranger;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.ext.sun.nodes.script.expression.Expression;
import org.knime.ext.sun.nodes.script.settings.JavaScriptingSettings;
import org.knime.ext.sun.nodes.script.settings.JavaSnippetType;

/**
 *
 *
 * The {@link MultiColumnStringManipulationConfigurator} solves the following problems, which result from the
 * interaction of input data table, the java snippet {@link Expression} to evaluate, and the resulting output data
 * table.
 * <ul>
 * <li>Given an expression (e.g., <code>"capitalize(join($column1$, $$CURRENTCOLUMN$$))"</code>, determine the return
 * type of the expression (see {@link #determineReturnType(String)}) and thus the type of the output columns.</li>
 * <li>Bridge the gap between data in {@link DataTable} format and plain java, which is needed during the evaluation of
 * a java snippet expression. (see
 * {@link MultiColumnStringManipulationConfigurator.ColumnAccessor#getCellContents(DataRow)}).</li>
 * <li>Deliver the {@link DataTableSpec}s of the output data table (see {@link #determineOutputColumnSpecs()}).</li>
 * </ul>
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
class MultiColumnStringManipulationConfigurator {

    /**
     * Helper class for accessing data in a specific column of a {@link DataRow}. This is both used for the columns that
     * this node iterates over {@link MultiColumnStringManipulationConfigurator#m_iteratedInputColumns} and the fixed
     * columns that are referenced in the expression
     * {@link MultiColumnStringManipulationConfigurator#m_compiledExpression.m_usedInputFields} (e.g.,
     * <code>$column1$</code>).
     */
    static class ColumnAccessor {

        /** The offset of the column in the input data table. */
        final int indexInSourceTable;

        /** The name of the column in the input data table. */
        final String nameInSourceTable;

        /**
         * The type that is needed to convert from {@link DataCell} contents to plain java types -- to pass the value to
         * the java source generated for the expression.
         */
        final JavaSnippetType<?, ?, ?> javaSnippetType;

        public ColumnAccessor(final DataTableSpec spec, final String nameInSourceTable)
            throws InvalidSettingsException {
            this.indexInSourceTable = spec.findColumnIndex(nameInSourceTable);
            if (this.indexInSourceTable < 0) {
                throw new InvalidSettingsException(MessageFormat.format(
                    "Cannot iterate over column {0}. It does not exist in the input table.", nameInSourceTable));
            }
            this.nameInSourceTable = nameInSourceTable;
            javaSnippetType = JavaSnippetType.findType(spec.getColumnSpec(indexInSourceTable).getType());
        }

        /**
         * @param r the data row from which to retrieve the cell with the accessor's source table column index
         * @return contents of the cell in the row that corresponds to this column or null if it is missing.
         */
        public Object getCellContents(final DataRow r) {
            final DataCell cell = r.getCell(indexInSourceTable);
            return cell.isMissing() ? null : javaSnippetType.asJavaObject(cell);
        }

        /**
         * @return the value of the cell in the row that corresponds to this column. Uses {@link DataCell#toString()}.
         *         Returns null if the cell has a missing value.
         */
        public String getCellContentsString(final DataRow r) {
            final DataCell cell = r.getCell(indexInSourceTable);
            return cell.isMissing() ? null : cell.toString();
        }
    }

    static final NodeLogger LOGGER = NodeLogger.getLogger(MultiColumnStringManipulationConfigurator.class);

    /**
     * The raw java snippet expression to execute, e.g., "toNull($$CURRENTCOLUMN$$)". Will be wrapped in "return" and
     * ";" by {@link JavaScriptingSettings}. Should never be null or an empty String.
     */
    private final String m_expressionString;

    /**
     * Required by {@link JavaScriptingSettings#setReturnType(String)} and subsequently used by {@link Expression} to
     * generate source code (inserted as return type of a method). This field could be inlined (always equals
     * {@link #determineReturnType(String)}) but the typing aspect can use some documentation.
     */
    private final Class<?> m_returnTypeClass;

    /**
     * the return type of the expression as a JavaSnippetType, used to convert the result of a java snippet expression
     * to a {@link DataCell}, see {@link JavaSnippetType#asKNIMECell(Object)}.
     */
    private final JavaSnippetType<?, ?, ?> m_returnTypeJavaSnippet;

    /**
     * The return type of the expression as related to {@link DataCell}s. Needed for constructing
     * {@link DataColumnSpec}s in {@link #determineEvaluatedColumnSpecs()}.
     */
    private final DataType m_returnTypeKnime;

    /**
     * The schema of the input table.
     */
    private final DataTableSpec m_spec;

    /**
     * Accessors for the the columns in the input data table that the Multi Column String Manipulator node iterates
     * over, substituting their values one by one for the $$CURRENTCOLUMN$$ expression. This field is never null or an
     * empty array, otherwise the constructor will fail.
     */
    private final ColumnAccessor[] m_iteratedInputColumns;

    /**
     * The column specifications for the columns that result from evaluating the given {@link #m_expressionString} on
     * the {@link #m_iteratedInputColumns}. The order of the elements corresponds to the order of the
     * {@link #m_iteratedInputColumns}. Each {@link DataColumnSpec} is unique, because it holds a column name (either
     * the name of the iterated input column or a new name, when appending the calculated columns).
     */
    private final DataColumnSpec[] m_evaluatedColumnSpecs;

    /**
     * The column specifications of the output table. This depends on whether to replace or append column names
     * ({@link #isReplace()}), the desired output column names {@link #m_targetColumnNames} and the inferred return type
     * of the expression. The return type is more or less guessed from the java expression as a string, see
     * {@link #determineReturnType(String)}. Uses the specifications of the input columns that have been selected for
     * iteration as a starting point.
     */
    private final DataColumnSpec[] m_outputTableColumnSpecs;

    /**
     * The names of the output columns. E.g., if the input column names are {"column1", "column3"}, the this field could
     * be {"column1_transformed", "column3_transformed"}
     */
    private final String[] m_targetColumnNames;

    /**
     * Whether to replace the input columns with the calculated result columns or append them as new columns.
     */
    private final boolean m_replace;

    private final boolean m_isPassThrough;

    /**
     * Creates an instance of the temporary java code, sets the fields dynamically and evaluates the expression.
     *
     * @param spec Input data table specification.
     * @param iteratedColumns The names of the columns in the input table to iterate over.
     * @param expression The java snippet expression to execute.
     * @param targetColumns the names of the result columns. Either provide new names to achieve append behavior, or
     *            pass <code>iteratedColumns</code> again to achieve replace behavior.
     * @param provider Mapping from flow variable names to their values. Returns an empty optional if there is no
     *            variable with that name.
     * @throws InvalidSettingsException if target column names passed to
     *             {@link MultiColumnStringManipulationConfigurator} contains column names that are not in the
     *             {@link DataTableSpec} passed to the constructor.
     */
    MultiColumnStringManipulationConfigurator(final MultiColumnStringManipulationSettings settings,
        final DataTableSpec inputSpecification) throws InvalidSettingsException {

        final String[] sourceColumnNames =
            settings.getColumnFilterConfigurationSettingsModel().applyTo(inputSpecification).getIncludes();

        // new column names are formed by appending a user defined suffix and then making sure they are unique
        final Function<? super String, ? extends String> toOutputColumnName =
            s -> DataTableSpec.getUniqueColumnName(inputSpecification, s + settings.getAppendedColumnsSuffix());

        // compute new column names: use input columns if replace or create new ones if append
        final String[] targetColumnNames = settings.isReplace() ? sourceColumnNames
            : Arrays.stream(sourceColumnNames).map(toOutputColumnName).toArray(String[]::new);

        // if no columns are selected to iterate over, the
        // node does nothing, just outputs any input data unchanged
        m_isPassThrough = sourceColumnNames.length == 0;

        m_expressionString = settings.getExpression();

        m_replace = sourceColumnNames == targetColumnNames || Arrays.equals(sourceColumnNames, targetColumnNames);

        m_targetColumnNames = targetColumnNames;

        // use a for loop because ColumnAccessor might throw an InvalidSettingsException
        m_iteratedInputColumns = new ColumnAccessor[sourceColumnNames.length];
        for (int i = 0; i < sourceColumnNames.length; i++) {
            m_iteratedInputColumns[i] = new ColumnAccessor(inputSpecification, sourceColumnNames[i]);
        }

        m_spec = inputSpecification;

        // this is cheap (but not reliable), tries to guess return type from the expression string
        m_returnTypeClass = determineReturnType(getExpressionString());

        //
        // The return type for data cell construction, use JavaSnippetType functionality that looks up
        // the DataType corresponding to m_returnTypeClass
        //

        // this works via DataCell subclasses, e.g,. StringCell (isn't trivial to get rid of)
        m_returnTypeJavaSnippet = Arrays.stream(JavaSnippetType.TYPES)
            // match java classes
            .filter(t -> t.getJavaClass(false).equals(m_returnTypeClass))
            // should be one match
            .findAny()
            .orElseThrow(() -> new InvalidSettingsException(MessageFormat.format(
                "Return type of expression \"{0}\" cannot be determined. Figured out {1} as java return type, "
                    + "but no JavaSnippetType matches the result.",
                getExpressionString(), m_returnTypeClass.getName())));

        // TODO [array cell support] should be computed in determineReturnType, e.g., `stripLeft`, `string` and other
        // StringManipulators have multi-argument forms that return arrays. The original string manipulation node
        // also doesn't handle those correctly.
        final boolean isArrayReturn = false;
        m_returnTypeKnime = m_returnTypeJavaSnippet.getKNIMEDataType(isArrayReturn);

        m_evaluatedColumnSpecs = determineEvaluatedColumnSpecs(m_returnTypeKnime);

        m_outputTableColumnSpecs = determineOutputColumnSpecs();

    }

    /**
     * Guess the return type of a java snippet {@link Expression} by looking at the {@link StringManipulator}s it uses.
     * Fall back to string if in doubt.
     *
     * @param expression the java snippet to execute (excluding e.g., "return" and closing ";" as added by
     *            {@link JavaScriptingSettings}).
     * @return the runtime class of the return type, e.g., {@link String#getClass()}.
     */
    protected static Class<?> determineReturnType(final String expression) {

        // use string when in doubt
        final Class<?> fallback = String.class;

        final int endIndex = StringUtils.indexOf(expression, '(');
        if (endIndex < 0) {
            return fallback;
        }

        // assume the java snippet consists of nested manipulators only (which it doesn't have to)
        final String manipulatorName = expression.substring(0, endIndex);

        // look up the manipulator that matches the name
        final Collection<Manipulator> manipulators =
            StringManipulatorProvider.getDefault().getManipulators(ManipulatorProvider.ALL_CATEGORY);
        final Manipulator mp =
            manipulators.stream().filter(m -> manipulatorName.equals(m.getName())).findAny().orElse(null);

        return mp == null ? fallback : mp.getReturnType();

    }

    /**
     * @return the specifications of the columns that result from evaluating the expression on the columns selected for
     *         iteration ({@link #m_iteratedInputColumns}). The order of corresponds to {@link #m_iteratedInputColumns}.
     */
    private DataColumnSpec[] determineEvaluatedColumnSpecs(final DataType returnTypeKnime)
        throws InvalidSettingsException {

        final DataColumnSpec[] evaluatedColumnSpecs = new DataColumnSpec[m_iteratedInputColumns.length];

        // copy input column specifications, setting a new type and erasing domain information
        for (int i = 0; i < getIteratedInputColumns().length; i++) {
            // use the source column specification as starting point
            final DataColumnSpecCreator cloner =
                new DataColumnSpecCreator(getSpec().getColumnSpec(m_iteratedInputColumns[i].indexInSourceTable));
            cloner.setType(returnTypeKnime);
            cloner.removeAllHandlers();
            cloner.setDomain(null);
            cloner.setName(m_targetColumnNames[i]);
            evaluatedColumnSpecs[i] = cloner.createSpec();
        }

        return evaluatedColumnSpecs;

    }

    /**
     * @return {@link #m_outputTableColumnSpecs}
     * @throws InvalidSettingsException if the return type of the expression can not be determined
     */
    private DataColumnSpec[] determineOutputColumnSpecs() {

        int newNumberOfColumns =
            isReplace() ? m_spec.getNumColumns() : m_spec.getNumColumns() + getIteratedInputColumns().length;

        // copy over old specs, same size array if replace, with extra space if append
        final DataColumnSpec[] result =
            Arrays.copyOf(m_spec.stream().toArray(DataColumnSpec[]::new), newNumberOfColumns);

        // copy input column specification, setting a new type and erasing domain information
        // replace or append the new specification in the input table specification to get the output table spec
        for (int i = 0; i < getIteratedInputColumns().length; i++) {
            // use the source column specification as starting point
            DataColumnSpec columnSpecInOutput = m_evaluatedColumnSpecs[i];
            if (isReplace()) {
                result[m_iteratedInputColumns[i].indexInSourceTable] = columnSpecInOutput;
            } else {
                result[m_spec.getNumColumns() + i] = columnSpecInOutput;
            }
        }

        return result;
    }

    /**
     *
     * @param dataTableSpec
     * @param mcc
     * @return
     * @throws InvalidSettingsException
     */
    ColumnRearranger createColumnRearranger(final DataTableSpec dataTableSpec, final CellFactory cellFactory)
        throws InvalidSettingsException {
        final ColumnRearranger rearranger = new ColumnRearranger(dataTableSpec);
        if (isReplace()) {
            rearranger.replace(cellFactory, getIteratedColumnIndices());
        } else {
            rearranger.append(cellFactory);
        }
        return rearranger;
    }

    /**
     * Only used when replacing columns. Looks up the source columns' indices in the input data table specification.
     *
     * @return the indices of the source columns.
     */
    int[] getIteratedColumnIndices() {
        return Arrays.stream(m_iteratedInputColumns).mapToInt(ca -> ca.indexInSourceTable).toArray();
    }

    /**
     * @return {@link #m_evaluatedColumnSpecs}
     */
    DataColumnSpec[] getEvaluatedColumnSpecs() {
        return m_evaluatedColumnSpecs;
    }

    /**
     * @return {@link #m_outputTableColumnSpecs}
     */
    DataColumnSpec[] getOutputColumnSpecs() {
        return m_outputTableColumnSpecs;
    }

    /**
     * @return {@link #m_returnTypeClass}
     */
    Class<?> getReturnTypeClass() {
        return m_returnTypeClass;
    }

    /**
     * @return {@link #m_returnTypeJavaSnippet}
     */
    JavaSnippetType<?, ?, ?> getReturnJavaSnippetType() {
        return m_returnTypeJavaSnippet;
    }

    String getExpressionString() {
        return m_expressionString;
    }

    ColumnAccessor[] getIteratedInputColumns() {
        return m_iteratedInputColumns;
    }

    DataTableSpec getSpec() {
        return m_spec;
    }

    boolean isReplace() {
        return m_replace;
    }

    /**
     * @return whether the node is configured to just pass the input data unchanged as output data
     */
    boolean isPassThrough() {
        return m_isPassThrough;
    }

}
