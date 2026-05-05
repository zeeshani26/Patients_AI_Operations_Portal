import csv
import hashlib
import random
from datetime import datetime, timedelta


RANDOM_SEED = 42
INPUT_FILE = "healthcare_dataset.csv"
OUTPUT_FILE = "healthcare_dataset_augmented.csv"
LONGITUDINAL_FILE = "healthcare_dataset_longitudinal.csv"


def clamp(value, low, high):
    return max(low, min(high, value))


def parse_date(date_text):
    return datetime.strptime(date_text, "%Y-%m-%d")


def synthetic_patient_id(name, gender, blood_type):
    raw = f"{name}|{gender}|{blood_type}".encode("utf-8")
    return hashlib.sha256(raw).hexdigest()[:16]


def comorbidity_from_condition(condition):
    mapping = {
        "cancer": 3,
        "diabetes": 2,
        "obesity": 2,
        "asthma": 1,
        "hypertension": 2,
    }
    return mapping.get(condition.lower(), 1)


def severity_score(age, admission_type, test_results, condition):
    score = 20
    score += (age // 10)
    score += 12 if admission_type == "Emergency" else 6 if admission_type == "Urgent" else 2
    score += 15 if test_results == "Abnormal" else 6 if test_results == "Inconclusive" else 0
    score += comorbidity_from_condition(condition) * 8
    return clamp(score, 0, 100)


def outcome_probabilities(severity, comorbidity, smoker, exercise_mins):
    base = 0.03 + (severity / 180.0) + (comorbidity / 40.0)
    if smoker:
        base += 0.04
    if exercise_mins > 150:
        base -= 0.03
    readmission = clamp(base, 0.02, 0.85)
    complication = clamp(readmission + 0.05, 0.03, 0.90)
    mortality_1y = clamp((severity / 320.0) + (comorbidity / 30.0), 0.01, 0.60)
    return readmission, complication, mortality_1y


def main():
    random.seed(RANDOM_SEED)

    with open(INPUT_FILE, newline="", encoding="utf-8") as infile:
        reader = csv.DictReader(infile)
        rows = list(reader)

    visit_counter = {}
    augmented_rows = []
    longitudinal_rows = []
    for row in rows:
        age = int(float(row["Age"]))
        admission_type = row["Admission Type"]
        test_results = row["Test Results"]
        condition = row["Medical Condition"]

        patient_id = synthetic_patient_id(row["Name"], row["Gender"], row["Blood Type"])
        visit_counter[patient_id] = visit_counter.get(patient_id, 0) + 1
        visit_number = visit_counter[patient_id]

        base_date = parse_date(row["Date of Admission"])
        treatment_start = base_date + timedelta(days=random.randint(0, 2))
        treatment_duration_days = random.randint(5, 60)

        comorbidity_index = comorbidity_from_condition(condition) + random.randint(0, 3)
        smoker_status = random.random() < 0.27
        exercise_minutes_week = clamp(int(random.gauss(105, 55)), 0, 420)
        bmi = round(clamp(random.gauss(29.2, 5.8), 16.0, 48.0), 1)
        systolic_bp = int(clamp(random.gauss(128, 18), 85, 210))
        diastolic_bp = int(clamp(random.gauss(79, 12), 45, 130))
        heart_rate = int(clamp(random.gauss(77, 13), 45, 165))

        severity = severity_score(age, admission_type, test_results, condition)
        readmission_p, complication_p, mortality_p = outcome_probabilities(
            severity, comorbidity_index, smoker_status, exercise_minutes_week
        )

        outcome_readmission_30d = 1 if random.random() < readmission_p else 0
        outcome_complication_90d = 1 if random.random() < complication_p else 0
        outcome_mortality_1y = 1 if random.random() < mortality_p else 0

        prior_treatments_count = max(0, visit_number - 1) + random.randint(0, 2)
        treatment_intensity_score = clamp(
            int(20 + (treatment_duration_days / 2) + (severity / 3) + random.randint(-5, 8)),
            0,
            100,
        )

        followup_days = random.randint(7, 120)
        next_followup_date = (base_date + timedelta(days=followup_days)).strftime("%Y-%m-%d")

        row.update(
            {
                "patient_id": patient_id,
                "visit_number": visit_number,
                "observation_timestamp": row["Date of Admission"],
                "treatment_start_date": treatment_start.strftime("%Y-%m-%d"),
                "treatment_duration_days": treatment_duration_days,
                "prior_treatments_count": prior_treatments_count,
                "comorbidity_index": comorbidity_index,
                "severity_score": severity,
                "smoker_status": "yes" if smoker_status else "no",
                "exercise_minutes_week": exercise_minutes_week,
                "bmi": bmi,
                "systolic_bp": systolic_bp,
                "diastolic_bp": diastolic_bp,
                "heart_rate": heart_rate,
                "treatment_intensity_score": treatment_intensity_score,
                "outcome_readmission_30d": outcome_readmission_30d,
                "outcome_complication_90d": outcome_complication_90d,
                "outcome_mortality_1y": outcome_mortality_1y,
                "next_followup_date": next_followup_date,
                "data_generation_note": "synthetic_augmented_fields_seeded_42",
            }
        )
        augmented_rows.append(row)

        # Build simple longitudinal trajectory with follow-up snapshots.
        base_date = parse_date(row["observation_timestamp"])
        baseline = dict(row)
        baseline["longitudinal_phase"] = "baseline"
        longitudinal_rows.append(baseline)

        for phase_idx, phase_name in enumerate(["followup_30d", "followup_90d"], start=1):
            follow = dict(row)
            follow_date = base_date + timedelta(days=30 * phase_idx)
            # Slight synthetic progression/regression over time.
            severity_delta = random.randint(-6, 5)
            follow_severity = clamp(int(follow["severity_score"]) + severity_delta, 0, 100)
            follow["observation_timestamp"] = follow_date.strftime("%Y-%m-%d")
            follow["visit_number"] = int(follow["visit_number"]) + phase_idx
            follow["severity_score"] = follow_severity
            follow["systolic_bp"] = clamp(int(follow["systolic_bp"]) + random.randint(-7, 7), 85, 210)
            follow["diastolic_bp"] = clamp(int(follow["diastolic_bp"]) + random.randint(-5, 5), 45, 130)
            follow["heart_rate"] = clamp(int(follow["heart_rate"]) + random.randint(-6, 6), 45, 165)
            follow["bmi"] = round(clamp(float(follow["bmi"]) + random.uniform(-0.6, 0.5), 16.0, 48.0), 1)
            follow["treatment_intensity_score"] = clamp(
                int(follow["treatment_intensity_score"]) + random.randint(-8, 8),
                0,
                100,
            )
            follow["longitudinal_phase"] = phase_name
            longitudinal_rows.append(follow)

    fieldnames = list(augmented_rows[0].keys())
    with open(OUTPUT_FILE, "w", newline="", encoding="utf-8") as outfile:
        writer = csv.DictWriter(outfile, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(augmented_rows)

    longitudinal_fields = list(longitudinal_rows[0].keys())
    with open(LONGITUDINAL_FILE, "w", newline="", encoding="utf-8") as outfile:
        writer = csv.DictWriter(outfile, fieldnames=longitudinal_fields)
        writer.writeheader()
        writer.writerows(longitudinal_rows)

    print(f"Wrote {len(augmented_rows)} rows to {OUTPUT_FILE}")
    print(f"Wrote {len(longitudinal_rows)} rows to {LONGITUDINAL_FILE}")


if __name__ == "__main__":
    main()
