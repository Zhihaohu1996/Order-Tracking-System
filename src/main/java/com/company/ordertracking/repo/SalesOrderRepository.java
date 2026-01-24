package com.company.ordertracking.repo;

import com.company.ordertracking.entity.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    boolean existsByOrderNo(String orderNo);

    List<SalesOrder> findByOrderNoIn(Collection<String> orderNos);
}
