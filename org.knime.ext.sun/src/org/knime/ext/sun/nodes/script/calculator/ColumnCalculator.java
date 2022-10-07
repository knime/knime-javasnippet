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
package org.knime.ext.sun.nodes.script.calculator;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.RowKey;
import org.knime.core.data.collection.CollectionDataValue;
import org.knime.core.data.container.CellFactory;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
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

/**
 * Interface implementation that executes the java code snippet and calculates
 * the new column, either appended or replaced.
 *
 * @author Bernd Wiswedel, University of Konstanz
 */
public class ColumnCalculator implements CellFactory {
    private static final NodeLogger LOGGER = NodeLogger
            .getLogger(ColumnCalculator.class);

    private final JavaScriptingSettings m_settings;
    private final ExpressionInstance m_expression;
    private final FlowVariableProvider m_flowVarProvider;
    private boolean m_hasReportedMissing = false;

    private final DataColumnSpec[] m_colSpec;

    private Map<InputField, Object> m_flowVarAssignmentMap;

    /**
     * The row index may be used for calculation. Need to be set immediately
     * before calculate is called.
     */
    private int m_lastProcessedRow = 0;

    private final int[] m_requiredIndices;

    /**
     * Creates new factory for a column appender. It creates an instance of the
     * temporary java code, sets the fields dynamically and evaluates the
     * expression.
     *
     * @param settings settings & other infos (e.g. return type)
     * @param flowVarProvider Accessor for flow variables (the NodeModel)
     * @throws InstantiationException if the instance cannot be instantiated.
     * @throws InvalidSettingsException If settings invalid.
     */
    public ColumnCalculator(final JavaScriptingSettings settings,
            final FlowVariableProvider flowVarProvider)
            throws InstantiationException, InvalidSettingsException {
        m_settings = settings;
        m_flowVarProvider = flowVarProvider;
        Expression compiledExpression = settings.getCompiledExpression();
        if (compiledExpression == null) {
            throw new InstantiationException(
                    "No compiled expression in settings");
        }
        m_expression = compiledExpression.getInstance();
        m_colSpec = new DataColumnSpec[]{m_settings.getNewColSpec()};
        var inputSpec = m_settings.getInputSpec();
        m_requiredIndices = inputSpec.columnsToIndices(inputSpec.stream()//
                .map(DataColumnSpec::getName)//
                .map(n -> new InputField(n, FieldType.Column))//
                .filter(m_expression::needsInputField)//
                .map(InputField::getColOrVarName)//
                .toArray(String[]::new));

    }

