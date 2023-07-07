package searchengine.repositories;
import org.hibernate.*;

import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

@Repository
public class PageDAO extends AbstractHibernateDao {

    public PageDAO() {
        super();
        settClass(PageEntity.class);
    }

    public PageEntity findPageByPathAndSite(PageEntity pageEntity) {

        Function<Session, PageEntity> find = session -> {
            return (PageEntity) findOneByTwoParameters(session,
                "path", pageEntity.getPath(),
                "siteEntity", pageEntity.getSiteEntity());};

        return (PageEntity) fromSession(find);
    }

    public int save(Session session, PageEntity pageEntity) {
        return findByTwoParameters(session,
                "path", pageEntity.getPath(),
                "siteEntity", pageEntity.getSiteEntity(), 1).
                get(0) == null ?
                (int) session.save(pageEntity) : 0;
    }

    public HashMap<String, Boolean> containsPagesByPathAndSite(
            List<String> pathList , SiteEntity siteEntity) {

        Function<Session, HashMap<String, Boolean>> contains = session -> {
            HashMap<String, Boolean> map = new HashMap<>();

            pathList.forEach(path -> map.put(path,
                    findOneByTwoParameters(session, "path", path,
                            "siteEntity", siteEntity) != null));
            return map;
        };

        return (HashMap<String, Boolean>) fromSession(contains);
    }

}