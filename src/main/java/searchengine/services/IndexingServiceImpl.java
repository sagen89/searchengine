package searchengine.services;


import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexPageResponse;
import searchengine.dto.indexing.StartIndexingResponse;
import searchengine.dto.indexing.StopIndexingResponse;
import searchengine.exceptions.IndexPageIsNotPossible;
import searchengine.exceptions.StartIndexingIsNotPossible;
import searchengine.exceptions.StopIndexingIsNotPossible;

import searchengine.model.SiteEntity;
import searchengine.model.StatusType;
import searchengine.repositories.SiteDAO;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService{

    private final Logger logger = LogManager.
            getLogger(IndexingService.class);
    private final Marker historyMarker = MarkerManager.
            getMarker("history");
    private final SitesList sites;
    private final SiteDAO siteDAO;
    private final ApplicationContext context;

    private ForkJoinPool pool;
    private ExecutorService executorService;
    private boolean indexingStopped;

    @Override
    public StartIndexingResponse startIndexing() {
        logger.info(historyMarker,
                "запуск полной индексации: {}",
                sites);

        if (indexingStarted()) {
            logger.info(historyMarker,
                    "запуск полной индексации во время" +
                            " индексации {}",
                    sites);
            throw new StartIndexingIsNotPossible("Индексация уже запущена");
        }

        createForkJoinPool();
        sites.getSites().forEach(this::startIndexingPagesSite);

        StartIndexingResponse indexingResponse = new StartIndexingResponse();
        indexingResponse.setResult(true);
        return indexingResponse;
    }

    @Override
    public StopIndexingResponse stopIndexing() {
        logger.info(historyMarker,
                "запуск полной остановки индексации: {}",
                sites);

        if (!siteDAO.containsByStatus(StatusType.INDEXING) &&
                indexingStopped) {
            logger.info(historyMarker,
                    "остановка полной индексации при" +
                            " незапущенной индексации");
            throw new StopIndexingIsNotPossible("Индексация не запущена");
        }

        shutdownForkJoinPool();

        StopIndexingResponse stopIndexingResponse = new StopIndexingResponse();
        stopIndexingResponse.setResult(true);
        return stopIndexingResponse;
    }

    @Override
    public IndexPageResponse indexPage(String indexURL) {

        if (indexingStarted()) {
            logger.info(historyMarker,
                    "запуск индексации страницы {} во время индексации",
                    indexURL);
            throw new StartIndexingIsNotPossible("Индексация уже запущена");
        }

        logger.info(historyMarker,"запуск индексации страницы: {}",
                indexURL);
        
        HashMap<String, String> homeAndPathURL =
                splitIndexUrl(indexURL.trim());

        Site foundSite = findSiteEntity(homeAndPathURL);

        SiteEntity siteEntity = saveOrUpdateSiteInDataBase(foundSite,
                StatusType.INDEXING);

        createForkJoinPool();
        startIndexingPageSite(siteEntity,
                homeAndPathURL.get(siteEntity.getUrl()));

        return getIndexPageResponse(siteEntity);
    }

    private boolean indexingStarted() {
        return siteDAO.containsByStatus(StatusType.INDEXING);
    }

    private void createForkJoinPool() {
        if (pool == null || pool.isShutdown()){
            pool = new ForkJoinPool();
        }
    }

    private void startIndexingPagesSite(Site site) {
        SiteEntity foundSiteEntity = siteDAO.
                findSiteByUrl(site.getUrl());

        if (foundSiteEntity != null) {
            siteDAO.delete(foundSiteEntity);
        }

        SiteEntity siteEntity = saveOrUpdateSiteInDataBase(site,StatusType.INDEXING);

        PathSearcher pathSearcher = new PathSearcher(siteEntity,"/", context);
        pool.execute(pathSearcher);

        updateSiteAfterIndexing(siteEntity, pathSearcher);
    }

    private SiteEntity saveOrUpdateSiteInDataBase(Site site, StatusType status) {
        SiteEntity siteEntity = new SiteEntity();

        siteEntity.setName(site.getName());
        siteEntity.setUrl(site.getUrl());
        siteEntity.setStatus(status);
        siteEntity.setStatusTime(new Date());

        siteDAO.saveOrUpdate(siteEntity);
        return  siteEntity;
    }

    private void updateSiteAfterIndexing(SiteEntity siteEntity,
                                         PathSearcher pathSearcher) {
        createExecutorService();
        executorService.execute(() -> {
            do {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (!pathSearcher.isDone() && !pool.isTerminated());
            if (pool.isShutdown()) {
                updateSiteEntity(siteEntity, StatusType.FAILED,
                        "Индексация остановлена пользователем");
                logger.info(historyMarker,
                        "индексация {} остановлена пользователем",
                        siteEntity.getUrl());
            }
            if (!pool.isShutdown() && (pathSearcher.getException() != null ||
                    siteEntity.getLastError() != null)) {
                String error = pathSearcher.getException() != null ?
                        pathSearcher.getException().getMessage().
                                concat(siteEntity.getLastError().isEmpty() ?
                                        "" : "; ").
                                concat(siteEntity.getLastError()) :
                        siteEntity.getLastError();
                updateSiteEntity(siteEntity, StatusType.FAILED, error);
                logger.info(historyMarker,
                        "при индексации {} возникли ошибки: {}",
                        siteEntity.getUrl(), siteEntity.getLastError());
            }
            if (!pool.isShutdown() && pathSearcher.getException() == null &&
            siteEntity.getLastError() == null) {
                updateSiteEntity(siteEntity, StatusType.INDEXED, null);
                logger.info(historyMarker,
                        "индексация {} заврешена",
                        siteEntity.getUrl().
                                concat(!pathSearcher.getIndexingOfAllPages() ?
                                        pathSearcher.getPath() : ""));
            }
            if (!siteDAO.containsByStatus(StatusType.INDEXING)) {
                shutdownForkJoinPool();
                executorService.shutdown();
            }
        });
    }

    private void createExecutorService() {
        if (executorService == null || executorService.isShutdown()){
            executorService = Executors.
                    newFixedThreadPool(sites.getSites().size());
        }
    }

    private void updateSiteEntity(SiteEntity siteEntity, StatusType status,
                                  String lastError) {
        siteEntity.setLastError(lastError);
        siteEntity.setStatus(status);
        siteEntity.setStatusTime(new Date());
        siteDAO.saveOrUpdate(siteEntity);
    }

    private void shutdownForkJoinPool() {
        indexingStopped = false;
        do {
            pool.shutdownNow();
        } while (siteDAO.containsByStatus(StatusType.INDEXING));
        indexingStopped = true;
    }

    private HashMap<String, String> splitIndexUrl(String indexURL) {
        String regexHomeUrl = "https?://[^,\\s/]+";
        Pattern pattern = Pattern.compile(regexHomeUrl);
        Matcher matcher = pattern.matcher(indexURL);
        String regexUrl = "https?://[^,\\s]+";

        if (!(matcher.find() && indexURL.matches(regexUrl))) {
            return new HashMap<>(0);
        }

        String homeURL = matcher.group();
        String path = indexURL.replaceAll(homeURL,"");
        return new HashMap<>(1){{put(homeURL,
                (path.isEmpty() ? "/" : path));}};
    }

    private Site findSiteEntity(HashMap<String, String> homeAndPathURL) {
        Site foundSite;
        try {
            foundSite = sites.getSites().stream().
                    filter(site -> (homeAndPathURL.containsKey(site.getUrl())))
                    .findFirst().get();
        } catch (NoSuchElementException ex) {
                logger.info(historyMarker,
                        "запуск индексации страницы {} " +
                                "находящейся за пределами сайтов," +
                                "указанных в конфигурационном файле",
                        (homeAndPathURL.keySet().stream().findFirst().toString()).
                                concat(homeAndPathURL.values().stream().findFirst().
                                        toString()));
            throw new IndexPageIsNotPossible("Данная страница находится за " +
                    "пределами сайтов, указанных в конфигурационном файле");
        }
        return foundSite;
    }

    private void startIndexingPageSite(SiteEntity site, String path) {
        PathSearcher pathSearcher = new PathSearcher(site, path, context);
        pathSearcher.setIndexingOfAllPages(false);
        pool.execute(pathSearcher);
        updateSiteAfterIndexing(site, pathSearcher);
    }

    private IndexPageResponse getIndexPageResponse(SiteEntity siteEntity) {
        do {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } while ((((SiteEntity)siteDAO.findOneById(siteEntity.getId())).getStatus()
                == StatusType.INDEXING));

        IndexPageResponse indexPageResponse = new IndexPageResponse();
        indexPageResponse.setResult(siteEntity.getLastError() == null);
        return indexPageResponse;
    }

}
