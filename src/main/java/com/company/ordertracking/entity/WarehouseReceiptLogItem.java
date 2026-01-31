package com.company.ordertracking.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/**
 * 多次入仓记录的明细（关联到订单行项目）。
 */
@Entity
@Table(name = "warehouse_receipt_log_item")
public class WarehouseReceiptLogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    @JsonIgnore
    private WarehouseReceiptLog receipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(nullable = false)
    private Integer qty;

    public WarehouseReceiptLogItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public WarehouseReceiptLog getReceipt() { return receipt; }
    public void setReceipt(WarehouseReceiptLog receipt) { this.receipt = receipt; }

    public OrderItem getOrderItem() { return orderItem; }
    public void setOrderItem(OrderItem orderItem) { this.orderItem = orderItem; }

    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
}
