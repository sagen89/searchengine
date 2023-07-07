package searchengine.repositories;

import org.hibernate.*;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteEntity;
import searchengine.model.StatusType;

import java.util.function.Consumer;
import java.util.function.Function;

@Repository
public class SiteDAO extends AbstractHibernateDao {

    public SiteDAO() {
        super();
        settClass(SiteEntity.class);
    }

    public void saveOrUpdate(SiteEntity siteEntity){

        Consumer<Session> saveOrUpdate = session -> {
            SiteEntity foundEntity = (SiteEntity) findByParameter(session,
                    "url", siteEntity.getUrl(),1).get(0);

            if (foundEntity == null) {
                session.save(siteEntity);
            } else {
                siteEntity.setId(foundEntity.getId());
                session.merge(siteEntity);
            }
        };
        inSessionWithTransaction(saveOrUpdate);
    }

    public boolean containsByStatus(StatusType statusType) {
        Function<Session, SiteEntity> find = session ->
                (SiteEntity) findByParameter(session, "status",
                        statusType, 1)
                        .get(0);
        return fromSession(find) != null ? true : false;
    }

    public SiteEntity findSiteByUrl(String url) {
        Function<Session, SiteEntity> find = session ->
                findSiteByUrl(session, url);
        return (SiteEntity) fromSession(find);
    }

    public SiteEntity findSiteByUrl(Session session, String url) {
        return (SiteEntity) findByParameter(session, "url",
                url,1).get(0);
    }
}
