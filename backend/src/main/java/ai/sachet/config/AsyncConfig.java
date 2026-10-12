package ai.sachet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Bounded async pool for ingestion jobs. Bounded queue + explicit rejection:
 * overload fails a single job safely instead of exhausting the server.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

  @Override
  public Executor getAsyncExecutor() {
    ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
    ex.setCorePoolSize(2);
    ex.setMaxPoolSize(4);
    ex.setQueueCapacity(100);
    ex.setThreadNamePrefix("ingest-");
    ex.setRejectedExecutionHandler((r, e) -> {
      throw new RejectedExecutionException("Ingestion queue full.");
    });
    ex.initialize();
    return ex;
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    Logger log = LoggerFactory.getLogger(AsyncConfig.class);
    return (Throwable ex, Method method, Object... params) ->
        log.error("async failure method={} type={}", method.getName(), ex.getClass().getSimpleName());
  }
}
