package com.kdc.tsdms.repository;

import com.kdc.tsdms.entity.TeacherEvaluation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository bảng TeacherEvaluation — thêm method truy vấn theo nhu cầu feature tại đây. */
public interface TeacherEvaluationRepository extends JpaRepository<TeacherEvaluation, Integer> {

    /** Mọi đánh giá còn hiệu lực — dùng tính điểm trung bình cho dashboard. */
    List<TeacherEvaluation> findByDeletedFalse();
}
