package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@Getter
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    public static final String[] EXCEPTION_LINKS = new String[]{"#", "@", ".com", ".pdf", ".php",
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
        log.info("Запрос пользователя на останавку индексации");
        isStoppedByUser = true;
        while (pool.getActiveThreadCount() > 0) {}
        try {
            pool.shutdown();
            threads.forEach(Thread::interrupt);
        } catch (Exception ex) {
            log.error(ErrorMessages.STOPPING_ERROR + ex);
            System.out.println(ErrorMessages.STOPPING_ERROR + ex);
        }
        batchIndexWriter.close();
        log.info("Индексация остановлена пользователем");
        return new DefaultResponse();
    }
    @Override
    public DefaultResponse startIndexing() {
        log.info("Запускаем полную индексацию");
////------------------------------------------------------------------// Отключена кнопка Start Indexing,
//        if (isStoppedByUser) {                                      // чтобы всякие хулиганы не запускали
//            return new ErrorResponse("Не трогай эту кнопку!"); //  долгую полную индексацию.
//        }                                                           // Закоментируйте этот код, чтобы включить
////------------------------------------------------------------------// кнопку обратно и запустить индексацию.
        for (Thread thread : threads) {
            if (thread.isAlive()) {
                log.error(ErrorMessages.INDEXING_HAS_ALREADY_STARTED);
                return new ErrorResponse(ErrorMessages.INDEXING_HAS_ALREADY_STARTED);
            }
        }
        isStoppedByUser = false;
        clearDataByUrlList();
        indexingAllSitesFromConfig();

        return new DefaultResponse();
    }
    @Override
    public DefaultResponse indexPage(String absolutePath) {
        log.info("Запускаем индексацию страницы " + absolutePath);
        if (isMatchedWithSkipList(absolutePath)) {
            log.error(ErrorMessages.INVALID_CHARACTERS_IN_THE_ADDRESS);
            return new ErrorResponse(ErrorMessages.INVALID_CHARACTERS_IN_THE_ADDRESS);
        }
        List<Site> sites = siteRepository.findAll().stream().filter(site ->
                absolutePath.replaceAll("www.","").contains(
                        site.getUrl().replaceAll("www.",""))).toList();

        if (sites.isEmpty()) {
            log.error(ErrorMessages.OUT_OF_SITE);
            return new ErrorResponse(ErrorMessages.OUT_OF_SITE);
        }

        Site site = sites.get(0);
        AtomicInteger statusCode = new AtomicInteger(0);

        new  Thread(()-> {
            WebParser webParser = new WebParser(absolutePath, site,this);
            webParser.deletePageFromRepository(absolutePath);
            statusCode.set(webParser.updateOnePage());
           }).start();

        while (statusCode.get() == 0) {}
        if (statusCode.get() != 200) {
            log.error(ErrorMessages.PAGE_IS_NOT_AVAILABLE);
            return new ErrorResponse(ErrorMessages.PAGE_IS_NOT_AVAILABLE);
        }
        batchIndexWriter.close();
        log.info("Страница проиндексирована");
        return new DefaultResponse();
    }

    private void indexingAllSitesFromConfig() {
        if (!threads.isEmpty()) {
            threads.forEach(Thread::interrupt);
            threads = new ArrayList<>();
        }
        settingSites.getSites().forEach(settingSite ->
            threads.add(new Thread(()-> {
                    Site newSite = getNewSiteEntity(settingSite);

                    WebParser webParser = new WebParser(
                            settingSite.getUrl(),
                            newSite,
                            this);
                    runPool(webParser, newSite);
                })
            )
        );
        threads.forEach(Thread::start);
    }

    private void runPool(WebParser webParser, Site newSite) {
        int parallelism = Math.max(1, parserSetting.getCpuForPool() - settingSites.getSites().size());

        try {
            pool = new ForkJoinPool(parallelism);
            pool.invoke(webParser);
        } catch (NullPointerException ex) {
            newSite.setLastError(ErrorMessages.IO_OR_NOT_FOUND);
            log.error(ErrorMessages.IO_OR_NOT_FOUND);
            log.debug(ErrorMessages.IO_OR_NOT_FOUND, ex);
            newSite.setStatus(StatusType.FAILED);
        } catch (DataIntegrityViolationException ex) {
            String error = ErrorMessages.ERROR_ADD_ENTITY_TO_DB +
                    (ex.toString().contains("Duplicate") ? " (дубликат)" : "");
            newSite.setLastError(error);
            log.error(error);
            log.debug(error, ex);
        } catch (Exception ex) {
            newSite.setLastError(ErrorMessages.UNKNOWN_INDEXING_ERROR + ex);
            log.error(ErrorMessages.UNKNOWN_INDEXING_ERROR + ex);
            log.debug(ErrorMessages.UNKNOWN_INDEXING_ERROR, ex);
            newSite.setStatus(StatusType.FAILED);
        }
        finally {
            if (newSite.getStatus().equals(StatusType.INDEXING) && !isStoppedByUser) {
                log.info("Сайт " + newSite.getName() + " проиндексирован.");
                newSite.setStatus(StatusType.INDEXED);
            }
        }
        newSite.setStatusTime(LocalDateTime.now());
        siteRepository.saveAndFlush(newSite);
        batchIndexWriter.close();
    }
    private void clearDataByUrlList() {
        List<Site> repositorySites = siteRepository.findAll();
        settingSites.getSites()
                .stream()
                .map(searchengine.config.Site::getUrl)
                .forEach(url -> {
                    String shortUrl = url.replaceAll("https://", "")
                                         .replaceAll("www.", "");

                    repositorySites.forEach(repositorySite -> {
                        if (repositorySite.getUrl().contains(shortUrl)) {
                            deleteSiteInfo(repositorySite);
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

    private void deleteSiteInfo(Site site) {
        log.info("Удаляем все для сайта №" + site.getId() +
                " " + site.getName());

        log.info("Удаляем из таблицы index");
        pageRepository.findPagesBySiteId(site.getId())
                .forEach(page -> indexRepository.deleteByPageId(page.getId()));

        log.info("Удаляем из таблицы lemma");
        lemmaRepository.deleteBySiteId(site.getId());

        log.info("Удаляем из таблицы page");
        pageRepository.deleteBySiteId(site.getId());

        log.info("Удаляем из таблицы site");
        siteRepository.deleteBySiteId(site.getId());
    }

    public boolean isMatchedWithSkipList(String linkAbsolutePath) {
        for (String skipString : EXCEPTION_LINKS) {
            if (linkAbsolutePath.contains(skipString)) {
                return true;
            }
        }
        return false;
    }

}
