package com.curo.curo_backend.service;

import com.curo.curo_backend.model.LicenceRegistryEntry;
import com.curo.curo_backend.repository.LicenceRegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

/** Fills licence_registry with the invented records on startup. Adds missing records only, never overwrites. */
@Component
@Order(10)
@ConditionalOnProperty(name = "licence.verification.mode", havingValue = "mock", matchIfMissing = true)
public class LicenceRegistrySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LicenceRegistrySeeder.class);

    @Autowired
    private LicenceRegistryRepository registryRepository;

    @Value("${licence.verification.seed-on-startup:true}")
    private boolean seedOnStartup;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedOnStartup) {
            return;
        }
        int added = 0;
        try {
            for (LicenceRegistryEntry entry : LicenceRegistrySeed.entries()) {
                if (!registryRepository.existsByTypeAndLicenceNumber(entry.getType(), entry.getLicenceNumber())) {
                    registryRepository.save(entry);
                    added++;
                }
            }
        } catch (RuntimeException e) {
            // Stop at the first failure (each retry would wait out the driver timeout). Log the exception type
            // only: driver messages can contain connection details.
            log.warn("licence_registry seed skipped after {} records: MongoDB unreachable or failing ({}). "
                    + "Until it is seeded, well-formed licence numbers will be PENDING instead of VERIFIED.",
                    added, e.getClass().getSimpleName());
            return;
        }
        log.info("licence_registry seed: {} added, {} already present", added, LicenceRegistrySeed.entries().size() - added);
    }
}
