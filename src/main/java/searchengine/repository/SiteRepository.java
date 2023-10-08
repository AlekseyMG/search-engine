package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.Site;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {
    @Modifying
    @Transactional
    @Query(value = "ALTER TABLE site AUTO_INCREMENT=1", nativeQuery = true)
    public void resetIdCounter();
    @Query(value = "SELECT * FROM site WHERE url LIKE %?1%", nativeQuery = true)
    public Site findByUrl(String url);

//    @Query(value = "DELETE FROM site WHERE url LIKE '%?1%'", nativeQuery = true)
//    public void deleteBySitePath(String path);
}
