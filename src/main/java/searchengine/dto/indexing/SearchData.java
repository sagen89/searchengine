package searchengine.dto.indexing;

import lombok.Data;

@Data
public class SearchData implements Comparable {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;

    @Override
    public String toString() {
        return "SearchData{" +
                "site='" + site + '\'' +
                ", name='" + siteName + '\'' +
                ", uri='" + uri + '\'' +
                ", title='" + title + '\'' +
                ", snippet=" + snippet +
                ", relevance=" + relevance +
                '}';
    }

    @Override
    public int compareTo(Object o) {
        SearchData that = (SearchData) o;

        String nameThisObject = site.concat(uri);
        String nameThatObject = that.site.concat(that.uri);

        int compareRelevance = Float.compare(that.getRelevance(), relevance);
        return compareRelevance != 0 ?
                compareRelevance : nameThisObject.compareTo(nameThatObject);
    }

}
