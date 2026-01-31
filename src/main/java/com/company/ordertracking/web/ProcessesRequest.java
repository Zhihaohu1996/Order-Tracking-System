package com.company.ordertracking.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class ProcessesRequest {
    @Valid
    public List<Process> processes;

    public static class Process {
        public Long id;

        @NotBlank
        public String processName;

        public Integer targetQuantity;
        public Integer finishedQuantity;
        public String note;
    }
}
