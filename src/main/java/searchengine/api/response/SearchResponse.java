package searchengine.api.response;

import lombok.Getter;
import lombok.Setter;
import searchengine.dto.SearchItem;
import java.util.List;

@Getter
@Setter
public class SearchResponse {
    private final boolean result = true;
    private int count;
    private List<SearchItem> data;

    public SearchResponse(List<SearchItem> data) {
        this.data = data;
        this.count = data.size();
    }
}
