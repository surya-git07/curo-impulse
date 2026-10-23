package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.DispenseOrderRequest;
import com.curo.curo_backend.dto.DispenseView;
import com.curo.curo_backend.dto.StockUpdateRequest;
import com.curo.curo_backend.dto.StockView;
import com.curo.curo_backend.service.PharmacyStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/pharmacy")
public class PharmacyStockController {

    @Autowired
    private PharmacyStockService pharmacyStockService;

    @GetMapping("/stock/{pharmacyId}")
    public List<StockView> stock(@PathVariable String pharmacyId) {
        return pharmacyStockService.stock(pharmacyId);
    }

    @PutMapping("/stock/{pharmacyId}")
    public StockView updateStock(@PathVariable String pharmacyId, @RequestBody StockUpdateRequest request) {
        return pharmacyStockService.updateStock(pharmacyId, request);
    }

    @PostMapping("/dispense")
    public DispenseView dispense(@RequestBody DispenseOrderRequest request) {
        return pharmacyStockService.dispense(request);
    }
}
