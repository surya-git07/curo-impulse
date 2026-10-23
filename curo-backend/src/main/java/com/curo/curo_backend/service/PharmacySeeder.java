package com.curo.curo_backend.service;

import com.curo.curo_backend.model.Pharmacy;
import com.curo.curo_backend.model.PharmacyStock;
import com.curo.curo_backend.repository.PharmacyRepository;
import com.curo.curo_backend.repository.PharmacyStockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

import java.time.Clock;

/** Fills pharmacies and pharmacy_stock with the demo data. Adds what is missing, never overwrites, and never stops the app starting. */
@Component
@Order(10)
public class PharmacySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PharmacySeeder.class);

    @Value("${pharmacies.seed-on-startup:true}")
    private boolean seedOnStartup;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private PharmacyStockRepository stockRepository;

    @Autowired
    private Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedOnStartup) {
            return;
        }
        int pharmacies = 0;
        int stockRows = 0;
        try {
            for (PharmacySeed.Entry entry : PharmacySeed.entries()) {
                Pharmacy pharmacy = pharmacyRepository.findBySeedKey(entry.seedKey()).orElse(null);
                if (pharmacy == null) {
                    pharmacy = new Pharmacy();
                    pharmacy.setSeedKey(entry.seedKey());
                    pharmacy.setName(entry.name());
                    pharmacy.setAddress(entry.address());
                    pharmacy.setState("KA");
                    pharmacy.setLat(entry.lat());
                    pharmacy.setLng(entry.lng());
                    pharmacy.setVerified(true);
                    pharmacy = pharmacyRepository.save(pharmacy);
                    pharmacies++;
                }
                for (var stock : entry.stock().entrySet()) {
                    String normalized = DrugCatalog.normalize(stock.getKey());
                    if (!stockRepository.existsByPharmacyIdAndNormalizedDrug(pharmacy.getId(), normalized)) {
                        PharmacyStock row = new PharmacyStock();
                        row.setPharmacyId(pharmacy.getId());
                        row.setDrug(stock.getKey());
                        row.setNormalizedDrug(normalized);
                        row.setQuantity(stock.getValue());
                        row.setUpdatedAt(clock.instant());
                        stockRepository.save(row);
                        stockRows++;
                    }
                }
            }
        } catch (RuntimeException e) {
            // exception type only: driver messages can contain connection details
            log.warn("pharmacy seed stopped after {} pharmacies and {} stock rows: MongoDB unreachable or failing ({}). "
                    + "The nearby finder will be empty until it is seeded.", pharmacies, stockRows, e.getClass().getSimpleName());
            return;
        }
        log.info("pharmacy seed: {} pharmacies and {} stock rows added", pharmacies, stockRows);
    }
}
