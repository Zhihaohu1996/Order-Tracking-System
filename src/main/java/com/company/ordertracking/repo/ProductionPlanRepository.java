package com.company.ordertracking.repo;

import com.company.ordertracking.entity.ProductionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductionPlanRepository extends JpaRepository<ProductionPlan, Long> {
    Optional<ProductionPlan> findByOrder_Id(Long orderId);
}
