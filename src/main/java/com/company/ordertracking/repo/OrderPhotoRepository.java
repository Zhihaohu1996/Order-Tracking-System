package com.company.ordertracking.repo;

import com.company.ordertracking.entity.OrderPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderPhotoRepository extends JpaRepository<OrderPhoto, Long> {
    List<OrderPhoto> findByOrder_IdOrderByCreatedAtDesc(Long orderId);
}
