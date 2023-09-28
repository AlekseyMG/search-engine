package searchengine.services;

import searchengine.api.response.DefaultResponse;

public interface IndexingService {
    DefaultResponse startIndexing();
    DefaultResponse stopIndexing();
}
