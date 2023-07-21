package searchengine.repositories;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.*;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteEntity;
import searchengine.model.StatusType;

import java.util.function.Function;


@Repository
public class SiteDAO extends AbstractHibernateDao {

    private final Logger logger = LogManager.getLogger(SiteDAO.class);

    public SiteDAO() {
        super();
        settClass(SiteEntity.class);
    }

    public SiteEntity save(SiteEntity siteEntity) {
        Function<Session, SiteEntity> save = session ->
                (SiteEntity) findOneById(session, (int) session.save(siteEntity));

        try {
            return (SiteEntity) fromSessionWithTransaction(save);
        } catch (RuntimeException e) {
            logger.error("При сохранении сайта {}" +
                            " возникли ошибки: {}", siteEntity.getUrl(), e);
            return null;
        }
    }

    public SiteEntity update(SiteEntity siteEntity) {

        Function<Session, SiteEntity> update = session ->
                session.merge(siteEntity);
        try {
            return (SiteEntity) fromSessionWithTransaction(update);
        } catch (RuntimeException e) {
            logger.error("При обновлении сайта {}" +
                    " возникли ошибки: {}", siteEntity.getUrl(), e);
            return null;
        }

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

    public SiteEntity findById(Session session, SiteEntity siteEntity) {
        return (SiteEntity) findOneById(session, siteEntity.getId());
    }
}
