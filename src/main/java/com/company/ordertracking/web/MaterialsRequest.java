package com.company.ordertracking.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

import java.util.List;

public class MaterialsRequest {
    @Valid
    public List<Material> materials;

    public static class Material {
        public Long id;

        @NotBlank
        public String materialName;

        @Min(0)
        public Integer quantity; // optional


        @NotBlank
        public String procurementType; // EXTERNAL_PURCHASE / IN_STOCK

        public String note;
    }
}
