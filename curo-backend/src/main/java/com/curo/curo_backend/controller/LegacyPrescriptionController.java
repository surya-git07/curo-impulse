package com.curo.curo_backend.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import com.curo.curo_backend.dto.PrescriptionGroupRequest;
import com.curo.curo_backend.model.*;
import com.curo.curo_backend.model.legacy.DispensingLog;
import com.curo.curo_backend.model.legacy.DrugMaster;
import com.curo.curo_backend.model.legacy.PharmacyStock;
import com.curo.curo_backend.repository.legacy.DispensingLogRepository;
import com.curo.curo_backend.repository.legacy.PharmacyStockRepository;
import com.curo.curo_backend.model.legacy.Prescription;
import com.curo.curo_backend.model.legacy.PrescriptionGroup;
import com.curo.curo_backend.repository.legacy.DrugMasterRepository;
import com.curo.curo_backend.repository.legacy.PrescriptionGroupRepository;
import com.curo.curo_backend.repository.legacy.PrescriptionRepository;
import com.curo.curo_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;


import java.time.LocalDateTime;
import java.util.*;

@Profile("legacy-jpa")
@RestController
@RequestMapping("/legacy/prescription")
public class LegacyPrescriptionController {
    @Autowired private StaffRepository staffRepository;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private PrescriptionGroupRepository prescriptionGroupRepository;
    @Autowired private VisitRepository visitRepository;
    @Autowired private HospitalRepository hospitalRepository;
    @Autowired private DrugMasterRepository drugMasterRepository;
    @Autowired private PharmacyStockRepository pharmacyStockRepository;
    @Autowired private DispensingLogRepository dispensingLogRepository;

    private static final String SANGAMI_BASE_URL = "https://amr-pvms.onrender.com";
    @Value("${patient-service.api-key}")
    private String apiKey;

    private List<String> getPatientAllergyClasses(String abha) {
        if (abha.equals("ABHA-1001")) {
            return List.of("Penicillin");
        }
        return List.of();
    }

