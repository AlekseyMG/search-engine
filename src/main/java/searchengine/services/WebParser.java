package searchengine.services;

import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.dao.DataIntegrityViolationException;
import searchengine.config.ParserSetting;
import searchengine.dto.ErrorMessages;
import searchengine.dto.statistics.LemmaCount;
import searchengine.model.*;
import searchengine.model.Index;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveTask;

public class WebParser extends RecursiveTask<String> {
    private final String absolutePath;
    private final String relativePath;
    private final Site currentSiteEntity;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final String siteUrl;
    private final ParserSetting parserSetting;
    private final IndexingServiceImpl indexingServiceImpl;
    private final int siteId;
    List<WebParser> subTasks = new LinkedList<>();
    int statusCode = 0;
//    @PersistenceContext
//    private EntityManagerFactory emf;

    public WebParser(String absolutePath, Site currentSiteEntity, IndexingServiceImpl indexingServiceImpl) {
        this.absolutePath = absolutePath.replaceAll("www.", "");
        this.relativePath = absolutePath.replaceAll("www.", "").replaceAll(
                currentSiteEntity.getUrl().replaceAll("www.", ""), "")
                .isEmpty() ? "/" : absolutePath.replaceAll(
                        currentSiteEntity.getUrl().replaceAll("www.", ""), "");
        this.currentSiteEntity = currentSiteEntity;
        this.pageRepository = indexingServiceImpl.getPageRepository();
        this.siteRepository = indexingServiceImpl.getSiteRepository();
        this.lemmaRepository = indexingServiceImpl.getLemmaRepository();
        this.indexRepository = indexingServiceImpl.getIndexRepository();
        this.siteUrl = currentSiteEntity.getUrl().replaceAll("www.", "");
        this.parserSetting = indexingServiceImpl.getParserSetting();
        this.indexingServiceImpl = indexingServiceImpl;
        this.siteId = currentSiteEntity.getId();
    }

    @Override
    protected String compute() {
        if (!indexingServiceImpl.isRunning()) {
            stop();
            return "";
        }

        Set<String> links = saveCurrentPageAndGetLinks();

//        if (!indexingServiceImpl.isRunning) {
//            stop();
//            return "";
//        }

        if (links.isEmpty()) {
            return "";
        }

        for (String link : links) {
            WebParser task = new WebParser(link, currentSiteEntity, indexingServiceImpl);
            task.fork();
            subTasks.add(task);
        }
        links.clear();
        for (WebParser task : subTasks) {
            task.join();
        }
        return "";
    }

    public int updateOnePage() {
        saveCurrentPageAndGetLinks();
        return statusCode;
    }
    private Set<String> saveCurrentPageAndGetLinks() {
        Set<String> links = new HashSet<>();
//        String url = "";
        Connection.Response response = null;
        String errorMessage = "";
        Document htmlDoc = new Document("");

        if (absolutePath.contains(".pdf") || absolutePath.contains("#")) {
            return new HashSet<>();
        }
        try {
            Thread.sleep((int) (Math.random() *
                    parserSetting.getRandomDelayDeltaBeforeConnection()) +
                    parserSetting.getMinDelayBeforeConnection());

            System.out.println("Идем по ссылке - " + absolutePath);

            Connection connection = Jsoup.connect(absolutePath);
            response = connection
                    .userAgent(parserSetting.getUserAgent())
                    .referrer(parserSetting.getReferrer())
                    .timeout(parserSetting.getConnectionTimeout())
                    .ignoreHttpErrors(true)
                    .execute();

            htmlDoc = connection.get();
            //statusCode = response == null ? 0 : response.statusCode();
            statusCode = response.statusCode();

//            Elements htmlLinks = htmlDoc.select("a[href]");
//
//            for (Element link : htmlLinks) {
//                url = link.attr("abs:href").replaceAll("www.","").toLowerCase();
//
//                if (url.contains(siteUrl) &&
//                        !url.equals(absolutePath) &&
//                        !url.contains("#") &&
//                        !url.contains("@") &&
//                        !url.contains(".com") &&
//                        !url.contains(".pdf") &&
//                        !url.contains(".php") &&
//                        !url.contains(".png") &&
//                        !url.contains(".jpg") &&
//                        !url.contains(".jpeg") &&
//                        !url.contains(".gif") &&
//                        !url.contains("upload") &&
//                        !url.contains("img") &&
//                        !url.contains("image") //&&
//                ) {
//                    if (pageRepository.findBySiteIdAndPath(siteId, url.replaceAll(siteUrl,"")) == null) {
//                        savePage(0, "", url.replaceAll(siteUrl,""));
//                        links.add(url);
//                    }
//                }
//
//            }

            links = getLinksFromHTML(htmlDoc);

        } catch (SocketTimeoutException ex) {
            if (response != null) {
                errorMessage = response.statusMessage().equals("OK") ? "" : response.statusMessage() + " ";
                statusCode = response.statusCode();
            }
            if (ex.toString().contains("Connect timed out")) {
                statusCode = 522;
            }
            if (ex.toString().contains("Read timed out")) {
                statusCode = 598;
            }

            errorMessage =  ErrorMessages.connectTimedOut + absolutePath;

            if (absolutePath.equals(siteUrl + "/")) {
                currentSiteEntity.setStatus(StatusType.FAILED);
            }

            System.out.println(errorMessage);

        } catch (InterruptedException ex) {
            errorMessage = ErrorMessages.abortedByUser;
            System.out.println(errorMessage);

        } catch (IOException ex) {
            errorMessage = ErrorMessages.ioOrNotFound + absolutePath;

            if (absolutePath.equals(siteUrl)) {
                currentSiteEntity.setStatus(StatusType.FAILED);
            }
            System.out.println(errorMessage);

        } catch (DataIntegrityViolationException ex) {
            errorMessage = ErrorMessages.errorAddEntityToDB + (ex.toString().contains("Duplicate") ? " (дубликат)" : "");
            System.out.println(errorMessage);
        }
        //String currentPagePath = absolutePath.equals(siteUrl + "/") ? "/" : absolutePath.replaceAll(siteUrl,"");
        savePage(statusCode, htmlDoc.html(), relativePath);
        updateSiteStatus(errorMessage);
//
//        htmlDoc = null;
//        response = null;
//        errorMessage = "";
//
        return links;
    }

