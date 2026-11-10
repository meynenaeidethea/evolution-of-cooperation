"""Run the paired 95:5 invasion series with JDK 25, NumPy and Matplotlib."""

import argparse
import csv
from itertools import product
from pathlib import Path
import shutil
import subprocess
import tempfile

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import PercentFormatter
import numpy as np


ROOT = Path(__file__).resolve().parents[1]
GENOTYPES = ["n_" + "".join(actions) for actions in product("CD", repeat=5)]
CONDITIONS = {
    "defectors": "A · Вторжение ALLD в популяцию CCDDD",
    "cooperators": "Б · Вторжение CCDDD в популяцию ALLD",
}


def load_results(path, repetitions):
    groups = {tag: {seed: [] for seed in range(41001, 41001 + repetitions)} for tag in CONDITIONS}
    with path.open(newline="") as handle:
        for row in csv.DictReader(handle):
            groups[row["condition"]][int(row["seed"])].append(row)
    for tag, runs in groups.items():
        for rows in runs.values():
            generations = [int(r["generation"]) for r in rows]
            assert generations == list(range(len(rows))) and 2 <= len(rows) <= 61
            start = rows[0]
            assert int(start["living_agents"]) == 160 and int(start["ticks"]) == 0
            assert int(start["n_CCDDD"]) == (152 if tag == "defectors" else 8)
            assert int(start["n_DDDDD"]) == (8 if tag == "defectors" else 152)
            assert rows[-1]["extinct"] == "true" or len(rows) == 61
            for row in rows:
                n = int(row["living_agents"])
                counts = [int(row[gt]) for gt in GENOTYPES]
                actions = int(row["played_actions"])
                cooperative = int(row["cooperative_actions"])
                assert sum(counts) == n and min(counts) >= 0
                assert 0 <= cooperative <= actions
                assert row["extinct"] == str(n == 0).lower()
                if actions:
                    assert float(row["C"]) == cooperative / actions
                else:
                    assert row["C"] == ""
            assert all(r["extinct"] == "false" for r in rows[:-1])
            assert all(int(r["ticks"]) == 50 for r in rows[1:] if r["extinct"] == "false")
            assert sum(int(rows[1][gt]) for gt in GENOTYPES if gt not in ("n_CCDDD", "n_DDDDD")) == 0
    return groups


def trajectory(rows, fields):
    series = np.full(61, np.nan)
    for row in rows:
        if fields:
            population = int(row["living_agents"])
            if population:
                series[int(row["generation"])] = sum(int(row[gt]) for gt in fields) / population
        elif row["C"] and row["extinct"] == "false":
            series[int(row["generation"])] = float(row["C"])
    return series


def plot_condition(tag, runs, destination):
    figure, (behavior, genotype) = plt.subplots(
        2, 1, figsize=(9, 6.4), sharex=True, layout="constrained"
    )
    c = np.array([trajectory(rows, []) for rows in runs.values()])
    generations = np.arange(61)
    observed = np.isfinite(c).any(axis=0)
    x, c = generations[observed], c[:, observed]
    low, high = np.nanquantile(c, [.10, .90], axis=0)
    behavior.fill_between(x, low, high, color="#398bb0", alpha=.24, label="10–90-й процентили")
    behavior.plot(x, np.nanmean(c, axis=0), color="#135b79", lw=2, label="Средняя доля C")
    for boundary in (.1, .9):
        behavior.axhline(boundary, color="#9ea6ab", lw=.85, ls=":")
    behavior.legend(loc="center left")
    behavior.set_ylabel("Доля действий C")

    for fields, label, color, linestyle in (
        (["n_CCDDD"], "CCDDD", "#147862", "-"),
        (["n_DDDDD"], "ALLD (DDDDD)", "#b2502e", "-"),
        ([gt for gt in GENOTYPES if gt.startswith("n_CCD")], "Все CCD??", "#577098", "--"),
    ):
        frequencies = np.array([trajectory(rows, fields) for rows in runs.values()])
        observed = np.isfinite(frequencies).any(axis=0)
        genotype.plot(generations[observed], np.nanmean(frequencies[:, observed], axis=0),
                      color=color, lw=1.8, ls=linestyle, label=label)
    genotype.legend(loc="center left")
    genotype.set(xlabel="Поколение (0 — стартовая популяция)", ylabel="Доля среди живых")
    for ax in (behavior, genotype):
        ax.set(xlim=(0, 60), ylim=(-.035, 1.035), yticks=[0, .25, .5, .75, 1])
        ax.yaxis.set_major_formatter(PercentFormatter(1))
        ax.grid(axis="y", alpha=.17)
    figure.suptitle(f"{CONDITIONS[tag]} · K=10 · {len(runs)} запусков")
    figure.savefig(destination, dpi=165, facecolor="white")
    plt.close(figure)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-root", type=Path, default=ROOT)
    parser.add_argument("--repetitions", type=int, choices=(10, 20), default=20)
    args = parser.parse_args()
    output = args.output_root.resolve()
    with tempfile.TemporaryDirectory() as tmp:
        temporary = Path(tmp)
        source = ROOT / "simulation" / "src" / "main" / "java"
        subprocess.run([
            "javac", "--release", "25", "-sourcepath", str(source),
            "-d", tmp, str(ROOT / "tools" / "InvasionStudy.java"),
        ], check=True)
        raw = temporary / "invasion_results.csv"
        with raw.open("w") as handle:
            subprocess.run([
                "java", "-Xmx768m", "-cp", tmp, "InvasionStudy",
                str(ROOT / "profiles" / "research.properties"), str(args.repetitions),
            ], stdout=handle, check=True)
        groups = load_results(raw, args.repetitions)
        for tag, runs in groups.items():
            plot_condition(tag, runs, temporary / f"invasion_{tag}.png")
        (output / "analysis").mkdir(parents=True, exist_ok=True)
        (output / "figures").mkdir(parents=True, exist_ok=True)
        shutil.copyfile(raw, output / "analysis" / raw.name)
        for tag in groups:
            name = f"invasion_{tag}.png"
            shutil.copyfile(temporary / name, output / "figures" / name)


if __name__ == "__main__":
    main()
