package cn.scut.raputa.repository;

import cn.scut.raputa.entity.Model;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelRepository extends JpaRepository<Model, String>, JpaSpecificationExecutor<Model> {
}
