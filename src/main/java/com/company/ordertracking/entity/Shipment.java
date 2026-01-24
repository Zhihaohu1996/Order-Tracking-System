package com.company.ordertracking.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "shipment",
        uniqueConstraints = {@UniqueConstraint(name = "uk_ship_order", columnNames = "order_id")})
public class Shipment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "planned_ship_date")
    private LocalDate plannedShipDate;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "confirmed_by", length = 50)
    private String confirmedBy;

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

    public Shipment() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getPlannedShipDate() { return plannedShipDate; }
    public void setPlannedShipDate(LocalDate plannedShipDate) { this.plannedShipDate = plannedShipDate; }

    public LocalDateTime getShippedAt() { return shippedAt; }
    public void setShippedAt(LocalDateTime shippedAt) { this.shippedAt = shippedAt; }

    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public SalesOrder getOrder() { return order; }
    public void setOrder(SalesOrder order) { this.order = order; }
}
