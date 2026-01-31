package com.company.ordertracking.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

public class OrderRequest {

    @NotBlank
    public String orderNo;

    public String customerName;
    public String contact;
    public String currency;
    public String paymentTerms;
    public BigDecimal totalAmount;
    public String productReq;
    public String packagingReq;
    public String status;

    @Valid
    public List<Item> items;

    public static class Item {
        @NotBlank
        public String productName;
        public String spec;
        public Integer quantity;
        public Double unitPrice;
        public String notes;
    }
}
