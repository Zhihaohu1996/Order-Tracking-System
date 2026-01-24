package com.company.ordertracking.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 多次入仓记录（一次入仓动作一条记录，可在工序未完成时记录）。
 */
@Entity
@Table(name = "warehouse_receipt_log")
public class WarehouseReceiptLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "received_by", length = 100)
    private String receivedBy;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private SalesOrder order;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<WarehouseReceiptLogItem> items = new ArrayList<>();

    public WarehouseReceiptLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }

    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public SalesOrder getOrder() { return order; }
    public void setOrder(SalesOrder order) { this.order = order; }

    public List<WarehouseReceiptLogItem> getItems() { return items; }

    public void setItems(List<WarehouseReceiptLogItem> items) {
        this.items.clear();
        if (items == null) return;
        for (WarehouseReceiptLogItem it : items) {
            it.setReceipt(this);
            this.items.add(it);
        }
    }
}
