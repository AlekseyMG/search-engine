package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.api.response.SearchResponse;
import searchengine.dto.SearchItem;
import java.util.Comparator;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    @Override
    public SearchResponse search(String query, String site, int offset, int limit) {
        TreeSet<SearchItem> set = new TreeSet<>(Comparator.comparingDouble(SearchItem::getRelevance).reversed());

        for (int i = 0; i < 5; i++ ) {
            set.add(new SearchItem(
                    site,                                               //"site": "http://www.site.com",
                    site.replaceAll("https://", ""),    //"siteName": "Имя сайта",
                    "site " + i + " " + " /path/to/page/6784",          //"uri": "/path/to/page/6784",
                    "Какая-то страница",                                //"title": "Заголовок страницы, которую выводим"
                    "site " + i + "<b>" + query + "</b>",               //"snippet": "Фрагмент текста, в котором найдены совпадения, <b>выделенныежирным</b>, в формате HTML"
                    ((3. + i)/10))                                      //"relevance": 0.93362
            );
        }
        set.forEach(searchItem -> System.out.println(
                        searchItem.getSite() + " " +
                        searchItem.getSiteName() + " " +
                        searchItem.getUri() + " " +
                        searchItem.getTitle() + " " +
                        searchItem.getSnippet() + " " +
                        searchItem.getRelevance() + " "
        ));
        return new SearchResponse(set);
    }
}
