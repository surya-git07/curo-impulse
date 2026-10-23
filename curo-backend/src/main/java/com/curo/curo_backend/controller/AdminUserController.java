package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.AdminUserView;
import com.curo.curo_backend.dto.PagedResponse;
import com.curo.curo_backend.dto.RejectRequest;
import com.curo.curo_backend.service.AdminUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired
    private AdminUserService service;

    @GetMapping
    public ResponseEntity<PagedResponse<AdminUserView>> list(@RequestParam(required = false) String verificationStatus,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.list(verificationStatus, page, size));
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<AdminUserView> verify(@PathVariable String id) {
        return ResponseEntity.ok(service.verify(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<AdminUserView> reject(@PathVariable String id, @RequestBody(required = false) RejectRequest request) {
        return ResponseEntity.ok(service.reject(id, request == null ? null : request.getReason()));
    }
}
