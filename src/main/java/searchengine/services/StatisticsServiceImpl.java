package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.hibernate.Session;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.StringUtils;
import searchengine.dto.indexing.SearchData;
import searchengine.dto.indexing.SearchResponse;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.exceptions.QueryFormatIsWrong;
import searchengine.exceptions.QueryIsEmpty;
import searchengine.model.*;
import searchengine.repositories.LemmaDAO;
import searchengine.repositories.SiteDAO;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final Logger logger = LogManager.getLogger(IndexingService.class);
    private final Marker historyMarker = MarkerManager.getMarker("history");
    private final int lengthLine = 110;
    private final int countLine = 3;

    private final SiteDAO siteDAO;
    private final LemmaDAO lemmaDAO;
    private final LemmaFinder lemmaFinder;

    @Override
    public StatisticsResponse getStatistics() {
        String[] errors = {
                "Ошибка индексации: главная страница сайта не доступна",
                "Ошибка индексации: сайт не доступен",
                ""
        };

        logger.info(historyMarker, "запрос на получение статистики");

        Session session = siteDAO.getSession();

        List<SiteEntity> siteEntities =
                 siteDAO.getEntities(session);

        List<DetailedStatisticsItem> items =
                getDetailedStatistics(siteEntities, errors);
        TotalStatistics total = getTotalStatistics(siteEntities, items);

        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(items);
        session.close();

        StatisticsResponse response = new StatisticsResponse();
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    @Override
    public SearchResponse getSearchResults(
            String query, Integer offset, Integer limit, String site) {

        logger.info(historyMarker,
                "запрос на получение данных по поисковому запросу \"{}".
                        concat(StringUtils.isEmpty(site) ? "\"" : "\" на сайте {}"),
                query, site);

        if (StringUtils.isEmpty(query)) {
            throw new QueryIsEmpty("Задан пустой запрос");
        }
        if (!query.matches("[А-яЁё\\s]+")) {
            throw new QueryFormatIsWrong("Задан некорректный запрос");
        }

        Session session = lemmaDAO.getSession();
        List<LemmaEntity> lemmaEntitiesOnQuery =
                getLemmaEntities(query, site, session);
        Map<PageEntity, List<IndexEntity>> pageAndIndexesMapOnQueryLemmas =
                getPageAndIndexes(lemmaEntitiesOnQuery);
        session.close();

        List<SearchData> dataList = getSearchData(pageAndIndexesMapOnQueryLemmas);

        SearchResponse searchResponse = new SearchResponse();
        searchResponse.setResult(true);
        searchResponse.setCount(dataList.size());

        searchResponse.setData(dataList.stream()
                .skip(offset).limit(limit).collect(Collectors.toList())
        );

        return searchResponse;
    }

    private List<DetailedStatisticsItem> getDetailedStatistics(
            List<SiteEntity> siteEntities, String[] errors) {
        return siteEntities.stream().map(siteEntity -> {
            String errorText = (siteEntity.getLastError() != null &&
                    siteEntity.getPageEntitySet().size() > 1) ?
                    errors[1] : errors[0];

            DetailedStatisticsItem item = new DetailedStatisticsItem();

            item.setUrl(siteEntity.getUrl());
            item.setName(siteEntity.getName());
            item.setStatus(siteEntity.getStatus().toString());
            item.setStatusTime(siteEntity.getStatusTime().getTime());
            item.setPages(siteEntity.getPageEntitySet().size());
            item.setLemmas(siteEntity.getLemmaEntitySet().size());
            item.setError(siteEntity.getLastError() == null ?
                    errors[2] : errorText);
            return item;
        }).collect(Collectors.toList());
    }

    private TotalStatistics getTotalStatistics(
            List<SiteEntity> siteEntities,
            List<DetailedStatisticsItem> items) {

        TotalStatistics total = new TotalStatistics();

        total.setSites(siteEntities.size());
        total.setPages(items.stream().
                mapToInt(DetailedStatisticsItem::getPages).sum());
        total.setLemmas(items.stream()
                .mapToInt(DetailedStatisticsItem::getLemmas).sum());
        total.setIndexing(siteDAO.containsByStatus(StatusType.INDEXING));
        return total;
    }

    private List<LemmaEntity> getLemmaEntities(
            String text, String siteUrl, Session session) {
        return lemmaFinder.getUniqueWords(text).
                stream().map(lemmaFinder::getNormalForms).
                filter(normalForms -> normalForms.size() != 0).
                flatMap(normalForms ->
                        lemmaDAO.findLemmasByNormalForm(session,
                                normalForms.get(0)).stream()).
                filter(Objects::nonNull).
                filter(lemmaEntity -> siteUrl == null ||
                        lemmaEntity.getSiteEntity() == siteDAO.
                        findSiteByUrl(session, siteUrl)).
                sorted().collect(Collectors.toList());
    }

    private Map<PageEntity, List<IndexEntity>> getPageAndIndexes(
            List<LemmaEntity> lemmaEntities) {

        Map<PageEntity, List<IndexEntity>> map = new HashMap<>();
        if (lemmaEntities.isEmpty()) {
            return map;
        }

        int numberOfDistinctLemmas = (int) lemmaEntities.stream().
                map(LemmaEntity::getLemma).distinct().count();

        lemmaEntities.stream().
                flatMap(lemmaEntity -> lemmaEntity.getIndexEntities().stream()).
                collect(Collectors.groupingBy(IndexEntity::getPageEntity)).
                forEach((k, v) -> {
                    if (v.size() >= numberOfDistinctLemmas) {
                        map.put(k, v);
                    }
                });

        return map;
    }

    private List<SearchData> getSearchData(
            Map<PageEntity, List<IndexEntity>> pageAndIndexesMapOnQueryLemmas) {

        if (pageAndIndexesMapOnQueryLemmas.size() == 0) {
            return Collections.emptyList();
        }

        int maxAbsoluteRelevance =
                pageAndIndexesMapOnQueryLemmas.values().stream()
                .mapToInt(indexEntities -> indexEntities.stream().parallel()
                        .mapToInt(IndexEntity::getRank).sum()).sequential()
                .max().getAsInt();

        ExecutorService executor = Executors.newCachedThreadPool();
        List<Future<SearchData>>  futureList =
                submitTaskToCreateSearchData(executor,
                        pageAndIndexesMapOnQueryLemmas, maxAbsoluteRelevance);
        List<SearchData> data = futureList.stream().map(future -> {
            try {
                return future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }).sorted().collect(Collectors.toList());
        executor.shutdown();

        return data;
    }

    private List<Future<SearchData>> submitTaskToCreateSearchData(
            ExecutorService executor,
            Map<PageEntity, List<IndexEntity>> pageAndIndexesMapOnQueryLemmas,
            int maxAbsoluteRelevance) {

        List<Future<SearchData>> futureList = new ArrayList<>();

        pageAndIndexesMapOnQueryLemmas.forEach(
                (pageEntityOnQuery, indexEntityListOnQuery) -> {
                    Future<SearchData> future =
                            executor.submit(() ->
                                    getTaskToCreateSearchData(pageEntityOnQuery,
                                            indexEntityListOnQuery,
                                            maxAbsoluteRelevance)
                            );
                    futureList.add(future);
                });

        return futureList;
    }

    private SearchData getTaskToCreateSearchData(PageEntity pageEntityOnQuery,
                                                 List<IndexEntity> indexEntityListOnQuery,
                                                 int maxAbsoluteRelevance) {

        Document htmlCode = Jsoup.parse(pageEntityOnQuery.getContent());

        SearchData searchData = new SearchData();

        searchData.setSite(pageEntityOnQuery.getSiteEntity().getUrl());
        searchData.setSiteName(pageEntityOnQuery.getSiteEntity().getName());
        searchData.setUri(pageEntityOnQuery.getPath());
        searchData.setTitle(htmlCode.title());
        searchData.setSnippet(getSnippet(htmlCode, indexEntityListOnQuery));
        searchData.setRelevance((float) indexEntityListOnQuery.stream().
                mapToInt(IndexEntity::getRank).sum() / maxAbsoluteRelevance);

        return searchData;
    }

    private String getSnippet(
            Document htmlCode,
            List<IndexEntity> indexEntityListOnQuery) {

        List<String> lemmasOnQuery =
                indexEntityListOnQuery.stream().
                        map(indexEntity -> indexEntity.getLemmaEntity().getLemma()).
                        sorted().toList();

        Map<String, List<String>> matchingLemmasOfWordsInHtmlAndQuery = lemmaFinder.
                collectLemmasEntity(lemmaFinder.
                        htmlCodeToTextWhitRussianWords(htmlCode)).stream().
                filter(lemmaEntityOfWordOfHtmlText -> lemmasOnQuery.
                        contains(lemmaEntityOfWordOfHtmlText.getLemma())).
                collect(Collectors.groupingBy(LemmaEntity::getLemma,
                        Collectors.mapping(LemmaEntity::getWord, Collectors.toList())));

        return getPartsOfHtmlTextWithQueryWords(htmlCode.text(),
                matchingLemmasOfWordsInHtmlAndQuery);
    }

    private String getPartsOfHtmlTextWithQueryWords(
            String htmlText,
            Map<String, List<String>> matchingLemmasOfWordsInHtmlAndQuery) {

        List<String> distinctWordsAccordingToQueryLemmas =
                matchingLemmasOfWordsInHtmlAndQuery.values().stream().
                        flatMap(Collection::stream).
                        distinct().collect(Collectors.toList());

        int lengthAllIdenticalWords =
                distinctWordsAccordingToQueryLemmas.stream().
                        mapToInt(String::length).sum();

        int lengthSubstring =
                (lengthLine * countLine - lengthAllIdenticalWords) /
                distinctWordsAccordingToQueryLemmas.size();

        String regex = "[^А-яЁё]";
        String partsOfHtmlText = getPartsOfHtmlTextWithQueryWords(htmlText,
                distinctWordsAccordingToQueryLemmas, lengthSubstring, regex);

        StringBuilder builder = new StringBuilder(partsOfHtmlText);
        for (String word : distinctWordsAccordingToQueryLemmas) {
            builder = multiPerformTagBSelection(builder, word, regex);
        }

        return builder.toString();
    }

    private String getPartsOfHtmlTextWithQueryWords(
            String htmlText,
            List<String> distinctWordsAccordingToQueryLemmas,
            int lengthSubstring, String regex) {

        String text = " ".concat(htmlText).concat(" ");

        StringBuilder builder = new StringBuilder();

        for (String word : distinctWordsAccordingToQueryLemmas) {
            Pattern pattern = Pattern.compile(regex + word + regex);
            Matcher matcher =  pattern.matcher(text);
            if (matcher.find()) {
                int startIndex = matcher.start();
                int endIndex = matcher.end();

                int indexDown = startIndex;
                int indexUp = endIndex;
                while ((indexUp - indexDown < lengthSubstring) &&
                        (indexDown > 0 && indexUp < text.length())) {

                    startIndex = text.charAt(indexDown) == ' ' ?
                            startIndex : indexDown;
                    endIndex = text.charAt(indexUp) == ' ' ?
                            endIndex : indexUp;
                    indexDown--;
                    indexUp++;
                }

                builder.append(" ... ".
                        concat(text.substring(startIndex, endIndex)).
                        concat(" ... "));
            }
        }
        return builder.toString();
    }

    private StringBuilder multiPerformTagBSelection(
            StringBuilder builder, String word, String regex) {

        String text = builder.toString();

        Pattern pattern = Pattern.compile(regex + word + regex);
        Matcher matcher = pattern.matcher(text);
        builder.delete(0, builder.length());
        while (matcher.find()) {
            String charStart = text.substring(matcher.start(), matcher.start()+1);
            String charEnd = text.substring(matcher.end()-1, matcher.end());
            matcher.appendReplacement(builder, charStart.concat("<b>").
                    concat(word).concat("</b>").concat(charEnd));
        }
        matcher.appendTail(builder);
        return builder;
    }

}
