package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.NearbyPharmacyView;
import com.curo.curo_backend.model.Pharmacy;
import com.curo.curo_backend.model.PharmacyStock;
import com.curo.curo_backend.repository.DrugMasterRepository;
import com.curo.curo_backend.repository.PharmacyRepository;
import com.curo.curo_backend.repository.PharmacyStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs against the demo pharmacies (PharmacySeed) held in memory, with the real haversine maths.
 * Rough distances from Koramangala: itself 0, Jayanagar 4.5 km, Indiranagar 5.1 km, Malleshwaram 10 km, Whitefield 14 km.
 */
class NearbyPharmacyServiceTest {

    // Koramangala
    private static final double LAT = 12.9352;
    private static final double LNG = 77.6245;

    private final List<Pharmacy> pharmacies = new ArrayList<>();
    private final List<PharmacyStock> stock = new ArrayList<>();
    private PharmacyRepository pharmacyRepo;
    private NearbyPharmacyService service;

    @BeforeEach
    void setUp() {
        int n = 1;
        for (PharmacySeed.Entry e : PharmacySeed.entries()) {
            Pharmacy p = new Pharmacy();
            p.setId("ph" + n++);
            p.setName(e.name());
            p.setAddress(e.address());
            p.setState("KA");
            p.setLat(e.lat());
            p.setLng(e.lng());
            p.setVerified(true);
            pharmacies.add(p);
            e.stock().forEach((drug, qty) -> {
                PharmacyStock s = new PharmacyStock();
                s.setPharmacyId(p.getId());
                s.setDrug(drug);
                s.setNormalizedDrug(drug.toLowerCase());
                s.setQuantity(qty);
                stock.add(s);
            });
        }

        pharmacyRepo = mock(PharmacyRepository.class);
        when(pharmacyRepo.findByVerifiedTrueAndLatBetweenAndLngBetween(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenAnswer(inv ->
                pharmacies.stream().filter(p -> p.isVerified() && p.getLat() != null && p.getLng() != null
                        && p.getLat() >= (double) inv.getArgument(0) && p.getLat() <= (double) inv.getArgument(1)
                        && p.getLng() >= (double) inv.getArgument(2) && p.getLng() <= (double) inv.getArgument(3)).toList());

        PharmacyStockRepository stockRepo = mock(PharmacyStockRepository.class);
        when(stockRepo.findByPharmacyIdInAndNormalizedDrugAndQuantityGreaterThan(any(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(inv -> stock.stream().filter(s -> ((Collection<?>) inv.getArgument(0)).contains(s.getPharmacyId())
                        && s.getNormalizedDrug().equals(inv.getArgument(1)) && s.getQuantity() > (int) inv.getArgument(2)).toList());

        DrugMasterRepository drugRepo = mock(DrugMasterRepository.class);
        when(drugRepo.findByNormalizedName(anyString())).thenReturn(Optional.empty());
        when(drugRepo.findFirstByAliasesContaining(anyString())).thenReturn(Optional.empty());
        DrugCatalog catalog = new DrugCatalog();
        ReflectionTestUtils.setField(catalog, "drugMasterRepository", drugRepo);

        service = new NearbyPharmacyService();
        ReflectionTestUtils.setField(service, "pharmacyRepository", pharmacyRepo);
        ReflectionTestUtils.setField(service, "stockRepository", stockRepo);
        ReflectionTestUtils.setField(service, "distanceService", new HaversineDistanceService());
        ReflectionTestUtils.setField(service, "drugCatalog", catalog);
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }

    private List<String> names(List<NearbyPharmacyView> views) {
        return views.stream().map(NearbyPharmacyView::getName).toList();
    }

    private void assertApiError(Supplier<?> call, HttpStatus status, String code) {
        assertThatThrownBy(call::get).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    // ---- sorting

    @Test
    void resultsAreSortedNearestFirstWithDistanceInKilometres() {
        List<NearbyPharmacyView> result = service.nearby(LAT, LNG, "Paracetamol", 12.0, null);

        assertThat(names(result)).containsExactly("Koramangala Care Pharmacy", "Jayanagar Wellness Pharmacy",
                "Indiranagar Health Chemists", "Malleshwaram Medicals");
        assertThat(result).extracting(NearbyPharmacyView::getDistanceKm).isSorted();
        assertThat(result.get(0).getDistanceKm()).isLessThan(0.05);
        assertThat(result.get(1).getDistanceKm()).isBetween(4.2, 4.8);
        assertThat(result.get(2).getDistanceKm()).isBetween(4.8, 5.4);
        assertThat(result.get(3).getDistanceKm()).isBetween(9.5, 10.5);
    }

    @Test
    void theOrderFollowsWhereTheUserIs() {
        // from Whitefield the order flips
        List<NearbyPharmacyView> result = service.nearby(12.9698, 77.7500, "Paracetamol", 50.0, null);

        assertThat(result.get(0).getName()).isEqualTo("Whitefield Medico");
        assertThat(result.get(result.size() - 1).getName()).isEqualTo("Malleshwaram Medicals"); // about 20 km, vs Jayanagar 18.5
        assertThat(result).extracting(NearbyPharmacyView::getDistanceKm).isSorted();
    }

    @Test
    void distancesAreRoundedToTwoDecimals() {
        service.nearby(LAT, LNG, "Paracetamol", 12.0, null)
                .forEach(v -> assertThat(v.getDistanceKm()).isEqualTo(Math.round(v.getDistanceKm() * 100.0) / 100.0));
    }

    @Test
    void equalDistancesComeBackInNameOrder() {
        Pharmacy twin = new Pharmacy();
        twin.setId("twin");
        twin.setName("Aaa Twin Pharmacy");
        twin.setLat(pharmacies.get(0).getLat());
        twin.setLng(pharmacies.get(0).getLng());
        twin.setVerified(true);
        pharmacies.add(twin);

        assertThat(names(service.nearby(LAT, LNG, null, 1.0, null))).containsExactly("Aaa Twin Pharmacy", "Koramangala Care Pharmacy");
    }

    // ---- stock

    @Test
    void aPharmacyOutOfStockOfTheDrugIsLeftOut() {
        // Indiranagar holds 0 Amoxicillin
        List<NearbyPharmacyView> result = service.nearby(LAT, LNG, "Amoxicillin", 12.0, null);

        assertThat(names(result)).containsExactly("Koramangala Care Pharmacy", "Jayanagar Wellness Pharmacy", "Malleshwaram Medicals");
        assertThat(names(result)).doesNotContain("Indiranagar Health Chemists");
        assertThat(result).allSatisfy(v -> assertThat(v.getInStock()).isTrue());
    }

    @Test
    void aPharmacyThatNeverStockedTheDrugIsLeftOut() {
        // only Koramangala, Indiranagar (18) and Whitefield (10) list Warfarin, and Koramangala has 0
        assertThat(names(service.nearby(LAT, LNG, "Warfarin", 50.0, null)))
                .containsExactly("Indiranagar Health Chemists", "Whitefield Medico");
    }

    @Test
    void whenNobodyHasTheDrugTheListIsEmpty() {
        assertThat(service.nearby(LAT, LNG, "Digoxin", 5.0, null)).isEmpty();      // only Malleshwaram, 10 km away
        assertThat(service.nearby(LAT, LNG, "Zorbix", 50.0, null)).isEmpty();      // not a drug anyone stocks
    }

    @Test
    void theLastUnitCountsAsInStockButZeroDoesNot() {
        pharmacies.stream().filter(p -> p.getName().startsWith("Jayanagar")).findFirst().ifPresent(p ->
                stock.stream().filter(s -> s.getPharmacyId().equals(p.getId()) && s.getNormalizedDrug().equals("cefixime"))
                        .forEach(s -> s.setQuantity(1)));
        assertThat(names(service.nearby(LAT, LNG, "Cefixime", 12.0, null))).containsExactly("Jayanagar Wellness Pharmacy");

        stock.stream().filter(s -> s.getNormalizedDrug().equals("cefixime")).forEach(s -> s.setQuantity(0));
        assertThat(service.nearby(LAT, LNG, "Cefixime", 12.0, null)).isEmpty();
    }

    @Test
    void drugNamesMatchIgnoringCaseSpacingAndAliases() {
        List<String> expected = names(service.nearby(LAT, LNG, "Paracetamol", 12.0, null));

        assertThat(names(service.nearby(LAT, LNG, "  PARACETAMOL ", 12.0, null))).isEqualTo(expected);
        assertThat(names(service.nearby(LAT, LNG, "acetaminophen", 12.0, null))).isEqualTo(expected);
    }

    @Test
    void withoutADrugEveryPharmacyInRangeIsListedAndInStockIsNull() {
        List<NearbyPharmacyView> result = service.nearby(LAT, LNG, null, 12.0, null);

        assertThat(result).hasSize(4);
        assertThat(result).allSatisfy(v -> assertThat(v.getInStock()).isNull());
        assertThat(service.nearby(LAT, LNG, "   ", 12.0, null)).hasSize(4);
    }

    @Test
    void quantitiesAreNeverExposed() {
        assertThat(NearbyPharmacyView.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
                .containsExactlyInAnyOrder("id", "name", "address", "state", "location", "distanceKm", "inStock");
    }

    // ---- range, limit and who is listed

    @Test
    void theDefaultRadiusIsFiveKilometres() {
        // Koramangala 0 and Jayanagar 4.5 are in; Indiranagar at 5.1 is just out
        assertThat(names(service.nearby(LAT, LNG, null, null, null))).containsExactly("Koramangala Care Pharmacy", "Jayanagar Wellness Pharmacy");
    }

    @Test
    void theRadiusIsAnExactDistanceNotJustABox() {
        // a pharmacy inside the search box but beyond the radius must be dropped: Indiranagar is 5.1 km away
        assertThat(names(service.nearby(LAT, LNG, null, 5.0, null))).doesNotContain("Indiranagar Health Chemists");
        assertThat(names(service.nearby(LAT, LNG, null, 5.5, null))).contains("Indiranagar Health Chemists");
    }

    @Test
    void theLimitKeepsTheNearest() {
        assertThat(names(service.nearby(LAT, LNG, null, 50.0, 2))).containsExactly("Koramangala Care Pharmacy", "Jayanagar Wellness Pharmacy");
        assertThat(service.nearby(LAT, LNG, null, 50.0, 1)).hasSize(1);
    }

    @Test
    void unverifiedPharmaciesAreNeverListed() {
        pharmacies.get(0).setVerified(false);

        assertThat(names(service.nearby(LAT, LNG, null, 12.0, null))).doesNotContain("Koramangala Care Pharmacy");
    }

    @Test
    void pharmaciesWithoutALocationAreNeverListed() {
        Pharmacy nowhere = new Pharmacy();
        nowhere.setId("nowhere");
        nowhere.setName("No Location Chemists");
        nowhere.setVerified(true);
        pharmacies.add(nowhere);

        assertThat(names(service.nearby(LAT, LNG, null, 50.0, 50))).doesNotContain("No Location Chemists");
    }

    @Test
    void aPlaceFarFromAnyPharmacyGetsAnEmptyListAndNoStockLookup() {
        assertThat(service.nearby(28.6139, 77.2090, "Paracetamol", 50.0, null)).isEmpty(); // Delhi
    }

    @Test
    void theResponseCarriesTheContractFields() {
        NearbyPharmacyView v = service.nearby(LAT, LNG, "Paracetamol", 1.0, null).get(0);

        assertThat(v.getId()).isEqualTo("ph1");
        assertThat(v.getName()).isEqualTo("Koramangala Care Pharmacy");
        assertThat(v.getAddress()).contains("Koramangala");
        assertThat(v.getState()).isEqualTo("KA");
        assertThat(v.getLocation().getLat()).isEqualTo(12.9352);
        assertThat(v.getLocation().getLng()).isEqualTo(77.6245);
    }

    // ---- validation

    @Test
    void badCoordinatesAre400() {
        for (Double[] bad : new Double[][]{{null, 77.0}, {12.0, null}, {91.0, 77.0}, {-91.0, 77.0}, {12.0, 181.0}, {12.0, -181.0},
                {Double.NaN, 77.0}, {12.0, Double.POSITIVE_INFINITY}}) {
            assertApiError(() -> service.nearby(bad[0], bad[1], null, null, null), HttpStatus.BAD_REQUEST, "INVALID_COORDINATES");
        }
        verify(pharmacyRepo, never()).findByVerifiedTrueAndLatBetweenAndLngBetween(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void theEdgesOfTheValidRangeAreAccepted() {
        assertThat(service.nearby(90.0, 180.0, null, null, null)).isEmpty();
        assertThat(service.nearby(-90.0, -180.0, null, null, null)).isEmpty();
    }

    @Test
    void radiusAndLimitAreBounded() {
        assertApiError(() -> service.nearby(LAT, LNG, null, 0.0, null), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertApiError(() -> service.nearby(LAT, LNG, null, -1.0, null), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertApiError(() -> service.nearby(LAT, LNG, null, 50.1, null), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertApiError(() -> service.nearby(LAT, LNG, null, Double.NaN, null), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertApiError(() -> service.nearby(LAT, LNG, null, null, 0), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertApiError(() -> service.nearby(LAT, LNG, null, null, 51), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertThat(service.nearby(LAT, LNG, null, 50.0, 50)).isNotEmpty();
    }

    // ---- swapping the distance source

    @Test
    void theDistanceSourceCanBeReplacedWithoutTouchingTheFinder() {
        DistanceService other = mock(DistanceService.class);
        when(other.distancesKm(any(), any())).thenAnswer(inv -> {
            int n = ((List<?>) inv.getArgument(1)).size();
            List<Double> reversed = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                reversed.add((double) (n - i)); // the last pharmacy looks nearest: not the straight-line order
            }
            return reversed;
        });
        ReflectionTestUtils.setField(service, "distanceService", other);

        List<NearbyPharmacyView> result = service.nearby(LAT, LNG, null, 50.0, null);

        assertThat(names(result)).containsExactly("Malleshwaram Medicals", "Whitefield Medico", "Jayanagar Wellness Pharmacy",
                "Indiranagar Health Chemists", "Koramangala Care Pharmacy");
        assertThat(result).extracting(NearbyPharmacyView::getDistanceKm).containsExactly(1.0, 2.0, 3.0, 4.0, 5.0);
    }

    @Test
    void theRadiusStillAppliesToWhateverTheSourceReports() {
        DistanceService far = mock(DistanceService.class);
        when(far.distancesKm(any(), any())).thenAnswer(inv -> java.util.Collections.nCopies(((List<?>) inv.getArgument(1)).size(), 80.0));
        ReflectionTestUtils.setField(service, "distanceService", far);

        assertThat(service.nearby(LAT, LNG, null, 50.0, null)).isEmpty();
    }
}
