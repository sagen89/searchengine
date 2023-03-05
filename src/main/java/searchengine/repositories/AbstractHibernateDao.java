package searchengine.repositories;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Repository;


import java.util.ArrayList;
import java.util.List;

@Repository
public abstract class AbstractHibernateDao<T> {
    private Class<T> tClass;
    @Autowired
    EntityManagerFactory entityManagerFactory;

    public Session getSession(){
        return entityManagerFactory
                .createEntityManager()
                .unwrap(Session.class);
    }

    public void settClass(Class<T> tClass) {
        this.tClass = tClass;
    }

    public void  save(Object object){
        Session session = getSession();

        Transaction tx = null;

        try {
            tx = session.beginTransaction();
            session.persist(object);

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

    public T findOneById(int id){
        return getSession().find(tClass,id);
    }

    protected List findOneByParameter(Session session, String field, T parameter, int limit){
        List result = session
                .createQuery("from " +  tClass.getSimpleName() +
                        " where " +  field + " = :parameter", tClass)
                .setParameter("parameter", parameter)
                .setMaxResults(limit)
                .list();
        if (result.size() == 0) {
            return new ArrayList<>();
        }
        return result;
    }

    protected T findOneByTwoParameters(Session session, String field1, T parameter1,
                                       String field2, T parameter2){
        List result = session
                .createQuery("from " +  tClass.getSimpleName() +
                        " where " +  field1 + " = :parameter1" +
                        " and " +  field2 + " = :parameter2", tClass)
                .setParameter("parameter1", parameter1)
                .setParameter("parameter2", parameter2)
                .setMaxResults(1)
                .list();
        if (result.size() == 0) {
            return null;
        }

        return (T) result.get(0);
    }

    public void  deleteById(int id){
        Session session = getSession();

        Transaction tx = null;

        try {
            tx = session.beginTransaction();

//                Object findEntity = session.find(tClass, id);
//                Object mergedEntity = session.merge(findEntity);

//                session.remove(mergedEntity);
//            session.detach(session.find(tClass, id));
                session.remove(session.find(tClass, id));
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

    protected void deleteByParameter(Session session, String field, T parameter){
        session.createQuery("delete from " +  tClass.getSimpleName() +
                " where " +  field + " = :parameter")
                .setParameter("parameter", parameter).executeUpdate();
    }

}


