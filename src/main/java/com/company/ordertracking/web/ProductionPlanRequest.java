package com.company.ordertracking.web;

import java.time.LocalDate;

public class ProductionPlanRequest {
    public LocalDate plannedStartDate;
    public LocalDate plannedEndDate;
    public LocalDate plannedShipDate;
    public String note;
}
