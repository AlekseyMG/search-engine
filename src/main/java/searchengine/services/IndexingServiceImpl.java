package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import searchengine.config.ParserSetting;
import searchengine.api.response.ErrorResponse;
import searchengine.dto.ErrorMessages;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.config.SitesList;
import searchengine.api.response.DefaultResponse;
import searchengine.model.StatusType;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Getter
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    public static final String[] SKIP_LIST = new String[]{"#", "@", ".com", ".pdf", ".php",
                                ".png", ".jpg", ".jpeg", ".gif", "upload", "img", "image"};
    private final SitesList settingSites;
    private final ParserSetting parserSetting;
    public volatile boolean isStoppedByUser = true;
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final LemmaRepository lemmaRepository;
    @Autowired
    private final IndexRepository indexRepository;
    private final BatchIndexWriter batchIndexWriter;
    List<Thread> threads = new ArrayList<>();
    ForkJoinPool pool;

    @Override
    public DefaultResponse stopIndexing() {
        isStoppedByUser = true;
        while (pool.getActiveThreadCount() > 0) {}
        try {
            pool.shutdown();
            threads.forEach(Thread::interrupt);
        } catch (Exception ex) {
            System.out.println("Ошибка остановки: " + ex);
        }
        batchIndexWriter.close();

        return new DefaultResponse();
    }
    @Override
    public DefaultResponse startIndexing() {
////------------------------------------------------------------------// Отключена кнопка Start Indexing,
//        if (isStoppedByUser) {                                      // чтобы всякие хулиганы не запускали
//            return new ErrorResponse("Не трогай эту кнопку!"); //  долгую полную индексацию.
//        }                                                           // Закоментируйте этот код, чтобы включить
////------------------------------------------------------------------// кнопку обратно и запустить индексацию.
        for (Thread thread : threads) {
            if (thread.isAlive()) {
                return new ErrorResponse("Индексация уже запущена");
            }
        }
        isStoppedByUser = false;
        clearDataByUrlList();             //ВКЛЮЧИТЬ!!!
        indexingAllSitesFromConfig();     //ВКЛЮЧИТЬ!!!

        return new DefaultResponse();
    }
    @Override
    public DefaultResponse indexPage(String absolutePath) {
        if (isMatchedWithSkipList(absolutePath)) {
            return new ErrorResponse("Адрес содержит недопустимые символы");
        }
        List<Site> sites = siteRepository.findAll().stream().filter(site ->
                absolutePath.replaceAll("www.","").contains(
                        site.getUrl().replaceAll("www.",""))).toList();

        if (sites.isEmpty()) {
            return new ErrorResponse("Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле");
        }

        Site site = sites.get(0);
        AtomicInteger statusCode = new AtomicInteger(0);

        new  Thread(()-> {
            WebParser webParser = new WebParser(absolutePath, site,this);
            webParser.deleteLemma(absolutePath);
            statusCode.set(webParser.updateOnePage());
           }).start();

        while (statusCode.get() == 0) {}
        if (statusCode.get() != 200) {
            return new ErrorResponse("Страница недоступна");
        }
        return new DefaultResponse();
    }

    private void indexingAllSitesFromConfig() {
        if (!threads.isEmpty()) {
            threads.forEach(Thread::interrupt);
            threads = new ArrayList<>();
        }
        settingSites.getSites().forEach(settingSite -> {
            threads.add(
                new Thread(()-> {
                    Site newSite = getNewSiteEntity(settingSite);
                    WebParser webParser = new WebParser(
                            settingSite.getUrl(),
                            newSite,
                            this);
                    try {
                        pool = new ForkJoinPool(8);
                        pool.invoke(webParser);
                    } catch (NullPointerException ex) {
                        newSite.setLastError(ErrorMessages.ioOrNotFound);
                        System.out.println("+++++ " + ex + " ++++++");
                        newSite.setStatus(StatusType.FAILED);
                    } catch (DataIntegrityViolationException ex) {
                        newSite.setLastError(ErrorMessages.errorAddEntityToDB +
                                (ex.toString().contains("Duplicate") ?
                                " (дубликат)" : ""));
                    } catch (Exception ex) {
                        newSite.setLastError(ErrorMessages.unknownIndexingError + ex);
                        System.out.println("+++++ " + ex + " ++++++");
                        newSite.setStatus(StatusType.FAILED);
                    }
                    finally {
                        if (newSite.getStatus().equals(StatusType.INDEXING) && !isStoppedByUser) {
                            newSite.setStatus(StatusType.INDEXED);
                        }
                    }
                    newSite.setStatusTime(LocalDateTime.now());
                    siteRepository.saveAndFlush(newSite);
                    batchIndexWriter.close();
                })
            );
        });
        threads.forEach(Thread::start);
    }

    private void clearDataByUrlList() {
        indexRepository.deleteAll();
        settingSites.getSites()
                .stream()
                .map(searchengine.config.Site::getUrl)
                .forEach(url -> {
                    String shortUrl = url.replaceAll("https://", "")
                                         .replaceAll("www.", "");
                    siteRepository.findAll().forEach(site -> {
                        if (site.getUrl().contains(shortUrl)) {
                            lemmaRepository.deleteBySiteId(site.getId());
                            pageRepository.deleteBySiteId(site.getId());
                            siteRepository.deleteById(site.getId());
                        }
                    });
                });
        pageRepository.resetIdCounter();
        siteRepository.resetIdCounter();
        indexRepository.resetIdCounter();
        lemmaRepository.resetIdCounter();
    }

    private Site getNewSiteEntity(searchengine.config.Site settingSite) {
        Site newSite = new Site();
        newSite.setName(settingSite.getName());
        newSite.setUrl(settingSite.getUrl());
        newSite.setStatus(StatusType.INDEXING);
        newSite.setLastError("");
        newSite.setStatusTime(LocalDateTime.now());

        Page page = new Page();
        page.setSite(newSite);
        page.setCode(0);
        page.setContent("");
        page.setPath("/");

        siteRepository.saveAndFlush(newSite);
        pageRepository.saveAndFlush(page);

        return newSite;
    }
    public boolean isMatchedWithSkipList(String linkAbsolutePath) {
        for (String skipString : SKIP_LIST) {
            if (linkAbsolutePath.contains(skipString)) {
                return true;
            }
        }
        return false;
    }

}
