package com.company.ordertracking.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_process")
public class OrderProcess {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "process_name", nullable = false, length = 100)
    private String processName;

    @Column(name = "target_quantity")
    private Integer targetQuantity;

    @Column(name = "finished_quantity", nullable = false)
    private Integer finishedQuantity = 0;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private SalesOrder order;

    public OrderProcess() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public Integer getTargetQuantity() { return targetQuantity; }
    public void setTargetQuantity(Integer targetQuantity) { this.targetQuantity = targetQuantity; }

    public Integer getFinishedQuantity() { return finishedQuantity; }
    public void setFinishedQuantity(Integer finishedQuantity) { this.finishedQuantity = finishedQuantity; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public SalesOrder getOrder() { return order; }
    public void setOrder(SalesOrder order) { this.order = order; }
}
