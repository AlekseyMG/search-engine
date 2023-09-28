package searchengine.api.response;

import lombok.Data;
import searchengine.dto.statistics.StatisticsData;

@Data
public class StatisticsResponse {
    private boolean result;
    private StatisticsData statistics;
}
