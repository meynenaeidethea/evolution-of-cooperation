#!/usr/bin/env bash
set -euo pipefail
label=${1:?Usage: bash tools/reproduce.sh NEW_LABEL}
if [[ ! "$label" =~ ^[a-zA-Z0-9_-]+$ ]]; then echo 'Use a new label with letters, digits, _ or -' >&2; exit 2; fi
./gradlew :simulation:build
./gradlew :simulation:runStudy --args="--profile profiles/research.properties --output results/$label-data --repetitions 40 --seed-start 10001 --threads 2"
python3 tools/analyze_study.py --input "results/$label-data" --output "results/$label-report" --strict-main
./gradlew :simulation:analyzeExperiments --args="--input results/$label-data --output results/$label-java"
./gradlew :simulation:exportStudyFrames --args="profiles/research.properties results/$label-frames"
python3 tools/allele_diagnostics.py --input "results/$label-data" --output "results/$label-alleles.json"
python3 tools/finalize_materials.py --report "results/$label-report" --data "results/$label-data" --frames "results/$label-frames" --output "results/$label-materials"
python3 tools/archive_study.py --output "archives/$label.tar.gz" --include "results/$label-data" "results/$label-report" "results/$label-java" "results/$label-frames" "results/$label-alleles.json" "results/$label-materials"
