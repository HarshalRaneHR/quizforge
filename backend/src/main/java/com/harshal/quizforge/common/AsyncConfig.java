package com.harshal.quizforge.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    /**
     * Background AI generation. Virtual threads (Java 21+): each job mostly waits on the LLM over HTTP,
     * so a cheap thread per job is ideal; concurrency is limited per user in GenerationService.
     */
    @Bean(destroyMethod = "")
    Executor generationExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
