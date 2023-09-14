package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    @Query(value = "SELECT * FROM page WHERE path=?1", nativeQuery = true)
    public Page findByPath(String path);
}
