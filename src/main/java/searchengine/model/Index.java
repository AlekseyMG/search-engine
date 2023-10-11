package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "`index`",
        indexes = {
                @jakarta.persistence.Index(name = "page_rank_index", columnList = "page_id, `rank`"),
                @jakarta.persistence.Index(name = "page_index", columnList = "page_id, lemma_id", unique = true)
        })
public class Index {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "page_id", referencedColumnName = "id")
    private Page page;


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lemma_id", referencedColumnName = "id")
    private Lemma lemma;

    @Column(name = "`rank`", nullable = false)
    private float rank;
}
