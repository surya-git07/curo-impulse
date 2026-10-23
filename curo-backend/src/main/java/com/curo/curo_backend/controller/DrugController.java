package com.curo.curo_backend.controller;

import org.springframework.context.annotation.Profile;
import com.curo.curo_backend.model.DrugMaster;
import com.curo.curo_backend.repository.DrugMasterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Profile("legacy-jpa")
@RestController
@RequestMapping("/drugs")
public class DrugController {

    @Autowired
    private DrugMasterRepository drugMasterRepository;

    @GetMapping("/list")
    public ResponseEntity<List<DrugMaster>> list() {
        return ResponseEntity.ok(drugMasterRepository.findAll());
    }
}