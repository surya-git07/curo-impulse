package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.DrugView;
import com.curo.curo_backend.repository.DrugMasterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/drugs")
public class DrugController {

    @Autowired
    private DrugMasterRepository drugMasterRepository;

    // Reference data, not patient data, so no AccessGrant is involved
    @GetMapping("/list")
    public List<DrugView> list() {
        return drugMasterRepository.findAll(Sort.by("name")).stream()
                .map(d -> new DrugView(d.getId(), d.getName(), d.getDrugClass(), d.getScheduleType()))
                .toList();
    }
}
