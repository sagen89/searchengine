package searchengine.services;

import searchengine.dto.indexing.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;

public interface StatisticsService {
    StatisticsResponse getStatistics();
    SearchResponse getSearchResults(String query, Integer offset, Integer limit, String site);
}
