package searchengine.services;


import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.hibernate.Session;

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

import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.StatusType;
import searchengine.repositories.SiteDAO;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService{

    private final Logger logger = LogManager.getLogger(IndexingService.class);
    private final Marker historyMarker = MarkerManager.getMarker("history");

    private final SitesList sites;
    private final SiteDAO siteDAO;
    private final ApplicationContext context;

    private ForkJoinPool pool;
    private ExecutorService executorService;
    private boolean hasIndexingStarted = false;

    @Override
    public StartIndexingResponse startIndexing() {
        logger.info(historyMarker, "Запуск полной индексации: {}",
                sites);

        if (isPoolRunning()) {
            logger.info(historyMarker,
                    "Запуск полной индексации во время" +
                            " индексации {}", sites);
            throw new StartIndexingIsNotPossible("Индексация уже запущена");
        }

        hasIndexingStarted = true;

        createExecutorService(sites.getSites().size() + 1);
        executorService.execute(runPathSearchingAndIndexing());

        StartIndexingResponse indexingResponse = new StartIndexingResponse();
        indexingResponse.setResult(true);
        return indexingResponse;
    }

    @Override
    public StopIndexingResponse stopIndexing() {
        logger.info(historyMarker,
                "Запуск полной остановки индексации: {}", sites);

        if (!isPoolRunning() && hasIndexingStarted) {
            logger.info(historyMarker,
                    "Остановка полной индексации при" +
                            " подготовке к переиндексации");
            throw new StopIndexingIsNotPossible("Идет подготовка к переиндексации");
        }

        if (!isPoolRunning()) {
            logger.info(historyMarker,
                    "Остановка полной индексации при не запущенной индексации");
            throw new StopIndexingIsNotPossible("Индексация не запущена");
        }

        shutdownForkJoinPool();

        StopIndexingResponse stopIndexingResponse = new StopIndexingResponse();
        stopIndexingResponse.setResult(true);
        return stopIndexingResponse;
    }

    @Override
    public IndexPageResponse indexPage(String indexURL) {
        logger.info(historyMarker,"Запуск индексации страницы: {}",
                indexURL);

        if (isPoolRunning()) {
            logger.info(historyMarker,
                    "Запуск индексации страницы {} во время индексации",
                    indexURL);
            throw new StartIndexingIsNotPossible("Индексация уже запущена");
        }

        HashMap<String, String> homeAndPathURL = splitIndexUrl(indexURL.trim());

        Site foundSite = findSite(homeAndPathURL);
        SiteEntity foundSiteEntity = siteDAO.findSiteByUrl(foundSite.getUrl());

        SiteEntity siteEntity = foundSiteEntity == null ?
                saveSiteInDataBase(foundSite):
                updateSiteEntity(foundSiteEntity, StatusType.INDEXING, null);

        createExecutorService(1);
        createForkJoinPool();
        startPathSearchingAndIndexing(siteEntity, homeAndPathURL.get(siteEntity.getUrl()),
                false);

        IndexPageResponse indexPageResponse = new IndexPageResponse();
        indexPageResponse.setResult(true);
        return indexPageResponse;
    }

    private boolean isPoolRunning() {
        return pool != null && pool.getRunningThreadCount() != 0;
    }

    private Runnable runPathSearchingAndIndexing(){
        return ()-> {
            deleteSitesEntities();
            createForkJoinPool();
            sites.getSites().forEach(site -> {
                SiteEntity siteEntity = saveSiteInDataBase(site);
                startPathSearchingAndIndexing(siteEntity, "/", true);
            });
        };
    }

    private void createForkJoinPool() {
        if (pool == null || pool.isShutdown()) {
            pool = new ForkJoinPool();
        }
    }

    private void deleteSitesEntities() {
        Consumer<Session> delete = session ->
                sites.getSites().forEach(site -> {
                    SiteEntity foundSiteEntity = siteDAO.
                            findSiteByUrl(site.getUrl());
                    if (foundSiteEntity != null) {
                        siteDAO.delete(session, foundSiteEntity);
                    }
                });
        do {
            try {
                siteDAO.inSessionWithTransaction(delete);
            } catch (RuntimeException e) {
                logger.error("При удалении сайтов {} возникли " +
                                "ошибки: {}",
                        sites.getSites().stream().map(site -> site.getUrl()).
                                collect(Collectors.toList()), e);
            }
        } while (siteDAO.containsByStatus(StatusType.INDEXED)
            || siteDAO.containsByStatus(StatusType.FAILED));
    }

    private void startPathSearchingAndIndexing(SiteEntity siteEntity, String path,
                                               boolean indexingOfAllPages) {

        PathSearcher pathSearcher = new PathSearcher(siteEntity,path, context);
        pathSearcher.setIndexingOfAllPages(indexingOfAllPages);
        pool.execute(pathSearcher);

        updateSiteEntityAfterIndexing(siteEntity, pathSearcher);
    }

    private SiteEntity saveSiteInDataBase(Site site) {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setName(site.getName());
        siteEntity.setUrl(site.getUrl());
        siteEntity.setStatus(StatusType.INDEXING);
        siteEntity.setStatusTime(new Date());
        return siteDAO.save(siteEntity);
    }

    private void updateSiteEntityAfterIndexing(SiteEntity siteEntity,
                                               PathSearcher pathSearcher) {

        executorService.execute(() -> {
            waitForEndOfIndexing(pathSearcher);
            if (pool.isShutdown()) {
                updateSiteEntity(siteEntity, StatusType.FAILED,
                        StatisticsServiceImpl.ERRORS[2]);
                logger.info(historyMarker,
                        "индексация {} остановлена пользователем",
                        siteEntity.getUrl());
            } else {
                String lastError = getErrorText(siteEntity);
                updateSiteEntity(siteEntity,
                        lastError == null ? StatusType.INDEXED : StatusType.FAILED,
                        lastError);
                logger.info(historyMarker, "индексация {} заврешена",
                        siteEntity.getUrl().
                                concat(!pathSearcher.getIndexingOfAllPages() ?
                                        pathSearcher.getPath() : ""));
            }
            shutdownIfIndexingIsOver();
        });
    }

    private void createExecutorService(int nThreads) {
        if (executorService == null || executorService.isShutdown()){
            executorService = Executors.
                    newFixedThreadPool(nThreads);
        }
    }

    private void waitForEndOfIndexing(PathSearcher pathSearcher) {
        do {
            if (pathSearcher.getIndexingOfAllPages()) {
                try {
                    Thread.sleep(20000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } while (!pathSearcher.isDone() && !pool.isTerminated());
    }

    private String getErrorText(SiteEntity siteEntity) {
        Session session = siteDAO.getSession();
        Set<PageEntity> pageEntities = siteDAO.findById(session, siteEntity).
                getPageEntitySet();
        Set<String> pathsWithErrors = pageEntities.stream().
                filter(pageEntity -> pageEntity.getCode() >= 400).
                map(PageEntity::getPath).
                collect(Collectors.toSet());
        session.close();
        if (pathsWithErrors.isEmpty()) {
            return null;
        }
        return pageEntities.size() == 1 && pathsWithErrors.contains("/") ?
                StatisticsServiceImpl.ERRORS[0] :
                StatisticsServiceImpl.ERRORS[1].
                        concat(" на страниц").
                        concat(pathsWithErrors.size() == 1 ? "е " : "ах ").
                        concat(pathsWithErrors.toString().
                                replaceAll("\\[?]?", ""));
    }

    private SiteEntity updateSiteEntity(SiteEntity siteEntity, StatusType status,
                                  String lastError) {
        siteEntity.setLastError(lastError);
        siteEntity.setStatus(status);
        siteEntity.setStatusTime(new Date());
        return siteDAO.update(siteEntity);
    }

    private void shutdownIfIndexingIsOver() {
        if (!siteDAO.containsByStatus(StatusType.INDEXING)) {
            shutdownForkJoinPool();
            executorService.shutdown();
            hasIndexingStarted = false;
        }
    }

    private void shutdownForkJoinPool() {
        do {
            pool.shutdownNow();
        } while (isPoolRunning());
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

    private Site findSite(HashMap<String, String> homeAndPathURL) {
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

}
