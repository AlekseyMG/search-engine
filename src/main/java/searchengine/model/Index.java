package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "`index`",
        indexes = {
                @jakarta.persistence.Index(name = "rank_index", columnList = "`rank`"),
                @jakarta.persistence.Index(name = "page_index", columnList = "page_id, lemma_id")
        })
public class Index {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "page_id", referencedColumnName = "id")
    //@Column(name = "page_id", nullable = false)
    //private int pageId;
    private Page page;


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lemma_id", referencedColumnName = "id")
   // @Column(name = "lemma_id", nullable = false)
   // private int lemmaId;
    private Lemma lemma;

    @Column(name = "`rank`", nullable = false)
    private float rank;
}
