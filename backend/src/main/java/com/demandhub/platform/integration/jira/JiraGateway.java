package com.demandhub.platform.integration.jira;

import com.demandhub.platform.integration.common.IntegrationMode;
import java.util.List;

/** Porta para o Jira PMO (lifecycle da demanda). */
public interface JiraGateway {

    /** Prefixo usado nos comentários publicados pela plataforma (evita eco via webhook). */
    String COMMENT_PREFIX = "[Demand Hub] ";

    IntegrationMode mode();

    CreatedIssue createIssue(IssueRequest request);

    void updateIssue(String issueKey, String priorityName, List<String> labels);

    void addComment(String issueKey, String text);

    /** Transiciona para o status de destino pelo NOME configurado. Retorna false se não houver transição disponível. */
    boolean transitionTo(String issueKey, String statusName);

    record IssueRequest(String projectKey, String summary, String description, String priorityName, List<String> labels) {}

    record CreatedIssue(String id, String key, String url) {}

    class JiraException extends RuntimeException {
        public JiraException(String message, Throwable cause) {
            super(message, cause);
        }

        public JiraException(String message) {
            super(message);
        }
    }
}
