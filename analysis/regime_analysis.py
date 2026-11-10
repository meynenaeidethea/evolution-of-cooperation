"""Analyze the saved genetic-v2 series. Takes the directory containing main and robustness data."""

import argparse
import csv
from collections import Counter, defaultdict
from dataclasses import dataclass
from itertools import groupby
from math import ceil
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import PercentFormatter
import numpy as np


REGIMES = ("collapse", "plateau", "slow_growth", "variable")
LABELS = dict(zip(REGIMES, ("Коллапс", "Плато", "Поздний рост", "Промежуточный / переменный")))
COLORS = dict(zip(REGIMES, ("#a13b38", "#17695d", "#3b60a8", "#6e627e")))
KEY = ("scenario_id", "seed", "repetition")
SCENARIOS = {
    "main": {
        s: 40 for s in (
            "baseline", "rounds-1", "rounds-10", "normalized-1", "normalized-10",
            "game-zero", "mutation-zero", "mutation-high", "resource-poor",
            "resource-rich", "global", "global-matched",
        )
    },
    "reference": {
        s: 80 for s in ("baseline", "global", "global-matched", "resource-poor", "resource-rich")
    },
}
for profile in ("small", "large", "sparse", "dense", "long"):
    SCENARIOS[profile] = {
        s: 40 for s in ("baseline", "normalized-1", "normalized-10", "global", "global-matched")
    }


@dataclass
class Run:
    profile: str
    metadata: dict
    generations: np.ndarray
    c: np.ndarray
    energy: np.ndarray
    first_c: np.ndarray

    @property
    def key(self):
        return tuple(self.metadata[k] for k in KEY)

    @property
    def scenario(self):
        return self.metadata["scenario_id"]

    @property
    def condition(self):
        return f"{self.profile}/{self.scenario}"

    @property
    def run_id(self):
        return f"{self.condition}/{self.metadata['seed']}"

    @property
    def tail_size(self):
        return ceil(len(self.c) / 5)

    @property
    def tail(self):
        return self.c[-self.tail_size:]

    @property
    def slope(self):
        return float(np.polyfit(self.generations[-self.tail_size:], self.tail, 1)[0])

    def classify(self, range_limit=.20):
        if np.ptp(self.tail) <= range_limit:
            if self.c[-1] < .1:
                return "collapse"
            if self.c[-1] > .9:
                return "plateau"
        if self.slope * (self.tail_size - 1) >= .10:
            return "slow_growth"
        return "variable"

    @property
    def regime(self):
        return self.classify()


def read_runs(profile, path):
    fields = (
        "generation", "cooperation_rate", "average_energy_hundredths",
        "first_round_cooperation_rate",
    )
    trajectories = defaultdict(list)
    with (path / "generations.csv").open() as handle:
        for row in csv.DictReader(handle):
            key = tuple(row[k] for k in KEY)
            assert row["extinct"] == "false", (profile, key)
            trajectories[key].append(tuple(float(row[k]) for k in fields))
    with (path / "runs.csv").open() as handle:
        metadata = list(csv.DictReader(handle))
    assert Counter(row["scenario_id"] for row in metadata) == SCENARIOS[profile]
    horizon = 120 if profile == "long" else 60
    seeds = range(10001, 10041) if profile == "main" else range(
        20001, 20081 if profile == "reference" else 20041
    )
    assert {tuple(row[k] for k in KEY) for row in metadata} == {
        (scenario, str(seed), str(rep))
        for scenario in SCENARIOS[profile] for rep, seed in enumerate(seeds, start=1)
    }
    for row in metadata:
        key = tuple(row[k] for k in KEY)
        assert row["model_version"] == "genetic-v2-controls" and row["data_schema"] == "2"
        assert row["status"] == "completed" and row["extinct"] == "false"
        assert int(row["completed_generations"]) == int(row["maximum_generations"]) == horizon
        g, c, energy, first_c = np.array(sorted(trajectories.pop(key))).T
        np.testing.assert_array_equal(g, np.arange(1, horizon + 1))
        assert np.all((0 <= c) & (c <= 1)) and np.all((0 <= first_c) & (first_c <= 1))
        assert np.all(np.isfinite(energy) & (energy > 0))
        yield Run(profile, row, g, c, energy / 100, first_c)
    assert not trajectories


