package searchengine.model;

import jakarta.persistence.*;
import jakarta.persistence.Index;
import lombok.Data;

@Data
@Entity
@Table(name = "page",
        indexes = {
            @Index(name = "path_index", columnList = "path, site_id", unique = true)
        })
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.EAGER)//(cascade = CascadeType.ALL)
    @JoinColumn(name = "site_id", referencedColumnName = "id")//, insertable = false, updatable = false)
    private Site site;

    //TODO: Сделать поле TEXT NOT NULL с индексом
    @Column(nullable = false)
//    @Index(name = "idx_page_path", columnNames = "path", length = 255)
//    @Column(columnDefinition = "TEXT, UNIQUE KEY path_index INDEX (path(250))")
//    @Index(name = "page_path", columnList = "path", unique = true)
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}
