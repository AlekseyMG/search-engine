package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.api.response.SearchResponse;
import searchengine.config.ParserSetting;
import searchengine.config.SearchSetting;
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
    private String lastQuery;
    private String lastQuerySite;
    private final SearchSetting searchSetting;
    private final ParserSetting parserSetting;
    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final LemmaRepository lemmaRepository;
    @Autowired
    private final IndexRepository indexRepository;
    private int maxPagesForLemma;
    private boolean isLastSite = false;
    @Override
    public SearchResponse search(String query, String site, int offset, int limit) {
        if (!searchResult.isEmpty() && query.equals(lastQuery) && site.equals(lastQuerySite)) {
            return new SearchResponse(searchResult
                    .subList(getMinOffset(offset), getMinOffset(offset) + getMinLimit(offset, limit)),
                    searchResult.size());
        }
        lastQuery = query;
        lastQuerySite = site;
        searchResult = new ArrayList<>();
        maxPagesForLemma = searchSetting.getMaxPagesForLemma();

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
            if (site.equals(siteList.get(siteList.size() - 1))) {
                isLastSite = true;
            }
            searchInOneSite(query, site.getUrl(), offset, limit, isLastSite);
        }
        return new SearchResponse(searchResult
                .subList(getMinOffset(offset), getMinOffset(offset) + getMinLimit(offset, limit)),
                searchResult.size());
    }

    private SearchResponse searchInOneSite(String query, String site, int offset, int limit, boolean isLastSite) {
        Set<String> lemmas = new HashSet<>();

        if (!query.isBlank()) {
            lemmas = getLemmas(query.replaceAll("Ё", "Е")
                    .replaceAll("ё", "е"));
        }

        lemmas = getLemmaCheckedForDuplicate(lemmas, query);
        Site siteEntity = siteRepository
                .findByUrl(site.replaceAll("https?://","")
                .replaceAll("www.",""));
        List<Page> pages = getPagesByLemmasAndSiteId(lemmas, siteEntity.getId());

        for (Page page : pages) {
            setDataToSearchResultFromPage(page, siteEntity, lemmas);
        }
        if (isLastSite) {
            setRelativeRelevance();
        }
        searchResult.sort(Comparator.comparingDouble(SearchItem::getRelevance).reversed());

        return new SearchResponse(searchResult
                .subList(getMinOffset(offset), getMinOffset(offset) + getMinLimit(offset, limit)),
                searchResult.size());
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

    private List<Page> getPagesByLemmasAndSiteId(Set<String> lemmas, int siteId) {
        TreeSet<Lemma> lemmaEntities = getLemmaEntitiesByWordsAndSiteId(lemmas, siteId);
        Set<Integer> pagesIdsByFirstLemmaFromIndex = new HashSet<>();

        if (!lemmaEntities.isEmpty()) {
            pagesIdsByFirstLemmaFromIndex = indexRepository
                    .findPagesIdsByLemmaId(lemmaEntities.first().getId());
        }
        if (lemmas.size() == 1) {
            return pagesIdsByFirstLemmaFromIndex.size() <= maxPagesForLemma ?
                    pageRepository.findAllById(pagesIdsByFirstLemmaFromIndex) :
                    new ArrayList<>();
        }
        return getMatchedPages(lemmaEntities, pagesIdsByFirstLemmaFromIndex);
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

    private List<Page> getMatchedPages(TreeSet<Lemma> lemmaEntities, Set<Integer> firstMatchedPageIds) {
        lemmaEntities.remove(lemmaEntities.first());
        Set<Integer> matchedPageIds = new HashSet<>();
        Set<Integer> pageIds;

        for (Lemma lemmaEntity : lemmaEntities) {
            matchedPageIds = new HashSet<>();
            pageIds = indexRepository.findPagesIdsByLemmaId(lemmaEntity.getId());
            if (pageIds.size() < maxPagesForLemma) {
                for (int id : pageIds) {
                    if (firstMatchedPageIds.stream().anyMatch(pageId -> pageId == id)) {
                        matchedPageIds.add(id);
                    }
                }
            }
            firstMatchedPageIds = matchedPageIds;
        }
        return pageRepository.findAllById(matchedPageIds);
    }

    private Set<String> getLemmaCheckedForDuplicate(Set<String> lemmas, String query) {
        if (lemmas.size() < 2) {
            return lemmas;
        }
        String[] queryWords = query.split("(?<=-|\\s)");
        Set<String> checkedLemmas = new HashSet<>();
        List<String> tempLemmas = lemmas.stream()
                .sorted(Comparator.comparingInt(String::length))
                .toList();
        for (String queryWord : queryWords) {
            int charsCount = 0;
            boolean finded = false;
            while (true) {
                for (String lemma : tempLemmas) {
                    if (lemma.contains(queryWord.toLowerCase().substring(0, queryWord.length() - charsCount))) {
                        checkedLemmas.add(lemma.toLowerCase());
                        finded = true;
                        break;
                    }
                }
                charsCount++;
                if (charsCount == queryWord.length() || finded) {
                    break;
                }
            }
        }
        return checkedLemmas;
    }

    private void setDataToSearchResultFromPage(Page page, Site site, Set<String> lemmas) {
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
        double maxAbsoluteRelevance = 0;
        if (!searchResult.isEmpty()) {
            maxAbsoluteRelevance = searchResult.stream()
                    .map(SearchItem::getRelevance)
                    .max(Comparator.comparingDouble(Double::doubleValue))
                    .get();
        }
        for (SearchItem searchItem : searchResult) {
            searchItem.setRelevance(searchItem.getRelevance() / maxAbsoluteRelevance);
        }
    }

    private String getSnippet(String pageText, Set<String> lemmas) {
        for (String lemma : lemmas) {
            pageText = getTextWithBoldedWords(lemma, pageText);
            if (!pageText.contains("<b>")) {
                pageText = getTextWithBoldedWords(lemma
                        .replaceAll("ть|ся","")
                        .substring(0, lemma
                                .replaceAll("ть|ся","")
                                .length() - 2), pageText);
            }
        }
        return getSnippetFromBoldedText(pageText);
    }
    private String getTextWithBoldedWords(String lemma, String text) {
        int lemmaLength = lemma.length();
        lemma = lemma.replaceAll("ться", "");
        String endingChars = "уеыаоэяийюёьъ";
        endingChars += endingChars.toUpperCase();

        while (endingChars.contains("" + lemma.charAt(lemma.length() - 1))) {
            lemma = lemma.substring(0, lemma.length() - 1);
        }

        text = text.replaceAll("Ё", "Е")
                .replaceAll("ё", "е");
        lemma = lemma.replaceAll("Ё", "Е")
                .replaceAll("ё", "е");

        char bigFirstChar = lemma.toUpperCase().charAt(0);
        char lowFirstChar = lemma.toLowerCase().charAt(0);

        String lemmaPart = lemma.substring(1);
        String PunctuationRegex = "-|\\s|\\.|\\!|\\:|\\;|\\?|\\'|\\," +
                "|\\(|\\)|\\+|\\*|\\«|\\»|\"|\\[|\\]|\\{|\\}|\\„|\\“";
        String bolderRegex = "(?<="+ PunctuationRegex +")" +
                "(" + bigFirstChar + "|" + lowFirstChar + ")" +
                lemmaPart + "[А-я]{0," + (lemmaLength + 3) + "}";

        return text.replaceAll(bolderRegex, "<b>$0</b>");
    }

    private String getSnippetFromBoldedText(String pageText) {
        pageText = pageText.replaceAll("-</b>\\s+<b>", "-")
                .replaceAll("</b>\\s+<b>", " ")
                .replaceAll("\\s+", " ");
        List<String> snippets = new ArrayList<>();
        int maxSnippetSize = searchSetting.getMaxSnippetSize();
        int minCharsCountAroundWord = searchSetting.getMinCharsCountAroundWord();
        int charsCountAroundWord = 0;
        int snippetsCount = 0;
        int wordLength = 0;

        while (pageText.contains("<b>")) {
            snippetsCount++;
            wordLength += pageText.substring(pageText.indexOf("<b>")).length() -
                    pageText.substring(pageText.indexOf("</b>")).length() + minCharsCountAroundWord * 2;
            charsCountAroundWord = (maxSnippetSize - wordLength) / 2 / snippetsCount;
            int startIndex = Math.max(pageText.indexOf("<b>") - maxSnippetSize / 2, 0);
            int endIndex = Math.min(pageText.indexOf("</b>") + maxSnippetSize / 2, pageText.length());
            snippets.add(pageText.substring(startIndex, endIndex));
            pageText = pageText.substring(pageText.indexOf("</b>") + 4);
        }
        charsCountAroundWord = Math.max(charsCountAroundWord, minCharsCountAroundWord);

        return trimSnippet(snippets, charsCountAroundWord, maxSnippetSize);
    }

    private String trimSnippet(List<String> snippets, int charsCountAroundWord, int maxSnippetSize) {
        StringBuilder finalSnippet = new StringBuilder();

        for (String snip : snippets) {
            int startIndex = Math.max(snip.indexOf("<b>") -
                    charsCountAroundWord, 0);
            int endIndex = Math.min(snip.indexOf("</b>") +
                    charsCountAroundWord + 4, snip.length());
            finalSnippet
                    .append("...")
                    .append(snip, startIndex, endIndex)
                    .append("</b>")
                    .append("...")
                    .append(" ");
        }
        return finalSnippet.substring(0, Math.min(finalSnippet.length(), maxSnippetSize))
                .replaceAll("<.?h.>","");
    }

    private double getAbsoluteRelevance(Page page, Set<String> lemmas) {
        Set<Index> indices = new HashSet<>();
        List<Double> ranks = new ArrayList<>();
        double maxRank = 0;
        TreeSet<Lemma> lemmaEntities = getLemmaEntitiesByWordsAndSiteId(
                lemmas, page.getSite().getId()
        );

        lemmaEntities.forEach(lemma -> indices.add(indexRepository
                .findByPageIdAndLemmaId(page.getId(), lemma.getId())));
        if (!indices.isEmpty()) {
            ranks = new ArrayList<>(indices.stream()
                    .filter(Objects::nonNull)
                    .map(Index::getRank)
                    .map(Float::doubleValue)
                    .toList());
            maxRank = ranks.stream()
                .max(Comparator.comparingDouble(Double::doubleValue))
                .get();
        }
        double sumRanks = 0;
        for (Double rank : ranks) {
            sumRanks += rank;
        }
        return sumRanks / maxRank;
    }

    private int getMinLimit(int offset, int limit) {
        return Math.min(searchResult.size() - offset, limit);
    }
    private int getMinOffset(int offset) {
        return Math.min(searchResult.size(), offset);
    }
}
