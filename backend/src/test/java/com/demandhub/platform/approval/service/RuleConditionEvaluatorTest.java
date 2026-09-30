package com.demandhub.platform.approval.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.demandhub.platform.approval.domain.ApprovalRule;
import com.demandhub.platform.approval.domain.ConditionOperator;
import com.demandhub.platform.catalog.domain.Priority;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.project.domain.Project;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RuleConditionEvaluatorTest {

    private final RuleConditionEvaluator evaluator = new RuleConditionEvaluator();

    @Test
    void ruleWithoutConditionAlwaysMatches() {
        assertThat(evaluator.matches(rule(null, null, null), new Demand())).isTrue();
    }

    @Test
    void numericComparisonOnBudget() {
        Demand d = new Demand();
        d.setEstimatedBudget(new BigDecimal("600000.00"));
        assertThat(evaluator.matches(rule("estimatedBudget", ConditionOperator.GT, "500000"), d)).isTrue();
        d.setEstimatedBudget(new BigDecimal("500000.00"));
        assertThat(evaluator.matches(rule("estimatedBudget", ConditionOperator.GT, "500000"), d)).isFalse();
        d.setEstimatedBudget(null);
        assertThat(evaluator.matches(rule("estimatedBudget", ConditionOperator.GT, "500000"), d)).isFalse();
    }

    @Test
    void priorityEqualityAndInOperator() {
        Demand d = new Demand();
        Priority p1 = new Priority();
        p1.setCode("P1");
        d.setPriority(p1);
        assertThat(evaluator.matches(rule("priority", ConditionOperator.EQ, "P1"), d)).isTrue();
        assertThat(evaluator.matches(rule("priority", ConditionOperator.IN, "P2, P3"), d)).isFalse();
        assertThat(evaluator.matches(rule("priority", ConditionOperator.IN, "P1,P2"), d)).isTrue();
    }

    @Test
    void booleanRegulatoryFlag() {
        Demand d = new Demand();
        d.setRegulatoryRequirement(true);
        assertThat(evaluator.matches(rule("regulatoryRequirement", ConditionOperator.EQ, "true"), d)).isTrue();
        d.setRegulatoryRequirement(false);
        assertThat(evaluator.matches(rule("regulatoryRequirement", ConditionOperator.EQ, "true"), d)).isFalse();
    }

    @Test
    void projectScopedRuleOnlyAppliesToThatProject() {
        ApprovalRule r = rule(null, null, null);
        r.setProjectId(10L);
        Demand d = new Demand();
        assertThat(evaluator.matches(r, d)).isFalse();
        Project p = new Project();
        p.setId(10L);
        d.setProject(p);
        assertThat(evaluator.matches(r, d)).isTrue();
    }

    @Test
    void unknownFieldIsFailSafe() {
        assertThat(evaluator.matches(rule("campoInexistente", ConditionOperator.EQ, "x"), new Demand())).isTrue();
    }

    private static ApprovalRule rule(String field, ConditionOperator op, String value) {
        ApprovalRule r = new ApprovalRule();
        r.setConditionField(field);
        r.setConditionOperator(op);
        r.setConditionValue(value);
        return r;
    }
}
