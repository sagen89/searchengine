package searchengine.repositories;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.HashMap;
import java.util.List;


@Repository
public class PageDAO extends AbstractHibernateDao {
    public PageDAO() {
        super();
        settClass(PageEntity.class);
    }


    public void savePage(PageEntity pageEntity, Boolean typeIsPAGES) {
        Session session = getSession();

        Transaction tx = null;
        PageEntity entity = null;
        try {
            tx = session.beginTransaction();

            if (typeIsPAGES) {
                entity = (PageEntity) findOneByTwoParameters(session, "path", pageEntity.getPath(),
                        "siteEntity", pageEntity.getSiteEntity());
            }
            if (entity == null) {
                session.saveOrUpdate(pageEntity.getPath(), pageEntity);
            }

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

//    public PageEntity findPageByPathAndSite(PageEntity pageEntity) {
//        Session session = getSession();
//
//        PageEntity result = (PageEntity) findOneByTwoParameters(session, "path", pageEntity.getPath(),
//                "siteEntity", pageEntity.getSiteEntity());
//
//        session.close();
//        return result;
//    }

    public HashMap<String, Boolean> containsPagesByPathAndSite(List<String> pathList , SiteEntity siteEntity){
        HashMap<String, Boolean> result = new HashMap<>();
        Session session = getSession();

        pathList.forEach(path -> {
            result.put(path, findOneByTwoParameters(session, "path", path,
                    "siteEntity", siteEntity) == null ? false : true);
        });

        session.close();
        return result;
    }

    public void deletePageBySiteId(SiteEntity siteEntity) {
        Session session = getSession();

        Transaction tx = null;

        try {
            tx = session.beginTransaction();

            deleteByParameter(session, "siteEntity", siteEntity);

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