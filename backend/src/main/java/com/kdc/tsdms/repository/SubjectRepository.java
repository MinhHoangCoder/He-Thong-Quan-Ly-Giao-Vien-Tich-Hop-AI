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

    boolean existsByCodeAndDeletedFalse(String code);

    boolean existsByCodeAndDeletedFalseAndIdNot(String code, Integer id);

    /**
     * Dùng cho trang "Nhóm môn học": liệt kê môn theo từng nhóm (CRUD môn lồng
     * trong nhóm).
     */
    List<Subject> findByCategoryIdAndDeletedFalseOrderByName(Integer categoryId);

    List<Subject> findByDeletedFalseOrderByName();
}
