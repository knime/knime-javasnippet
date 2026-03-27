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
import org.knime.node.impl.description.ExternalResource;
import org.knime.node.impl.description.PortDescription;

/**
 * This factory creates all necessary object for the business rule node for variables with rules table.
 *
 * @author Thorsten Meinl, University of Konstanz
 * @author Gabor Bakos
 * @author Jochen Reißinger, TNG Technology Consulting GmbH
 */
@SuppressWarnings("restriction")
public class RuleEngineVariable2PortsNodeFactory extends NodeFactory<RuleEngineVariable2PortsNodeModel>
        implements NodeDialogFactory, KaiNodeInterfaceFactory {

    private static final String NODE_NAME = "Rule Engine Variable (Dictionary)";

    private static final String NODE_ICON = "./rule_engine_variable.png";

    private static final String SHORT_DESCRIPTION =
        "Applies user-defined (in a table) business rules to the flow variables";

    private static final String FULL_DESCRIPTION = """
            This node takes a list of user-defined rules and tries to match them to the defined flow variables. \
            If a rule matches, its outcome value will become the value of the flow variable. \
            The first matching rule will determine the outcome.
            <p>
            Each rule is represented by a row. To add comments, start a line in a (condition) cell with \
            <tt>//</tt> (comments can not be placed in the same line as a rule). Anything after <tt>//</tt> \
            will not be interpreted as a rule. Rules consist of a condition part (antecedent), which must evaluate \
            to <i>true</i> or <i>false</i>, and an outcome (consequent, after the =&gt; symbol) which is put into \
            the new flow variable if the rule matches.
            </p>
            <p>
            The outcome of a rule can either be a constant string, a constant number or boolean constant, or a \
            reference to a flow variable value. The type of the outcome column is the common super type of all \
            possible outcomes (including the rules that can never match). If no rule matches, the outcome is "", \
            0, or 0.0 depending on the output type.
            </p>
            <p>
            Numbers are given in the usual decimal representation. Note that strings must not contain \
            (double-)quotes (for those cases use the following syntax: \
            <tt>/Oscar Wilde's wisdom: "Experience is simply the name we give our mistakes."/</tt>). \
            The flow variables are represented by <b>$${</b>TypeCharacterAndFlowVarName<b>}$$</b>. \
            The TypeCharacter should be 'D' for double (real) values, 'I' for integer values and 'S' for strings.
            </p>
            <p>The logical expressions can be grouped with parentheses. The precedence rules for them are the \
            following: <tt>NOT</tt> binds most, <tt>AND</tt>, <tt>XOR</tt> and finally <tt>OR</tt> the least. \
            Comparison operators always take precedence over logical connectives. \
            All operators (and their names) are case-sensitive.
            </p>
            <p>Some example rules (each should be in one row):</p>
            <pre>
// This is a comment
$${DFlowVar0}$$ > 0 => "Positive"
</pre>
            FlowVar0 has value above zero, in which case the result flow variable has the value Positive.
            <pre>
$${SFlowVar0}$$ = "Active" AND
    $${IFlowVar1}$$ &lt;= 5 => "Outlier"
</pre>
            When FlowVar0 is "Active" and FlowVar1 is greater or equal to 5, then the result is Outlier.
            <pre>
$${SFlowVar0}$$ LIKE "Market Street*" AND
    ($${SFlowVar1}$$ IN ("married", "divorced")
        OR $${IFlowVar2}$$ > 40) => "Strange"
</pre>
            The logical connectives help express complex conditions.
            <pre>
$${SFlowVar10}$$ MATCHES $${SFlowVar0}$$ OR $${DFlowVar2}$$ &lt; $${IFlowVar1}$$ =>
    $${SFlowVar0}$$
</pre>
            You can compare different flow variables.
            <p>
            The NaNs equal to other NaN values (other values are neither less, nor greater or equal to NaN values).
            </p>
            """;

    private static final List<ExternalResource> EXTERNAL_RESOURCES = List.of(new ExternalResource(
        "https://docs.knime.com/ap/latest/analytics_platform_flow_control_guide/#flow-variables",
        "KNIME E-Learning Course: Creation and usage of Flow Variables in a KNIME workflow"));

    private static final List<PortDescription> INPUT_PORTS = List.of( //
        fixedPort("Input variables (optional)", "Input variables (optional)"), //
        fixedPort("Rules", "Table containing the rules"));

    private static final List<PortDescription> OUTPUT_PORTS =
        List.of(fixedPort("Output variables", "Output variable"));

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
        return new DefaultNodeDialog(SettingsType.MODEL, RuleEngineVariable2PortsNodeParameters.class);
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
            EXTERNAL_RESOURCES, //
            RuleEngineVariable2PortsNodeParameters.class, //
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
        return new DefaultKaiNodeInterface(
            Map.of(SettingsType.MODEL, RuleEngineVariable2PortsNodeParameters.class));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RuleEngineVariable2PortsNodeModel createNodeModel() {
        return new RuleEngineVariable2PortsNodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeView<RuleEngineVariable2PortsNodeModel> createNodeView(final int index,
            final RuleEngineVariable2PortsNodeModel model) {
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
