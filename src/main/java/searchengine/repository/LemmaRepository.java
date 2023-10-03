package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    @Query(value = "SELECT * FROM lemma WHERE site_id=?1 AND lemma=?2", nativeQuery = true)
    public Lemma findBySiteIdAndLemma(int siteId, String word);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM lemma WHERE site_id=?1", nativeQuery = true)
    public void deleteBySiteId(int siteId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM lemma WHERE site_id=?1 AND lemma=?2", nativeQuery = true)
    public void deleteBySiteIdAndLemma(int siteId, String word);

    @Modifying
    @Transactional
    @Query(value = "ALTER TABLE lemma AUTO_INCREMENT=1", nativeQuery = true)
    public void resetIdCounter();
}
