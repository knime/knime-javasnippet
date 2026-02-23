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
 * ------------------------------------------------------------------------
 */

package org.knime.base.node.rules.engine.decisiontree;

import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.updates.util.BooleanReference;

/**
 * Node parameters for Decision Tree to Ruleset.
 *
 * @author Magnus Gohm, KNIME GmbH, Konstanz, Germany
 * @author AI Migration Pipeline v1.2
 */
@LoadDefaultsForAbsentFields
final class FromDecisionTreeNodeParameters implements NodeParameters {

    @Section(title = "Score Distribution")
    interface ScoreDistributionSection {
    }

    @Widget(title = "Split rules to condition and outcome columns", description = """
            When checked, two columns will be created for the rules, <b>Condition</b> and \
            <b>Outcome</b>, otherwise the rules will reside in a single column, <b>Rule</b>.\
            """)
    @Persist(configKey = "split.rules")
    boolean m_splitRules;

    @Widget(title = "Add confidence and weight columns", description = """
            From PMML the <a href="http://www.dmg.org/v4-2-1/RuleSet.html#xsdGroup_Rule">\
            confidence and weight</a> attributes are extracted to columns. (It will create columns with \
            missing values.)\
            """)
    @Persist(configKey = "confidence.and.weight")
    boolean m_confidenceAndWeight;

    @Widget(title = "Add Record count and Number of correct statistics columns", description = """
            In PMML, the <tt>recordCount</tt> and the <tt>nbCorrect</tt> attributes provide \
            statistics about the input (training/test/validation) data, with this option, this \
            information can be extracted to the columns: <b>Record count</b> and \
            <b>Number of correct</b>\
            """)
    @Persist(configKey = "statistics")
    boolean m_provideStatistics;

    @Widget(title = "Use additional parentheses to document precedence rules", description = """
            If checked the output will contain additional parenthesis around rule parts to \
            clearly document precedence. For instance, NOT is a stronger operator than AND than OR - \
            using parenthesis improves readability. Checking this option does not change any of the rule \
            logic.\
            """)
    @Persist(configKey = "additional.parentheses")
    boolean m_useAdditionalParentheses;

    @Layout(ScoreDistributionSection.class)
    @Widget(title = "Provide score distribution record count in PMML", description = """
            PMML will store the information obtained from the rules on record counts of score \
            distribution (how many times the different values were present in the training data, \
            not necessarily integer)\
            """)
    @Persist(configKey = "pmml.score.recordCount")
    @ValueReference(ScorePmmlRecordCountRef.class)
    boolean m_scorePmmlRecordCount;

    static final class ScorePmmlRecordCountRef implements BooleanReference {
    }

    @Layout(ScoreDistributionSection.class)
    @Widget(title = "Provide score distribution record count in table with column name prefix", description = """
            Information about the record count of score distribution is saved to the table with \
            the specified prefix.\
            """)
    @Persist(configKey = "table.score.recordCount")
    @ValueReference(ScoreTableRecordCountRef.class)
    @Effect(predicate = ScorePmmlRecordCountRef.class, type = EffectType.SHOW)
    boolean m_scoreTableRecordCount;

    static final class ScoreTableRecordCountRef implements BooleanReference {
    }

    @Layout(ScoreDistributionSection.class)
    @Widget(title = "Record count column prefix",
        description = "Prefix for the score distribution record count columns.")
    @Persist(configKey = "table.score.recordCount.prefix")
    @Effect(predicate = ScoreTableRecordCountEnabled.class, type = EffectType.SHOW)
    String m_scoreTableRecordCountPrefix = "Record count ";

    @Layout(ScoreDistributionSection.class)
    @Widget(title = "Provide score distribution probability in PMML", description = """
            PMML will store the information obtained from the rules on probabilities of score \
            distribution (in the training data what the probability was of certain values for that \
            decision tree leaf)\
            """)
    @Persist(configKey = "pmml.score.probability")
    @ValueReference(ScorePmmlProbabilityRef.class)
    @Effect(predicate = ScorePmmlRecordCountRef.class, type = EffectType.SHOW)
    boolean m_scorePmmlProbability;

    static final class ScorePmmlProbabilityRef implements BooleanReference {
    }

    @Layout(ScoreDistributionSection.class)
    @Widget(title = "Provide score distribution probability in table with column name prefix", description = """
            Information about the probabilities of score distribution is saved to the table \
            with the specified prefix.\
            """)
    @Persist(configKey = "table.score.probability")
    @ValueReference(ScoreTableProbabilityRef.class)
    @Effect(predicate = ScorePmmlProbabilityEnabled.class, type = EffectType.SHOW)
    boolean m_scoreTableProbability;

    static final class ScoreTableProbabilityRef implements BooleanReference {
    }

    @Layout(ScoreDistributionSection.class)
    @Widget(title = "Probability column prefix",
        description = "Prefix for the score distribution probability columns.")
    @Persist(configKey = "table.score.probability.prefix")
    @Effect(predicate = ScoreTableProbabilityEnabled.class, type = EffectType.SHOW)
    String m_scoreTableProbabilityPrefix = "Probability ";

    static final class ScoreTableRecordCountEnabled implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getPredicate(ScorePmmlRecordCountRef.class).and(i.getPredicate(ScoreTableRecordCountRef.class));
        }

    }

    static final class ScorePmmlProbabilityEnabled implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getPredicate(ScorePmmlRecordCountRef.class).and(i.getPredicate(ScorePmmlProbabilityRef.class));
        }

    }

    static final class ScoreTableProbabilityEnabled implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getPredicate(ScorePmmlRecordCountRef.class).and(i.getPredicate(ScorePmmlProbabilityRef.class))
                    .and(i.getPredicate(ScoreTableProbabilityRef.class));
        }

    }

}
