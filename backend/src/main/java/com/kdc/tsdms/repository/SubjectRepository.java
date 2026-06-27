package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.Subject;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository bảng Subject — thêm method truy vấn theo nhu cầu feature tại đây.
 */
public interface SubjectRepository extends JpaRepository<Subject, Integer> {

    List<Subject> findByIdInAndDeletedFalse(List<Integer> ids);

    Optional<Subject> findByIdAndDeletedFalse(Integer id);

    long countByCategoryIdAndDeletedFalse(Integer categoryId);
}
