package searchengine.dto.statistics;

import lombok.Data;

@Data
public class DetailedStatisticsItem {
    private String url;
    private String name;
    private String status;
    private long statusTime;
    private String error;
    private int pages;
    private int lemmas;

    @Override
    public String toString() {
        return "DetailedStatisticsItem{" +
                "url='" + url + '\'' +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                ", statusTime=" + statusTime +
                ", error='" + error + '\'' +
                ", pages=" + pages +
                ", lemmas=" + lemmas +
                '}';
    }
}
