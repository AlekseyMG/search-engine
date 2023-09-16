package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.ParserSetting;
import searchengine.dto.indexing.WebParser;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.config.SitesList;
import searchengine.dto.DefaultResponse;
import searchengine.model.StatusType;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

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
    private String pageUrl;
    private final SitesList sites;
    private final ParserSetting parserSetting;
    public volatile boolean isRunning = false;
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final SiteRepository siteRepository;
    List<Thread> threads = new ArrayList<>();
    List<WebParser> webParsers = new ArrayList<>();
    ForkJoinPool pool;

    @Override
    public DefaultResponse stopIndexing() {
//        System.out.println("************************************************************");
//        System.out.println("************           ОСТАНОВКА           *****************");
//        threads.forEach(Thread::interrupt);
//        threads.forEach(thread -> System.out.println(thread.isInterrupted()));
//        webParsers.forEach(webParser -> {
////            webParser.isRunning = false;
////            webParser.stop();
//            System.out.println("ОСТАНОВКА");
//            webParser.cancel(true);
//        });
//        isRunning = false;
//        try {
//            pool.wait(5000);
//            pool.shutdownNow();
//        } catch (Exception ex) {
//            System.out.println("ОШИБКА " + ex);
//        }
        //pool.shutdown();
//        System.out.println("************************************************************");
        isRunning = false;
//        System.out.println("1 pool.isShutdown: " + pool.isShutdown());
        while (pool.getActiveThreadCount() > 0) {}

            //pool.shutdown();
//            System.out.println("2 pool.isShutdown: " + pool.getActiveThreadCount());
//        }

        //System.out.println("pool.isShutdown");
        pool.shutdown();
        return new DefaultResponse();
    }
    @Override
    public DefaultResponse startIndexing() {
        isRunning = true;
        clearDataByUrlList();             //ВКЛЮЧИТЬ!!!
        indexingAllSitesFromConfig();     //ВКЛЮЧИТЬ!!!

//        Set<String> linksSet = new TreeSet<>(new Comparator<String>() {
//            @Override
//            public int compare(String s1, String s2) {
//                return s1.trim().compareTo(s2.trim());
//            }
//        });
//        linksSet.addAll(Arrays.asList(links.split("\n")));
//        linksSet.forEach(System.out::println);




//        Site site = new Site();
//        site.setName(sites.getSites().get(0).getName());
//        site.setUrl(sites.getSites().get(0).getUrl());
//        site.setStatus(StatusType.INDEXED);
//        site.setLastError("vse norm");
//        site.setStatusTime(LocalDateTime.now());
//        Page page = new Page();
//        page.setCode(200);
//        page.setSite(site);
//        page.setPath(site.getUrl());
//        page.setContent("fignya");
//        pageRepository.saveAndFlush(page);

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
                siteRepository.saveAndFlush(newSite);
                WebParser webParser = new WebParser(
                        site.getUrl(),
                        newSite,
                        pageRepository,
                        siteRepository,
                        site.getUrl(),
                        parserSetting,
                        this
                );
                webParsers.add(webParser);
                try {
                    //ForkJoinPool pool = new ForkJoinPool();
                    pool = new ForkJoinPool();
                    pool.invoke(webParser);
                } catch (Exception ex) {
                    newSite.setLastError("+++++" + ex.toString() + "++++++");
                    System.out.println("+++++" + ex + "++++++");
                    newSite.setStatus(StatusType.FAILED);
                } finally {
                    newSite.setStatus(StatusType.INDEXED);
                }
                //String links = pool.invoke(webParser);

                newSite.setStatusTime(LocalDateTime.now());
                siteRepository.saveAndFlush(newSite);
                //System.out.println("ГОТОВО" + links.substring(0,10));
            })//.start();
            );
        });
            threads.forEach(Thread::start);
        //    System.out.println("ГОТОВО");
        //System.out.println("ГОТОВО");
    }

    /** !! СДЕЛАНО !!
     * продумать удаление всех страниц из таблицы page по id сайта одним запросом к базе
    **/
    private void clearDataByUrlList() {
        //threads.forEach(Thread::interrupt);

        sites.getSites()
                .stream()
                .map(searchengine.config.Site::getUrl)
                .forEach(url -> {
                    String shortUrl = url
                            //.replaceAll("https://", "")
                            .replaceAll("www.", "") + "/";
//                    System.out.println(shortUrl);
//                    System.out.println("++++++++++++++\n".repeat(3));
                    //System.out.println("pageRepository.findBySitePath(url) = " + pageRepository.findByPath(url));
//                    System.out.println("pageRepository.findByPath(url) = " + pageRepository.findByPath(shortUrl));
//                    System.out.println("++++++++++++++\n".repeat(3));
                    //Page page = pageRepository.findByPath(url);
//                    System.out.println("path LIKE -");
                    //pageRepository.findAllByPath(shortUrl).forEach(page -> System.out.println("path LIKE -" + page + "\n"));
                    if (pageRepository.findByPath(shortUrl) != null) {
                        pageRepository.deleteBySitePath(shortUrl);
                    }
//                    if (siteRepository.findByPath(url) != null) {
//                        siteRepository.deleteBySitePath(url);
//                    }
//                    pageRepository.findAll().forEach(page -> {
//                        System.out.println("page " + page.getPath() + " - shortUrl " + shortUrl);
//                        if (page.getPath().contains(shortUrl)) {
//                            System.out.println("DELETE " + shortUrl);
//                            pageRepository.deleteById(page.getId());
//                        }
//                    });
                    siteRepository.findAll().forEach(site -> {
                        //System.out.println("site " + site.getUrl() + " - shortUrl " + shortUrl);

                        if (site.getUrl().contains(shortUrl)) {
                          //  System.out.println("DELETE " + shortUrl);
                            siteRepository.deleteById(site.getId());
                        }
                    });
                });
    }


}