    private void updateSiteStatus(String errorMessage) {
        if (!errorMessage.equals("")) {
            currentSiteEntity.setLastError(errorMessage);
        }
        currentSiteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.saveAndFlush(currentSiteEntity);
    }

    private Set<String> getLinksFromHTML(Document htmlDoc) {
//        String[] skipList = new String[]{"#", "@", ".com", ".pdf", ".php",
//                ".png", ".jpg", ".jpeg", ".gif", "upload", "img", "image"};
        Set<String> links = new HashSet<>();
        String parsedLinkAbsolutePath;
        String parsedLinkRelativePath;
        Elements htmlLinks = htmlDoc.select("a[href]");

        for (Element link : htmlLinks) {
            parsedLinkAbsolutePath = link.attr("abs:href").replaceAll("www.","").toLowerCase();
            parsedLinkRelativePath = parsedLinkAbsolutePath.replaceAll(siteUrl,"");
            if (parsedLinkAbsolutePath.contains(siteUrl) &&
                    !parsedLinkAbsolutePath.equals(absolutePath) &&
                    !indexingServiceImpl.isMatchedWithSkipList(parsedLinkAbsolutePath) //&&
//                    !parsedLinkAbsolutePath.contains("#") &&
//                    !parsedLinkAbsolutePath.contains("@") &&
//                    !parsedLinkAbsolutePath.contains(".com") &&
//                    !parsedLinkAbsolutePath.contains(".pdf") &&
//                    !parsedLinkAbsolutePath.contains(".php") &&
//                    !parsedLinkAbsolutePath.contains(".png") &&
//                    !parsedLinkAbsolutePath.contains(".jpg") &&
//                    !parsedLinkAbsolutePath.contains(".jpeg") &&
//                    !parsedLinkAbsolutePath.contains(".gif") &&
//                    !parsedLinkAbsolutePath.contains("upload") &&
//                    !parsedLinkAbsolutePath.contains("img") &&
//                    !parsedLinkAbsolutePath.contains("image") //&&
            ) {
                if (pageRepository.findBySiteIdAndPath(siteId, parsedLinkRelativePath) == null) {
                    savePage(0, "", parsedLinkRelativePath);
                    links.add(parsedLinkAbsolutePath);
                }
            }

        }
        return links;
    }

//    private boolean isNotMatchedWithSkipList(String linkAbsolutePath, String[] skipList) {
//        for (String skipString : skipList) {
//            if (linkAbsolutePath.contains(skipString)) {
//                return false;
//            }
//        }
//        return true;
//    }

    private void savePage(int statusCode, String content, String pagePath) {
        Page page = pageRepository.findBySiteIdAndPath(siteId, pagePath);

        if (page == null) {
            page = new Page();
        }

        page.setCode(statusCode);
        page.setSite(currentSiteEntity);
        page.setContent(content);
        page.setPath(pagePath);
        pageRepository.saveAndFlush(page);

        if (statusCode == 200) {
            saveLemma(page, content);
        }
//        page = null;
    }

