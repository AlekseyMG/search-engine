package searchengine.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.ParserSetting;
import searchengine.model.Index;
import java.util.*;

@Service
@Getter
@RequiredArgsConstructor
@Transactional
public class BatchIndexWriter {

    private final ParserSetting parserSetting;
    private volatile List<Index> indices = Collections.synchronizedList(new ArrayList<>());

    @PersistenceContext
    private EntityManager entityManager;

    public synchronized void addForSave(Index index) {
        indices.add(index);
        if (indices.size() + 1 > parserSetting.getBatchSize()) {
            bulkSave(indices);
            indices = Collections.synchronizedList(new ArrayList<>());
        }
    }

    public synchronized void close() {
        bulkSave(indices);
        entityManager.flush();
        entityManager.clear();
    }

    public synchronized void bulkSave(List<Index> entities) throws ConstraintViolationException {
        System.out.println("++++++++++bulkSave+++++++");
        int i = 0;
        for (Index index : entities) {

            if (index.getId() == null) {
                entityManager.persist(index);
            } else {
                entityManager.merge(index);
            }
            i++;

            if (i % parserSetting.getBatchSize() == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
    }
}
