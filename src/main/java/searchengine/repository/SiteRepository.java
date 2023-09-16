package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {
    @Query(value = "SELECT * FROM site WHERE url LIKE '%?1%'", nativeQuery = true)
    public Site findByPath(String path);
    @Query(value = "DELETE FROM site WHERE url LIKE '%?1%'", nativeQuery = true)
    public void deleteBySitePath(String path);
}
