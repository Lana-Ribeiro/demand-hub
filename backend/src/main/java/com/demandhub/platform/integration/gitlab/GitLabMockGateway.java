package com.demandhub.platform.integration.gitlab;

import com.demandhub.platform.integration.common.ExternalLink;
import com.demandhub.platform.integration.common.IntegrationMode;
import com.demandhub.platform.integration.scm.ScmGateway;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GitLab MOCK para desenvolvimento local: não chama API. Mudanças de status são simuladas por endpoint explícito
 * (somente em modo mock) que passa pelo MESMO processador dos webhooks reais.
 */
public class GitLabMockGateway implements ScmGateway {

    private static final Logger log = LoggerFactory.getLogger(GitLabMockGateway.class);
    private final AtomicInteger sequence = new AtomicInteger((int) (System.currentTimeMillis() % 10000));

    @Override
    public ExternalLink.System system() {
        return ExternalLink.System.GITLAB;
    }

    @Override
    public IntegrationMode mode() {
        return IntegrationMode.MOCK;
    }

    @Override
    public CreatedIssue createIssue(String projectId, String title, String description, List<String> labels) {
        int iid = sequence.incrementAndGet();
        log.info("[GITLAB MOCK] issue #{} criada no projeto {} — {} {}", iid, projectId, title, labels);
        return new CreatedIssue(String.valueOf(iid), String.valueOf(iid), null);
    }

    @Override
    public void addNote(String projectId, String issueIid, String body) {
        log.info("[GITLAB MOCK] nota na issue #{}: {}", issueIid, body);
    }
}
