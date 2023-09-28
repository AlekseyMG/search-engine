package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
//@Table(indexes = @Index(columnList = "path"))
@Table(name = "page",
        indexes = {
        @Index(name = "path_index", columnList = "path, site_id", unique = true)
})
public class Page {
    //id INT NOT NULL AUTO_INCREMENT;
    //● site_id INT NOT NULL — ID веб-сайта из таблицы site;
    //● path TEXT NOT NULL — адрес страницы от корня сайта (должен
    //начинаться со слэша, например: /news/372189/);
    //2
    //● code INT NOT NULL — код HTTP-ответа, полученный при запросе
    //страницы (например, 200, 404, 500 или другие);
    //● content MEDIUMTEXT NOT NULL — контент страницы (HTML-код).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.EAGER)//(cascade = CascadeType.ALL)
    @JoinColumn(name = "site_id", referencedColumnName = "id")//, insertable = false, updatable = false)
    private Site site;


    @Column(nullable = false)
//    @Index(name = "idx_page_path", columnNames = "path", length = 255)
//    @Column(columnDefinition = "TEXT, UNIQUE KEY path_index INDEX (path(255))")
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}
