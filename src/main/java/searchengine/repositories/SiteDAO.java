package searchengine.repositories;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteEntity;
import searchengine.model.StatusType;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Repository
public class SiteDAO extends AbstractHibernateDao{
    public SiteDAO() {
        super();
        settClass(SiteEntity.class);
    }

    public boolean containsByStatus(StatusType statusType) {
        Session session = getSession();

        Transaction tx = null;
        List result = new ArrayList<>();

        try {
            tx = session.beginTransaction();

            result = findOneByParameter(session, "status", statusType, 1);

            tx.commit();
        } catch (HibernateException hex) {
            if (tx != null) {
                tx.rollback();
            } else {
                hex.printStackTrace();
            }
        } finally {
            session.close();
        }

        return result.size() > 0 ? true : false;
    }

    public SiteEntity findSiteByUrl(String name) {
        Session session = getSession();
        List result = findOneByParameter(session, "url", name,1);
        session.close();
        return result.size() != 0 ? (SiteEntity) result.get(0) : null ;
    }

    public void updateSiteByTime(SiteEntity siteEntity) {
        Session session = getSession();
        Transaction tx = null;

        try {
            tx = session.beginTransaction();
            SiteEntity findEntity = session.find(SiteEntity.class, siteEntity.getId());
            findEntity.setStatusTime(new Date());
            session.merge(findEntity);
            tx.commit();
        } catch (HibernateException hex) {
            if (tx != null) {
                tx.rollback();
            } else {
                throw new RuntimeException(hex);
            }
        } finally {
            session.close();
        }
    }

    public void updateSiteByStatusErrorStatusTime(SiteEntity siteEntity) {
        Session session = getSession();
        Transaction tx = null;

        try {
            tx = session.beginTransaction();
            SiteEntity findEntity = session.find(SiteEntity.class, siteEntity.getId());
            findEntity.setStatus(siteEntity.getStatus());
            findEntity.setLastError(siteEntity.getLastError());
            findEntity.setStatusTime(siteEntity.getStatusTime());
            session.merge(findEntity);
            tx.commit();
        } catch (HibernateException hex) {
            if (tx != null) {
                tx.rollback();
            } else {
                throw new RuntimeException(hex);
            }
        } finally {
            session.close();
        }
    }

}
