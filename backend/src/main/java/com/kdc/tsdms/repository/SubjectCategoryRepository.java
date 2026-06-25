package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.SubjectCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng danh mục SubjectCategory — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface SubjectCategoryRepository extends JpaRepository<SubjectCategory, Integer> {

    List<SubjectCategory> findByDeletedFalseOrderByName();

    Optional<SubjectCategory> findByCodeAndDeletedFalse(String code);

    Optional<SubjectCategory> findByIdAndDeletedFalse(Integer id);
}
