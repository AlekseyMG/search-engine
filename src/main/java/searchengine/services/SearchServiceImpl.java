package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.api.response.SearchResponse;
import searchengine.dto.SearchItem;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    //private LemmaFinder lemmaFinder;
    //private TreeSet<SearchItem> searchResult = new TreeSet<>(Comparator.comparingDouble(SearchItem::getRelevance).reversed());
    private ArrayList<SearchItem> searchResult = new ArrayList<>();
    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final LemmaRepository lemmaRepository;
    @Autowired
    private final IndexRepository indexRepository;
    private final int maxPagesForLemma = 500;
    @Override
    public SearchResponse search(String query, String site, int offset, int limit) {
        //searchResult = new TreeSet<>(Comparator.comparingDouble(SearchItem::getRelevance).reversed());
        searchResult = new ArrayList<>();

        if (site.isEmpty()) {
            return searchInAllSites(query, offset, limit);
        }
        return searchInOneSite(query, site, offset, limit);

//        for (int i = 0; i < 5; i++ ) {
//            set.add(new SearchItem(
//                    site,                                               //"site": "http://www.site.com",
//                    site.replaceAll("https://", ""),                    //"siteName": "Имя сайта",
//                    "site " + i + " " + " /path/to/page/6784",          //"uri": "/path/to/page/6784",
//                    "Какая-то страница",                                //"title": "Заголовок страницы, которую выводим"
//                    "site " + i + "<b>" + query + "</b>",               //"snippet": "Фрагмент текста, в котором найдены совпадения, <b>выделенныежирным</b>, в формате HTML"
//                    ((3. + i)/10))                                      //"relevance": 0.93362
//            );
//        }
//        set.forEach(searchItem -> System.out.println(
//                        searchItem.getSite() + " " +
//                        searchItem.getSiteName() + " " +
//                        searchItem.getUri() + " " +
//                        searchItem.getTitle() + " " +
//                        searchItem.getSnippet() + " " +
//                        searchItem.getRelevance() + " "
//        ));
//        return new SearchResponse(set);
    }

    private SearchResponse searchInAllSites(String query, int offset, int limit) {
        siteRepository.findAll().forEach(site -> {
            searchInOneSite(query, site.getUrl(), offset, limit);
        });
        //searchInOneSite(query, site, offset, limit);
        return new SearchResponse(searchResult.subList(getMinOffset(offset), getMinOffset(offset) + getMinLimit(limit)));
    }

    private SearchResponse searchInOneSite(String query, String site, int offset, int limit) {
        System.out.println( offset + " " + limit);
        Set<String> lemmas = getLemmas(query);
//        System.out.println(site.replaceAll("https?://","")
//                .replaceAll("www.",""));
        Site siteEntity = siteRepository
                .findByUrl(site.replaceAll("https?://","")
                .replaceAll("www.",""));
        Set<Page> pages = getPagesByLemmasAndSiteId(lemmas, siteEntity.getId());
        //pages.forEach(page -> System.out.println(page.getPath()));
        pages.forEach(page -> getDataFromPage(page, siteEntity, lemmas));
        searchResult.sort(Comparator.comparingDouble(SearchItem::getRelevance).reversed());
        return new SearchResponse(searchResult.subList(getMinOffset(offset), getMinOffset(offset) + getMinLimit(limit)));
    }

    private Set<String> getLemmas(String text) {
        try {
            LemmaFinder lemmaFinder = new LemmaFinder(new RussianLuceneMorphology());
            return lemmaFinder.getLemmaSet(text);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return new HashSet<>();
    }

    private Set<Page> getPagesByLemmasAndSiteId(Set<String> lemmas, int siteId) {
        Set<Page> pages = new HashSet<>();
        lemmas.forEach(lemma -> {
            Lemma lemmaEntity = lemmaRepository.findBySiteIdAndLemma(siteId, lemma);
            if (lemmaEntity != null) {
                int lemmaId = lemmaRepository.findBySiteIdAndLemma(siteId, lemma).getId();
                HashSet<Integer> pagesIds = indexRepository.findPagesIdsByLemmaId(lemmaId);
                System.out.println("lemmaId: " + lemmaId);
                pagesIds.forEach(System.out::println);
                if (pagesIds.size() < maxPagesForLemma) {
                    pages.addAll(pageRepository.findAllById(pagesIds));
                }
            }
        });

        return pages;
    }

    private void getDataFromPage(Page page, Site site, Set<String> lemmas) {
        String title = Jsoup.parse(page.getContent()).title();
        searchResult.add(new SearchItem(
                    site.getUrl(),                                               //"site": "http://www.site.com",
                    site.getName(),                    //"siteName": "Имя сайта",
                    page.getPath(),          //"uri": "/path/to/page/6784",
                    title,                                //"title": "Заголовок страницы, которую выводим"
                    "... текст сайта <b>" + lemmas.toString() + "</b> текст сайта ...",               //"snippet": "Фрагмент текста, в котором найдены совпадения, <b>выделенныежирным</b>, в формате HTML"
                (Math.random() * 100))                                      //"relevance": 0.93362
            );
    }

    private int getMinLimit(int limit) {
        return Math.min(searchResult.size(), limit);
    }
    private int getMinOffset(int offset) {
        return Math.min(searchResult.size(), offset);
    }
}
