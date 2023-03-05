package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;

import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "page")
public class PageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

//    @ManyToOne
//    @JoinColumn(name = "site_id", referencedColumnName = "id")

    @ManyToOne
    @JoinColumn(name="site_id")
    private SiteEntity siteEntity;

    @Column(columnDefinition = "TEXT not null, KEY(path(50))")
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT not null")
    private String content;

    @Override
    public String toString() {
        return "PageEntity{" +
                "id=" + id +
                ", siteEntity=" + siteEntity +
                ", path='" + path + '\'' +
                ", code=" + code +
                '}';
    }
}
