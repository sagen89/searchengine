package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "`index`", schema = "search_engine")
public class IndexEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @ManyToOne
    @JoinColumn(name = "page_id")
    private PageEntity pageEntity;
    @ManyToOne
    @JoinColumn(name = "lemma_id")
    private LemmaEntity lemmaEntity;

    @Column(name = "`rank`", nullable = false)
    private int rank;

    @Override
    public String toString() {
        return "IndexEntity{" +
                "id=" + id +
                ", pageEntity=" + pageEntity.getPath() +
                ", lemmaEntity=" + lemmaEntity.getLemma() +
                ", rank=" + rank +
                '}';
    }
}
