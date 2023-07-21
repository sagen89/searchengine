package searchengine.repositories;

import org.hibernate.*;
import org.springframework.stereotype.Repository;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;

import java.util.List;
import java.util.function.Consumer;

@Repository
public class IndexDAO extends AbstractHibernateDao {

    public IndexDAO() {
        super();
        settClass(IndexEntity.class);
    }

    public void multiInsert(StringBuilder insertQuery) {
        Consumer<Session> save = session -> session.createNativeQuery(
                "Insert into `index`(`rank`, lemma_id, page_id) values "
                        + insertQuery).executeUpdate();
        inSessionWithTransaction(save);
    }

    public List<IndexEntity> findIndexesByPage(Session session,
                                               PageEntity pageEntity) {
        return findByParameter(session,
                "pageEntity", pageEntity,Integer.MAX_VALUE);
    }
}
