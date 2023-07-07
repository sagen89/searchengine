package searchengine.model;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;
import searchengine.dto.indexing.SearchData;

import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "lemma")
public class LemmaEntity implements Comparable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "site_id")
    private SiteEntity siteEntity;

    @Column(columnDefinition = "VARCHAR(255) not null")
    private String lemma;

    @Column(nullable = false)
    private int frequency;

    @OneToMany(cascade = {CascadeType.REMOVE}, fetch = FetchType.LAZY, mappedBy = "lemmaEntity")
    private Set<IndexEntity> indexEntities;

    @Transient
    private String word;

    @Override
    public String toString() {
        return "LemmaEntity{" +
                "id=" + id +
                ", siteEntity=" + siteEntity +
                ", lemma='" + lemma + '\'' +
                ", frequency=" + frequency +
                '}';
    }

    @Override
    public int compareTo(Object o) {
        LemmaEntity that = (LemmaEntity) o;

        String nameThisObject = siteEntity.getName().concat(lemma);
        String nameThatObject = that.siteEntity.getName().concat(that.lemma);

        int compareFrequency = Float.compare(frequency, that.frequency);

        return compareFrequency != 0 ?
                compareFrequency : nameThisObject.compareTo(nameThatObject);
    }

}
