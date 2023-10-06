package searchengine.repository;

import com.fasterxml.jackson.databind.util.RawValue;
import com.mysql.cj.x.protobuf.MysqlxDatatypes;
import com.mysql.cj.xdevapi.JsonArray;
import com.mysql.cj.xdevapi.JsonString;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryRewriter;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import java.util.HashSet;


@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {
    //public String query1 = "(3, 88, 1.0)";
    @Query(value = "SELECT * FROM `index` WHERE page_id=?1 AND lemma_id=?2", nativeQuery = true)
    public Index findByPageIdAndLemmaId(int pageId, int lemmaId);

    @Query(value = "SELECT * FROM `index` WHERE page_id=?1", nativeQuery = true)
    public HashSet<Index> findByPageId(int pageId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM `index`", nativeQuery = true)
    public void deleteAll();

    @Modifying
    @Transactional
    @Query(value = "ALTER TABLE `index` AUTO_INCREMENT=1", nativeQuery = true)
    public void resetIdCounter();

//    @Modifying
//    @Transactional
//    @Query(value = "INSERT INTO `index` (`lemma_id`, `page_id`, `rank`) VALUES ", nativeQuery = true)
//    public void insertData(String jsonValues);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM `index` WHERE page_id=?1", nativeQuery = true)
    public void deleteByPageId(int pageId);
}
