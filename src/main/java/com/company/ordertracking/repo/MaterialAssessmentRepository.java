package com.company.ordertracking.repo;

import com.company.ordertracking.entity.MaterialAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialAssessmentRepository extends JpaRepository<MaterialAssessment, Long> {
    List<MaterialAssessment> findByOrder_IdOrderByIdAsc(Long orderId);
    void deleteByOrder_Id(Long orderId);
}
