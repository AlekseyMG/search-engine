package searchengine.services;

import searchengine.api.response.SearchResponse;

public interface SearchService {
    SearchResponse search(String query, String site, int offset, int limit);
}
