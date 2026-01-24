package com.company.ordertracking.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales_order")
public class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="order_no", nullable = false, length = 64, unique = true)
    private String orderNo;

    @Column(name="customer_name", length = 128)
    private String customerName;

    @Column(length = 128)
    private String contact;

    @Column(length = 16)
    private String currency;

    @Column(name="payment_terms", length = 64)
    private String paymentTerms;

    @Column(name="total_amount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name="product_req", length = 255)
    private String productReq;

    @Column(name="packaging_req", length = 255)
    private String packagingReq;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private OrderStatus status = OrderStatus.DRAFT;

    @Column(name="created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    public SalesOrder() {}

    public void setItems(List<OrderItem> items) {
        this.items.clear();
        if (items == null) return;
        for (OrderItem it : items) {
            it.setOrder(this); // ✅ critical for NOT NULL FK
            this.items.add(it);
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getProductReq() { return productReq; }
    public void setProductReq(String productReq) { this.productReq = productReq; }

    public String getPackagingReq() { return packagingReq; }
    public void setPackagingReq(String packagingReq) { this.packagingReq = packagingReq; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<OrderItem> getItems() { return items; }
}
