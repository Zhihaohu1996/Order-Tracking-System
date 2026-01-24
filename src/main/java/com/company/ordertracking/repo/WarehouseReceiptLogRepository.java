package com.company.ordertracking.repo;

import com.company.ordertracking.entity.WarehouseReceiptLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WarehouseReceiptLogRepository extends JpaRepository<WarehouseReceiptLog, Long> {
    List<WarehouseReceiptLog> findByOrder_IdOrderByReceivedAtDesc(Long orderId);
    boolean existsByOrder_Id(Long orderId);
}
