package searchengine.api.response;

import lombok.Getter;
import lombok.Setter;
import searchengine.dto.SearchItem;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

@Getter
@Setter
public class SearchResponse {
    private final boolean result = true;
    private int count;
    //private TreeSet<SearchItem> data;
    private List<SearchItem> data;

    public SearchResponse(List<SearchItem> data) {
        this.data = data;
        this.count = data.size();
    }
}
