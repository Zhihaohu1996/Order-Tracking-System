package com.company.ordertracking.repo;

import com.company.ordertracking.entity.WarehouseReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WarehouseReceiptRepository extends JpaRepository<WarehouseReceipt, Long> {
    Optional<WarehouseReceipt> findByOrder_Id(Long orderId);
}
