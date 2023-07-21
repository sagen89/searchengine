package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;

import lombok.Setter;
import org.jsoup.nodes.Document;

import javax.swing.text.html.HTML;
import java.util.Objects;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "page")
public class PageEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name="site_id")
    private SiteEntity siteEntity;

    @Column(columnDefinition = "TEXT not null, KEY(path(50))")
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT not null")
    private String content;

    @OneToMany(cascade = {CascadeType.REMOVE}, fetch = FetchType.LAZY, mappedBy = "pageEntity")
    private Set<IndexEntity> indexEntitySet;

    @Override
    public String toString() {
        return "PageEntity{" +
                "id=" + id +
                ", siteEntity=" + siteEntity +
                ", path='" + path + '\'' +
                ", code=" + code +
                '}';
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PageEntity that = (PageEntity) o;
        return siteEntity.equals(that.siteEntity) && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(siteEntity, path);
    }
}
