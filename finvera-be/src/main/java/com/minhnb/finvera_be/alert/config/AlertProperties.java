package com.minhnb.finvera_be.alert.config;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix="finvera.alert.worker")
public record AlertProperties(boolean enabled,Duration pollDelay,Duration recoveryDelay,Duration leaseDuration,int batchSize,int maxAttempts){
 public AlertProperties {if(pollDelay==null)pollDelay=Duration.ofSeconds(10);if(recoveryDelay==null)recoveryDelay=Duration.ofSeconds(30);if(leaseDuration==null)leaseDuration=Duration.ofSeconds(60);if(batchSize<1||batchSize>100)batchSize=50;if(maxAttempts<1||maxAttempts>2)maxAttempts=2;}
}
