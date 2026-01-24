package com.company.ordertracking.repo;

import com.company.ordertracking.entity.OrderProcess;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderProcessRepository extends JpaRepository<OrderProcess, Long> {
    List<OrderProcess> findByOrder_IdOrderByIdAsc(Long orderId);
    void deleteByOrder_Id(Long orderId);
}
