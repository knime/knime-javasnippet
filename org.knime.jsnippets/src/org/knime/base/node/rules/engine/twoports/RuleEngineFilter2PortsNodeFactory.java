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
 * ---------------------------------------------------------------------
 *
 * History
 *   11.04.2008 (thor): created
 */
package org.knime.base.node.rules.engine.twoports;

import static org.knime.node.impl.description.PortDescription.fixedPort;

import java.util.List;
import java.util.Map;

import org.knime.core.node.NodeDescription;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;
import org.knime.core.webui.node.dialog.NodeDialog;
import org.knime.core.webui.node.dialog.NodeDialogFactory;
import org.knime.core.webui.node.dialog.NodeDialogManager;
import org.knime.core.webui.node.dialog.SettingsType;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultKaiNodeInterface;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeDialog;
import org.knime.core.webui.node.dialog.kai.KaiNodeInterface;
import org.knime.core.webui.node.dialog.kai.KaiNodeInterfaceFactory;
import org.knime.node.impl.description.DefaultNodeDescriptionUtil;
import org.knime.node.impl.description.PortDescription;

/**
 * This factory creates all necessary object for the Rule-based Row Filter (Dictionary) node.
 *
 * @author Thorsten Meinl, University of Konstanz
 * @author Gabor Bakos
 * @author Jochen Reißinger, TNG Technology Consulting GmbH
 */
@SuppressWarnings("restriction")
public class RuleEngineFilter2PortsNodeFactory extends NodeFactory<RuleEngineFilter2PortsNodeModel>
    implements NodeDialogFactory, KaiNodeInterfaceFactory {

    private static final String NODE_NAME = "Rule-based Row Filter (Dictionary)";

    private static final String NODE_ICON = "./rule_engine_filter.png";

    private static final String SHORT_DESCRIPTION =
        "Filters the input table based on user-defined (in a table) business rules";

    private static final String FULL_DESCRIPTION = """
            This node takes a list of user-defined rules and tries to match them to each row in the input table. \
            If the first matching rule has a <tt>TRUE</tt> outcome, the row will be selected for inclusion. \
            Otherwise (i.e. if the first matching rule yields <tt>FALSE</tt>) it will be excluded. \
            If no rule matches the row will be excluded. Inclusion and exclusion may be inverted, see the options below.
            <p>
            Each rule is represented by a row. To add comments, start a line in a (condition) cell with \
            <tt>//</tt> (comments can not be placed in the same line as a rule). Anything after <tt>//</tt> \
            will not be interpreted as a rule. Rules consist of a condition part (antecedent), which must evaluate \
            to <i>true</i> or <i>false</i>, and an outcome (consequent, after the =&gt; symbol) which is either \
            <tt>TRUE</tt> or <tt>FALSE</tt>.
            </p>
            <p>
            If no rule matches, the outcome is treated as if it was <tt>FALSE</tt>.
            </p>
            <p>
            Columns are given by their names surrounded by $, numbers are given in the usual decimal representation. \
            Note that strings must not contain (double-)quotes (for those cases use the following syntax: \
            <tt>/Oscar Wilde's wisdom: "Experience is simply the name we give our mistakes."/</tt>). \
            The flow variables are represented by <b>$${</b>TypeCharacterAndFlowVarName<b>}$$</b>. \
            The TypeCharacter should be 'D' for double (real) values, 'I' for integer values and 'S' for strings.
            </p>
            <p>The logical expressions can be grouped with parentheses. The precedence rules for them are the \
            following: <tt>NOT</tt> binds most, <tt>AND</tt>, <tt>XOR</tt> and finally <tt>OR</tt> the least. \
            Comparison operators always take precedence over logical connectives. \
            All operators (and their names) are case-sensitive.
            </p>
            <p>
            The <tt>ROWID</tt> represents the row key string, the <tt>ROWINDEX</tt> is the index of the row \
            (first row has <tt>0</tt> value), while <tt>ROWCOUNT</tt> stands for the number of rows in the table.
            </p>
            <p>Some example rules (each should be in one row):</p>
            <pre>
// This is a comment
$Col0$ > 0 => TRUE
</pre>
            When the values in Col0 are greater than 0, we select the row (if no previous rule matched with FALSE \
            outcome).
            <pre>
$Col0$ = "Active" AND $Col1$ &lt;= 5 => TRUE
</pre>
            You can combine conditions.
            <pre>
$Col0$ LIKE "Market Street*" AND
    ($Col1$ IN ("married", "divorced")
        OR $Col2$ > 40) => FALSE
</pre>
            With parentheses you can combine multiple conditions.
            <pre>
$Col0$ MATCHES $${SFlowVar0}$$ OR $$ROWINDEX$$ &lt; $${IFlowVar1}$$ =>
    FALSE
</pre>
            The flow variables, table constants can also appear in conditions.
            <p>
            The following comparisons result true (other values are neither less, nor greater or equal to missing \
            and NaN values):
            <ul>
            <li><b>?</b> =,&lt;=,&gt;= <b>?</b></li>
            <li><b>NaN</b> =,&lt;=,&gt;= <b>NaN</b></li>
            </ul>
            </p>
            """;

    private static final List<PortDescription> INPUT_PORTS = List.of( //
        fixedPort("Input table", "Any data table from which to filter rows"), //
        fixedPort("Rules", "Table containing the rules"));

    private static final List<PortDescription> OUTPUT_PORTS =
        List.of(fixedPort("Filtered", "Data table with the included rows"));

    /**
     * {@inheritDoc}
     */
    @Override
    protected NodeDialogPane createNodeDialogPane() {
        return NodeDialogManager.createLegacyFlowVariableNodeDialog(createNodeDialog());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeDialog createNodeDialog() {
        return new DefaultNodeDialog(SettingsType.MODEL, RuleEngineFilter2PortsNodeParameters.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeDescription createNodeDescription() {
        return DefaultNodeDescriptionUtil.createNodeDescription( //
            NODE_NAME, //
            NODE_ICON, //
            INPUT_PORTS, //
            OUTPUT_PORTS, //
            SHORT_DESCRIPTION, //
            FULL_DESCRIPTION, //
            List.of(), //
            RuleEngineFilter2PortsNodeParameters.class, //
            null, //
            NodeType.Manipulator, //
            List.of(), //
            null //
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public KaiNodeInterface createKaiNodeInterface() {
        return new DefaultKaiNodeInterface(Map.of(SettingsType.MODEL, RuleEngineFilter2PortsNodeParameters.class));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RuleEngineFilter2PortsNodeModel createNodeModel() {
        return new RuleEngineFilter2PortsNodeModel(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeView<RuleEngineFilter2PortsNodeModel> createNodeView(final int index,
            final RuleEngineFilter2PortsNodeModel model) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getNrNodeViews() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean hasDialog() {
        return true;
    }
}
