package com.company.ordertracking.repo;

import com.company.ordertracking.entity.WarehouseReceiptLogItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WarehouseReceiptLogItemRepository extends JpaRepository<WarehouseReceiptLogItem, Long> {

    @Query("""
            select i.orderItem.id, coalesce(sum(i.qty), 0)
            from WarehouseReceiptLogItem i
            where i.receipt.order.id = :orderId
            group by i.orderItem.id
            """)
    List<Object[]> sumQtyByOrderItemId(@Param("orderId") Long orderId);
}
