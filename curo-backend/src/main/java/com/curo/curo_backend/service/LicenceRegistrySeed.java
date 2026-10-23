package com.curo.curo_backend.service;

import com.curo.curo_backend.model.LicenceRegistryEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock licence registry contents: 13 doctors, 3 clinics, 3 hospitals and 8 pharmacies, all invented. Names, numbers and GSTINs
 * do not belong to real people or businesses; any resemblance is coincidence. Numbers follow plausible
 * formats only. Every GSTIN carries a correct check character and matches the entry's state, so it passes GstinValidator.
 * Doctors hold no GSTIN. Clinic and hospital entries are for facility verification, which is not wired to signup yet.
 */
public final class LicenceRegistrySeed {

    private LicenceRegistrySeed() {
    }

    public static List<LicenceRegistryEntry> entries() {
        List<LicenceRegistryEntry> all = new ArrayList<>();

        all.add(doctor("TN-48213", "Anjali Varadarajan", "Tamil Nadu Medical Council", "TN"));
        all.add(doctor("TN-61907", "Karthik Subramaniam Iyer", "Tamil Nadu Medical Council", "TN"));
        all.add(doctor("KMC-77120", "Rohan Gowdaiah", "Karnataka Medical Council", "KA"));
        all.add(doctor("KMC-35684", "Shreya Nanjundaswamy", "Karnataka Medical Council", "KA"));
        all.add(doctor("MMC-2009/03345", "Aditya Kulkarni Deshpande", "Maharashtra Medical Council", "MH"));
        all.add(doctor("MMC-2016/11872", "Neha Joglekar", "Maharashtra Medical Council", "MH"));
        all.add(doctor("KSMC-30456", "Vishnu Namboothiri", "Kerala State Medical Council", "KL"));
        all.add(doctor("KSMC-18873", "Lakshmi Kurup", "Kerala State Medical Council", "KL"));
        all.add(doctor("DMC-R-9021", "Imran Siddiqui", "Delhi Medical Council", "DL"));
        all.add(doctor("TSMC-45219", "Sneha Reddipalli", "Telangana State Medical Council", "TG"));

        all.add(pharmacy("TN/CHE/20B-48213", "Sri Velan Medicals", "33AAGFS4821K1Z5", "TN"));
        all.add(pharmacy("KA/BLR/20B-77310", "Nandi Hills Pharma", "29AAGCN7731L1ZW", "KA"));
        all.add(pharmacy("MH/PUN/20B-30519", "Sahyadri Care Chemists", "27AAHFS3052D1ZF", "MH"));
        all.add(pharmacy("KL/EKM/20B-18764", "Periyar Health Medicals", "32AAIFP1876M1ZV", "KL"));
        all.add(pharmacy("DL/20B-5102/2021", "Yamuna Wellness Pharmacy", "07AAJCY5102G1ZE", "DL"));

        all.add(doctor("GMC-G-52871", "Hetal Chauhan", "Gujarat Medical Council", "GJ"));
        all.add(doctor("WBMC-S-31406", "Arindam Bhattacharjee", "West Bengal Medical Council", "WB"));
        all.add(doctor("TN-72450", "Meenakshi Rajagopalan", "Tamil Nadu Medical Council", "TN"));

        all.add(facility(LicenceRegistryEntry.CLINIC, "TN-CL-20417", "Sundaram Family Clinic", "33AAGCS6120M1ZB", "TN"));
        all.add(facility(LicenceRegistryEntry.CLINIC, "KA-CL-55302", "Banashankari Care Clinic", "29AAHFB3345K1Z9", "KA"));
        all.add(facility(LicenceRegistryEntry.CLINIC, "MH-CL-80914", "Mulund Wellness Clinic", "27AAJCM8810P1ZS", "MH"));

        all.add(facility(LicenceRegistryEntry.HOSPITAL, "KL-HOSP-31182", "Kochi Lakeshore Hospital", "32AAKCK5507R1Z2", "KL"));
        all.add(facility(LicenceRegistryEntry.HOSPITAL, "DL-HOSP-60275", "Dwarka Central Hospital", "07AALCD7714B1ZT", "DL"));
        all.add(facility(LicenceRegistryEntry.HOSPITAL, "TG-HOSP-44961", "Tarnaka General Hospital", "36AAMCT2209H1ZB", "TG"));

        all.add(pharmacy("GJ/GNR/20B-40962", "Gandhinagar Jan Medicals", "24AANCG4096E1ZH", "GJ"));
        all.add(pharmacy("WB/KOL/20B-66218", "Eastern Ganga Pharmacy", "19AAPCE6633J1Z2", "WB"));
        all.add(pharmacy("TN/TRI/20B-51094", "Tiruchi Arogya Pharmacy", "33AAQCT1180L1Z0", "TN"));

        return all;
    }

    private static LicenceRegistryEntry facility(String type, String licenceNumber, String name, String gstin, String state) {
        LicenceRegistryEntry e = new LicenceRegistryEntry();
        e.setType(type);
        e.setLicenceNumber(licenceNumber);
        e.setName(name);
        e.setGstin(gstin);
        e.setState(state);
        return e;
    }

    private static LicenceRegistryEntry doctor(String licenceNumber, String name, String stateCouncil, String state) {
        LicenceRegistryEntry e = new LicenceRegistryEntry();
        e.setType(LicenceRegistryEntry.DOCTOR);
        e.setLicenceNumber(licenceNumber);
        e.setName(name);
        e.setStateCouncil(stateCouncil);
        e.setState(state);
        return e;
    }

    private static LicenceRegistryEntry pharmacy(String drugLicenceNumber, String name, String gstin, String state) {
        LicenceRegistryEntry e = new LicenceRegistryEntry();
        e.setType(LicenceRegistryEntry.PHARMACY);
        e.setLicenceNumber(drugLicenceNumber);
        e.setName(name);
        e.setGstin(gstin);
        e.setState(state);
        return e;
    }
}
