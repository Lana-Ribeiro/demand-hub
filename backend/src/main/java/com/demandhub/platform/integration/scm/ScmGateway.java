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

    /** Troca a label de status da issue (mantém labels e board coerentes quando o status muda por outra via). */
    default void replaceStatusLabel(String projectRef, String issueKey, String oldLabel, String newLabel) {}

    // ---- Board (opcional: GitHub Projects). Implementações sem board mantêm os padrões abaixo.

    default boolean hasBoard() {
        return false;
    }

    /** Adiciona a issue ao board na coluna indicada. Retorna o id do item no board. */
    default String addToBoard(CreatedIssue issue, String columnName) {
        return null;
    }

    default void moveOnBoard(String boardItemId, String columnName) {}

    /** Coluna atual de cada item do board (itemId → nome da coluna). Itens sem coluna são omitidos. */
    default java.util.Map<String, String> readBoardColumns(java.util.Collection<String> boardItemIds) {
        return java.util.Map.of();
    }

    /** @param key GitLab: iid; GitHub: número da issue */
    record CreatedIssue(String id, String key, String url) {}

    class ScmException extends RuntimeException {
        public ScmException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
