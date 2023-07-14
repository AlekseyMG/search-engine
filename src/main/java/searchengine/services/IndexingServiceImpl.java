package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final SiteRepository siteRepository;

    @Override
    public DefaultResponse startIndexing() {

        clearDataByUrlList();
        indexingAllSitesFromConfig();

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
        sites.getSites().forEach(site -> {
            new Thread(()-> {
                Site dataSite = new Site();
                dataSite.setName(site.getName());
                dataSite.setUrl(site.getUrl());
                dataSite.setStatus(StatusType.INDEXING);
                dataSite.setLastError("");
                dataSite.setStatusTime(LocalDateTime.now());
                siteRepository.saveAndFlush(dataSite);

                WebParser webParser = new WebParser(site.getUrl(), dataSite, pageRepository, siteRepository, new HashSet<>());
                ForkJoinPool pool = new ForkJoinPool();
                String links = pool.invoke(webParser);
                dataSite.setStatus(StatusType.INDEXED);
                dataSite.setStatusTime(LocalDateTime.now());
                siteRepository.saveAndFlush(dataSite);
                //System.out.println("ГОТОВО" + links.substring(0,10));
            }).start();
        });
    }

    private void clearDataByUrlList() {
        sites.getSites()
                .stream()
                .map(searchengine.config.Site::getUrl)
                .forEach(url -> {
                    String shortUrl = url
                            .replaceAll("https://", "")
                            .replaceAll("www.", "");
                    pageRepository.findAll().forEach(page -> {
                        if (page.getPath().contains(shortUrl)) {
                            pageRepository.deleteById(page.getId());
                        }
                    });
                    siteRepository.findAll().forEach(site -> {
                        if (site.getUrl().contains(shortUrl)) {
                            siteRepository.deleteById(site.getId());
                        }
                    });
                });
    }


}
