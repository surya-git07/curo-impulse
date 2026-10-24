// ruleChecks.js
// Home's interaction and allergy checks, computed locally from the patient's real
// medicine names and allergies. Same matching as the backend's
// TableInteractionChecker, run over every pair of current medicines.
// Tables live in drugRules.js. Clinical content: have a clinician review it.

import { DRUGS, INTERACTION_RULES, ALLERGY_SYNONYMS, CROSS_REACTIONS } from './drugRules';

const SEVERITY = { HIGH: 'major', MODERATE: 'moderate', LOW: 'minor' }; // keys of SEVERITY_COLORS
const RANK = { major: 0, moderate: 1, minor: 2 };

const normalize = (name) => (name == null ? '' : String(name).trim().toLowerCase().replace(/\s+/g, ' '));

// name or alias -> { name, normalizedName, drugClass, aliases }
const INDEX = {};
DRUGS.forEach(([name, drugClass, ...aliases]) => {
  const drug = { name, normalizedName: name.toLowerCase(), drugClass, aliases: aliases.map((a) => a.toLowerCase()) };
  INDEX[drug.normalizedName] = drug;
  drug.aliases.forEach((a) => { INDEX[a] = drug; });
});

const resolve = (name) => INDEX[normalize(name)] || null;

// Everything the rules may call this drug: its name, aliases and class, lower case.
// An unknown drug is just its own name.
function tokens(name, drug) {
  const set = new Set([normalize(name)]);
  if (drug) {
    set.add(drug.normalizedName);
    drug.aliases.forEach((a) => set.add(a));
    set.add(drug.drugClass.toLowerCase());
  }
  return set;
}

// Lower case, "allergy to" wording removed, common synonyms mapped to the rule token.
function canonicalAllergen(allergen) {
  if (allergen == null) return '';
  const cleaned = String(allergen).toLowerCase().replace(/\ballerg(y|ic|ies)\b( to)?/g, ' ').replace(/\s+/g, ' ').trim();
  return ALLERGY_SYNONYMS[cleaned] || cleaned;
}

// One entry per distinct drug, so two prescriptions of the same medicine do not
// produce duplicate warnings.
function distinctDrugs(names) {
  const seen = new Set();
  const out = [];
  (names || []).forEach((n) => {
    const drug = resolve(n);
    const key = drug ? drug.normalizedName : normalize(n);
    if (!key || seen.has(key)) return;
    seen.add(key);
    out.push({ name: drug ? drug.name : String(n).trim(), drug, tokens: tokens(n, drug) });
  });
  return out;
}

// Every pair of medicines against the interaction table.
// -> [{ id, drugA, drugB, severity: 'major'|'moderate'|'minor', warning, explanation }], worst first
export function findInteractions(medicineNames) {
  const meds = distinctDrugs(medicineNames);
  const found = [];
  for (let i = 0; i < meds.length; i++) {
    for (let j = i + 1; j < meds.length; j++) {
      const x = meds[i];
      const y = meds[j];
      INTERACTION_RULES.forEach((rule, r) => {
        const applies = (x.tokens.has(rule.a) && y.tokens.has(rule.b)) || (x.tokens.has(rule.b) && y.tokens.has(rule.a));
        if (applies) {
          found.push({
            id: `${x.name}|${y.name}|${r}`,
            drugA: x.name,
            drugB: y.name,
            severity: SEVERITY[rule.severity],
            warning: rule.warning,
            explanation: rule.explanation,
          });
        }
      });
    }
  }
  return found.sort((p, q) => RANK[p.severity] - RANK[q.severity]);
}

// Each medicine against each recorded allergy (three tiers, as on the backend:
// same drug/class, same class as an allergen drug, known cross-reaction).
// -> [{ id, drug, allergy, severity, warning, explanation }], worst first
export function findAllergyConflicts(medicineNames, allergies) {
  const meds = distinctDrugs(medicineNames);
  const found = [];
  (Array.isArray(allergies) ? allergies : []).forEach((recorded) => {
    const allergen = canonicalAllergen(recorded);
    if (!allergen) return;
    const shown = String(recorded).trim();
    const allergenDrug = resolve(allergen);

    meds.forEach((med) => {
      const add = (severity, warning, explanation) => found.push({
        id: `${med.name}|${shown}`, drug: med.name, allergy: shown, severity: SEVERITY[severity], warning, explanation,
      });

      // 1. the allergen is this drug, one of its names, or its class
      if (med.tokens.has(allergen)) {
        return add('HIGH', `Recorded allergy: ${shown}.`,
          `You have a recorded allergy to ${shown}, which matches ${med.name} (same drug or drug class).`);
      }

      // 2. the allergen is a specific drug and this is another drug of the same class
      if (allergenDrug && med.drug && allergenDrug.drugClass.toLowerCase() === med.drug.drugClass.toLowerCase()) {
        const penicillins = med.drug.drugClass === 'PENICILLIN';
        return add(penicillins ? 'HIGH' : 'MODERATE', `Recorded allergy to ${shown} (same class).`,
          `You are allergic to ${shown}, and ${med.name} is in the same drug class (${med.drug.drugClass.toLowerCase().replace(/_/g, ' ')}).`
          + (penicillins ? ' Penicillins cross-react strongly.' : ' Cross-reaction is possible.'));
      }

      // 3. known cross-reactions between families
      const allergenClass = allergenDrug ? allergenDrug.drugClass.toLowerCase() : null;
      const cr = CROSS_REACTIONS.find((c) => (c.allergen === allergen || c.allergen === allergenClass) && med.tokens.has(c.drugToken));
      if (cr) add(cr.severity, `${cr.warning} (recorded allergy: ${shown})`, `${med.name}: ${cr.explanation} You have a recorded allergy to ${shown}.`);
    });
  });
  return found.sort((p, q) => RANK[p.severity] - RANK[q.severity]);
}
