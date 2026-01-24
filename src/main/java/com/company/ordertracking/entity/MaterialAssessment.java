package com.company.ordertracking.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "material_assessment")
public class MaterialAssessment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "material_name", nullable = false, length = 100)
    private String materialName;

    @Column(name = "procurement_type", nullable = false, length = 50)
    private String procurementType; // "EXTERNAL_PURCHASE" / "IN_STOCK"

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonIgnore
    private SalesOrder order;

    public MaterialAssessment() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }

    public String getProcurementType() { return procurementType; }
    public void setProcurementType(String procurementType) { this.procurementType = procurementType; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public SalesOrder getOrder() { return order; }
    public void setOrder(SalesOrder order) { this.order = order; }
}
