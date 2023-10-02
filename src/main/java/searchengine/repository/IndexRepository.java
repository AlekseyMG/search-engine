package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {
    @Query(value = "SELECT * FROM `index` WHERE page_id=?1 AND lemma_id=?2", nativeQuery = true)
    public Index findByPageIdAndLemmaId(int pageId, int lemmaId);
}
