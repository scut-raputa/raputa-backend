package cn.scut.raputa.repository;

import cn.scut.raputa.entity.TempFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TempFileRepository extends JpaRepository<TempFile, String> {
}
