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
        lemmas = getLemmaWithDublicateCheck(lemmas, query);
        Site siteEntity = siteRepository
                .findByUrl(site.replaceAll("https?://","")
                .replaceAll("www.",""));
        Set<Page> pages = getPagesByLemmasAndSiteId(lemmas, siteEntity.getId());
        for (Page page : pages) {
            getDataFromPage(page, siteEntity, lemmas);
        }
        //pages.forEach(page -> getDataFromPage(page, siteEntity, lemmas));
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
        Set<Page> pagesByLemmaFromIndex = new HashSet<>();
        TreeSet<Lemma> lemmaEntitys = new TreeSet<>(Comparator.comparingDouble(Lemma::getFrequency));
        lemmas.forEach(lemma -> {
            Lemma lemmaEntity = lemmaRepository.findBySiteIdAndLemma(siteId, lemma);
            if (lemmaEntity == null) {
                lemmaEntity = new Lemma();
            }
            lemmaEntitys.add(lemmaEntity);
        });
//        System.out.println("site " + siteId + " lemmaEntitys");
//        lemmaEntitys.stream().map(Lemma::getLemma).forEach(System.out::println);
//        System.out.println("site " + siteId + " lemmaEntitys");

        Set<Integer> firstPagesIdsByLemmaFromIndex = indexRepository.findPagesIdsByLemmaId(lemmaEntitys.first().getId());

        if (lemmas.size() == 1) {
            return firstPagesIdsByLemmaFromIndex.size() <= maxPagesForLemma ?
                    Set.copyOf(pageRepository.findAllById(firstPagesIdsByLemmaFromIndex)) :
                    new HashSet<>();
        }

        Set<Page> matchedPages = new HashSet<>();

//        System.out.println("site " + siteId + " lemmaEntitys-------------------------");
//        lemmaEntitys.stream().map(Lemma::getLemma).forEach(System.out::println);
//        lemmaEntitys.stream().map(Lemma::getFrequency).forEach(System.out::println);
//        System.out.println("site " + siteId + " lemmaEntitys-------------------------\n");

        Set<Integer> nextPagesIdsByLemmaFromIndex = new HashSet<>();
        for (Lemma lemmaEntity : lemmaEntitys) {
            //matchedPages = new HashSet<>();
            //firstPagesIdsByLemmaFromIndex = indexRepository.findPagesIdsByLemmaId(lemmaEntity.getId());
            if (!lemmaEntity.equals(lemmaEntitys.first())) {
                nextPagesIdsByLemmaFromIndex = indexRepository.findPagesIdsByLemmaId(lemmaEntity.getId());
            }
            if (firstPagesIdsByLemmaFromIndex.size() < maxPagesForLemma) {
                pagesByLemmaFromIndex.addAll(pageRepository.findAllById(firstPagesIdsByLemmaFromIndex));
            }
//            System.out.println("site " + siteId + " lemma-------------data-----------");
//            System.out.println("site " + siteId + " lemmaEntity " + lemmaEntity.getLemma());
//            System.out.println("site " + siteId + " Frequency " + lemmaEntity.getFrequency());
//            System.out.println("site " + siteId + " firstPagesIdsByLemmaFromIndex " + firstPagesIdsByLemmaFromIndex + " length :" + firstPagesIdsByLemmaFromIndex.size());
//            System.out.println("site " + siteId + " nextPagesIdsByLemmaFromIndex " + nextPagesIdsByLemmaFromIndex);
//            System.out.println("site " + siteId + " lemma-------------data-----------\n");
            for (int pageIdByLemmaFromIndex : firstPagesIdsByLemmaFromIndex) {

                if (nextPagesIdsByLemmaFromIndex.stream().anyMatch(pageId -> pageId == pageIdByLemmaFromIndex)) {
                    matchedPages.addAll(pagesByLemmaFromIndex.stream().filter(page -> page.getId() == pageIdByLemmaFromIndex).toList());
                    firstPagesIdsByLemmaFromIndex = Set.copyOf(matchedPages.stream().map(Page::getId).toList());
                }

            }
            System.out.println("site " + siteId + " matchedPagesIds-----------");
            matchedPages.stream().map(Page::getId).forEach(id -> System.out.print(id + ", "));
            System.out.println("\nsite " + siteId + " matchedPagesIds-----------\n");
//            nextPagesIdsByLemmaFromIndex = indexRepository.findPagesIdsByLemmaId(lemmaEntity.getId());
            //pagesByLemmaFromIndex = matchedPages;
        }
        return matchedPages;
    }

    private Set<String> getLemmaWithDublicateCheck(Set<String> lemmas, String query) {
        String[] queryWords = query.split("(?<=-|\\s)");
        Set<String> tempLemas = Set.copyOf(lemmas);
        Set<String> checkedLemmas = new HashSet<>();
        for (String queryWord : queryWords) {
//            System.out.println("queryWord " + queryWord);
//            System.out.println("tempLemas " + tempLemas);
            int lemmaCount = 0;
            boolean finded = false;
            while (true) {
                for (String lemma : tempLemas) {
//                    System.out.println("queryWord.substring " + queryWord.substring(0, queryWord.length() - lemmaCount));
//                    System.out.println("lemma " + lemma);
                    if (lemma.contains(queryWord.substring(0, queryWord.length() - lemmaCount))) {
                        checkedLemmas.add(lemma);
                        finded = true;
                        break;
                    }
                }
                lemmaCount++;
                if (lemmaCount == queryWord.length() || finded) {
                    break;
                }
            }
        }
        System.out.println("checkedLemmas " + checkedLemmas);
        return checkedLemmas;
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
        text = text.replaceAll("Ё", "Е").replaceAll("ё", "е");
        lemma = lemma.replaceAll("Ё", "Е").replaceAll("ё", "е");
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
//        String lowFirstCharLemmaWithYo = lowFirstCharLemma.replace("е", "ё");
//        String bigFirstCharLemmaWithYo = bigFirstCharLemma.replace("е", "ё");
//        String upperCaseLemmaWithYo = lowFirstCharLemmaWithYo.toUpperCase().replaceAll("Е", "Ё");

        String PunctuationRegex = "[\\.\\!\\:\\;\\?\\'\\,\\(\\)\\+\\*\\«\\»\"\\[\\]\\{\\}\\„\\“]";
        //String splitterRegex = "-|\\s+" + PunctuationRegex + "?";
        String splitterRegex = "(?<=-|\\s|\\.|\\!|\\:|\\;|\\?|\\'|\\,|\\(|\\)|\\+|\\*|\\«|\\»|\"|\\[|\\]|\\{|\\}|\\„|\\“)";
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
                    wordWithoutPunctuation.contains(lemma.toUpperCase()) //||
//                    wordWithoutPunctuation.contains(lowFirstCharLemmaWithYo) ||
//                    wordWithoutPunctuation.contains(bigFirstCharLemmaWithYo) ||
//                    wordWithoutPunctuation.toUpperCase().contains(upperCaseLemmaWithYo)
                    ;

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
        int exponent = 20;
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
        return finalSnippet.substring(0, Math.min(finalSnippet.length(), 400)).replaceAll("-</b>\\s+<b>", "-");
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
