package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@Entity
public class Site {
    //id INT NOT NULL AUTO_INCREMENT;
    //● status ENUM('INDEXING', 'INDEXED', 'FAILED') NOT NULL — текущий
    //статус полной индексации сайта, отражающий готовность поискового
    //движка осуществлять поиск по сайту — индексация или переиндексация
    //в процессе, сайт полностью проиндексирован (готов к поиску) либо его не
    //удалось проиндексировать (сайт не готов к поиску и не будет до
    //устранения ошибок и перезапуска индексации);
    //● status_time DATETIME NOT NULL — дата и время статуса (в случае
    //статуса INDEXING дата и время должны обновляться регулярно при
    //добавлении каждой новой страницы в индекс);
    //● last_error TEXT — текст ошибки индексации или NULL, если её не было;
    //● url VARCHAR(255) NOT NULL — адрес главной страницы сайта;
    //● name VARCHAR(255) NOT NULL — имя сайта.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;

    @Column(nullable = false)
    private LocalDateTime statusTime;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String name;
}
