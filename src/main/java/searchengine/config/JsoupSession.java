package searchengine.config;

import lombok.Setter;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Setter
@Component
@ConfigurationProperties(prefix = "jsoup-settings")
public class JsoupSession {
    private String userAgent;
    private String referrer;
    private int timeOut;


    @Override
    public String toString() {
        return "JsoupConnectSettings{" +
                "userAgent='" + userAgent + '\'' +
                ", referrer='" + referrer + '\'' +
                ", timeOut=" + timeOut +
                '}';
    }

    public Connection getSession() {
        return Jsoup.newSession()
                .userAgent(userAgent)
                .referrer(referrer)
                .timeout(timeOut);
    }
}
