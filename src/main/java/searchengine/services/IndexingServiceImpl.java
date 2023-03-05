package searchengine.services;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.JsoupSession;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexPageResponse;
import searchengine.dto.indexing.StartIndexingResponse;
import searchengine.dto.indexing.StopIndexingResponse;
import searchengine.exceptions.IndexPageIsNotPossible;
import searchengine.exceptions.StartIsNotPossible;
import searchengine.exceptions.StopIsNotPossible;

import searchengine.model.SiteEntity;
import searchengine.model.StatusType;
import searchengine.repositories.PageDAO;
import searchengine.repositories.SiteDAO;

import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sites;
    private final SiteDAO siteDAO;
    private final PageDAO pageDAO;
    private final JsoupSession jsoupSession;
    private ForkJoinPool startPool;

    @Override
    public StartIndexingResponse startIndexing() {

        if (siteDAO.containsByStatus(StatusType.INDEXING)) {
            throw new StartIsNotPossible("Индексация уже запущена");
        }

        startPool = new ForkJoinPool();

        System.out.println("pathSearcher start");
        sites.getSites().forEach(site -> startParsingPagesSite(site, startPool));



        StartIndexingResponse indexingResponse = new StartIndexingResponse();
        indexingResponse.setResult(true);
        return indexingResponse;
    }

    @Override
    public StopIndexingResponse stopIndexing() {

        if (!siteDAO.containsByStatus(StatusType.INDEXING)) {
            throw new StopIsNotPossible("Индексация не запущена");
        }
        do {
            startPool.shutdownNow();
        } while (siteDAO.containsByStatus(StatusType.INDEXING));

        System.out.println("pathSearcher stop");

        StopIndexingResponse stopIndexingResponse = new StopIndexingResponse();
        stopIndexingResponse.setResult(true);
        return stopIndexingResponse;
    }

    @Override
    public IndexPageResponse indexPage(String url) {
        SiteEntity findSite = findSiteByUrl(url);

        if (findSite == null) {
            throw new IndexPageIsNotPossible("Данная страница находится за пределами сайтов," +
                    "указанных в конфигурационном файле");
        }

        ForkJoinPool indexPool = new ForkJoinPool();

        startParsingPageSite(url, findSite, indexPool);

        IndexPageResponse indexPageResponse = new IndexPageResponse();
        indexPageResponse.setResult(true);
        return indexPageResponse;
    }

    private void startParsingPagesSite(Site site, ForkJoinPool pool) {

        SiteEntity findSiteEntity = siteDAO
                .findSiteByUrl(site.getUrl());
        if (findSiteEntity != null) {
            siteDAO.deleteById(findSiteEntity.getId());
        }

        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setName(site.getName());
        siteEntity.setUrl(site.getUrl());
        siteEntity.setStatus(StatusType.INDEXING);
        siteEntity.setStatusTime(new Date());
        siteDAO.save(siteEntity);

        PathSearcher pathSearcher = createPathSearcher("/", siteEntity,
                PathSearcher.TypeOfParsing.PAGES);
        pool.execute(pathSearcher);

        updateSiteAfterIndexing(siteEntity, pathSearcher , startPool);
    }

    private PathSearcher createPathSearcher(String starPath, SiteEntity siteEntity,
                                            PathSearcher.TypeOfParsing type) {

        HashMap<String, Object> pathSearcherData = new HashMap<>(){{
            put(SiteEntity.class.getSimpleName(), siteEntity);
            put(PageDAO.class.getSimpleName(), pageDAO);
            put(SiteDAO.class.getSimpleName(), siteDAO);
            put(JsoupSession.class.getSimpleName(), jsoupSession);
        }};

        return new PathSearcher(starPath,
                pathSearcherData, type);
    }

    private void updateSiteAfterIndexing(SiteEntity siteEntity,
                                         PathSearcher pathSearcher, ForkJoinPool pool) {
        new Thread(() -> {
            do {

            } while (!pathSearcher.isDone() && !pool.isTerminated());
            if (pool.isShutdown()) {
                updateSite(siteEntity, StatusType.FAILED, "Индексация остановлена пользователем");
            }
            if (!pool.isShutdown() && pathSearcher.getException() != null) {
                updateSite(siteEntity, StatusType.FAILED, pathSearcher.getException().toString());
            }
            if (!pool.isShutdown() && pathSearcher.getException() == null) {
                updateSite(siteEntity, StatusType.INDEXED, null);
            }
        }).start();
    }
    private void updateSite(SiteEntity entity,StatusType status, String lastError) {
        entity.setLastError(lastError);
        entity.setStatus(status);
        entity.setStatusTime(new Date());
        siteDAO.updateSiteByStatusErrorStatusTime(entity);
    }

    private SiteEntity findSiteByUrl(String url){
        String regexHomeUrl = "https?://[^,\\s/]+";
        Pattern pattern = Pattern.compile(regexHomeUrl);
        Matcher matcher = pattern.matcher(url);
        String regexUrl = "https?://[^,\\s]+";
        return matcher.find() && url.matches(regexUrl) ?
                siteDAO.findSiteByUrl(matcher.group()) : null;
    }

    private void startParsingPageSite(String url, SiteEntity site, ForkJoinPool pool) {
        String path = url.replaceAll(site.getUrl(),"");

        PathSearcher pathSearcher = createPathSearcher(path,
                site, PathSearcher.TypeOfParsing.PAGE);

        pool.execute(pathSearcher);

    }
}