    @Override
    public Optional<int[]> getRequiredColumns() {
        return Optional.of(m_requiredIndices);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataColumnSpec[] getColumnSpecs() {
        return m_colSpec;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataCell[] getCells(final DataRow row) {
        return new DataCell[]{calculate(row)};
    }

    /**
     * @deprecated
     */
    @Deprecated
    @Override
    public void setProgress(final int curRowNr, final int rowCount,
            final RowKey lastKey, final ExecutionMonitor exec) {
        exec.setProgress(curRowNr / (double)rowCount, "Calculated row "
                + curRowNr + " (\"" + lastKey + "\")");
    }

    /**
     * Performs the calculation.
     *
     * @param row the row to process
     * @return the resulting cell
     */
    public DataCell calculate(final DataRow row) {
        if (m_flowVarAssignmentMap == null) {
            initFlowVarAssignmentMap();
        }
        var inputValues = createInputValues(row);
        if (inputValues == null) {
            // null indicates that we can't calculate the output because of a
            // missing value in the input
            return DataType.getMissingCell();
        }
        Object o = evaluateExpression(inputValues, row);
        return getCell(o);
    }

    private Object evaluateExpression(final Map<InputField, Object> inputValues, final DataRow row) {
        try {
            m_expression.set(inputValues);
            return m_expression.evaluate();
            // class correctness is asserted by compiler
        } catch (Abort ee) {
            var builder = new StringBuilder("Calculation aborted: ");
            String message = ee.getMessage();
            builder.append(message == null ? "<no details>" : message);
            // changing the exception type might break backwards compatibility
            throw new RuntimeException(builder.toString(), ee); //NOSONAR
        } catch (EvaluationFailedException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof InvocationTargetException) {
                cause = ((InvocationTargetException)cause).getCause();
            }
            String message =
                cause != null ? cause.getMessage() : ee.getMessage();
            LOGGER.warn("Evaluation of expression failed for row \""
                    + row.getKey() + "\": " + message, ee);
        } catch (IllegalPropertyException ipe) {
            LOGGER.warn("Evaluation of expression failed for row \""
                    + row.getKey() + "\": " + ipe.getMessage(), ipe);
        }
        return null;
    }

    private Map<InputField, Object> createInputValues(final DataRow row) {
        DataTableSpec spec = m_settings.getInputSpec();
        Map<InputField, Object> nameValueMap =
            new HashMap<>();
        nameValueMap.put(new InputField(Expression.ROWINDEX,
                FieldType.TableConstant), m_lastProcessedRow);
        m_lastProcessedRow++;
        nameValueMap.put(new InputField(Expression.ROWID,
                FieldType.TableConstant), row.getKey().getString());
        @SuppressWarnings("deprecation") // must be an int
        var rowCount = m_flowVarProvider.getRowCount();
        nameValueMap.put(new InputField(Expression.ROWCOUNT,
                FieldType.TableConstant), rowCount);
        nameValueMap.putAll(m_flowVarAssignmentMap);
        for (int i : m_requiredIndices) { //NOSONAR
            DataColumnSpec columnSpec = spec.getColumnSpec(i);
            var inputField =
                new InputField(columnSpec.getName(), FieldType.Column);
            DataCell cell = row.getCell(i);
            DataType cellType = columnSpec.getType();
            Object cellVal = null;
            if (cell.isMissing()) {
                if (m_settings.isInsertMissingAsNull()) {
                    // leave value as null
                } else {
                    reportMissing(row, columnSpec);
                    return null;
                }
            } else {
                cellVal = getJavaValue(cell, cellType);
            }
            nameValueMap.put(inputField, cellVal);
        }
        return nameValueMap;
    }

    private void reportMissing(final DataRow row, final DataColumnSpec columnSpec) {
        if (!m_hasReportedMissing) {
            m_hasReportedMissing = true;
            String message = "Row \"" + row.getKey() + "\" "
                    + "contains missing value in column \""
                    + columnSpec.getName() + "\" - returning missing";
            LOGGER.warn(message + " (omitting further warnings)");
        }
    }

    private void initFlowVarAssignmentMap() {
        m_flowVarAssignmentMap = new HashMap<>();
        for (Map.Entry<InputField, ExpressionField> e
                : m_expression.getFieldMap().entrySet()) {
            InputField f = e.getKey();
            if (f.getFieldType() == FieldType.Variable) {
                Class<?> c = e.getValue().getFieldClass();
                m_flowVarAssignmentMap.put(f,
                        m_flowVarProvider.readVariable(
                                f.getColOrVarName(), c));
            }
        }
    }

    private DataCell getCell(final Object obj) {
        var returnType = m_settings.getReturnType();
        var isArrayReturn = m_settings.isArrayReturn();
        for (JavaSnippetType<?, ?, ?> t : JavaSnippetType.TYPES) {
            if (returnType.equals(t.getJavaClass(false))) {
                if (obj == null) {
                    return DataType.getMissingCell();
                } else if (isArrayReturn) {
                    return t.asKNIMEListCell((Object[])obj);
                } else {
                    return t.asKNIMECell(obj);
                }
            }
        }
        var className = obj == null ? "null" : obj.getClass();
        throw new InternalError("No mapping for objects of class "
                + className);
    }

    private static Object getJavaValue(final DataCell cell, DataType cellType) {
        boolean isArray = cellType.isCollectionType();
        if (isArray) {
            cellType = cellType.getCollectionElementType();
        }
        for (JavaSnippetType<?, ?, ?> t : JavaSnippetType.TYPES) {
            if (t.checkCompatibility(cellType)) {
                if (isArray) {
                    return t.asJavaArray((CollectionDataValue)cell);
                } else {
                    return t.asJavaObject(cell);
                }
            }
        }
        return null;
    }

}
