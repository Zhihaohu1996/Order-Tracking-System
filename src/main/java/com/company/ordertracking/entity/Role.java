package com.company.ordertracking.entity;

public enum Role {
    GM, SALES, PMC, PRODUCTION, WAREHOUSE, GUEST;

    public static Role fromHeader(String raw) {
        if (raw == null || raw.isBlank()) return GM;
        try {
            return Role.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return GM;
        }
    }

    public boolean canSeeSensitive() {
        return this == GM || this == SALES;
    }

    public boolean canEditOrderBasics() {
        return this == GM || this == SALES;
    }

    public boolean canDeleteOrder() {
        return this == GM;
    }

    public boolean canManageProductionPlan() {
        return this == GM || this == PMC;
    }

    public boolean canUpdateProcessProgress() {
        return this == GM || this == PRODUCTION;
    }

    public boolean canWarehouseOps() {
        return this == GM || this == WAREHOUSE;
    }

    public boolean canManageShippingPlan() {
        return this == GM || this == SALES;
    }
}
