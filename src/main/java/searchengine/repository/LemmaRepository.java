package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    @Query(value = "SELECT * FROM lemma WHERE site_id=?1 AND lemma=?2", nativeQuery = true)
    public Lemma findBySiteIdAndLemma(int siteId, String word);
}