def read_genotypes(path, runs):
    by_key = {run.key: run for run in runs}
    frequencies = {run.run_id: defaultdict(dict) for run in runs}
    with (path / "genotype-frequencies.csv").open() as handle:
        for row in csv.DictReader(handle):
            key = tuple(row[k] for k in KEY)
            if key in by_key:
                row_frequencies = frequencies[by_key[key].run_id][int(row["generation"])]
                genotype = row["genotype"]
                assert genotype not in row_frequencies
                row_frequencies[genotype] = float(row["frequency"])
    for run in runs:
        series = frequencies[run.run_id]
        assert set(series) == set(run.generations)
        assert all(len(row) == 32 for row in series.values())
        np.testing.assert_allclose([sum(row.values()) for row in series.values()], 1)
    return frequencies


def write_csv(path, header, rows):
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(header)
        writer.writerows(rows)


def style_c(ax):
    ax.set_ylim(-.03, 1.03)
    ax.set_yticks([0, .5, 1])
    ax.yaxis.set_major_formatter(PercentFormatter(1))
    ax.grid(axis="y", alpha=.2)


def save(fig, directory, name):
    fig.savefig(directory / f"{name}.png", dpi=165, facecolor="white")
    plt.close(fig)


def small_multiples(runs, directory, name, title):
    # Evenly spaced seed ranks, independent of the observed outcomes.
    indices = np.linspace(0, len(runs) - 1, 15).round().astype(int)
    fig, axes = plt.subplots(3, 5, figsize=(13, 6), sharex=True, sharey=True, layout="constrained")
    for ax, index in zip(axes.flat, indices):
        run = runs[index]
        ax.plot(run.generations, run.c, color=COLORS[run.regime], lw=1.55)
        ax.set_title(f"Seed {run.key[1]} · {LABELS[run.regime]}", fontsize=8.7)
        ax.set_xticks([1, 20, 40, 60])
        ax.axvspan(run.generations[-run.tail_size], run.generations[-1], color="#aebac0", alpha=.17)
        style_c(ax)
    fig.suptitle(title, fontsize=15)
    fig.supxlabel("Поколение")
    fig.supylabel("Доля действий C")
    save(fig, directory, name)


def histograms(groups, directory):
    fig, axes = plt.subplots(2, 3, figsize=(12, 6), sharex=True, sharey=True, layout="constrained")
    for ax, runs in zip(axes.flat, groups):
        values = np.array([run.c[-1] for run in runs])
        ax.hist(values, bins=np.linspace(0, 1, 11), color="#397285", edgecolor="white")
        ax.set_title(f"{runs[0].condition} (n={len(values)})", fontsize=11)
        ax.text(.5, .93, f"C < 0.10: {sum(values < .1)}   |   C > 0.90: {sum(values > .9)}",
                ha="center", va="top", transform=ax.transAxes, fontsize=8)
        ax.set_ylim(0, 63)
        ax.set_xlim(0, 1)
        ax.set_xticks([0, .25, .5, .75, 1])
        ax.xaxis.set_major_formatter(PercentFormatter(1))
        ax.grid(axis="y", alpha=.2)
    fig.suptitle("Конечная кооперация в baseline и контролях K = 1, 10", fontsize=15)
    fig.supxlabel("Доля действий C в последнем поколении")
    fig.supylabel("Число запусков")
    save(fig, directory, "hist_finalC_baseline")


def allele_series(run, frequencies, prefix):
    return np.array([
        sum(value for genotype, value in frequencies[run.run_id][g].items()
            if genotype.startswith(prefix))
        for g in run.generations
    ])


