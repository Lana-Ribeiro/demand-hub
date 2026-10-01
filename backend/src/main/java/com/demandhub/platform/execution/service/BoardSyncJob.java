package com.demandhub.platform.execution.service;

import com.demandhub.platform.shared.util.SystemTasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lê periodicamente o board do repositório (GitHub Projects) e aplica movimentos de cards como status técnico.
 * Necessário porque boards de conta pessoal não enviam webhooks — e funciona mesmo sem endereço público.
 * Sem board configurado, não faz nada.
 */
@Component
public class BoardSyncJob {

    private static final Logger log = LoggerFactory.getLogger(BoardSyncJob.class);

    private final ExecutionService executions;
    private final SystemTasks system;

    public BoardSyncJob(ExecutionService executions, SystemTasks system) {
        this.executions = executions;
        this.system = system;
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "${app.github.project-poll-interval:PT1M}")
    public void sync() {
        try {
            int updated = system.call(executions::syncFromBoard);
            if (updated > 0) {
                log.info("Board sincronizado: {} execução(ões) técnica(s) atualizada(s).", updated);
            }
        } catch (RuntimeException e) {
            log.warn("Falha ao ler o board do repositório: {}", e.getMessage());
        }
    }
}