    private void saveLemma(Page page, String content) {
        try {
//            ArrayList<Index> indexList = new ArrayList<>();
//            StringBuilder indexInsertQuery = new StringBuilder();
            LemmaFinder lemmaFinder = new LemmaFinder(new RussianLuceneMorphology());
            lemmaFinder.collectLemmasFromHTML(content).forEach((normalWord, integer) -> {
                Lemma lemma = lemmaRepository.findBySiteIdAndLemma(currentSiteEntity.getId(), normalWord);

                if (lemma == null) {
                    lemma = new Lemma();
                    lemma.setFrequency(0);
                }
//
//                Index index = indexRepository.findByPageIdAndLemmaId(page.getId(), lemma.getId());
//
//                if (index == null) {
//                    Index index = new Index();
//                }
//
                lemma.setLemma(normalWord);
                lemma.setSite(currentSiteEntity);
                lemma.setFrequency(lemma.getFrequency() + 1);
//
//                indexInsertQuery.append(indexInsertQuery.length() == 0 ? "" : ",")
//                        .append("('")
//                        .append(page)
//                        .append("', '")
//                        .append(lemma)
//                        .append("', ")
//                        .append(integer)
//                        .append(")");

//                if (indexInsertQuery.length()) {
//                    indexRepository.insertData(indexInsertQuery.toString());
//                    indexInsertQuery = new StringBuilder();
//                }
//
                lemmaRepository.saveAndFlush(lemma);
                saveIndex(page, lemma, integer);
                LemmaCount.increase(siteId, integer);

            });
//
//            EntityManager em = emf.createEntityManager();
//
//            Query query = em.createNativeQuery("INSERT INTO `index` (`id`, `lemma_id`, `page_id`, `rank`) VALUES (`1`, `1`, `1`, `1`)");
//            em.getTransaction().begin();
//            query.executeUpdate();
//            em.getTransaction().commit();
//            em.close();
//
//            ArrayList<Integer> list1 = new ArrayList<>();
//            list1.add(1);
//            list1.add(2);
//            list1.add(1);
//
//            List<IndexItem> indexItems = new ArrayList<>();
//            IndexItem indexItem = new IndexItem();
//            indexItem.setPageId(2);
//            indexItem.setLemmaId(88);
//            indexItem.setRank((float) 0.9);
//            indexItems.add(indexItem);
//            //indexInsertQuery.append("(3, 88, 1.0)");
//            JsonArray jsonValues = new JsonArray();
//            jsonValues.add(0, new JsonString().setValue("(1,1,0.9"));

            //indexRepository.insertData("\"(1,1,0.9)\"");



//            for (Index index : indexList) {
//                indexInsertQuery.append(indexInsertQuery.length() == 0 ? "" : ",")
//                        .append("(`")
//                        .append(page.getId())
//                        .append("`, `")
//                        .append(index.getLemma().getId())
//                        .append("`, `")
//                        .append(index.getRank())
//                        .append("`)");
//                if (indexInsertQuery.length() > 50_000_000) {
//                    indexRepository.insertData(indexInsertQuery + ";");
//                    indexInsertQuery = new StringBuilder();
//                }
//            }
//            indexRepository.insertData(indexInsertQuery + ";");

//            indexRepository.saveAllAndFlush(indexList);
//
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void saveIndex(Page page, Lemma lemma, int integer) {
        Index index = new Index();
        index.setPage(page);
        index.setLemma(lemma);
        index.setRank(integer);
        indexRepository.saveAndFlush(index);
    }

    public void deleteLemma(String url) {
        Page page = pageRepository.findBySiteIdAndPath(siteId, url.replaceAll(siteUrl,""));

        System.out.println("Пытаемся удалить: " + url);
        System.out.println("Ищем страницу с путем: " + url.replaceAll(siteUrl,""));

        if (page != null) {

            System.out.println("Удаляем страницу: " + url);

            HashSet<Index> indexes = indexRepository.findByPageId(page.getId());
            indexRepository.deleteByPageId(page.getId());

            indexes.forEach(index -> {
                Lemma lemma = index.getLemma();
                int frequency = lemma.getFrequency() - 1;
                if (frequency < 1) {
                    lemmaRepository.delete(lemma);
                } else {
                    lemma.setFrequency(frequency);
                    lemmaRepository.saveAndFlush(lemma);
                }
                LemmaCount.decrease(siteId, 1);
            });

        }
    }
    private void stop() {
        currentSiteEntity.setStatus(StatusType.FAILED);
        currentSiteEntity.setLastError("Прервано пользователем");
        siteRepository.saveAndFlush(currentSiteEntity);
        savePage(statusCode, "", relativePath);
    }
}
