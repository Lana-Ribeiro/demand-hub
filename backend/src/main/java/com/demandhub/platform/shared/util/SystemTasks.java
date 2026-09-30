package com.demandhub.platform.shared.util;

import java.util.function.Supplier;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executa trabalho do sistema (listeners pós-commit, tarefas assíncronas) em transação nova e
 * sem o contexto de segurança do usuário — a auditoria registra o ator como SYSTEM.
 */
@Component
public class SystemTasks {

    private final TransactionTemplate requiresNew;

    public SystemTasks(PlatformTransactionManager txManager) {
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void run(Runnable task) {
        call(() -> {
            task.run();
            return null;
        });
    }

    public <T> T call(Supplier<T> task) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContextHolder.clearContext();
        try {
            return requiresNew.execute(status -> task.get());
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
