package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.AlertView;
import com.curo.curo_backend.dto.PagedResponse;
import com.curo.curo_backend.service.AlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own alerts. Patients, caregivers and doctors all receive some. */
@RestController
@RequestMapping("/alerts")
public class AlertController {

    @Autowired
    private AlertService alertService;

    @GetMapping
    public PagedResponse<AlertView> list(@RequestParam(defaultValue = "false") boolean unread,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return alertService.list(unread, page, size);
    }

    @PostMapping("/{id}/read")
    public AlertView read(@PathVariable String id) {
        return alertService.markRead(id);
    }
}
