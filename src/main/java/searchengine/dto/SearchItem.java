package searchengine.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
public class SearchItem {
//        "site": "http://www.site.com",
//        "siteName": "Имя сайта",
//        "uri": "/path/to/page/6784",
//        "title": "Заголовок страницы,
//                  которую выводим",
//        "snippet": "Фрагмент текста,
//                      в котором найдены
//                      совпадения, <b>выделенные
//                      жирным</b>, в формате HTML",
//        "relevance": 0.93362
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private double relevance;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchItem that = (SearchItem) o;
        return Double.compare(that.relevance, relevance) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(relevance);
    }
}
