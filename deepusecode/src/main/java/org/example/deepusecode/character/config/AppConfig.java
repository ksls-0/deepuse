package org.example.deepusecode.character.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    /**
     * AI 同步生成一次可能要 15~60s，默认的无限/过短超时都不合适，
     * 这里显式给足读超时。
     */
    @Bean
    public RestTemplate restTemplate(
            @Value("${deepseek.api.timeout-ms:120000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }

    /**
     * SSE 流式对话的异步执行线程池。
     * 流式请求会把 Tomcat 工作线程让出来，真正的模型读取放在这里跑。
     */
    @Bean("chatStreamExecutor")
    public ThreadPoolTaskExecutor chatStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("chat-sse-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
