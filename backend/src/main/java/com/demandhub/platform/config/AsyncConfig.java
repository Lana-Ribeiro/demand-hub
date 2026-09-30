package com.demandhub.platform.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Tarefas longas (IA, integrações externas) rodam em executor assíncrono.
 * Com {@code app.async.enabled=false} (testes) tudo roda de forma síncrona e determinística.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public TaskExecutor taskExecutor(AppProperties props) {
        if (props.async() == null || !props.async().enabled()) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("demandhub-async-");
        executor.initialize();
        return executor;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
