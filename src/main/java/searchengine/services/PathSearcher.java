package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
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

    private final Logger logger = LogManager.
            getLogger(IndexingService.class);
    private final Marker historyMarker = MarkerManager.
            getMarker("history");
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
                getLemmasAndSaveLemmasEntityPage(page, htmlCode);

        if (lemmaEntities.size() == 0) {
            return;
        }

        saveIndex(page, lemmaEntities);

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

    private void decrementLemmaFrequencyAndDeletePage(PageEntity page) {
        Consumer<Session> delete = session -> {
            indexDAO.findIndexByPage(session, page).
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
            pageDAO.delete(session, page);
        };
        pageDAO.inSessionWithTransaction(delete);
    }

    private Object[] getStatusAndHTML() {
        Connection connection = jsoupSession.getSession().newRequest()
                .url(siteEntity.getUrl().concat(path));

        Object[] statusAndHTML = new Object[2];
        try {
            statusAndHTML[0] = connection.execute().statusCode();
            statusAndHTML[1] = connection.get();
        } catch (HttpStatusException ex) {
            statusAndHTML[0] = 404;
            statusAndHTML[1] = Jsoup.parse("");
            siteEntity.setLastError(ex.toString());
            logger.info(historyMarker,
                    "при индексации {} возникла ошибка: {}",
                    siteEntity.getUrl().concat(path), ex.getMessage());
        } catch (SocketTimeoutException ex) {
            statusAndHTML[0] = 408;
            statusAndHTML[1] = Jsoup.parse("");
            siteEntity.setLastError(ex.toString());
            logger.info(historyMarker,
                    "при индексации {} возникла ошибка: {}",
                    siteEntity.getUrl().concat(path), ex.getMessage());
        } catch (IOException ex) {
            statusAndHTML[0] = 429;
            statusAndHTML[1] = Jsoup.parse("");
            siteEntity.setLastError(ex.toString());
            logger.info(historyMarker,
                    "при индексации {} возникла ошибка: {}",
                    siteEntity.getUrl().concat(path), ex.getMessage());
        }
        return statusAndHTML;
    }

    private HashMap<LemmaEntity, Integer> getLemmasAndSaveLemmasEntityPage(
            PageEntity page, Document htmlCode) {

        String text = lemmaFinder.htmlCodeToTextWhitRussianWords(htmlCode);
        HashMap<String, Integer> lemmaAndFrequencyMap =
                lemmaFinder.collectLemmas(text);

        synchronized (page.getSiteEntity()) {
            Function<Session, HashMap<LemmaEntity, Integer>> save =
                    (session -> {
                if (pageDAO.save(session, page) == 0) {
                    return new HashMap<>(0);
                }
                if (lemmaAndFrequencyMap.size() == 0) {
                    return new HashMap<>(0);
                }

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
                siteEntity.setStatusTime(new Date());
                session.merge(siteEntity);
                return lemmaEntities;
            });
            return (HashMap) pageDAO.fromSessionWithTransaction(save);
        }
    }

    private void saveIndex(PageEntity pageEntity,
                           HashMap<LemmaEntity, Integer> lemmaEntities) {
        StringBuilder insertQuery = new StringBuilder();
        lemmaEntities.forEach((k, v) -> insertQuery.
                append((insertQuery.length() == 0 ? "" : ",") +
                        "(" + v + ", " + k.getId() + ", " + pageEntity.getId() + ")"));
        Consumer<Session> save = session -> session.createNativeQuery(
                        "Insert into `index`(`rank`, lemma_id, page_id) values "
                                + insertQuery).
                executeUpdate();
        indexDAO.inSessionWithTransaction(save);
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
