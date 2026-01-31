package com.company.ordertracking.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 每日/多次入仓记录：一次提交对应 warehouse_receipt_log + 多条 warehouse_receipt_log_item。
 */
public class WarehouseReceiptLogRequest {

    public LocalDateTime receivedAt; // optional, default now
    public String receivedBy;
    public String note;

    @Valid
    public List<Item> items;

    public static class Item {
        @NotNull
        public Long orderItemId;

        @NotNull
        public Integer qty;
    }
}
