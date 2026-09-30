package com.demandhub.platform.integration.common;

import com.demandhub.platform.config.AppProperties;
import com.demandhub.platform.integration.github.GitHubMockGateway;
import com.demandhub.platform.integration.github.GitHubRestGateway;
import com.demandhub.platform.integration.gitlab.GitLabMockGateway;
import com.demandhub.platform.integration.gitlab.GitLabRestGateway;
import com.demandhub.platform.integration.jira.JiraGateway;
import com.demandhub.platform.integration.jira.JiraMockGateway;
import com.demandhub.platform.integration.jira.JiraRestGateway;
import com.demandhub.platform.integration.scm.ScmGateway;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seleção das integrações por variável de ambiente:
 * Jira ({@code JIRA_MODE}) e repositório de código ({@code SCM_PROVIDER} = gitlab | github, com {@code GITLAB_MODE}/{@code GITHUB_MODE}).
 * DISABLED → nenhum gateway (integração não executada).
 */
@Configuration
public class IntegrationConfig {

    private static final Logger log = LoggerFactory.getLogger(IntegrationConfig.class);

    @Bean
    public IntegrationGateways integrationGateways(AppProperties props) {
        IntegrationMode jiraMode = IntegrationMode.from(props.jira().mode());
        JiraGateway jira = switch (jiraMode) {
            case REAL -> new JiraRestGateway(props.jira());
            case MOCK -> new JiraMockGateway();
            case DISABLED -> null;
        };

        String provider = props.scm() == null || props.scm().provider() == null ? "gitlab"
                : props.scm().provider().trim().toLowerCase(Locale.ROOT);
        ExternalLink.System scmSystem = switch (provider) {
            case "gitlab" -> ExternalLink.System.GITLAB;
            case "github" -> ExternalLink.System.GITHUB;
            default -> throw new IllegalStateException("SCM_PROVIDER inválido: " + provider + " (use gitlab ou github)");
        };
        IntegrationMode scmMode = IntegrationMode.from(scmSystem == ExternalLink.System.GITHUB ? props.github().mode() : props.gitlab().mode());
        ScmGateway scm = switch (scmMode) {
            case REAL -> scmSystem == ExternalLink.System.GITHUB ? new GitHubRestGateway(props.github()) : new GitLabRestGateway(props.gitlab());
            case MOCK -> scmSystem == ExternalLink.System.GITHUB ? new GitHubMockGateway() : new GitLabMockGateway();
            case DISABLED -> null;
        };
        log.info("Integrações: Jira={}, repositório de código={} ({})", jiraMode, scmSystem, scmMode);
        return new IntegrationGateways(jira, scm, scmSystem);
    }

    /** Gateways configurados (podem ser nulos quando DISABLED). */
    public record IntegrationGateways(JiraGateway jira, ScmGateway scm, ExternalLink.System scmSystem) {

        public IntegrationMode jiraMode() {
            return jira == null ? IntegrationMode.DISABLED : jira.mode();
        }

        public IntegrationMode scmMode() {
            return scm == null ? IntegrationMode.DISABLED : scm.mode();
        }

        /** Nome de exibição do repositório de código ("GitLab" ou "GitHub"). */
        public String scmName() {
            return scmSystem == ExternalLink.System.GITHUB ? "GitHub" : "GitLab";
        }
    }
}
