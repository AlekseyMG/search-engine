package searchengine.model;

import jakarta.persistence.*;
import jakarta.persistence.Index;
import lombok.Data;

@Data
@Entity
@Table(name = "lemma",
        indexes = {
                @Index(name = "lemma_index", columnList = "lemma, site_id")
        })
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "site_id", referencedColumnName = "id")
    private Site site;

    @Column(nullable = false)
    private String lemma;

    @Column(nullable = false)
    private int frequency;
}
