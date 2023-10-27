package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "search-setting")
public class SearchSetting {
    private int pagesPercentForLemma;
    private int maxSnippetSize;
    private int minCharsCountAroundWord;
}