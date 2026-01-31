package com.company.ordertracking.repo;

import com.company.ordertracking.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    Optional<Shipment> findByOrder_Id(Long orderId);
}
