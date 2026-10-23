package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.AccessGrantView;
import com.curo.curo_backend.dto.AccessRequest;
import com.curo.curo_backend.dto.ApproveRequest;
import com.curo.curo_backend.dto.PagedResponse;
import com.curo.curo_backend.service.AccessGrantService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/access-grants")
public class AccessGrantController {

    @Autowired
    private AccessGrantService accessGrantService;

    @PostMapping
    public ResponseEntity<AccessGrantView> request(@Valid @RequestBody AccessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accessGrantService.requestAccess(request));
    }

    @PostMapping("/{id}/approve")
    public AccessGrantView approve(@PathVariable String id, @RequestBody(required = false) ApproveRequest request) {
        return accessGrantService.approve(id, request);
    }

    @PostMapping("/{id}/revoke")
    public AccessGrantView revoke(@PathVariable String id) {
        return accessGrantService.revoke(id);
    }

    @GetMapping
    public PagedResponse<AccessGrantView> list(@RequestParam(required = false) String status,
                                               @RequestParam(required = false) String careId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return accessGrantService.list(status, careId, page, size);
    }
}
