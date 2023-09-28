package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import java.util.HashSet;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    //@Query(value = "SELECT * FROM page WHERE path LIKE (%?1%) limit 1", nativeQuery = true)
    @Query(value = "SELECT * FROM page WHERE path=?1", nativeQuery = true)
    public Page findByPath(String path);
    @Query(value = "SELECT * FROM page WHERE site_id=?1 AND path=?2", nativeQuery = true)
    public Page findBySiteIdAndPath(int siteId, String path);
    @Query(value = "SELECT * FROM page WHERE site_id=?1", nativeQuery = true)
    public HashSet<Page> findPagesBySiteId(int siteId);
    @Query(value = "SELECT count(*) FROM page WHERE site_id=?1", nativeQuery = true)
    public int getPagesCountBySiteId(int siteId);

//    @Query(value = "SELECT * FROM page WHERE path LIKE (%?1%)", nativeQuery = true)
//    public List<Page> findAllByPath(String path);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM page WHERE site_id=?1", nativeQuery = true)
    public void deleteBySiteId(int siteId);

    @Modifying
    @Transactional
    @Query(value = "ALTER TABLE page AUTO_INCREMENT=1", nativeQuery = true)
    public void resetIdCounter();
}
