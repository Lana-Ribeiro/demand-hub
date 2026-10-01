package com.demandhub.platform.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração tipada da aplicação. Valores vêm de variáveis de ambiente (ver .env.example). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String publicUrl,
        List<String> corsOrigins,
        Async async,
        Security security,
        Storage storage,
        Upload upload,
        Ai ai,
        Jira jira,
        Scm scm,
        Gitlab gitlab,
        Github github,
        Notifications notifications,
        Dashboard dashboard,
        DevSeed devSeed) {

    public record Async(boolean enabled) {}

    public record Security(String jwtSecret, Duration jwtTtl) {}

    public record Storage(String path) {}

    public record Upload(int maxSizeMb) {}

    public record Ai(String provider, String model, String apiKey, String baseUrl,
                     String azureDeployment, String azureApiVersion, Duration timeout,
                     int maxDocumentChars, int maxOutputTokens) {}

    public record Jira(String mode, String url, String project, String issueType, String userEmail,
                       String apiToken, String webhookSecret, String priorityMap) {}

    /** Provedor do repositório de código para a execução técnica: gitlab | github. */
    public record Scm(String provider) {}

    /** projectOwner/projectNumber: board do GitHub Projects (opcional); pollInterval: intervalo de leitura do board. */
    public record Github(String mode, String apiUrl, String token, String repository, String webhookSecret,
                         String projectOwner, String projectNumber, Duration projectPollInterval) {}

    public record Gitlab(String mode, String url, String token, String defaultProjectId, String webhookSecret) {}

    public record Notifications(Email email, Teams teams) {
        public record Email(boolean enabled, String from) {}
        public record Teams(boolean enabled, String webhookUrl) {}
    }

    public record Dashboard(int stalledDays, int minSampleSize) {}

    public record DevSeed(String password) {}
}
