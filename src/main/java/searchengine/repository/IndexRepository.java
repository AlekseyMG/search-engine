package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import java.util.HashSet;


@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {
    @Query(value = "SELECT * FROM `index` WHERE page_id=?1 AND lemma_id=?2", nativeQuery = true)
    public Index findByPageIdAndLemmaId(int pageId, int lemmaId);

    @Query(value = "SELECT * FROM `index` WHERE page_id=?1", nativeQuery = true)
    public HashSet<Index> findByPageId(int pageId);

    @Query(value = "SELECT page_id FROM `index` WHERE lemma_id=?1", nativeQuery = true)
    public HashSet<Integer> findPagesIdsByLemmaId(int lemmaId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM `index`", nativeQuery = true)
    public void deleteAll();

    @Modifying
    @Transactional
    @Query(value = "ALTER TABLE `index` AUTO_INCREMENT=1", nativeQuery = true)
    public void resetIdCounter();

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM `index` WHERE page_id=?1", nativeQuery = true)
    public void deleteByPageId(int pageId);
}
