package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "search-setting")
public class SearchSetting {
    private int maxPagesForLemma;
    private int maxSnippetSize;
    private int minCharsCountAroundWord;
}