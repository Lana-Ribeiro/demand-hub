package com.demandhub.platform.workflow.service;

import com.demandhub.platform.approval.domain.ApprovalRule;
import com.demandhub.platform.approval.domain.ConditionOperator;
import com.demandhub.platform.approval.repository.ApprovalRepositories.ApprovalRuleRepository;
import com.demandhub.platform.catalog.domain.DemandType;
import com.demandhub.platform.catalog.repository.CatalogRepositories.DemandTypeRepository;
import com.demandhub.platform.workflow.domain.StageAction;
import com.demandhub.platform.workflow.domain.StageCategory;
import com.demandhub.platform.workflow.domain.WorkflowDefinition;
import com.demandhub.platform.workflow.domain.WorkflowStage;
import com.demandhub.platform.workflow.domain.WorkflowTransition;
import com.demandhub.platform.workflow.repository.WorkflowRepositories.WorkflowDefinitionRepository;
import com.demandhub.platform.workflow.repository.WorkflowRepositories.WorkflowStageRepository;
import com.demandhub.platform.workflow.repository.WorkflowRepositories.WorkflowTransitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrega a configuração padrão de workflows, regras de aprovação e tipos de demanda
 * ({@code seed/workflows.json}) apenas quando ainda não existe nenhum workflow.
 * Depois disso, a configuração é mantida pela Administração — nada é hardcoded no código.
 */
@Component
@Order(1)
public class WorkflowBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkflowBootstrap.class);

    private final WorkflowDefinitionRepository workflows;
    private final WorkflowStageRepository stages;
    private final WorkflowTransitionRepository transitions;
    private final ApprovalRuleRepository rules;
    private final DemandTypeRepository demandTypes;
    private final ExitRequirementService exitRequirements;
    private final ObjectMapper mapper;

    public WorkflowBootstrap(WorkflowDefinitionRepository workflows, WorkflowStageRepository stages,
                             WorkflowTransitionRepository transitions, ApprovalRuleRepository rules,
                             DemandTypeRepository demandTypes, ExitRequirementService exitRequirements, ObjectMapper mapper) {
        this.workflows = workflows;
        this.stages = stages;
        this.transitions = transitions;
        this.rules = rules;
        this.demandTypes = demandTypes;
        this.exitRequirements = exitRequirements;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (workflows.count() > 0) {
            return;
        }
        try (InputStream in = new ClassPathResource("seed/workflows.json").getInputStream()) {
            SeedFile seed = mapper.readValue(in, SeedFile.class);
            Map<String, WorkflowDefinition> byCode = new HashMap<>();
            for (WorkflowSeed ws : seed.workflows()) {
                byCode.put(ws.code(), createWorkflow(ws));
            }
            for (TypeSeed ts : seed.demandTypes()) {
                DemandType type = new DemandType();
                type.setCode(ts.code());
                type.setName(ts.name());
                type.setDescription(ts.description());
                type.setTechnical(ts.technical());
                type.setWorkflow(byCode.get(ts.workflow()));
                demandTypes.save(type);
            }
            log.info("Configuração padrão carregada: {} workflows, {} tipos de demanda.", byCode.size(), seed.demandTypes().size());
        }
    }

    private WorkflowDefinition createWorkflow(WorkflowSeed ws) {
        WorkflowDefinition wf = new WorkflowDefinition();
        wf.setCode(ws.code());
        wf.setName(ws.name());
        wf.setDescription(ws.description());
        workflows.save(wf);

        Map<String, WorkflowStage> stageByCode = new HashMap<>();
        int position = 0;
        for (StageSeed ss : ws.stages()) {
            WorkflowStage s = new WorkflowStage();
            s.setWorkflow(wf);
            s.setCode(ss.code());
            s.setName(ss.name());
            s.setCategory(StageCategory.valueOf(ss.category()));
            s.setPosition(position++);
            s.setAutoAdvance(ss.autoAdvance());
            s.setJiraStatus(ss.jiraStatus());
            if (ss.onEnter() != null) {
                ss.onEnter().forEach(StageAction::valueOf); // valida
                s.setOnEnterActions(String.join(",", ss.onEnter()));
            }
            if (ss.exitRequirements() != null) {
                ss.exitRequirements().forEach(code -> {
                    if (!exitRequirements.isKnown(code)) {
                        throw new IllegalStateException("Requisito de saída desconhecido no seed: " + code);
                    }
                });
                s.setExitRequirements(String.join(",", ss.exitRequirements()));
            }
            stages.save(s);
            stageByCode.put(s.getCode(), s);
            wf.getStages().add(s);
        }

        for (TransitionSeed ts : ws.transitions()) {
            List<String> froms = "*".equals(ts.from())
                    ? stageByCode.values().stream()
                        .filter(s -> !s.getCategory().isTerminal() && s.getCategory() != StageCategory.DRAFT)
                        .map(WorkflowStage::getCode).toList()
                    : Arrays.asList(ts.from().split(","));
            for (String from : froms) {
                WorkflowTransition t = new WorkflowTransition();
                t.setWorkflow(wf);
                t.setFromStage(required(stageByCode, from.trim()));
                t.setToStage(required(stageByCode, ts.to()));
                t.setAction(ts.action());
                t.setLabel(ts.label());
                t.setRequiredPermission(ts.permission());
                t.setRequiresReason(ts.requiresReason());
                t.setForward(ts.forward());
                t.setSystemOnly(ts.systemOnly());
                transitions.save(t);
            }
        }

        int rulePos = 0;
        for (RuleSeed rs : ws.approvalRules() == null ? List.<RuleSeed>of() : ws.approvalRules()) {
            required(stageByCode, rs.stage());
            ApprovalRule r = new ApprovalRule();
            r.setWorkflowId(wf.getId());
            r.setStageCode(rs.stage());
            r.setName(rs.name());
            r.setApproverRole(rs.role());
            r.setConditionField(rs.field());
            r.setConditionOperator(rs.operator() == null ? null : ConditionOperator.valueOf(rs.operator()));
            r.setConditionValue(rs.value());
            r.setPosition(rulePos++);
            rules.save(r);
        }
        return wf;
    }

    private static WorkflowStage required(Map<String, WorkflowStage> stages, String code) {
        WorkflowStage s = stages.get(code);
        if (s == null) {
            throw new IllegalStateException("Estágio inexistente no seed: " + code);
        }
        return s;
    }

    record SeedFile(List<WorkflowSeed> workflows, List<TypeSeed> demandTypes) {}

    record WorkflowSeed(String code, String name, String description, List<StageSeed> stages,
                        List<TransitionSeed> transitions, List<RuleSeed> approvalRules) {}

    record StageSeed(String code, String name, String category, boolean autoAdvance, String jiraStatus,
                     List<String> onEnter, List<String> exitRequirements) {}

    record TransitionSeed(String from, String to, String action, String label, String permission,
                          boolean requiresReason, boolean forward, boolean systemOnly) {}

    record RuleSeed(String stage, String name, String role, String field, String operator, String value) {}

    record TypeSeed(String code, String name, String description, boolean technical, String workflow) {}
}
