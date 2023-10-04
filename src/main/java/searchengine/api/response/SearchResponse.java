package searchengine.api.response;

import lombok.Getter;
import lombok.Setter;
import searchengine.dto.SearchItem;
import java.util.TreeSet;

@Getter
@Setter
public class SearchResponse {
    private final boolean result = true;
    private int count;
    private TreeSet<SearchItem> data;

    public SearchResponse(TreeSet<SearchItem> data) {
        this.data = data;
        this.count = data.size();
    }
}
