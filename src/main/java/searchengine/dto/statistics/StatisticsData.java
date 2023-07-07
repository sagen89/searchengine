package searchengine.dto.statistics;

import lombok.Data;

import java.util.List;

@Data
public class StatisticsData {
    private TotalStatistics total;
    private List<DetailedStatisticsItem> detailed;

    @Override
    public String toString() {
        return "StatisticsData{" +
                "total=" + total +
                ", detailed=" + detailed +
                '}';
    }
}
