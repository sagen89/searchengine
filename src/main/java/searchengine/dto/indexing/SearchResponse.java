package searchengine.dto.indexing;

import lombok.Data;


import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Data
public class SearchResponse {
    private boolean result;
    private int count;
    private List<SearchData> data;

    @Override
    public String toString() {
        return "SearchResponse{" +
                "result=" + result +
                ", count=" + count +
                ", data=" + data +
                '}';
    }
}
