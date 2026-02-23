package org.knime.base.node.rules.engine.decisiontree;

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
 * <code>NodeFactory</code> for the "Decision Tree to Rules" Node.
 * Converts a decision tree model to PMML <a href="http://www.dmg.org/v4-2-1/RuleSet.html">RuleSet</a> model.
 *
 * @author Gabor Bakos
 * @author Magnus Gohm, KNIME GmbH, Konstanz, Germany
 * @author AI Migration Pipeline v1.2
 */
@SuppressWarnings("restriction")
public class FromDecisionTreeNodeFactory extends NodeFactory<FromDecisionTreeNodeModel>
    implements NodeDialogFactory, KaiNodeInterfaceFactory {

    @Override
    public FromDecisionTreeNodeModel createNodeModel() {
        return new FromDecisionTreeNodeModel();
    }

    @Override
    public int getNrNodeViews() {
        return 0;
    }

    @Override
    public NodeView<FromDecisionTreeNodeModel> createNodeView(final int viewIndex,
            final FromDecisionTreeNodeModel nodeModel) {
        throw new IndexOutOfBoundsException("No views: " + viewIndex);
    }

    @Override
    public boolean hasDialog() {
        return true;
    }

    private static final String NODE_NAME = "Decision Tree to Ruleset";

    private static final String NODE_ICON = "./dectree2rules.png";

    private static final String SHORT_DESCRIPTION = """
            Converts a decision tree model to PMML RuleSet model.
            """;

    private static final String FULL_DESCRIPTION = """
            Converts (a single) decision tree model to PMML <a href="http://www.dmg.org/v4-2-1/RuleSet.html">RuleSet</a>
             model and also to a table containing the rules in a textual form. The resulting rules are independent of
            each other, the order of rules is not specified, can be changed. Missing value strategies are ignored, it
            will always evaluate to missing value.
            """;

    private static final List<PortDescription> INPUT_PORTS = List.of(
            fixedPort("PMML Decision Tree", """
                A PMML Decision Tree model.
                """)
    );

    private static final List<PortDescription> OUTPUT_PORTS = List.of(
            fixedPort("PMML RuleSet", """
                The decision tree model represented as PMML RuleSets (with <tt>firstHit</tt> rule selection method).
                """),
            fixedPort("Rules table", """
                The table contains the rules' text (in single (<b>Rule</b>) or two columns (<b>Condition</b>,
                <b>Outcome</b>)), the rule <b>Confidence</b> and <b>Weight</b> information and optionally the <b>Record
                count</b> (for how many rows the ruleset matched when created) and <b>Number of correct</b> values
                where the outcome of the rule matched the expected label when the model was created.
                """)
    );

    @Override
    public NodeDialogPane createNodeDialogPane() {
        return NodeDialogManager.createLegacyFlowVariableNodeDialog(createNodeDialog());
    }

    @Override
    public NodeDialog createNodeDialog() {
        return new DefaultNodeDialog(SettingsType.MODEL, FromDecisionTreeNodeParameters.class);
    }

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
            FromDecisionTreeNodeParameters.class, //
            null, //
            NodeType.Manipulator, //
            List.of(), //
            null //
        );
    }

    @Override
    public KaiNodeInterface createKaiNodeInterface() {
        return new DefaultKaiNodeInterface(Map.of(SettingsType.MODEL, FromDecisionTreeNodeParameters.class));
    }

}

