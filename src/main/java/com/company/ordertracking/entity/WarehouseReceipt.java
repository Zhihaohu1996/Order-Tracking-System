package com.company.ordertracking.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "warehouse_receipt",
        uniqueConstraints = {@UniqueConstraint(name = "uk_receipt_order", columnNames = "order_id")})
public class WarehouseReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "received_at", insertable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "received_by", length = 50)
    private String receivedBy;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private SalesOrder order;

    public WarehouseReceipt() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getReceivedAt() { return receivedAt; }

    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public SalesOrder getOrder() { return order; }
    public void setOrder(SalesOrder order) { this.order = order; }
}
