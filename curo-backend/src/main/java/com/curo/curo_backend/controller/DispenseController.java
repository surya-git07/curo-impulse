package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.DispenseOrderRequest;
import com.curo.curo_backend.dto.DispenseView;
import com.curo.curo_backend.service.DispenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pharmacy")
public class DispenseController {

    @Autowired
    private DispenseService dispenseService;

    @PostMapping("/dispense")
    public DispenseView dispense(@RequestBody DispenseOrderRequest request) {
        return dispenseService.dispense(request);
    }
}
