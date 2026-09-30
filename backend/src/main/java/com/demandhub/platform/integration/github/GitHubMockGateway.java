package com.demandhub.platform.integration.github;

import com.demandhub.platform.integration.common.ExternalLink;
import com.demandhub.platform.integration.common.IntegrationMode;
import com.demandhub.platform.integration.scm.ScmGateway;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** GitHub MOCK para desenvolvimento local: não chama API; vínculos gravados com mode=MOCK. */
public class GitHubMockGateway implements ScmGateway {

    private static final Logger log = LoggerFactory.getLogger(GitHubMockGateway.class);
    private final AtomicInteger sequence = new AtomicInteger((int) (System.currentTimeMillis() % 10000));

    @Override
    public ExternalLink.System system() {
        return ExternalLink.System.GITHUB;
    }

    @Override
    public IntegrationMode mode() {
        return IntegrationMode.MOCK;
    }

    @Override
    public CreatedIssue createIssue(String repository, String title, String description, List<String> labels) {
        int number = sequence.incrementAndGet();
        log.info("[GITHUB MOCK] issue #{} criada em {} — {} {}", number, repository, title, labels);
        return new CreatedIssue(String.valueOf(number), String.valueOf(number), null);
    }

    @Override
    public void addNote(String repository, String issueNumber, String body) {
        log.info("[GITHUB MOCK] comentário na issue #{}: {}", issueNumber, body);
    }
}
