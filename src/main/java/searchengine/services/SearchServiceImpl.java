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
    private ArrayList<SearchItem> searchResult = new ArrayList<>();
    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final LemmaRepository lemmaRepository;
    @Autowired
    private final IndexRepository indexRepository;
    private final int maxPagesForLemma = 1000;
    @Override
    public SearchResponse search(String query, String site, int offset, int limit) {
        searchResult = new ArrayList<>();
        if (site.isEmpty()) {
            return searchInAllSites(query, offset, limit);
        }
        return searchInOneSite(query, site, offset, limit);
    }

    private SearchResponse searchInAllSites(String query, int offset, int limit) {
        siteRepository.findAll().forEach(site -> {
            searchInOneSite(query, site.getUrl(), offset, limit);
        });
        //searchInOneSite(query, site, offset, limit);
        return new SearchResponse(searchResult.subList(getMinOffset(offset), getMinOffset(offset) + getMinLimit(limit)));
    }

    private SearchResponse searchInOneSite(String query, String site, int offset, int limit) {
        Set<String> lemmas = getLemmas(query);
        Site siteEntity = siteRepository
                .findByUrl(site.replaceAll("https?://","")
                .replaceAll("www.",""));
        Set<Page> pages = getPagesByLemmasAndSiteId(lemmas, siteEntity.getId());
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
        TreeSet<Lemma> lemmaEntitys = new TreeSet<>(Comparator.comparingDouble(Lemma::getFrequency));
        lemmas.forEach(lemma -> {
            Lemma lemmaEntity = lemmaRepository.findBySiteIdAndLemma(siteId, lemma);
            if (lemmaEntity != null) {
                lemmaEntitys.add(lemmaEntity);
            }
        });
        Set<Integer> pagesIds;
        Set<Page> matchedPages;
        for (Lemma lemmaEntity : lemmaEntitys) {
            matchedPages = new HashSet<>();
            pagesIds = indexRepository.findPagesIdsByLemmaId(lemmaEntity.getId());

            if (pages.isEmpty() && pagesIds.size() < maxPagesForLemma) {
                    pages.addAll(pageRepository.findAllById(pagesIds));
            }

            for (int pageId : pagesIds) {
                if (pages.stream().map(Page::getId).toList().contains(pageId)) {
                    matchedPages.addAll(pages.stream().filter(page -> page.getId() == pageId).toList());
                }
            }
            pages = matchedPages;
        }
        return pages;
    }

    private void getDataFromPage(Page page, Site site, Set<String> lemmas) {
        String title = Jsoup.parse(page.getContent()).title();
        String pageText = Jsoup.parse(page.getContent()).text();
        searchResult.add(
                new SearchItem(
                    site.getUrl(),                                               //"site": "http://www.site.com",
                    site.getName(),                    //"siteName": "Имя сайта",
                    page.getPath(),          //"uri": "/path/to/page/6784",
                    title,                                //"title": "Заголовок страницы, которую выводим"
                    getSnippet(pageText, lemmas),               //"snippet": "Фрагмент текста, в котором найдены совпадения, <b>выделенныежирным</b>, в формате HTML"
                    getRelevance()
                )                                      //"relevance": 0.93362
            );
    }

    private String getSnippet(String pageText, Set<String> lemmas) {

        for (String lemma : lemmas) {
            pageText = getTextWithBoldedWords(lemma, pageText);
        }
        return getSnippetFromBoldedText(pageText);
    }
    private String getTextWithBoldedWords(String lemma, String text) {
        char bigFirstChar = lemma.toUpperCase().charAt(0);
        char lowFirstChar = lemma.toLowerCase().charAt(0);

        if (lemma.charAt(lemma.length() - 1) == 'ь') {
            lemma = lemma.substring(0, lemma.length() - 1);
        }
        if (lemma.length() > 4) {
            lemma = lemma.substring(0, lemma.length() - 1);
        }
        if (lemma.length() > 3) {
            lemma = lemma.substring(0, lemma.length() - 1);
        }

        String bigFirstCharLemma = bigFirstChar + lemma.substring(1);
        String lowFirstCharLemma = lowFirstChar + lemma.substring(1);
        String lowFirstCharLemmaWithYo = lowFirstCharLemma.replace("е", "ё");
        String bigFirstCharLemmaWithYo = bigFirstCharLemma.replace("е", "ё");
        String upperCaseLemmaWithYo = lowFirstCharLemmaWithYo.toUpperCase().replaceAll("Е", "Ё");

        String PunctuationRegex = "[\\.\\!\\:\\;\\?\\'\\,\\(\\)\\+\\*\\«\\»\"\\[\\]\\{\\}\\“]";
        String splitterRegex = "-|\\s+" + PunctuationRegex + "?";
        String[] splittedText = text.split(splitterRegex);
        StringBuilder textWithBoldedWords = new StringBuilder();
        String wordWithoutPunctuation;
        String punctuation;
        for (String word : splittedText) {
            word = word.replace("\\","");
            wordWithoutPunctuation = word.replaceAll(PunctuationRegex, "");
            punctuation = word.replaceAll(wordWithoutPunctuation, "");
            char firstWordChar = 0;
            if (!word.isBlank()) {
                firstWordChar = word.toLowerCase().charAt(0);
            }
            boolean isFirstCharMatched = firstWordChar == lowFirstChar;
            boolean lengthMatched = wordWithoutPunctuation.length() <= lemma.length() + 4;
            boolean lemmaMatched = wordWithoutPunctuation.contains(lowFirstCharLemma) ||
                    wordWithoutPunctuation.contains(bigFirstCharLemma) ||
                    wordWithoutPunctuation.contains(lemma.toUpperCase()) ||
                    wordWithoutPunctuation.contains(lowFirstCharLemmaWithYo) ||
                    wordWithoutPunctuation.contains(bigFirstCharLemmaWithYo) ||
                    wordWithoutPunctuation.toUpperCase().contains(upperCaseLemmaWithYo);

            if (lemmaMatched && lengthMatched && isFirstCharMatched) {
                word = "<b>" + wordWithoutPunctuation + "</b>" + punctuation;
            }
            textWithBoldedWords.append(" ").append(word);
        }
        return textWithBoldedWords.toString();
    }

    private String getSnippetFromBoldedText(String pageText) {
        StringBuilder snippet;
        String firstWords;
        List<StringBuilder> snippets = new ArrayList<>();
        int charsContAroundWord = 140;
        int exponent = 30;
        while (pageText.contains("<b>")) {
            snippet = new StringBuilder();
            firstWords = pageText.substring(Math.max(pageText.indexOf("<b>") - 140, 0), pageText.indexOf("<b>"));
            pageText = pageText.substring(pageText.indexOf("<b>"));
            int endIndex = Math.min(pageText.indexOf("</b>") + 140, pageText.length());
            snippet.append(firstWords).append(pageText, 0, endIndex);
            snippets.add(snippet);
            pageText = pageText.substring(pageText.indexOf("</b>") + 4);
            if (charsContAroundWord > 8 && exponent > 0) {
                exponent -= 4;
                charsContAroundWord = charsContAroundWord - exponent - 6;
            }
        }
        StringBuilder finalSnippet = new StringBuilder();
        for (StringBuilder snip : snippets) {
            int startIndex = Math.max(snip.toString().indexOf("<b>") - charsContAroundWord, 0);
            int endIndex = Math.min(snip.toString().indexOf("</b>") + charsContAroundWord + 4, snip.toString().length());
            finalSnippet.append("...").append(snip,startIndex, endIndex).append("</b>").append("...").append(" ");
        }
        return finalSnippet.substring(0, Math.min(finalSnippet.length(), 400));
    }
    private double getRelevance() {
        return Math.random() * 100;
    }

    private int getMinLimit(int limit) {
        return Math.min(searchResult.size(), limit);
    }
    private int getMinOffset(int offset) {
        return Math.min(searchResult.size(), offset);
    }
}
