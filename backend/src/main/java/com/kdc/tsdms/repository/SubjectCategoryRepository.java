package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.SubjectCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubjectCategoryRepository extends JpaRepository<SubjectCategory, Integer> {

    Optional<SubjectCategory> findByIdAndDeletedFalse(Integer id);

    boolean existsByCodeAndDeletedFalse(String code);

    boolean existsByCodeAndDeletedFalseAndIdNot(String code, Integer id);

    List<SubjectCategory> findByDeletedFalseOrderByName();

    List<SubjectCategory> findByStatusAndDeletedFalseOrderByName(String status);

    @Query("""
            SELECT sc FROM SubjectCategory sc
            WHERE sc.deleted = false
              AND (:keyword IS NULL OR sc.name LIKE %:keyword% OR sc.code LIKE %:keyword%)
            """)
    Page<SubjectCategory> search(@Param("keyword") String keyword, Pageable pageable);
}
