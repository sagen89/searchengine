package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;
import searchengine.config.JsoupSession;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageDAO;
import searchengine.repositories.SiteDAO;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.stream.Collectors;
@RequiredArgsConstructor
public class PathSearcher extends RecursiveAction {
    private final String path;
    private final HashMap<String, Object>  objectsToPathSearcher;
    private final TypeOfParsing type;

    private SiteEntity siteEntity;
    private PageDAO pageDAO;
    private SiteDAO siteDAO;

    private JsoupSession jsoupSession;

    private int statusCode;
    private Document htmlCode;
    private Boolean typeIsPages;

    public enum TypeOfParsing {
        PAGES,
        PAGE
    }

    @Override
    protected void compute() {
        initObjects(objectsToPathSearcher);
        typeIsPages = type == TypeOfParsing.PAGES;

        PageEntity page = new PageEntity();
        page.setSiteEntity(siteEntity);
        page.setPath(path);

        boolean containsPage = pageDAO.containsPagesByPathAndSite
                (new ArrayList<>(){{add(path);}}, siteEntity).get(path);

        if (typeIsPages && containsPage) {
            return;
        }

        connectionToSite(page);

        page.setCode(statusCode);
        page.setContent(htmlCode.toString());
        pageDAO.savePage(page, typeIsPages);

        siteDAO.updateSiteByTime(siteEntity);

        if ((statusCode == 200 || statusCode == 203) && typeIsPages) {
            startNewTask();
        }
    }

    private void initObjects(HashMap<String, Object>  objectsToPathSearcher) {
        siteEntity = (SiteEntity) objectsToPathSearcher.get(SiteEntity.class.getSimpleName());
        pageDAO = (PageDAO) objectsToPathSearcher.get(PageDAO.class.getSimpleName());
        siteDAO = (SiteDAO) objectsToPathSearcher.get(SiteDAO.class.getSimpleName());
        jsoupSession = (JsoupSession)  objectsToPathSearcher
                .get(JsoupSession.class.getSimpleName());
    }

    private void connectionToSite(PageEntity page)  {
        Connection connection = jsoupSession.getSession().newRequest()
                .url(siteEntity.getUrl().concat(path));
        try {
            statusCode = connection.execute().statusCode();
            htmlCode = connection.get();
        }
        catch (HttpStatusException  ex) {
            page.setCode(404);
            page.setContent("");
            pageDAO.savePage(page, typeIsPages);
        }
        catch (SocketTimeoutException ex) {
            page.setCode(408);
            page.setContent("");
            pageDAO.savePage(page, typeIsPages);
            throw new RuntimeException(ex);
        }
        catch (IOException ex) {
            page.setCode(429);
            page.setContent("");
            pageDAO.savePage(page, typeIsPages);
            throw new RuntimeException(ex);
        }

    }

    private void startNewTask() {
        List<PathSearcher> taskList = new ArrayList<>();
        pageDAO.containsPagesByPathAndSite(getPathList(), siteEntity)
                        .forEach((path, contains) ->{
                            if (!contains) {
                                taskList.add(new PathSearcher(path,
                                        objectsToPathSearcher,
                                        TypeOfParsing.PAGES));
                            }
                        });
        PathSearcher.invokeAll(taskList);
        taskList.forEach(ForkJoinTask::join);
    }

    private List<String> getPathList() {
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