def plot_case(run, frequencies, directory):
    fig, axes = plt.subplots(3, 1, figsize=(8, 6.6), sharex=True, layout="constrained")
    c_ax, geno_ax, energy_ax = axes
    c_ax.plot(run.generations, run.c, color=COLORS[run.regime], label="Все раунды")
    c_ax.plot(run.generations, run.first_c, color="#555b61", alpha=.75, lw=1, label="Первый раунд")
    c_ax.legend(loc="best", fontsize=8)
    c_ax.set_ylabel("Доля действий C")
    freqs = frequencies[run.run_id]
    genotypes = sorted(freqs[1])
    top = sorted(genotypes, key=lambda gt: (-sum(freqs[g][gt] for g in run.generations), gt))[:4]
    for gt, color in zip(top, ("#204683", "#9c4127", "#207458", "#823b79")):
        geno_ax.plot(run.generations, [freqs[g][gt] for g in run.generations], label=gt, color=color)
    geno_ax.plot(run.generations, allele_series(run, frequencies, "C"), "--",
                 color="#34373e", lw=1.2, label="Все C????")
    geno_ax.legend(loc="best", ncol=3, fontsize=8)
    geno_ax.set_ylabel("Частота генотипа\nсреди выживших")
    energy_ax.plot(run.generations, run.energy, color="#a05d15")
    energy_ax.set_ylabel("Средняя энергия\nживых, ед.")
    energy_ax.grid(axis="y", alpha=.2)
    energy_ax.set_xlabel("Поколение")
    for ax in (c_ax, geno_ax):
        style_c(ax)
    for ax in axes:
        ax.axvspan(run.generations[-run.tail_size], run.generations[-1], color="#aebac0", alpha=.2)
    fig.suptitle(f"{LABELS[run.regime]} · {run.condition} · seed {run.key[1]}")
    save(fig, directory, f"case_{run.regime}")


def baseline_diagnostic(groups, frequencies, directory):
    fig, axes = plt.subplots(3, 2, figsize=(10, 7), sharex=True, layout="constrained")
    for col, runs in enumerate(groups):
        axes[0, col].set_title(f"{runs[0].condition} (K=5)")
        for regime in ("collapse", "plateau"):
            selected = [run for run in runs if run.regime == regime]
            for row, values in enumerate((
                [run.c for run in selected],
                [allele_series(run, frequencies, "CCD") for run in selected],
                [run.energy for run in selected],
            )):
                lo, mid, hi = np.quantile(values, [.25, .5, .75], axis=0)
                ax = axes[row, col]
                g = selected[0].generations
                ax.plot(g, mid, color=COLORS[regime], label=f"{LABELS[regime]} (n={len(selected)})")
                ax.fill_between(g, lo, hi, color=COLORS[regime], alpha=.16)
        axes[0, col].legend(loc="best", fontsize=8)
        axes[2, col].set_xlabel("Поколение")
        for row in (0, 1):
            style_c(axes[row, col])
        axes[2, col].grid(axis="y", alpha=.2)
    for row, label in enumerate(("Доля действий C", "Доля CCD??\nсреди выживших", "Средняя энергия\nживых, ед.")):
        axes[row, 0].set_ylabel(label)
    fig.suptitle("Как расходятся траектории baseline · медиана и межквартильный размах")
    save(fig, directory, "baseline_regime_diagnostics")


def fit_logistic(x, y):
    """Newton updates for a logistic model with fixed unit L2 penalty."""
    beta = np.zeros(x.shape[1])
    penalty = np.eye(len(beta))
    penalty[0, 0] = 0
    for _ in range(60):
        p = probability(x @ beta)
        gradient = x.T @ (p - y) + penalty @ beta
        hessian = x.T @ ((p * (1 - p))[:, None] * x) + penalty
        step = np.linalg.solve(hessian, gradient)
        beta -= step
        if np.max(np.abs(step)) < 1e-8:
            return beta
    raise RuntimeError("Logistic fit did not converge")


def probability(log_odds):
    return np.exp(-np.logaddexp(0, -log_odds))


