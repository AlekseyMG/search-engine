package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.api.response.SearchResponse;
import searchengine.dto.SearchItem;
import searchengine.model.Index;
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
    private final int maxPagesForLemma = 1000; //TODO: сделать, чтобы бралось из настроек
    private boolean isLastSite = false;
    @Override
    public SearchResponse search(String query, String site, int offset, int limit) {
        searchResult = new ArrayList<>();

        if (site.isEmpty()) {
            isLastSite = false;
            return searchInAllSites(query, offset, limit, isLastSite);
        }
        isLastSite = true;
        return searchInOneSite(query, site, offset, limit, isLastSite);
    }

    private SearchResponse searchInAllSites(String query, int offset, int limit, boolean isLastSite) {
        List<Site> siteList = siteRepository.findAll();
        for (Site site : siteList) {
            if (site.equals(siteList.get(siteList.size() - 1))){
                isLastSite = true;
            }
            searchInOneSite(query, site.getUrl(), offset, limit, isLastSite);
        }
        return new SearchResponse(searchResult
                .subList(getMinOffset(offset), getMinOffset(offset) + getMinLimit(limit)));
    }

    private SearchResponse searchInOneSite(String query, String site, int offset, int limit, boolean isLastSite) {
        Set<String> lemmas = new HashSet<>();

        if (!query.isBlank()) {
            lemmas = getLemmas(query);
        }

        lemmas = getLemmaCheckedForDuplicate(lemmas, query);
        Site siteEntity = siteRepository
                .findByUrl(site.replaceAll("https?://","")
                .replaceAll("www.",""));
        Set<Page> pages = getPagesByLemmasAndSiteId(lemmas, siteEntity.getId());

        for (Page page : pages) {
            getDataFromPage(page, siteEntity, lemmas);
        }
        if (isLastSite) {
            setRelativeRelevance();
        }
        searchResult.sort(Comparator.comparingDouble(SearchItem::getRelevance).reversed());

        return new SearchResponse(searchResult
                .subList(getMinOffset(offset), getMinOffset(offset) + getMinLimit(limit)));
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
        TreeSet<Lemma> lemmaEntities = getLemmaEntitiesByWordsAndSiteId(lemmas, siteId);
        Set<Integer> firstPagesIdsByLemmaFromIndex = new HashSet<>();

        if (!lemmaEntities.isEmpty()) {
            firstPagesIdsByLemmaFromIndex = indexRepository
                    .findPagesIdsByLemmaId(lemmaEntities.first().getId());
        }
        if (lemmas.size() == 1) {
            return firstPagesIdsByLemmaFromIndex.size() <= maxPagesForLemma ?
                    Set.copyOf(pageRepository.findAllById(firstPagesIdsByLemmaFromIndex)) :
                    new HashSet<>();
        }
        return getMatchedPages(lemmaEntities, firstPagesIdsByLemmaFromIndex);
    }
    private TreeSet<Lemma> getLemmaEntitiesByWordsAndSiteId(Set<String> lemmas, int siteId) {
        TreeSet<Lemma> lemmaEntities = new TreeSet<>(Comparator.comparingDouble(Lemma::getFrequency));

        lemmas.forEach(lemma -> {
            Lemma lemmaEntity = lemmaRepository.findBySiteIdAndLemma(siteId, lemma);
            if (lemmaEntity == null) {
                lemmaEntity = new Lemma();
            }
            lemmaEntities.add(lemmaEntity);
        });
        return lemmaEntities;
    }

    private Set<Page> getMatchedPages(
            TreeSet<Lemma> lemmaEntities,
            Set<Integer> firstPagesIdsByLemmaFromIndex
    ) {
        Set<Page> matchedPages = new HashSet<>();
        Set<Page> pagesByLemmaFromIndex = new HashSet<>();
        Set<Integer> nextPagesIdsByLemmaFromIndex = new HashSet<>();

        for (Lemma lemmaEntity : lemmaEntities) {

            if (!lemmaEntity.equals(lemmaEntities.first())) {
                nextPagesIdsByLemmaFromIndex = indexRepository
                        .findPagesIdsByLemmaId(lemmaEntity.getId());
            }
            if (firstPagesIdsByLemmaFromIndex.size() < maxPagesForLemma) {
                pagesByLemmaFromIndex.addAll(pageRepository
                        .findAllById(firstPagesIdsByLemmaFromIndex));
            }

            for (int pageIdByLemmaFromIndex : firstPagesIdsByLemmaFromIndex) {

                if (nextPagesIdsByLemmaFromIndex.stream()
                        .anyMatch(pageId -> pageId == pageIdByLemmaFromIndex)) {
                    matchedPages.addAll(pagesByLemmaFromIndex.stream()
                            .filter(page -> page.getId() == pageIdByLemmaFromIndex)
                            .toList()
                    );
                    firstPagesIdsByLemmaFromIndex = Set.copyOf(matchedPages.stream()
                            .map(Page::getId)
                            .toList()
                    );
                }
            }
        }
        return matchedPages;
    }

    private Set<String> getLemmaCheckedForDuplicate(Set<String> lemmas, String query) {
        String[] queryWords = query.split("(?<=-|\\s)");
        Set<String> checkedLemmas = new HashSet<>();
        List<String> tempLemmas = lemmas.stream()
                .sorted(Comparator.comparingInt(String::length))
                .toList();
        for (String queryWord : queryWords) {
            int lemmaCount = 0;
            boolean finded = false;
            while (true) {
                for (String lemma : tempLemmas) {
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
        return checkedLemmas;
    }

    private void getDataFromPage(Page page, Site site, Set<String> lemmas) {
        String title = Jsoup.parse(page.getContent()).title();
        String pageText = Jsoup.parse(page.getContent()).text();
        searchResult.add(
                new SearchItem(
                        site.getUrl(),
                        site.getName(),
                        page.getPath(),
                        title,
                        getSnippet(pageText, lemmas),
                        getAbsoluteRelevance(page, lemmas)
                )
        );
    }
    private void setRelativeRelevance() {
        double maxAbsoluteRelevance = searchResult.stream()
                .map(SearchItem::getRelevance)
                .max(Comparator.comparingDouble(Double::doubleValue))
                .get();

        searchResult.forEach(searchItem -> searchItem
                .setRelevance(searchItem.getRelevance() / maxAbsoluteRelevance));
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

        text = text.replaceAll("Ё", "Е")
                .replaceAll("ё", "е");
        lemma = lemma.replaceAll("Ё", "Е")
                .replaceAll("ё", "е");

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

        String PunctuationRegex = "[\\.\\!\\:\\;\\?\\'" +
                "\\,\\(\\)\\+\\*\\«\\»\"\\[\\]\\{\\}\\„\\“]";
        String splitterRegex = "(?<=-|\\s|\\.|\\!|\\:|\\;|\\?|\\'|\\," +
                "|\\(|\\)|\\+|\\*|\\«|\\»|\"|\\[|\\]|\\{|\\}|\\„|\\“)";
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
                    wordWithoutPunctuation.contains(lemma.toUpperCase());

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

            int startIndex = Math.max(pageText.indexOf("<b>") - 140, 0);
            firstWords = pageText.substring(startIndex, pageText.indexOf("<b>"));

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
            int startIndex = Math.max(snip.toString().indexOf("<b>") -
                    charsContAroundWord, 0);
            int endIndex = Math.min(snip.toString().indexOf("</b>") +
                    charsContAroundWord + 4, snip.toString().length());

            finalSnippet
                    .append("...")
                    .append(snip,startIndex, endIndex)
                    .append("</b>")
                    .append("...")
                    .append(" ");
        }
        return finalSnippet.substring(0, Math.min(finalSnippet.length(), 400))
                .replaceAll("-</b>\\s+<b>", "-")
                .replaceAll("<.?h.>","");
    }
    private double getAbsoluteRelevance(Page page, Set<String> lemmas) {
        Set<Index> indices = new HashSet<>();
        List<Double> Ranks = new ArrayList<>();
        double maxRank = 0;
        TreeSet<Lemma> lemmaEntities = getLemmaEntitiesByWordsAndSiteId(
                lemmas, page.getSite().getId()
        );

        lemmaEntities.forEach(lemma -> indices.add(indexRepository
                .findByPageIdAndLemmaId(page.getId(), lemma.getId())));
        if (!indices.isEmpty()) {
            Ranks = new ArrayList<>(indices.stream()
                    .filter(Objects::nonNull)
                    .map(Index::getRank)
                    .map(Float::doubleValue)
                    .toList());
            maxRank = Ranks.stream()
                .max(Comparator.comparingDouble(Double::doubleValue))
                .get();
        }
        double sumRanks = 0;
        for (Double rank : Ranks) {
            sumRanks += rank;
        }
        return sumRanks / maxRank;
    }

    private int getMinLimit(int limit) {
        return Math.min(searchResult.size(), limit);
    }
    private int getMinOffset(int offset) {
        return Math.min(searchResult.size(), offset);
    }
}
