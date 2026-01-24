package com.company.ordertracking.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class MaterialsRequest {
    @Valid
    public List<Material> materials;

    public static class Material {
        public Long id;

        @NotBlank
        public String materialName;

        @NotBlank
        public String procurementType; // EXTERNAL_PURCHASE / IN_STOCK

        public String note;
    }
}
