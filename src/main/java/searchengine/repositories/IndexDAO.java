package searchengine.repositories;

import org.hibernate.*;
import org.springframework.stereotype.Repository;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;

import java.util.List;

@Repository
public class IndexDAO extends AbstractHibernateDao {

    public IndexDAO() {
        super();
        settClass(IndexEntity.class);
    }

    public List<IndexEntity> findIndexByPage(Session session,
                                             PageEntity pageEntity) {
        return findByParameter(session,
                "pageEntity", pageEntity,Integer.MAX_VALUE);
    }
}
