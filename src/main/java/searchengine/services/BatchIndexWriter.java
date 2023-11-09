package searchengine.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.ParserSetting;
import searchengine.model.Index;
import java.util.*;

@Slf4j
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
        }
    }

    public synchronized void close() {
        if (!indices.isEmpty()) {
            bulkSave(indices);
            entityManager.flush();
            entityManager.clear();
        }
    }

    public synchronized void bulkSave(List<Index> entities) throws ConstraintViolationException {
        StringBuilder insertQuery = new StringBuilder();
        int i = 0;
        for (Index index : entities) {
            insertQuery.append(insertQuery.length() == 0 ? "" : ",")
                    .append("('")
                    .append(index.getLemma().getId())
                    .append("', '")
                    .append(index.getPage().getId())
                    .append("', '")
                    .append(index.getRank())
                    .append("')");
        }
        String firstQueryPart = "INSERT INTO `index` (`lemma_id`, `page_id`, `rank`) VALUES ";
        log.info(firstQueryPart + "of " + indices.size() + " index entities.");
        Query query = entityManager.createNativeQuery(firstQueryPart + insertQuery);
        query.executeUpdate();
        entityManager.flush();
        entityManager.clear();
        indices = Collections.synchronizedList(new ArrayList<>());
    }
}
