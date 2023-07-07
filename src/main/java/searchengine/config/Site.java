package searchengine.config;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Site{
    private String url;
    private String name;

    @Override
    public String toString() {
        return "Site{" +
                "url='" + url + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
