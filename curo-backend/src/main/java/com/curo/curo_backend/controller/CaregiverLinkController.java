package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.CaregiverLinkRequest;
import com.curo.curo_backend.dto.CaregiverLinkView;
import com.curo.curo_backend.service.CaregiverLinkService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/caregiver-links")
public class CaregiverLinkController {

    @Autowired
    private CaregiverLinkService caregiverLinkService;

    @PostMapping
    public ResponseEntity<CaregiverLinkView> add(@Valid @RequestBody CaregiverLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(caregiverLinkService.add(request));
    }

    @GetMapping
    public List<CaregiverLinkView> list() {
        return caregiverLinkService.list();
    }

    @PostMapping("/{id}/revoke")
    public CaregiverLinkView revoke(@PathVariable String id) {
        return caregiverLinkService.revoke(id);
    }
}
