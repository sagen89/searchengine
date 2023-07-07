package searchengine.dto.statistics;

import lombok.Data;

@Data
public class TotalStatistics {
    private int sites;
    private int pages;
    private int lemmas;
    private boolean indexing;

    @Override
    public String toString() {
        return "TotalStatistics{" +
                "sites=" + sites +
                ", pages=" + pages +
                ", lemmas=" + lemmas +
                ", indexing=" + indexing +
                '}';
    }
}
