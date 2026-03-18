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
 *   Mar 19, 2026 (Jochen Reißinger, TNG Technology Consulting GmbH): created
 */
package org.knime.base.node.rules.engine.twoports;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.persistence.legacy.EnumBooleanPersistor;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;

/**
 * Node parameters for the Rule-based Row Filter (Dictionary) node.
 *
 * @author Jochen Reißinger, TNG Technology Consulting GmbH
 * @since 5.12
 */
@SuppressWarnings("restriction")
@LoadDefaultsForAbsentFields
final class RuleEngineFilter2PortsNodeParameters extends AbstractRuleEngine2PortsNodeParameters {

    private enum FilterBehavior {
            @Label(value = "Output matching rows",
                description = "Rows where the first matching rule evaluates to TRUE are included in the output.")
            MATCHING_ROWS,
            @Label(value = "Output non-matching rows",
                description = "Rows where the first matching rule evaluates to FALSE or no rule matches are included "
                    + "in the output.")
            NON_MATCHING_ROWS;
    }

    private static final class FilterBehaviorPersistor extends EnumBooleanPersistor<FilterBehavior> {
        FilterBehaviorPersistor() {
            super(RuleEngineFilter2PortsNodeModel.CFGKEY_INCLUDE_ON_MATCH, FilterBehavior.class,
                FilterBehavior.MATCHING_ROWS);
        }

        @Override
        public FilterBehavior load(final NodeSettingsRO settings) throws InvalidSettingsException {
            if (!settings.containsKey(RuleEngineFilter2PortsNodeModel.CFGKEY_INCLUDE_ON_MATCH)) {
                return FilterBehavior.MATCHING_ROWS;
            }
            return super.load(settings);
        }
    }

    @Widget(title = "Filter behavior", description = "Choose whether to output matching or non-matching rows.")
    @ValueSwitchWidget
    @Persistor(FilterBehaviorPersistor.class)
    FilterBehavior m_filterBehavior = FilterBehavior.MATCHING_ROWS;
}
