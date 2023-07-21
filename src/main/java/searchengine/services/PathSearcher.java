package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.context.ApplicationContext;
import searchengine.config.JsoupSession;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.*;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class PathSearcher extends RecursiveAction {
    private final Logger logger = LogManager.getLogger(PathSearcher.class);
    private final SiteEntity siteEntity;
    @Getter
    private final String path;
    private final ApplicationContext context;

    private PageDAO pageDAO;
    private LemmaDAO lemmaDAO;
    private IndexDAO indexDAO;
    private LemmaFinder lemmaFinder;
    private JsoupSession jsoupSession;

    @Setter
    @Getter
    private Boolean indexingOfAllPages = true;

    @Override
    protected void compute() {
        getBeans();

        PageEntity page = new PageEntity();
        page.setSiteEntity(siteEntity);
        page.setPath(path);

        PageEntity foundPage = pageDAO.findPageByPathAndSite(page);
        Boolean containsPage = foundPage != null;
        if (indexingOfAllPages && containsPage) {
            return;
        }
        if (!indexingOfAllPages && containsPage) {
            decrementLemmaFrequencyAndDeletePage(foundPage);
        }

        Object[] statusAndHTML = getStatusAndHTML();
        int statusCode = (int) statusAndHTML[0];
        Document htmlCode = (Document) statusAndHTML[1];

        page.setCode(statusCode);
        page.setContent(htmlCode.toString());

        HashMap<LemmaEntity, Integer> lemmaEntities =
                getAndSaveLemmasEntityIfPageEntityIsSaved(page, htmlCode);

        if (lemmaEntities.size() == 0) {
            return;
        }

        saveIndexes(page, lemmaEntities);

        if ((statusCode == 200 || statusCode == 203) && indexingOfAllPages) {
            startNewTask(htmlCode);
        }
    }

    private void getBeans() {
        pageDAO = context.getBean(PageDAO.class);
        lemmaDAO = context.getBean(LemmaDAO.class);
        indexDAO = context.getBean(IndexDAO.class);
        lemmaFinder = context.getBean(LemmaFinder.class);
        jsoupSession = context.getBean(JsoupSession.class);
    }

    private void decrementLemmaFrequencyAndDeletePage(PageEntity pageEntity) {
        Consumer<Session> delete = session -> {
            indexDAO.findIndexesByPage(session, pageEntity).
                    forEach(indexEntity -> {
                        if (indexEntity == null) {
                            return;
                        }
                        LemmaEntity lemmaByIndex = indexEntity.getLemmaEntity();
                        int newFrequency = lemmaByIndex.getFrequency() - 1;
                        if (newFrequency < 1) {
                            lemmaDAO.delete(session, lemmaByIndex);
                        } else {
                            lemmaByIndex.setFrequency(newFrequency);
                            session.merge(lemmaByIndex);
                        }
                    });
            pageDAO.delete(session, pageEntity);
        };
        try {
            pageDAO.inSessionWithTransaction(delete);
        } catch (RuntimeException e) {
            logger.error("При удалении страницы {}" +
                            " возникли ошибки: {}", pageEntity.getPath(), e);
        }

    }

    private Object[] getStatusAndHTML() {
        Connection connection = jsoupSession.getSession().newRequest()
                .url(siteEntity.getUrl().concat(path));

        Object[] statusAndHTML = new Object[2];
        try {
            statusAndHTML[0] = connection.execute().statusCode();
            statusAndHTML[1] = connection.get();
        } catch (HttpStatusException e) {
            statusAndHTML[0] = e.getStatusCode();
            statusAndHTML[1] = Jsoup.parse("");
            logger.error("при индексации {} возникла ошибка: {}",
                    siteEntity.getUrl().concat(path), e);
        } catch (SocketTimeoutException e) {
            statusAndHTML[0] = 408;
            statusAndHTML[1] = Jsoup.parse("");
            logger.error("при индексации {} возникла ошибка: {}",
                    siteEntity.getUrl().concat(path), e);
        } catch (IOException e) {
            statusAndHTML[0] = 429;
            statusAndHTML[1] = Jsoup.parse("");
            logger.error("при индексации {} возникла ошибка 429: {}",
                    siteEntity.getUrl().concat(path), e);
        }
        return statusAndHTML;
    }

    private HashMap<LemmaEntity, Integer> getAndSaveLemmasEntityIfPageEntityIsSaved(
            PageEntity pageEntity, Document htmlCode) {

        String text = lemmaFinder.htmlCodeToTextWhitRussianWords(htmlCode);
        HashMap<String, Integer> lemmaAndFrequencyMap =
                lemmaFinder.collectLemmas(text);

        HashMap<LemmaEntity, Integer> lemmaEntities = new HashMap<>();

        synchronized (pageEntity.getSiteEntity()) {
            Function<Session, HashMap<LemmaEntity, Integer>> save = session -> {
                if (pageDAO.save(session, pageEntity) == 0) {
                    return new HashMap<>(0);
                }
                siteEntity.setStatusTime(new Date());
                session.merge(siteEntity);
                if (lemmaAndFrequencyMap.size() == 0) {
                    return new HashMap<>(0);
                }
                return getLemmaEntities(lemmaAndFrequencyMap, session);
            };

            try {
                lemmaEntities = (HashMap) pageDAO.fromSessionWithTransaction(save);
            } catch (RuntimeException e) {
                logger.error("При сохранении страницы {} и ее лемм" +
                                " возникли ошибки: ",
                        siteEntity.getUrl().concat(pageEntity.getPath()), e);
            }
            return lemmaEntities;
        }
    }

    private HashMap<LemmaEntity, Integer> getLemmaEntities(
            HashMap<String, Integer> lemmaAndFrequencyMap,
            Session session) {

        HashMap<LemmaEntity, Integer> lemmaEntities = new HashMap<>();
        lemmaAndFrequencyMap.forEach((k, v) -> {
            LemmaEntity lemmaEntity = new LemmaEntity();
            lemmaEntity.setSiteEntity(siteEntity);
            lemmaEntity.setLemma(k);
            lemmaEntity.setFrequency(1);
            lemmaDAO.saveOrUpdateWithIncrementOrDecrement(session,
                    lemmaEntity, 1);
            lemmaEntities.put(lemmaEntity, v);
        });
        return lemmaEntities;
    }

    private void saveIndexes(PageEntity pageEntity,
                             HashMap<LemmaEntity, Integer> lemmaEntities) {

        StringBuilder insertQuery = new StringBuilder();
        lemmaEntities.forEach((lemmaEntity, rank) ->
                insertQuery.append(insertQuery.length() == 0 ? "" : ",").
                        append("(").append(rank).append(", ").append(lemmaEntity.getId()).
                        append(", ").append(pageEntity.getId()).append(")"));
        try {
            indexDAO.multiInsert(insertQuery);
        } catch (RuntimeException e) {
            decrementLemmaFrequencyAndDeletePage(pageEntity);
            logger.error("При сохранении индексов страницы {}" +
                            " возникли ошибки: {}",
                    siteEntity.getUrl().concat(pageEntity.getPath()), e);
        }
    }

    private void startNewTask(Document htmlCode) {
        List<PathSearcher> taskList = new ArrayList<>();
        pageDAO.containsPagesByPathAndSite(getPathList(htmlCode), siteEntity)
                .forEach((path, contains) -> {
                    if (!contains) {
                        taskList.add(
                                new PathSearcher(siteEntity, path, context));
                    }
                });
        PathSearcher.invokeAll(taskList);
        taskList.forEach(ForkJoinTask::join);
    }

    private List<String> getPathList(Document htmlCode) {
        return htmlCode.getElementsByTag("a").eachAttr("href")
                .stream().filter(href -> {
                    String regex = "[^,\\s]+";
                    return (href.matches("/" + regex) &&
                            !href.matches(regex + "[doc]") &&
                            !href.matches(regex + "[docx]") &&
                            !href.matches(regex + "[pdf]") &&
                            !href.matches(regex + "[jpg]") &&
                            !href.matches("/download" + regex));
                }).distinct().
                collect(Collectors.toList());
    }
}
