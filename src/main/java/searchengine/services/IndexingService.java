package searchengine.services;

import searchengine.dto.DefaultResponse;

public interface IndexingService {
    DefaultResponse startIndexing();
    DefaultResponse stopIndexing();
}
