package searchengine.services;

import searchengine.dto.indexing.IndexPageResponse;
import searchengine.dto.indexing.StartIndexingResponse;
import searchengine.dto.indexing.StopIndexingResponse;

public interface IndexingService {
    StartIndexingResponse startIndexing();
    StopIndexingResponse stopIndexing();
    IndexPageResponse indexPage(String url);
}