    @PostMapping("/issue")
    public ResponseEntity<?> issuePrescription(@RequestBody PrescriptionGroupRequest request) {

        if (request.getDrugs() == null || request.getDrugs().isEmpty()) {
            return ResponseEntity.badRequest().body("At least one drug is required.");
        }

        List<String> allergyClasses = getPatientAllergyClasses(request.getPatientAbha());

        for (PrescriptionGroupRequest.DrugItem item : request.getDrugs()) {
            Optional<DrugMaster> drugInfo = drugMasterRepository.findAll().stream()
                    .filter(d -> d.getName().equalsIgnoreCase(item.getDrug()))
                    .findFirst();

            if (drugInfo.isPresent() && allergyClasses.stream()
                    .anyMatch(a -> a.equalsIgnoreCase(drugInfo.get().getDrugClass()))) {
                return ResponseEntity.status(409).body(
                        "BLOCKED: Patient is allergic to " + drugInfo.get().getDrugClass() +
                                "-class drugs. " + item.getDrug() + " cannot be prescribed."
                );
            }
        }

        Integer routedPharmacyId = null;
        Integer hospitalId = request.getHospitalId() != null ? request.getHospitalId() : 1;
        Optional<Hospital> hospitalOpt = hospitalRepository.findById(hospitalId);
        if (hospitalOpt.isPresent() && hospitalOpt.get().getPharmacyId() != null) {
            routedPharmacyId = hospitalOpt.get().getPharmacyId();
        }

        PrescriptionGroup group = new PrescriptionGroup();
        group.setPatientAbha(request.getPatientAbha());
        group.setDoctorId(request.getDoctorId());
        group.setHospitalId(hospitalId);
        group.setPharmacyId(routedPharmacyId);
        group.setQrCode(UUID.randomUUID().toString());
        group.setStatus("ISSUED");
        group.setIssuedAt(LocalDateTime.now());
        PrescriptionGroup savedGroup = prescriptionGroupRepository.save(group);

        List<Prescription> savedItems = new ArrayList<>();
        for (PrescriptionGroupRequest.DrugItem item : request.getDrugs()) {
            Prescription p = new Prescription();
            p.setPatientAbha(request.getPatientAbha());
            p.setDoctorId(request.getDoctorId());
            p.setDrug(item.getDrug());
            p.setDose(item.getDose());
            p.setDuration(item.getDuration());
            p.setStatus("ISSUED");
            p.setIssuedAt(savedGroup.getIssuedAt());
            p.setPrescriptionGroupId(savedGroup.getId());
            savedItems.add(prescriptionRepository.save(p));
        }

        String hospitalNameForNotify = hospitalOpt.isPresent() ? hospitalOpt.get().getName() : "Unknown Hospital";
        notifyPatientService(request, savedGroup, savedItems, hospitalNameForNotify);

        List<Visit> visits = visitRepository.findAll();
        visits.stream()
                .filter(v -> v.getPatientAbha().equals(request.getPatientAbha()) && v.getStatus().equals("WAITING"))
                .findFirst()
                .ifPresent(v -> {
                    v.setStatus("COMPLETED");
                    visitRepository.save(v);
                });

        Map<String, Object> response = new HashMap<>();
        response.put("groupId", savedGroup.getId());
        response.put("qrCode", savedGroup.getQrCode());
        response.put("drugs", savedItems);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-qr/{qrCode}")
    public ResponseEntity<?> getByQr(@PathVariable String qrCode) {
        Optional<PrescriptionGroup> groupOpt = prescriptionGroupRepository.findByQrCode(qrCode);
        if (groupOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Invalid or expired QR code");
        }
        PrescriptionGroup group = groupOpt.get();
        if ("DISPENSED".equals(group.getStatus())) {
            return ResponseEntity.status(409).body("This prescription was already dispensed — QR cannot be reused");
        }
        if ("REVOKED".equals(group.getStatus())) {
            return ResponseEntity.status(409).body("This prescription was recalled by the doctor and is no longer valid.");
        }

        List<Prescription> items = prescriptionRepository.findAll().stream()
                .filter(p -> group.getId().equals(p.getPrescriptionGroupId()))
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("groupId", group.getId());
        response.put("patientAbha", group.getPatientAbha());
        response.put("status", group.getStatus());
        response.put("issuedAt", group.getIssuedAt());
        response.put("drugs", items);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<?> getGroupById(@PathVariable Integer groupId) {
        Optional<PrescriptionGroup> groupOpt = prescriptionGroupRepository.findById(groupId);
        if (groupOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Prescription group not found");
        }
        PrescriptionGroup group = groupOpt.get();
        List<Prescription> items = prescriptionRepository.findAll().stream()
                .filter(p -> groupId.equals(p.getPrescriptionGroupId()))
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("groupId", group.getId());
        response.put("patientAbha", group.getPatientAbha());
        response.put("status", group.getStatus());
        response.put("issuedAt", group.getIssuedAt());
        response.put("drugs", items);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/group/{groupId}/dispense")
    public ResponseEntity<?> dispenseGroup(@PathVariable Integer groupId) {
        Optional<PrescriptionGroup> groupOpt = prescriptionGroupRepository.findById(groupId);
        if (groupOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Prescription group not found");
        }
        PrescriptionGroup group = groupOpt.get();
        if ("DISPENSED".equals(group.getStatus())) {
            return ResponseEntity.status(409).body("Already dispensed.");
        }
        group.setStatus("DISPENSED");
        prescriptionGroupRepository.save(group);
        return ResponseEntity.ok(group);
    }

    private void notifyPatientService(PrescriptionGroupRequest request, PrescriptionGroup group, List<Prescription> items, String hospitalName) {        String doctorName = "Dr. Unknown";
        if (request.getDoctorId() != null) {
            Optional<Staff> doctorOpt = staffRepository.findById(request.getDoctorId());
            if (doctorOpt.isPresent()) {
                doctorName = "Dr. " + doctorOpt.get().getFullName();
            }
        }

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        for (Prescription item : items) {
            Map<String, Object> body = new HashMap<>();
            body.put("patient_abha", request.getPatientAbha());
            body.put("doctor_name", doctorName);
            System.out.println("HOSPITAL NAME BEING SENT: '" + hospitalName + "'");
            body.put("hospital_name", hospitalName);
            body.put("drug_name", item.getDrug());
            body.put("dosage", item.getDose());
            body.put("duration_days", item.getDuration());
            body.put("qr_code", group.getQrCode());
            body.put("visit_id", String.valueOf(group.getId()));// shared ID linking all drugs from this visit

            System.out.println("SENDING TO SANGAMI: " + body);
            System.out.println("visit_id VALUE = '" + body.get("visit_id") + "' TYPE = " + body.get("visit_id").getClass().getSimpleName());
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        SANGAMI_BASE_URL + "/prescription/issue", entity, Map.class);
                System.out.println("SANGAMI ISSUE RESPONSE: " + response.getBody());
                if (response.getBody() != null && response.getBody().get("prescription_id") != null) {
                    item.setExternalPrescriptionId(response.getBody().get("prescription_id").toString());
                    prescriptionRepository.save(item);
                    System.out.println("SAVED external_prescription_id: " + item.getExternalPrescriptionId());
                } else {
                    System.out.println("WARNING: No prescription_id in Sangami's response!");
                }
            } catch (Exception inner) {
                System.out.println("Failed to notify patient service for drug " + item.getDrug() + ": " + inner.getMessage());
            }
        }
    }

    @GetMapping("/patient/{abha}")
    public ResponseEntity<List<Prescription>> getPrescriptionsForPatient(@PathVariable String abha) {
        return ResponseEntity.ok(prescriptionRepository.findByPatientAbha(abha));
    }

    @GetMapping("/recent")
    public ResponseEntity<?> getRecent() {
        List<PrescriptionGroup> all = prescriptionGroupRepository.findAll();
        all.sort((a, b) -> b.getId().compareTo(a.getId()));
        List<PrescriptionGroup> limited = all.size() > 10 ? all.subList(0, 10) : all;

        List<Map<String, Object>> result = new ArrayList<>();
        for (PrescriptionGroup group : limited) {
            List<Prescription> items = prescriptionRepository.findAll().stream()
                    .filter(p -> group.getId().equals(p.getPrescriptionGroupId()))
                    .toList();

            Map<String, Object> entry = new HashMap<>();
            entry.put("id", group.getId());
            entry.put("patientAbha", group.getPatientAbha());
            entry.put("status", group.getStatus());
            entry.put("issuedAt", group.getIssuedAt());
            entry.put("extendedDays", group.getExtendedDays());
            entry.put("drugs", items);
            result.add(entry);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/for-pharmacy/{pharmacyId}")
    public ResponseEntity<List<PrescriptionGroup>> forPharmacy(@PathVariable Integer pharmacyId) {
        return ResponseEntity.ok(prescriptionGroupRepository.findByPharmacyId(pharmacyId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePrescription(@PathVariable Integer id) {
        if (!prescriptionGroupRepository.existsById(id)) {
            return ResponseEntity.status(404).body("Prescription not found.");
        }
        prescriptionGroupRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/active-conflicts/{abha}")
    public ResponseEntity<?> getActiveConflicts(@PathVariable String abha) {
        List<PrescriptionGroup> activeGroups = prescriptionGroupRepository.findByPatientAbhaOrderByIssuedAtDesc(abha)
                .stream()
                .filter(g -> "ISSUED".equals(g.getStatus()))
                .toList();

        List<Map<String, Object>> conflicts = new ArrayList<>();
        for (PrescriptionGroup group : activeGroups) {
            List<Prescription> items = prescriptionRepository.findAll().stream()
                    .filter(p -> group.getId().equals(p.getPrescriptionGroupId()))
                    .toList();

            for (Prescription item : items) {
                Optional<DrugMaster> drugInfo = drugMasterRepository.findAll().stream()
                        .filter(d -> d.getName().equalsIgnoreCase(item.getDrug()))
                        .findFirst();

                Map<String, Object> conflict = new HashMap<>();
                conflict.put("drug", item.getDrug());
                conflict.put("drugClass", drugInfo.map(DrugMaster::getDrugClass).orElse(null));
                conflict.put("doctorId", group.getDoctorId());
                conflict.put("issuedAt", group.getIssuedAt());
                conflict.put("dose", item.getDose());
                conflicts.add(conflict);
            }
        }
        return ResponseEntity.ok(conflicts);
    }

    @PostMapping("/group/{groupId}/recall")
    public ResponseEntity<?> recallPrescription(@PathVariable Integer groupId) {
        Optional<PrescriptionGroup> groupOpt = prescriptionGroupRepository.findById(groupId);
        if (groupOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Prescription not found.");
        }
        PrescriptionGroup group = groupOpt.get();

        if (!"ISSUED".equals(group.getStatus())) {
            return ResponseEntity.status(409).body("Only an active, undispensed prescription can be recalled.");
        }

        long minutesSinceIssued = java.time.Duration.between(group.getIssuedAt(), java.time.LocalDateTime.now()).toMinutes();
        if (minutesSinceIssued > 30) {
            return ResponseEntity.status(403).body("Recall window has expired (30 minutes). Contact an administrator to revoke this prescription.");
        }

        group.setStatus("REVOKED");
        prescriptionGroupRepository.save(group);
        return ResponseEntity.ok(group);
    }

    @PostMapping("/group/{groupId}/extend")
    public ResponseEntity<?> extendPrescription(@PathVariable Integer groupId, @RequestBody Map<String, Object> body) {
        Optional<PrescriptionGroup> groupOpt = prescriptionGroupRepository.findById(groupId);
        if (groupOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Prescription not found.");
        }
        PrescriptionGroup group = groupOpt.get();

        if (!"ISSUED".equals(group.getStatus()) && !"DISPENSED".equals(group.getStatus())) {
            return ResponseEntity.status(409).body("This prescription cannot be extended (status: " + group.getStatus() + ").");
        }

        Integer extendByDays = (Integer) body.get("extendByDays");
        String note = String.valueOf(body.getOrDefault("note", ""));

        if (extendByDays == null || extendByDays <= 0) {
            return ResponseEntity.badRequest().body("extendByDays must be a positive number.");
        }

        int currentExtended = group.getExtendedDays() != null ? group.getExtendedDays() : 0;
        group.setExtendedDays(currentExtended + extendByDays);
        group.setExtensionNote(note);
        if ("DISPENSED".equals(group.getStatus())) {
            group.setStatus("ISSUED");
        }
        prescriptionGroupRepository.save(group);

        return ResponseEntity.ok(group);
    }

    @PostMapping("/group/{groupId}/dispense-full")
    public ResponseEntity<?> dispenseGroupFull(@PathVariable Integer groupId, @RequestBody Map<String, Object> body) {
        Optional<PrescriptionGroup> groupOpt = prescriptionGroupRepository.findById(groupId);
        if (groupOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Prescription group not found.");
        }
        PrescriptionGroup group = groupOpt.get();

        if (!"ISSUED".equals(group.getStatus())) {
            return ResponseEntity.status(409).body("This prescription is not in an issuable state (status: " + group.getStatus() + ").");
        }

        Integer pharmacyId = group.getPharmacyId();
        if (pharmacyId == null) {
            return ResponseEntity.status(409).body("This prescription has no pharmacy routing.");
        }

        List<Prescription> items = prescriptionRepository.findAll().stream()
                .filter(p -> group.getId().equals(p.getPrescriptionGroupId()))
                .toList();

        // No stock-availability check — pharmacies don't disclose inventory levels.
        // Dispensing succeeds as long as the prescription itself is valid and routed correctly.
        for (Prescription item : items) {
            DispensingLog log = new DispensingLog();
            log.setPharmacyId(pharmacyId);
            log.setPrescriptionId(item.getId());
            log.setDrugName(item.getDrug());
            log.setPatientAbha(group.getPatientAbha());
            log.setQtyDispensed(1);
            log.setStatus("DISPENSED");
            log.setDispensedAt(LocalDateTime.now());
            dispensingLogRepository.save(log);

            item.setStatus("DISPENSED");
            prescriptionRepository.save(item);
            notifyPatientServiceDispense(item);
        }

        group.setStatus("DISPENSED");
        prescriptionGroupRepository.save(group);

        return ResponseEntity.ok(group);
    }

    @GetMapping("/for-patient/{abha}/active")
    public ResponseEntity<?> getActiveForPatient(@PathVariable String abha) {
        List<PrescriptionGroup> groups = prescriptionGroupRepository.findByPatientAbhaOrderByIssuedAtDesc(abha)
                .stream()
                .filter(g -> "ISSUED".equals(g.getStatus()))
                .toList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (PrescriptionGroup group : groups) {
            List<Prescription> items = prescriptionRepository.findAll().stream()
                    .filter(p -> group.getId().equals(p.getPrescriptionGroupId()))
                    .toList();

            Map<String, Object> entry = new HashMap<>();
            entry.put("groupId", group.getId());
            entry.put("patientAbha", group.getPatientAbha());
            entry.put("status", group.getStatus());
            entry.put("issuedAt", group.getIssuedAt());
            entry.put("drugs", items);
            result.add(entry);
        }
        return ResponseEntity.ok(result);
    }

    private void notifyPatientServiceDispense(Prescription item) {
        if (item.getExternalPrescriptionId() == null) {
            System.out.println("SKIPPING dispense notify — no external_prescription_id for item " + item.getId());
            return;
        }
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-Key", apiKey);
            HttpEntity<Void> verifyEntity = new HttpEntity<>(headers);

            String verifyUrl = SANGAMI_BASE_URL + "/prescription/" + item.getExternalPrescriptionId() + "/verify";
            System.out.println("CALLING VERIFY: " + verifyUrl);
            ResponseEntity<String> verifyResp = restTemplate.exchange(verifyUrl, org.springframework.http.HttpMethod.GET, verifyEntity, String.class);
            System.out.println("VERIFY RESPONSE: " + verifyResp.getStatusCode() + " - " + verifyResp.getBody());

            String dispenseUrl = SANGAMI_BASE_URL + "/prescription/" + item.getExternalPrescriptionId()
                    + "/dispense?delivery_method=Physical";
            System.out.println("CALLING DISPENSE: " + dispenseUrl);
            HttpEntity<Void> dispenseEntity = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(dispenseUrl, dispenseEntity, String.class);
            System.out.println("DISPENSE RESPONSE: " + resp.getStatusCode() + " - " + resp.getBody());
        } catch (Exception e) {
            System.out.println("Failed to notify patient service of dispense for prescription "
                    + item.getExternalPrescriptionId() + ": " + e.getMessage());
        }
    }

}