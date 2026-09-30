package com.demandhub.platform.integration.jira;

import com.demandhub.platform.integration.common.IntegrationMode;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jira MOCK para desenvolvimento local. Não chama nenhuma API: gera chaves "MOCK-n" e registra em log.
 * Todo vínculo criado por esta implementação é gravado com mode=MOCK e exibido como tal na interface.
 */
public class JiraMockGateway implements JiraGateway {

    private static final Logger log = LoggerFactory.getLogger(JiraMockGateway.class);
    private final AtomicInteger sequence = new AtomicInteger((int) (System.currentTimeMillis() % 100000));

    @Override
    public IntegrationMode mode() {
        return IntegrationMode.MOCK;
    }

    @Override
    public CreatedIssue createIssue(IssueRequest request) {
        int n = sequence.incrementAndGet();
        String key = (request.projectKey() == null ? "MOCK" : request.projectKey()) + "-M" + n;
        log.info("[JIRA MOCK] issue criada {} — {}", key, request.summary());
        return new CreatedIssue(String.valueOf(n), key, null);
    }

    @Override
    public void updateIssue(String issueKey, String priorityName, List<String> labels) {
        log.info("[JIRA MOCK] issue {} atualizada (prioridade={}, labels={})", issueKey, priorityName, labels);
    }

    @Override
    public void addComment(String issueKey, String text) {
        log.info("[JIRA MOCK] comentário em {}: {}", issueKey, text);
    }

    @Override
    public boolean transitionTo(String issueKey, String statusName) {
        log.info("[JIRA MOCK] issue {} → {}", issueKey, statusName);
        return true;
    }
}
