package cn.scut.raputa.repository;

import cn.scut.raputa.entity.Model;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ModelRepository extends JpaRepository<Model, String>, JpaSpecificationExecutor<Model> {

    long countByUploadTimeBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT m.uploader, COUNT(m) as cnt FROM Model m GROUP BY m.uploader ORDER BY cnt DESC")
    List<Object[]> findUploaderStats(Pageable pageable);
}
