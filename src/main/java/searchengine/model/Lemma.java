package searchengine.model;

import jakarta.persistence.*;
import jakarta.persistence.Index;
import lombok.Data;

@Data
@Entity
//@Table(indexes = @Index(columnList = "path"))

@Table(name = "lemma",
        indexes = {
                @Index(name = "lemma_index", columnList = "lemma, site_id", unique = true)
        })
public class Lemma {
    //● id INT NOT NULL AUTO_INCREMENT;
    //● site_id INT NOT NULL — ID веб-сайта из таблицы site;
    //● lemma VARCHAR(255) NOT NULL — нормальная форма слова (лемма);
    //● frequency INT NOT NULL — количество страниц, на которых слово
    //встречается хотя бы один раз. Максимальное значение не может
    //превышать общее количество слов на сайте.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.EAGER)//(cascade = CascadeType.ALL)
    @JoinColumn(name = "site_id", referencedColumnName = "id")//, insertable = false, updatable = false)
    private Site site;


    @Column(nullable = false)
//    @Index(name = "idx_page_path", columnNames = "path", length = 255)
//    @Column(columnDefinition = "TEXT, UNIQUE KEY path_index INDEX (path(255))")
    private String lemma;

    @Column(nullable = false)
    private int frequency;

//    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
//    private String content;
}
