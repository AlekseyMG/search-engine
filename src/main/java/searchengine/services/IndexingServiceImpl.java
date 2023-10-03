package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import searchengine.config.ParserSetting;
import searchengine.api.response.ErrorResponse;
import searchengine.dto.ErrorMessages;
import searchengine.dto.statistics.LemmaCount;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
//брать из конфигурации приложения список сайтов и по каждому сайту:

    //удалять все имеющиеся данные по этому сайту (записи из таблиц
//site и page);
//○ создавать в таблице site новую запись со статусом INDEXING;
//○ обходить все страницы, начиная с главной, добавлять их адреса,
//статусы и содержимое в базу данных в таблицу page;
//○ в процессе обхода постоянно обновлять дату и время в поле
//status_time таблицы site на текущее;
//○ по завершении обхода изменять статус (поле status) на INDEXED;
//○ если произошла ошибка и обход завершить не удалось, изменять
//статус на FAILED и вносить в поле last_error понятную
//информацию о произошедшей ошибке

    private final SitesList sites;
    private final ParserSetting parserSetting;
    public volatile boolean isRunning = false;
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final LemmaRepository lemmaRepository;
    @Autowired
    private final IndexRepository indexRepository;
    List<Thread> threads = new ArrayList<>();
    ForkJoinPool pool;

    @Override
    public DefaultResponse stopIndexing() {
        isRunning = false;
        while (pool.getActiveThreadCount() > 0) {}
        pool.shutdown();
        threads.forEach(Thread::interrupt);

        return new DefaultResponse();
    }
    @Override
    public DefaultResponse startIndexing() {
        if (isRunning) {
            return new ErrorResponse("Индексация уже запущена");
        }
        isRunning = true;
        clearDataByUrlList();             //ВКЛЮЧИТЬ!!!
        indexingAllSitesFromConfig();     //ВКЛЮЧИТЬ!!!

        return new DefaultResponse();
//        return new ErrorResponse("ошика 111");

    }
    @Override
    public DefaultResponse indexPage(String url) {
        if (isRunning) {
            return new ErrorResponse("Индексация уже запущена");
        }
//        siteRepository.findAll().stream().forEach(sites -> System.out.println(sites.getUrl()));
//        siteRepository.findAll().stream().filter(sites ->
//                        url.contains(sites.getUrl().replaceAll("www.",""))).forEach(System.out::println);
        List<Site> sites = siteRepository.findAll().stream().filter(site ->
                url.contains(site.getUrl().replaceAll("www.",""))).toList();
        if (sites.isEmpty()) {
            return new ErrorResponse("Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле");
        }
        Site site = sites.get(0);
//        System.out.println(sites.get(0).getUrl());
        AtomicInteger statusCode = new AtomicInteger(0);
        new  Thread(()-> {
            WebParser webParser = new WebParser(
                    url,
                    site,
                    pageRepository,
                    siteRepository,
                    lemmaRepository,
                    indexRepository,
                    site.getUrl(),
                    parserSetting,
                    this
            );
            webParser.deleteLemma(url);
            statusCode.set(webParser.updateCurrentPage());
                        //new ErrorResponse("Страница недоступна");

        }).start();
        while (statusCode.get() == 0) {}

//        System.out.println(url);
        if (statusCode.get() != 200) {
            return new ErrorResponse("Страница недоступна");
        }
        return new DefaultResponse();
//        return new ErrorResponse("ошика 111");

    }

    private void indexingAllSitesFromConfig() {
        if (!threads.isEmpty()) {
            threads.forEach(Thread::interrupt);
            threads = new ArrayList<>();
        }
        sites.getSites().forEach(site -> {
            threads.add(
                new Thread(()-> {
                    Site newSite = new Site();
                    newSite.setName(site.getName());
                    newSite.setUrl(site.getUrl());
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
                    LemmaCount.addSiteId(newSite.getId());
                    WebParser webParser = new WebParser(
                            site.getUrl() + "/",
                            newSite,
                            pageRepository,
                            siteRepository,
                            lemmaRepository,
                            indexRepository,
                            site.getUrl(),
                            parserSetting,
                            this
                    );
                    try {
                        pool = new ForkJoinPool(8);
                        pool.invoke(webParser);
                    } catch (NullPointerException ex) {
                        newSite.setLastError(ErrorMessages.ioOrNotFound);
                        System.out.println("+++++ " + ex + " ++++++");
                        newSite.setStatus(StatusType.FAILED);
                    } catch (DataIntegrityViolationException ex) {
                        newSite.setLastError(ErrorMessages.errorAddEntityToDB + (ex.toString().contains("Duplicate") ?
                                " (дубликат)" : ""));
                    } catch (Exception ex) {
                        newSite.setLastError(ErrorMessages.unknownIndexingError + ex);
                        System.out.println("+++++ " + ex + " ++++++");
                        newSite.setStatus(StatusType.FAILED);
                    }
                    finally {
                        if (newSite.getStatus().equals(StatusType.INDEXING) && isRunning) {
                            newSite.setStatus(StatusType.INDEXED);
                        }
                    }
                    newSite.setStatusTime(LocalDateTime.now());
                    siteRepository.saveAndFlush(newSite);
                })
            );
        });

        threads.forEach(Thread::start);
    }

    private void clearDataByUrlList() {
        indexRepository.deleteAll();
        sites.getSites()
                .stream()
                .map(searchengine.config.Site::getUrl)
                .forEach(url -> {
                    String shortUrl = url
                            .replaceAll("https://", "")
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


}
