package searchengine.repositories;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.FlushModeType;
import lombok.Setter;
import org.hibernate.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

@Repository
public abstract class AbstractHibernateDao<T> {

    private Class<T> tClass;
    @Autowired
    @Setter
    private EntityManagerFactory entityManagerFactory;


    public void settClass(Class<T> tClass) {
        this.tClass = tClass;
    }

    public Session getSession(){
        return entityManagerFactory.createEntityManager()
                .unwrap(Session.class);
    }

    public void inSession(Consumer<Session> action) {
        try (Session session = getSession()) {
            session.setFlushMode(FlushModeType.COMMIT);
            action.accept(session);
        }
    }

    public void inSessionWithTransaction(Consumer<Session> action){
        inSession(
                session -> {
                    Transaction tx = session.getTransaction();
                    try {
                        tx.begin();
                        action.accept(session);
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
        );
    }

    public <T> T fromSession(Function<Session,T> action) {
        try (Session session = getSession()) {
            session.setFlushMode(FlushModeType.COMMIT);
            return action.apply(session);
        }
    }

    public <T> T fromSessionWithTransaction(Function<Session,T> action) {
        return fromSession(
                session -> {
                    T result = null;
                    Transaction tx = session.getTransaction();
                    try {
                        tx.begin();
                        result = action.apply( session );
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
                    return result;
                }
        );
    }

    public List<T> getEntities(Session session) {
        return session
                .createSelectionQuery("from " +  tClass.getSimpleName(),
                        tClass).getResultList();
    }


    public T findOneById(Session session, int id) {
        return session.get(tClass, id);
    }

    protected List<T> findByParameter(Session session, String field,
                                      T parameter, int limit) {

        List<T> result = session
                .createSelectionQuery("from " +  tClass.getSimpleName() +
                        " where " +  field + " = :parameter", tClass)
                .setParameter("parameter", parameter)
                .setMaxResults(limit)
                .list();
        if (result.size() == 0) {
            return new ArrayList<T>(1){{add(null);}};
        }
        return result;
    }
    public T findOneByTwoParameters(Session session, String field1, T parameter1,
                                    String field2, T parameter2){

        List<T> result = findByTwoParameters(session, field1, parameter1,
                field2, parameter2, 1);
        return result.size() == 0 ? null : result.get(0);
    }

    protected List<T> findByTwoParameters(Session session, String field1, T parameter1,
                                          String field2, T parameter2, int limit){

        List<T> result = session
                .createSelectionQuery("from " +  tClass.getSimpleName() +
                        " where " +  field1 + " = :parameter1" +
                        " and " +  field2 + " = :parameter2", tClass)
                .setParameter("parameter1", parameter1)
                .setParameter("parameter2", parameter2)
                .setMaxResults(limit)
                .list();
        return result.size() == 0 ? new ArrayList<>(){{add(null);}} : result;
    }

    public void delete(Session session,T entity){
        session.remove(session.merge(entity));
    }
}


