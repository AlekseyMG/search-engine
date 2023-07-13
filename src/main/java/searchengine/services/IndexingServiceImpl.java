package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.ErrorResponse;
import searchengine.model.Site;
import searchengine.config.SitesList;
import searchengine.dto.DefaultResponse;
import searchengine.model.Page;
import searchengine.model.StatusType;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;

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
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final SiteRepository siteRepository;
    @Override
    public DefaultResponse startIndexing() {
        sites.getSites().forEach(site -> System.out.println(site.getUrl()));
//        sites.getSites().get(0).getUrl();
//        Site siteForDelete = siteRepository.findAll().get(0);

        Site site = new Site();
        site.setName(sites.getSites().get(0).getName());
        site.setUrl(sites.getSites().get(0).getUrl());
        site.setStatus(StatusType.INDEXED);
        site.setLastError("vse norm");
        site.setStatusTime(LocalDateTime.now());
        Page page = new Page();
        page.setCode(200);
        page.setSite(site);
        page.setPath(site.getUrl());
        page.setContent("fignya");
        pageRepository.saveAndFlush(page);

//        return new DefaultResponse();
        return new ErrorResponse("ошика 111");

    }
}
