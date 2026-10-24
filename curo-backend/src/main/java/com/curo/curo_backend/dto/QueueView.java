package com.curo.curo_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class QueueView {

    @Data
    @AllArgsConstructor
    public static class Counts {
        private int total;
        private int done;
        private int pending;
    }

    private Counts counts;
    private List<QueueEntryView> entries;
}
