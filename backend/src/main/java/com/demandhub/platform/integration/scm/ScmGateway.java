package com.demandhub.platform.integration.scm;

import com.demandhub.platform.integration.common.ExternalLink;
import com.demandhub.platform.integration.common.IntegrationMode;
import java.util.List;

/**
 * Porta do repositório de código (SCM) — fonte do lifecycle da EXECUÇÃO TÉCNICA.
 * Implementações: GitLab (Issues + labels escopadas) e GitHub (Issues + labels), cada uma com modo REAL e MOCK.
 * Selecionada por {@code SCM_PROVIDER}.
 */
public interface ScmGateway {

    /** Sistema externo (GITLAB ou GITHUB) — gravado no vínculo da demanda. */
    ExternalLink.System system();

    IntegrationMode mode();

    /**
     * @param projectRef GitLab: id do projeto; GitHub: "owner/repositorio"
     */
    CreatedIssue createIssue(String projectRef, String title, String description, List<String> labels);

    void addNote(String projectRef, String issueKey, String body);

    /** @param key GitLab: iid; GitHub: número da issue */
    record CreatedIssue(String id, String key, String url) {}

    class ScmException extends RuntimeException {
        public ScmException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
