package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "parser-setting")
public class ParserSetting {
    private String userAgent;
    private String referrer;
    private int randomDelayDeltaBeforeConnection;
    private int minDelayBeforeConnection;
    private int connectionTimeout;
    private int batchSize;
    private int cpuForPool;
}
