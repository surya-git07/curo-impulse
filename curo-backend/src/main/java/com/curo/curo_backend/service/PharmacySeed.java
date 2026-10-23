package com.curo.curo_backend.service;

import java.util.List;
import java.util.Map;

/**
 * Demo pharmacies for the nearby finder. The names are invented (any match with a real shop is coincidence) and the
 * coordinates are the centres of real Bengaluru neighbourhoods, not the addresses of real shops. Stock levels are made up;
 * a quantity of 0 is deliberate, so out-of-stock cases exist in the demo data.
 */
public final class PharmacySeed {

    public record Entry(String seedKey, String name, String address, double lat, double lng, Map<String, Integer> stock) {
    }

    private PharmacySeed() {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("demo-koramangala", "Koramangala Care Pharmacy", "Koramangala, Bengaluru 560034", 12.9352, 77.6245, Map.ofEntries(
                    Map.entry("Paracetamol", 240), Map.entry("Ibuprofen", 120), Map.entry("Amoxicillin", 60),
                    Map.entry("Azithromycin", 40), Map.entry("Metformin", 180), Map.entry("Amlodipine", 90),
                    Map.entry("Atorvastatin", 75), Map.entry("Omeprazole", 110), Map.entry("Cetirizine", 150),
                    Map.entry("Warfarin", 0), Map.entry("Salbutamol", 25), Map.entry("Insulin", 12))),
            new Entry("demo-indiranagar", "Indiranagar Health Chemists", "Indiranagar, Bengaluru 560038", 12.9784, 77.6408, Map.ofEntries(
                    Map.entry("Paracetamol", 300), Map.entry("Amoxicillin", 0), Map.entry("Azithromycin", 35),
                    Map.entry("Ciprofloxacin", 50), Map.entry("Metformin", 140), Map.entry("Losartan", 80),
                    Map.entry("Pantoprazole", 95), Map.entry("Aspirin", 200), Map.entry("Clopidogrel", 30),
                    Map.entry("Warfarin", 18), Map.entry("Levothyroxine", 60), Map.entry("Prednisolone", 45))),
            new Entry("demo-jayanagar", "Jayanagar Wellness Pharmacy", "Jayanagar, Bengaluru 560011", 12.9308, 77.5838, Map.ofEntries(
                    Map.entry("Paracetamol", 180), Map.entry("Amoxicillin", 45), Map.entry("Cefixime", 30),
                    Map.entry("Metformin", 220), Map.entry("Atenolol", 70), Map.entry("Enalapril", 55),
                    Map.entry("Sertraline", 20), Map.entry("Aspirin", 140), Map.entry("Diclofenac", 65),
                    Map.entry("Furosemide", 40), Map.entry("Insulin", 8), Map.entry("Cetirizine", 100))),
            new Entry("demo-whitefield", "Whitefield Medico", "Whitefield, Bengaluru 560066", 12.9698, 77.7500, Map.ofEntries(
                    Map.entry("Paracetamol", 150), Map.entry("Ibuprofen", 90), Map.entry("Amoxicillin", 25),
                    Map.entry("Metronidazole", 40), Map.entry("Fluconazole", 15), Map.entry("Omeprazole", 70),
                    Map.entry("Salbutamol", 30), Map.entry("Metformin", 100), Map.entry("Amlodipine", 60),
                    Map.entry("Warfarin", 10), Map.entry("Naproxen", 35))),
            new Entry("demo-malleshwaram", "Malleshwaram Medicals", "Malleshwaram, Bengaluru 560003", 13.0035, 77.5646, Map.ofEntries(
                    Map.entry("Paracetamol", 210), Map.entry("Amoxicillin", 80), Map.entry("Amoxicillin-Clavulanate", 50),
                    Map.entry("Azithromycin", 55), Map.entry("Levofloxacin", 25), Map.entry("Lisinopril", 65),
                    Map.entry("Simvastatin", 40), Map.entry("Digoxin", 12), Map.entry("Spironolactone", 30),
                    Map.entry("Insulin", 15), Map.entry("Metoprolol", 50))));

    public static List<Entry> entries() {
        return ENTRIES;
    }
}