def predictors(runs, directory):
    selected = [r for r in runs if r.profile == "main" and r.regime in ("collapse", "plateau")]
    settings = [r.metadata for r in selected]
    columns = [
        ("K=1", "rounds_per_match", "1"), ("K=10", "rounds_per_match", "10"),
        ("q=0,02", "resource_spawn_probability", "0.02"),
        ("q=0,25", "resource_spawn_probability", "0.25"),
        ("pm=0", "mutation_probability", "0.0"),
        ("pm=0,05", "mutation_probability", "0.05"),
    ]
    controls = [
        ("α=0", "game_payoff_weight_hundredths", "0"),
        ("α=0,10", "game_payoff_weight_hundredths", "10"),
        ("α=1", "game_payoff_weight_hundredths", "100"),
        ("global", "pairing_mode", "global"),
        ("global-matched", "pairing_mode", "global_matched"),
    ]
    names = ["Константа", *(name for name, _, _ in columns), "C(1) +0,10",
             *(name for name, _, _ in controls)]
    x = np.column_stack([
        np.ones(len(selected)),
        *[[r[k] == val for r in settings] for _, k, val in columns],
        [(r.c[0] - .5) / .10 for r in selected],
        *[[r[k] == val for r in settings] for _, k, val in controls],
    ])
    assert all(x[:, 1:].any(axis=0))
    y = np.array([r.regime == "plateau" for r in selected], dtype=float)
    folds = np.array([int(r["seed"]) % 5 for r in settings])
    fits = []
    metrics = []
    for size in (1 + len(columns), 2 + len(columns), len(names)):
        design = x[:, :size]
        fits.append(fit_logistic(design, y))
        oof = np.empty(len(y))
        null = np.empty(len(y))
        for fold in range(5):
            test = folds == fold
            beta = fit_logistic(design[~test], y[~test])
            oof[test] = probability(design[test] @ beta)
            null[test] = y[~test].mean()
        delta = oof[y == 1][:, None] - oof[y == 0]
        auc = ((delta > 0) + .5 * (delta == 0)).mean()
        metrics.append((auc, np.mean((oof - y) ** 2), np.mean((null - y) ** 2)))
    lines = [
        "# Предикторы коллапса и плато",
        "",
        f"Логистическая регрессия на основной серии. Из 480 запусков включён {len(y)} "
        f"с одним из двух исходов ({int(y.sum())} плато с меткой 1 и {int((1-y).sum())} коллапсов с меткой 0). "
        "Оценки относятся к выбору между этими двумя исходами. "
        "Модель максимизирует сумму логарифмов правдоподобий со штрафом "
        "½Σβ² без штрафа на константу. Штраф фиксирован и даёт конечные оценки "
        "в условиях без плато, включая K=1.",
        "",
        "A включает K, вероятность появления ресурса q и вероятность мутации pm. "
        "B добавляет C(1) как наблюдаемый ранний прокси стартового состояния. "
        "C(1) измерено уже после начала взаимодействий и зависит от сценария. "
        "Вариант C дополнительно учитывает вес выплат α и тип спаривания. "
        "За опорные взяты K=5, q=0,05, pm=0,01, C(1)=0,50, α=0,20 и local.",
        "",
        "| Предиктор | A, β | B, β | C, β |",
        "|---|---:|---:|---:|",
    ]
    for i, name in enumerate(names):
        estimates = [f"{fit[i]:+.3f}" if i < len(fit) else "—" for fit in fits]
        lines.append(f"| {name} | " + " | ".join(estimates) + " |")
    lines += [
        "",
        "Пять блоков по остатку от деления seed на 5. Все сценарии одного seed "
        "попадают в один блок. AUC и Brier рассчитаны на объединённых "
        "предсказаниях для отложенных блоков. Для сравнения Brier модели с одной "
        f"константой (доля плато в обучающем блоке) равен {metrics[0][2]:.3f}.",
        "",
        "| Спецификация | ROC AUC | Brier |",
        "|---|---:|---:|",
    ]
    for label, (auc, brier, _) in zip("ABC", metrics):
        lines.append(f"| {label} | {auc:.3f} | {brier:.3f} |")
    lines += [
        "",
        "Положительный β означает более высокие шансы плато среди двух исходов. "
        "Величины зависят от штрафа и состава сценариев. Параметры изменялись "
        "относительно базового профиля, поэтому взаимные эффекты факторов "
        "и переносимость на другие профили по этой регрессии не оценивались.",
    ]
    (directory / "regime_predictors.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("results", type=Path, help="directory containing the two study directories")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    paths = {
        "main": args.results / "main-20261020",
        **{profile: args.results / "robustness-20261102" / f"{profile}-data"
           for profile in SCENARIOS if profile != "main"},
    }
    runs = [run for profile, path in paths.items() for run in read_runs(profile, path)]
    runs.sort(key=lambda run: (list(paths).index(run.profile), run.scenario, run.key[1]))
    groups = {cond: list(rows) for cond, rows in groupby(runs, lambda run: run.condition)}
    analysis, figures = root / "analysis", root / "figures"
    predictors(runs, analysis)
    write_csv(analysis / "regime_classification.csv",
              ("run_id", "condition", "regime", "final_C", "slope_last20", "range_C"),
              [(r.run_id, r.condition, r.regime, r.c[-1], r.slope, np.ptp(r.c)) for r in runs])
    condition_rows = []
    for condition, group in groups.items():
        parameters = set(tuple(r.metadata[k] for k in (
            "rounds_per_match", "resource_spawn_probability", "mutation_probability",
            "game_payoff_weight_hundredths", "pairing_mode",
        )) for r in group)
        (settings,) = parameters
        counts = Counter(r.regime for r in group)
        condition_rows.extend(
            (condition, *settings, regime, counts[regime], len(group), f"{counts[regime] / len(group):.6f}")
            for regime in REGIMES
        )
    write_csv(analysis / "regime_by_condition.csv",
              ("condition", "K", "resource_q", "pm", "alpha_hundredths", "pairing_mode",
               "regime", "count", "total", "share"), condition_rows)
    small_multiples(groups["main/rounds-1"], figures, "small_multiples_baseline",
                    "K = 1 · rounds-1, α = 0.20")
    small_multiples(groups["main/baseline"], figures, "small_multiples_protocol_baseline",
                    "Протокольный baseline · K = 5, α = 0.20")
    small_multiples(groups["main/rounds-10"], figures, "small_multiples_k10",
                    "K = 10 · rounds-10, α = 0.20")
    histograms([groups[f"main/{s}"] for s in (
        "baseline", "rounds-1", "rounds-10", "normalized-1", "normalized-10",
    )] + [groups["reference/baseline"]], figures)

    # Use the protocol baseline wherever it contains the regime. Choose a
    # representative by distance to the componentwise medians of its features.
    cases = []
    for regime in REGIMES:
        candidates = [r for r in groups["main/baseline"] if r.regime == regime]
        if regime == "variable":
            candidates = [r for r in runs if r.profile == "main" and r.regime == regime]
        features = np.array([[r.c[-1], r.slope * (r.tail_size - 1), np.ptp(r.tail)] for r in candidates])
        distance = np.linalg.norm(features - np.median(features, axis=0), axis=1)
        cases.append(candidates[int(distance.argmin())])
    baseline_groups = [groups["main/baseline"], groups["reference/baseline"]]
    frequencies = {
        **read_genotypes(paths["main"], list({r.run_id: r for r in (
            cases + baseline_groups[0] + groups["main/mutation-zero"] + groups["main/rounds-1"]
        )}.values())),
        **read_genotypes(paths["reference"], baseline_groups[1]),
    }
    for run in cases:
        plot_case(run, frequencies, figures)
        print(run.run_id, run.regime, np.round(run.c[[0, 4, 9, 19, 47, 59]], 3),
              np.round(run.energy[[0, 4, 9, 19, 47, 59]], 2),
              np.round(allele_series(run, frequencies, "C")[[0, 4, 9, 19, 47, 59]], 3))
    baseline_diagnostic(baseline_groups, frequencies, figures)
    for group in baseline_groups + [groups["main/mutation-zero"], groups["main/rounds-1"]]:
        print(group[0].condition, "sensitivity", [Counter(r.classify(x) for r in group) for x in (.15, .20, .25)])
        for regime in ("collapse", "plateau"):
            subset = [r for r in group if r.regime == regime]
            if not subset:
                continue
            print(regime, len(subset), "loss first C",
                  sum(min(allele_series(r, frequencies, "C")) == 0 for r in subset),
                  "end first C", sum(allele_series(r, frequencies, "C")[-1] == 0 for r in subset),
                  "min C[1:20]<.1", sum(min(r.c[:20]) < .1 for r in subset))
            if regime == "plateau" and group[0].scenario == "baseline":
                trough = [int(np.flatnonzero(r.c < .1)[0]) for r in subset]
                rebounds = [
                    next(i for i in range(start + 1, len(r.c) - 2) if min(r.c[i:i+3]) > .9)
                    for r, start in zip(subset, trough)
                ]
                print("First three generations >.9 start at (min, median, max)",
                      np.quantile(np.array(rebounds) + 1, [0, .5, 1]),
                      "CCD?? >.5 one generation before",
                      sum(allele_series(r, frequencies, "CCD")[i - 1] > .5
                          for r, i in zip(subset, rebounds)))
            for name, values in (
                ("C", [r.c for r in subset]), ("energy", [r.energy for r in subset]),
                ("C????", [allele_series(r, frequencies, "C") for r in subset]),
                ("CCD??", [allele_series(r, frequencies, "CCD") for r in subset]),
            ):
                print(name, np.round(np.median(values, axis=0)[[0, 4, 9, 19, 39, 59]], 3))
    print("All", Counter(r.regime for r in runs))
    print("Main", Counter(r.regime for r in runs if r.profile == "main"))


if __name__ == "__main__":
    main()
