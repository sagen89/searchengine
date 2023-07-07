package searchengine.dto.indexing;

import lombok.Data;

@Data
public class RequestSearch {
    private String query;
    private String offset;
    private String limit;
    private String site;

    @Override
    public String toString() {
        return "RequestSearch{" +
                "query='" + query + '\'' +
                ", offset='" + offset + '\'' +
                ", limit='" + limit + '\'' +
                ", site='" + site + '\'' +
                '}';
    }
}
