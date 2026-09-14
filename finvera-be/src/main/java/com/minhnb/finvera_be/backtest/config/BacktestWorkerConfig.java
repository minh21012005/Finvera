package com.minhnb.finvera_be.backtest.config;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BacktestWorkerConfig {
    @ConfigurationProperties("finvera.backtest.worker")
    public record Properties(boolean enabled,int concurrency,Duration staleAfter){
        public Properties{if(concurrency<1||concurrency>8)concurrency=1;if(staleAfter==null)staleAfter=Duration.ofMinutes(5);}
    }
    @Bean(destroyMethod="shutdown") ExecutorService backtestExecutor(Properties p){return new ThreadPoolExecutor(p.concurrency(),p.concurrency(),0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(p.concurrency()*4),new ThreadPoolExecutor.AbortPolicy());}
}
