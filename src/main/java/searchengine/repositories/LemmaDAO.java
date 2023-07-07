package searchengine.repositories;

import org.hibernate.*;
import org.springframework.stereotype.Repository;
import searchengine.model.LemmaEntity;

import java.util.List;

@Repository
public class LemmaDAO extends AbstractHibernateDao{

    public LemmaDAO() {
        super();
        settClass(LemmaEntity.class);
    }

    public void saveOrUpdateWithIncrementOrDecrement(Session session,
                                                     LemmaEntity lemmaEntity,
                                                     int value) {

        LemmaEntity foundEntity = (LemmaEntity) findOneByTwoParameters(session,
                "lemma", lemmaEntity.getLemma(),
                "siteEntity", lemmaEntity.getSiteEntity());
        if (foundEntity == null){
            session.save(lemmaEntity);
        } else {
            lemmaEntity.setFrequency(foundEntity.getFrequency() + value);
            lemmaEntity.setId(foundEntity.getId());
            session.merge(lemmaEntity);
        }
    }

    public List<LemmaEntity> findLemmasByNormalForm(Session session,
                                                    String normalForm) {

        return  findByParameter(session,"lemma", normalForm, Integer.MAX_VALUE );
    }
}
