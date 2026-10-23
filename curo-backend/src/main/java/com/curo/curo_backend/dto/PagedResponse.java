package com.curo.curo_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PagedResponse<T> {
    private int page;
    private int size;
    private long total;
    private List<T> items;
}
