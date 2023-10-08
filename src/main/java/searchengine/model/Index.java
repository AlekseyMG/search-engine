package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity//(name = "index")

@Table(name = "`index`",
        indexes = {
                @jakarta.persistence.Index(name = "page_rank_index", columnList = "page_id, `rank`"),
                @jakarta.persistence.Index(name = "page_index", columnList = "page_id, lemma_id", unique = true)
        })
public class Index {
//    //● id INT NOT NULL AUTO_INCREMENT;
//    //● page_id INT NOT NULL — идентификатор страницы;
//    //● lemma_id INT NOT NULL — идентификатор леммы;
//    //● rank FLOAT NOT NULL — количество данной леммы для данной
//    //страницы.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.EAGER)//(cascade = CascadeType.ALL)
    @JoinColumn(name = "page_id", referencedColumnName = "id")//, insertable = false, updatable = false)
    private Page page;


    @ManyToOne(fetch = FetchType.EAGER)//(cascade = CascadeType.ALL)
    @JoinColumn(name = "lemma_id", referencedColumnName = "id")//, insertable = false, updatable = false)
    private Lemma lemma;

    @Column(name = "`rank`", nullable = false)
    private float rank;
}
