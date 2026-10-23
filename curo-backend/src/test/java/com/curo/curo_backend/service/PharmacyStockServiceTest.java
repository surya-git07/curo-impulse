package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.DispenseOrderRequest;
import com.curo.curo_backend.dto.DispenseView;
import com.curo.curo_backend.dto.StockUpdateRequest;
import com.curo.curo_backend.dto.StockView;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditOutcome;
import com.curo.curo_backend.model.DispenseStatus;
import com.curo.curo_backend.model.DispensingLog;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.GrantStatus;
import com.curo.curo_backend.model.Pharmacy;
import com.curo.curo_backend.model.PharmacyStock;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.curo.curo_backend.repository.DispensingLogRepository;
import com.curo.curo_backend.repository.PharmacyRepository;
import com.curo.curo_backend.repository.PharmacyStockRepository;
import com.curo.curo_backend.support.FakeStockUpdates;
import com.curo.curo_backend.support.PrescriptionHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.curo.curo_backend.support.Fixtures.signIn;
import static com.curo.curo_backend.support.Fixtures.signOut;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PharmacyStockServiceTest {

    private static final Instant T0 = PrescriptionHarness.T0;

    private PrescriptionHarness h;
    private PharmacyStockService service;
    private Pharmacy pharmacy;
    private final List<PharmacyStock> stock = new ArrayList<>();
    private final List<DispensingLog> logs = new ArrayList<>();
    private DispensingLogRepository logRepo;
    private Prescription rx;

    @BeforeEach
    void setUp() {
        h = new PrescriptionHarness();
        pharmacy = new Pharmacy();
        pharmacy.setId("pharm1");
        pharmacy.setName("Test Pharmacy");
        pharmacy.setVerified(true);
        h.pharmacy.setFacilityId("pharm1");

        PharmacyRepository pharmacyRepo = mock(PharmacyRepository.class);
        when(pharmacyRepo.findById(anyString())).thenAnswer(inv -> "pharm1".equals(inv.getArgument(0)) ? Optional.of(pharmacy) : Optional.empty());

        PharmacyStockRepository stockRepo = mock(PharmacyStockRepository.class);
        when(stockRepo.findByPharmacyIdOrderByDrug(anyString())).thenAnswer(inv -> stock.stream()
                .filter(s -> s.getPharmacyId().equals(inv.getArgument(0))).sorted(java.util.Comparator.comparing(PharmacyStock::getDrug)).toList());
        when(stockRepo.findByPharmacyIdAndNormalizedDrug(anyString(), anyString())).thenAnswer(inv -> stock.stream()
                .filter(s -> s.getPharmacyId().equals(inv.getArgument(0)) && s.getNormalizedDrug().equals(inv.getArgument(1))).findFirst());
        when(stockRepo.existsByPharmacyIdAndNormalizedDrug(anyString(), anyString())).thenAnswer(inv -> stock.stream()
                .anyMatch(s -> s.getPharmacyId().equals(inv.getArgument(0)) && s.getNormalizedDrug().equals(inv.getArgument(1))));

        logRepo = mock(DispensingLogRepository.class);
        when(logRepo.existsByPrescriptionIdAndStatus(anyString(), any(DispenseStatus.class))).thenAnswer(inv -> logs.stream()
                .anyMatch(l -> l.getPrescriptionId().equals(inv.getArgument(0)) && l.getStatus() == inv.getArgument(1)));
        when(logRepo.save(any(DispensingLog.class))).thenAnswer(inv -> {
            DispensingLog l = inv.getArgument(0);
            l.setId("log" + (logs.size() + 1));
            logs.add(l);
            return l;
        });

        service = new PharmacyStockService();
        ReflectionTestUtils.setField(service, "timeZone", "Asia/Kolkata");
        ReflectionTestUtils.setField(service, "currentUser", currentUserOf(h));
        ReflectionTestUtils.setField(service, "pharmacyRepository", pharmacyRepo);
        ReflectionTestUtils.setField(service, "stockRepository", stockRepo);
        ReflectionTestUtils.setField(service, "stockUpdates", new FakeStockUpdates(stock));
        ReflectionTestUtils.setField(service, "prescriptionRepository", h.prescriptionRepository);
        ReflectionTestUtils.setField(service, "dispensingLogRepository", logRepo);
        ReflectionTestUtils.setField(service, "accessGuard", h.guard);
        ReflectionTestUtils.setField(service, "drugCatalog", catalogOf(h));
        ReflectionTestUtils.setField(service, "clock", h.clock);

        addStock("Amoxicillin", 10);
        rx = h.existing("Amoxicillin", T0.minus(Duration.ofHours(1)), 7, PrescriptionStatus.ACTIVE);
        h.grantTo(h.pharmacy, GrantScope.PRESCRIPTIONS);
        signIn(h.pharmacy);
    }

    private static CurrentUserService currentUserOf(PrescriptionHarness h) {
        return (CurrentUserService) ReflectionTestUtils.getField(h.guard, "currentUser");
    }

    private static DrugCatalog catalogOf(PrescriptionHarness h) {
        return (DrugCatalog) ReflectionTestUtils.getField(h.prescriptionService, "drugCatalog");
    }

    @AfterEach
    void tearDown() {
        signOut();
    }

    private PharmacyStock addStock(String drug, int quantity) {
        PharmacyStock s = new PharmacyStock();
        s.setId("stock" + (stock.size() + 1));
        s.setPharmacyId("pharm1");
        s.setDrug(drug);
        s.setNormalizedDrug(drug.toLowerCase());
        s.setQuantity(quantity);
        stock.add(s);
        return s;
    }

    private int quantityOf(String normalizedDrug) {
        return stock.stream().filter(s -> s.getNormalizedDrug().equals(normalizedDrug)).findFirst().orElseThrow().getQuantity();
    }

    private DispenseOrderRequest order(int quantity) {
        DispenseOrderRequest r = new DispenseOrderRequest();
        r.setPrescriptionId(rx.getId());
        r.setQuantity(quantity);
        return r;
    }

    private StockUpdateRequest update(String drug, Integer quantity) {
        StockUpdateRequest r = new StockUpdateRequest();
        r.setDrug(drug);
        r.setQuantity(quantity);
        return r;
    }

    private void assertApiError(Supplier<?> call, HttpStatus status, String code) {
        assertThatThrownBy(call::get).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    // ---- stock list and update

    @Test
    void aPharmacistSeesTheirOwnStockSortedByDrug() {
        addStock("Aspirin", 40);

        List<StockView> view = service.stock("pharm1");

        assertThat(view).extracting(StockView::getDrug).containsExactly("Amoxicillin", "Aspirin");
        assertThat(view.get(0).getQuantity()).isEqualTo(10);
        assertThat(view.get(0).getPharmacyId()).isEqualTo("pharm1");
    }

    @Test
    void anotherPharmacysStockIsOffLimitsAndLooksTheSameAsOneThatDoesNotExist() {
        assertApiError(() -> service.stock("pharm2"), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertApiError(() -> service.stock("nope"), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertApiError(() -> service.updateStock("pharm2", update("Amoxicillin", 5)), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertThat(quantityOf("amoxicillin")).isEqualTo(10);
    }

    @Test
    void updatingSetsTheQuantityAndCreatesNewDrugs() {
        StockView existing = service.updateStock("pharm1", update("Amoxicillin", 55));
        StockView created = service.updateStock("pharm1", update("Metformin", 30));

        assertThat(existing.getQuantity()).isEqualTo(55);
        assertThat(quantityOf("amoxicillin")).isEqualTo(55);
        assertThat(created.getDrug()).isEqualTo("Metformin");
        assertThat(created.getQuantity()).isEqualTo(30);
        assertThat(stock).hasSize(2);
        assertThat(stock.get(1).getUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void updatingToZeroMarksTheDrugOutOfStockRatherThanRemovingIt() {
        service.updateStock("pharm1", update("Amoxicillin", 0));

        assertThat(quantityOf("amoxicillin")).isZero();
        assertThat(stock).hasSize(1);
    }

    @Test
    void anAliasIsStoredUnderTheCanonicalDrugName() {
        service.updateStock("pharm1", update("  ACETAMINOPHEN ", 80));

        assertThat(stock).anySatisfy(s -> {
            assertThat(s.getDrug()).isEqualTo("Paracetamol");
            assertThat(s.getNormalizedDrug()).isEqualTo("paracetamol");
        });
    }

    @Test
    void aDrugOutsideTheListIsStoredAsTyped() {
        service.updateStock("pharm1", update("Zorbix Plus", 5));

        assertThat(stock).anySatisfy(s -> assertThat(s.getNormalizedDrug()).isEqualTo("zorbix plus"));
    }

    @Test
    void stockUpdatesAreValidated() {
        assertApiError(() -> service.updateStock("pharm1", update(" ", 5)), HttpStatus.BAD_REQUEST, "DRUG_REQUIRED");
        assertApiError(() -> service.updateStock("pharm1", update("x".repeat(101), 5)), HttpStatus.BAD_REQUEST, "DRUG_REQUIRED");
        assertApiError(() -> service.updateStock("pharm1", update("Amoxicillin", null)), HttpStatus.BAD_REQUEST, "INVALID_QUANTITY");
        assertApiError(() -> service.updateStock("pharm1", update("Amoxicillin", -1)), HttpStatus.BAD_REQUEST, "INVALID_QUANTITY");
        assertApiError(() -> service.updateStock("pharm1", update("Amoxicillin", 100_001)), HttpStatus.BAD_REQUEST, "INVALID_QUANTITY");
        assertApiError(() -> service.updateStock("pharm1", null), HttpStatus.BAD_REQUEST, "DRUG_REQUIRED");
        assertThat(quantityOf("amoxicillin")).isEqualTo(10);
    }

    @Test
    void onlyPharmaciesManageStock() {
        for (AppUser other : List.of(h.doctor, h.patient, h.caregiver)) {
            signIn(other);
            assertApiError(() -> service.stock("pharm1"), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
            assertApiError(() -> service.updateStock("pharm1", update("Amoxicillin", 1)), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
        }
        assertThat(quantityOf("amoxicillin")).isEqualTo(10);
    }

    @Test
    void aPharmacistWithNoPharmacyOrAnUnverifiedOneIsRefused() {
        h.pharmacy.setFacilityId(null);
        assertApiError(() -> service.stock("pharm1"), HttpStatus.FORBIDDEN, "NO_PHARMACY");

        h.pharmacy.setFacilityId("pharm1");
        pharmacy.setVerified(false);
        assertApiError(() -> service.stock("pharm1"), HttpStatus.FORBIDDEN, "PHARMACY_NOT_VERIFIED");
        assertApiError(() -> service.dispense(order(1)), HttpStatus.FORBIDDEN, "PHARMACY_NOT_VERIFIED");
    }

    // ---- dispense

    @Test
    void dispensingMarksItDispensedReducesStockAndIsLogged() {
        DispenseView view = service.dispense(order(4));

        assertThat(view.getStatus()).isEqualTo(DispenseStatus.DISPENSED);
        assertThat(view.getQtyDispensed()).isEqualTo(4);
        assertThat(view.getPrescriptionId()).isEqualTo(rx.getId());
        assertThat(view.getPharmacyId()).isEqualTo("pharm1");
        assertThat(view.getDrugName()).isEqualTo("Amoxicillin");
        assertThat(view.getCareId()).isEqualTo("CID-7K3MQ9");
        assertThat(view.getDispensedAt()).isEqualTo(T0);
        assertThat(quantityOf("amoxicillin")).isEqualTo(6);

        assertThat(logs).singleElement().satisfies(l -> {
            assertThat(l.getStatus()).isEqualTo(DispenseStatus.DISPENSED);
            assertThat(l.getDispensedByUserId()).isEqualTo("ph1");
            assertThat(l.getQuantity()).isEqualTo(4);
        });
    }

    @Test
    void theWholeStockCanBeDispensedLeavingZero() {
        service.dispense(order(10));

        assertThat(quantityOf("amoxicillin")).isZero();
    }

    @Test
    void notEnoughStockIs409ChangesNothingAndLeavesARejectedRecord() {
        assertApiError(() -> service.dispense(order(11)), HttpStatus.CONFLICT, "INSUFFICIENT_STOCK");

        assertThat(quantityOf("amoxicillin")).isEqualTo(10);
        assertThat(logs).singleElement().satisfies(l -> {
            assertThat(l.getStatus()).isEqualTo(DispenseStatus.REJECTED);
            assertThat(l.getRejectReason()).isEqualTo("INSUFFICIENT_STOCK");
        });
    }

    @Test
    void anOutOfStockDrugIs409TooAndTheRejectionDoesNotBlockALaterDispense() {
        stock.get(0).setQuantity(0);
        assertApiError(() -> service.dispense(order(1)), HttpStatus.CONFLICT, "INSUFFICIENT_STOCK");

        service.updateStock("pharm1", update("Amoxicillin", 5));
        assertThat(service.dispense(order(1)).getStatus()).isEqualTo(DispenseStatus.DISPENSED);
        assertThat(quantityOf("amoxicillin")).isEqualTo(4);
    }

    @Test
    void aDrugThePharmacyNeverStockedIs404() {
        stock.clear();

        assertApiError(() -> service.dispense(order(1)), HttpStatus.NOT_FOUND, "NOT_STOCKED");
        assertThat(logs).singleElement().satisfies(l -> assertThat(l.getRejectReason()).isEqualTo("NOT_STOCKED"));
    }

    @Test
    void aPrescriptionIsDispensedOnlyOnce() {
        service.dispense(order(2));

        assertApiError(() -> service.dispense(order(2)), HttpStatus.CONFLICT, "ALREADY_DISPENSED");
        assertThat(quantityOf("amoxicillin")).as("no second deduction").isEqualTo(8);
    }

    @Test
    void ifTwoRequestsRaceTheDatabaseRefusesTheSecondAndTheUnitsAreGivenBack() {
        when(logRepo.save(any(DispensingLog.class))).thenThrow(new DuplicateKeyException("prescriptionId"));

        assertApiError(() -> service.dispense(order(3)), HttpStatus.CONFLICT, "ALREADY_DISPENSED");
        assertThat(quantityOf("amoxicillin")).isEqualTo(10);
    }

    @Test
    void ifTheDispenseCannotBeRecordedTheStockIsRestored() {
        when(logRepo.save(any(DispensingLog.class))).thenThrow(new DataAccessResourceFailureException("down"));

        assertThatThrownBy(() -> service.dispense(order(3))).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(quantityOf("amoxicillin")).isEqualTo(10);
    }

    @Test
    void aDiscontinuedOrFinishedPrescriptionCannotBeDispensed() {
        rx.setStatus(PrescriptionStatus.DISCONTINUED);
        assertApiError(() -> service.dispense(order(1)), HttpStatus.CONFLICT, "INVALID_STATE");

        rx.setStatus(PrescriptionStatus.ACTIVE);
        h.clock.advance(Duration.ofDays(8)); // a 7-day course issued yesterday
        h.grants.clear();
        h.grantTo(h.pharmacy, GrantStatus.ACTIVE, T0.plus(Duration.ofDays(30)), GrantScope.PRESCRIPTIONS);
        assertApiError(() -> service.dispense(order(1)), HttpStatus.CONFLICT, "INVALID_STATE");
        assertThat(quantityOf("amoxicillin")).isEqualTo(10);
    }

    @Test
    void theDrugSentMustBeTheDrugOnThePrescription() {
        DispenseOrderRequest wrong = order(1);
        wrong.setDrug("Warfarin");
        assertApiError(() -> service.dispense(wrong), HttpStatus.BAD_REQUEST, "DRUG_MISMATCH");

        DispenseOrderRequest alias = order(1);
        alias.setDrug("  AMOXICILLIN ");
        assertThat(service.dispense(alias).getStatus()).isEqualTo(DispenseStatus.DISPENSED);
    }

    @Test
    void dispenseRequestsAreValidated() {
        assertApiError(() -> service.dispense(order(0)), HttpStatus.BAD_REQUEST, "INVALID_QUANTITY");
        assertApiError(() -> service.dispense(order(-3)), HttpStatus.BAD_REQUEST, "INVALID_QUANTITY");
        assertApiError(() -> service.dispense(order(10_001)), HttpStatus.BAD_REQUEST, "INVALID_QUANTITY");
        DispenseOrderRequest noQuantity = order(1);
        noQuantity.setQuantity(null);
        assertApiError(() -> service.dispense(noQuantity), HttpStatus.BAD_REQUEST, "INVALID_QUANTITY");
        DispenseOrderRequest noPrescription = order(1);
        noPrescription.setPrescriptionId(" ");
        assertApiError(() -> service.dispense(noPrescription), HttpStatus.BAD_REQUEST, "PRESCRIPTION_REQUIRED");
        assertApiError(() -> service.dispense(null), HttpStatus.BAD_REQUEST, "PRESCRIPTION_REQUIRED");
        DispenseOrderRequest unknown = order(1);
        unknown.setPrescriptionId("nope");
        assertApiError(() -> service.dispense(unknown), HttpStatus.NOT_FOUND, "PRESCRIPTION_NOT_FOUND");
        assertThat(quantityOf("amoxicillin")).isEqualTo(10);
    }

    // ---- who may dispense

    @Test
    void onlyPharmaciesDispense() {
        for (AppUser other : List.of(h.doctor, h.patient, h.caregiver)) {
            signIn(other);
            assertApiError(() -> service.dispense(order(1)), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
        }
        assertThat(quantityOf("amoxicillin")).isEqualTo(10);
        assertThat(logs).isEmpty();
    }

    @Test
    void dispensingNeedsAGrantOnThePatientAndAnythingElseIs403NoActiveGrant() {
        for (Runnable arrange : List.<Runnable>of(
                () -> h.grants.clear(),                                                                       // none
                () -> { h.grants.clear(); h.grantTo(h.pharmacy, GrantStatus.ACTIVE, T0.minusSeconds(1), GrantScope.PRESCRIPTIONS); },   // expired
                () -> { h.grants.clear(); h.grantTo(h.pharmacy, GrantStatus.REVOKED, T0.plus(Duration.ofDays(1)), GrantScope.PRESCRIPTIONS); }, // revoked
                () -> { h.grants.clear(); h.grantTo(h.pharmacy, GrantStatus.PENDING, null, GrantScope.PRESCRIPTIONS); },                // pending
                () -> { h.grants.clear(); h.grantTo(h.pharmacy, GrantScope.PROFILE, GrantScope.MEDICATIONS); })) {                      // wrong scope
            arrange.run();
            assertApiError(() -> service.dispense(order(1)), HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT");
            assertThat(quantityOf("amoxicillin")).isEqualTo(10);
            assertThat(logs).isEmpty();
        }
        assertThat(h.audit).anySatisfy(e -> assertThat(e.getOutcome()).isEqualTo(AuditOutcome.DENIED));
    }

    @Test
    void anotherPharmacysGrantDoesNotCount() {
        h.grants.clear();
        h.users.put("ph2", com.curo.curo_backend.support.Fixtures.user("ph2", "PHARMACY", "Other Pharmacy", "CID-OTHPH2"));
        h.grantTo(h.users.get("ph2"), GrantScope.PRESCRIPTIONS);

        assertApiError(() -> service.dispense(order(1)), HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT");
    }

    @Test
    void aDispenseIsAuditedAsAWriteOnThatPrescription() {
        service.dispense(order(1));

        assertThat(h.audit).filteredOn(e -> e.getOutcome() == AuditOutcome.ALLOWED).singleElement().satisfies(e -> {
            assertThat(e.getAction()).isEqualTo(AuditAction.WRITE);
            assertThat(e.getResource()).isEqualTo("DISPENSE");
            assertThat(e.getResourceId()).isEqualTo(rx.getId());
            assertThat(e.getActorUserId()).isEqualTo("ph1");
            assertThat(e.getPatientId()).isEqualTo("p1");
        });
    }
}
