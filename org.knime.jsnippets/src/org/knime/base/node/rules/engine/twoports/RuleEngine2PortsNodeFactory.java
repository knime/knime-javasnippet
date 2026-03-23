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
 * <code>NodeFactory</code> for the "Rule Engine (Dictionary)" Node.
 * Applies the rules from the second input port to the first datatable.
 *
 * @author Gabor Bakos
 * @author Jochen Reißinger, TNG Technology Consulting GmbH
 */
@SuppressWarnings("restriction")
public class RuleEngine2PortsNodeFactory extends NodeFactory<RuleEngine2PortsNodeModel>
        implements NodeDialogFactory, KaiNodeInterfaceFactory {

    private static final String NODE_NAME = "Rule Engine (Dictionary)";

    private static final String NODE_ICON = "../rule_engine.png";

    private static final String SHORT_DESCRIPTION =
        "Applies the rules from the second input port to the first datatable.";

    private static final String FULL_DESCRIPTION = """
            Applies rules from a rules table to a data table. The rules follow the <b>Rule Engine</b> rules, \
            though for <a href="http://www.dmg.org/v4-2-1/RuleSet.html">PMML RuleSets</a> stricter rules apply \
            (no column reference in the outcome, cannot use regular expressions, 3-valued logic). \
            If no rules match, the default value specified in the PMML tab is used, or missing when no default \
            value was specified.
            <br />
            It takes a list of user-defined rules from the second input port (from the selected column(s)) and \
            tries to match them to each row in the input table. If a rule matches, its outcome value is added \
            into a new column. The first matching rule in order of definition determines the outcome.
            <p>
            Each rule is represented by a row, new line characters are replaced by spaces, even in string \
            constants. To add comments, start a line in a (condition) cell with <tt>//</tt> (comments can not \
            be placed in the same line as a rule). Anything after <tt>//</tt> will not be interpreted as a rule. \
            Rules consist of a condition part (antecedent), which must evaluate to <i>true</i> or <i>false</i>, \
            and an outcome (consequent, after the <tt>=&gt;</tt> symbol) which is put into the new column if the \
            rule matches.
            </p>
            <p>
            The outcome of a rule may be any of the following: a string (between quotes <tt>"</tt> or \
            <tt>/</tt>), a number, a boolean constant, a reference to another column or the value of a flow \
            variable value. The type of the outcome column is the common super type of all possible outcomes \
            (including the rules that can never match). If no rule matches, the outcome is a missing value \
            unless a default value is specified.
            </p>
            <p>
            Columns are given by their name surrounded by $, numbers are given in the usual decimal \
            representation. Note that strings must not contain (double-) quotes. Flow variables are represented \
            by <b>$${</b>TypeCharacterAndFlowVarName<b>}$$</b>. (Column references are not supported for PMML \
            outputs.) The TypeCharacter should be 'D' for double (real) values, 'I' for integer values and 'S' \
            for strings.
            </p>
            <p>
            The logical expressions can be grouped with parentheses. The precedence rules for them are the \
            following: <tt>NOT</tt> binds most, <tt>AND</tt>, <tt>XOR</tt> and finally <tt>OR</tt> the least. \
            Comparison operators always take precedence over logical connectives. All operators (and their names) \
            are case-sensitive.
            </p>
            <p>
            The <tt>ROWID</tt> represents the row key string, the <tt>ROWINDEX</tt> is the index of the row \
            (first row has <tt>0</tt> value), while <tt>ROWCOUNT</tt> stands for the number of rows in the table. \
            (These are not available for PMML.)
            </p>
            <p>Some example rules (each should be in one row):</p>
            <pre>
// This is a comment
$Col0$ > 0 =&gt; "Positive"
</pre>
            When the values in Col0 are greater than 0, we assign Positive to the result column value (if no \
            previous rule matched).
            <pre>
$Col0$ = "Active" AND $Col1$ &lt;= 5 =&gt; "Outlier"
</pre>
            You can combine conditions.
            <pre>
$Col0$ LIKE "Market Street*" AND
    ($Col1$ IN ("married", "divorced")
        OR $Col2$ > 40) =&gt; "Strange"
$Col0$ MATCHES $${SFlowVar0}$$ OR $$ROWINDEX$$ &lt; $${IFlowVar1}$$ =&gt;
    $Col0$
</pre>
            With parentheses you can combine multiple conditions. The result in the second case comes from one \
            of the columns.
            <pre>
$Col0$ > 5 =&gt; $${SCol1}$$
</pre>
            The result can also come from a flow variable.
            <p>
            The following comparisons result true (other values are neither less, nor greater or equal to \
            missing and NaN values):
            <ul>
            <li><b>?</b> =,&lt;=,&gt;= <b>?</b></li>
            <li><b>NaN</b> =,&lt;=,&gt;= <b>NaN</b></li>
            </ul>
            </p>
            """;

    private static final List<PortDescription> INPUT_PORTS = List.of( //
        fixedPort("Data", "Input data"), //
        fixedPort("Rules", "Rules to apply"));

    private static final List<PortDescription> OUTPUT_PORTS = List.of( //
        fixedPort("Rules applied", "Table containing the computed column"), //
        fixedPort("PMML ruleset", "Possibly missing PMML port containing the rules in PMML RuleSet format"));

    /**
     * {@inheritDoc}
     */
    @Override
    public RuleEngine2PortsNodeModel createNodeModel() {
        return new RuleEngine2PortsNodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNrNodeViews() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeView<RuleEngine2PortsNodeModel> createNodeView(final int viewIndex,
            final RuleEngine2PortsNodeModel nodeModel) {
        throw new ArrayIndexOutOfBoundsException("No views! " + viewIndex);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasDialog() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeDialogPane createNodeDialogPane() {
        return NodeDialogManager.createLegacyFlowVariableNodeDialog(createNodeDialog());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeDialog createNodeDialog() {
        return new DefaultNodeDialog(SettingsType.MODEL, RuleEngine2PortsNodeParameters.class);
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
            RuleEngine2PortsNodeParameters.class, //
            null, //
            NodeType.Predictor, //
            List.of(), //
            null //
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public KaiNodeInterface createKaiNodeInterface() {
        return new DefaultKaiNodeInterface(Map.of(SettingsType.MODEL, RuleEngine2PortsNodeParameters.class));
    }
}
