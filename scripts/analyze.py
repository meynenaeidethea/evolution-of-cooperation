#!/usr/bin/env python3
"""Rebuild figures and a Russian research report from the simulation's three CSVs."""
import argparse
import csv
from collections import defaultdict
from pathlib import Path
import numpy as np
from scipy import stats
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

METRICS = {
    'cooperation_fraction': 'Доля сотрудничества',
    'survival_fraction': 'Доля выживших',
    'average_energy_hundredths': 'Средняя энергия (сотые)',
    'clustering': 'Кластеризация генотипов',
}


def read_csv(path):
    with path.open(encoding='utf-8', newline='') as stream:
        return list(csv.DictReader(stream))


def interval(values):
    values = np.asarray(values, dtype=float)
    values = values[np.isfinite(values)]
    n = len(values)
    if n == 0:
        return n, np.nan, np.nan, np.nan
    mean = values.mean()
    if n < 2:
        return n, mean, np.nan, np.nan
    margin = stats.t.ppf(.975, n - 1) * values.std(ddof=1) / np.sqrt(n)
    return n, mean, mean - margin, mean + margin


def number(row, metric):
    return float(row[metric]) if row[metric] else np.nan


def holm(pvalues):
    """Holm family-wise correction across all baseline contrasts and endpoints."""
    order = np.argsort(pvalues)
    adjusted = np.ones(len(pvalues))
    running = 0.
    for rank, index in enumerate(order):
        running = max(running, (len(pvalues) - rank) * pvalues[index])
        adjusted[index] = min(1., running)
    return adjusted


def fmt(value):
    return '—' if not np.isfinite(value) else f'{value:.4f}'


def analyze(directory):
    runs = read_csv(directory / 'runs.csv')
    generations = read_csv(directory / 'generations.csv')
    figures = directory / 'figures'
    figures.mkdir(exist_ok=True)
    scenarios = list(dict.fromkeys(r['scenario'] for r in runs))
    run_meta = {(r['scenario'], r['seed']): r for r in runs}
    if len(run_meta) != len(runs):
        raise ValueError('Duplicate scenario/seed keys')
    by_run = defaultdict(list)
    for row in generations:
        by_run[row['scenario'], row['seed']].append(row)
    final = {}
    by_generation = defaultdict(list)
    for key, rows in by_run.items():
        rows.sort(key=lambda r: int(r['generation']))
        meta = run_meta[key]
        if len(rows) != int(meta['completed_generations']):
            raise ValueError(f'Incomplete generation data: {key}')
        # Terminal G endpoint. After extinction only survival can be extended as zero;
        # behavior and energy remain undefined, not the last observed value.
        end = dict(rows[-1])
        if meta['status'] == 'extinct':
            for metric in METRICS:
                if metric != 'survival_fraction':
                    end[metric] = ''
            for generation in range(len(rows) + 1, int(meta['maximum_generations']) + 1):
                by_generation[key[0], generation].append({'survival_fraction': '0',
                    **{m: '' for m in METRICS if m != 'survival_fraction'}})
        final[key] = end
        for row in rows:
            by_generation[key[0], int(row['generation'])].append(row)
    plt.rcParams.update({'font.family': 'DejaVu Sans', 'axes.spines.top': False,
                         'axes.spines.right': False, 'figure.dpi': 140})
    groups = {
        'rounds': ['baseline'] + [s for s in scenarios if s.startswith('rounds-')],
        'weight': ['baseline'] + [s for s in scenarios if s.startswith('weight-')],
        'locality': [s for s in ['baseline', 'global'] if s in scenarios],
        'mutation': ['baseline'] + [s for s in scenarios if s.startswith('mutation-')],
        'resources': ['baseline'] + [s for s in scenarios if s.startswith(('spawn-', 'resource-'))],
    }
    for group, names in groups.items():
        names = [s for s in names if s in scenarios]
        if not names:
            continue
        fig, axes = plt.subplots(2, 2, figsize=(12, 8), constrained_layout=True)
        for ax, (metric, title) in zip(axes.flat, METRICS.items()):
            for scenario in names:
                xs = sorted(g for s, g in by_generation if s == scenario)
                estimates = [interval([number(r, metric) for r in by_generation[scenario, g]]) for g in xs]
                means = [x[1] for x in estimates]
                line, = ax.plot(xs, means, label=scenario, lw=1.5)
                ax.fill_between(xs, [x[2] for x in estimates], [x[3] for x in estimates],
                                alpha=.10, color=line.get_color())
            ax.set_title(title)
            ax.set_xlabel('Поколение')
            if metric != 'average_energy_hundredths':
                ax.set_ylim(0, 1)
            ax.grid(alpha=.18)
        axes[0, 0].legend(fontsize=7)
        fig.savefig(figures / f'{group}.png')
        plt.close(fig)
    summary = []
    for scenario in scenarios:
        for metric in METRICS:
            n, mean, low, high = interval([number(r, metric) for (s, _), r in final.items() if s == scenario])
            summary.append(dict(scenario=scenario, metric=metric, n=n, mean=mean, ci_low=low, ci_high=high))
    comparisons = []
    for scenario in scenarios:
        if scenario == 'baseline':
            continue
        for metric in METRICS:
            differences = []
            for (s, seed), row in final.items():
                if s == scenario and ('baseline', seed) in final:
                    delta = number(row, metric) - number(final['baseline', seed], metric)
                    if np.isfinite(delta):
                        differences.append(delta)
            n, mean, low, high = interval(differences)
            if n < 2:
                p = np.nan
            elif np.std(differences) == 0:
                p = 1. if mean == 0 else 0.
            else:
                p = float(stats.ttest_1samp(differences, 0).pvalue)
            comparisons.append(dict(scenario=scenario, metric=metric, n_pairs=n,
                                    mean_difference=mean, ci_low=low, ci_high=high, p=p))
    valid = [r for r in comparisons if np.isfinite(r['p'])]
    for row, p in zip(valid, holm([r['p'] for r in valid])):
        row['p_holm'] = p
    for name, rows in [('summary', summary), ('comparisons', comparisons)]:
        if not rows:
            continue
        fields = list(rows[0]) + (['p_holm'] if name == 'comparisons' and 'p_holm' not in rows[0] else [])
        with (directory / f'{name}.csv').open('w', encoding='utf-8', newline='') as f:
            writer = csv.DictWriter(f, fieldnames=fields)
            writer.writeheader()
            writer.writerows({k: ('' if isinstance(v, float) and not np.isfinite(v) else v)
                             for k, v in r.items()} for r in rows)
    # Leading genotypes are selected by mean frequency across all baseline generations and seeds.
    frequencies = defaultdict(list)
    genotype_points = defaultdict(list)
    with (directory / 'genotype-frequencies.csv').open(encoding='utf-8', newline='') as stream:
        for row in csv.DictReader(stream):
            if row['scenario'] == 'baseline' and row['frequency']:
                value = float(row['frequency'])
                frequencies[row['genotype']].append(value)
                genotype_points[row['genotype'], int(row['generation'])].append(value)
    leading = sorted(frequencies, key=lambda g: np.mean(frequencies[g]), reverse=True)[:5]
    if leading:
        fig, ax = plt.subplots(figsize=(9, 4), constrained_layout=True)
        for genotype in leading:
            xs = sorted(g for gt, g in genotype_points if gt == genotype)
            ax.plot(xs, [np.mean(genotype_points[genotype, g]) for g in xs], label=genotype)
        ax.set(xlabel='Поколение', ylabel='Частота среди выживших', ylim=(0, 1))
        ax.legend(); ax.grid(alpha=.2)
        fig.savefig(figures / 'genotypes.png'); plt.close(fig)
    fig, axes = plt.subplots(1, 2, figsize=(14, 6), constrained_layout=True)
    for ax, metric in zip(axes, ['cooperation_fraction', 'survival_fraction']):
        selected = [r for r in summary if r['metric'] == metric]
        for i, r in enumerate(selected):
            if np.isfinite(r['mean']):
                error = None if not np.isfinite(r['ci_low']) else [[r['mean'] - r['ci_low']], [r['ci_high'] - r['mean']]]
                ax.errorbar(r['mean'], i, xerr=error, fmt='o', color='#147d92', capsize=3)
        ax.set_yticks(range(len(selected)), [r['scenario'] for r in selected], fontsize=8)
        ax.set_title(METRICS[metric] + ' при G'); ax.set_xlim(-.05, 1.05); ax.grid(axis='x', alpha=.2)
    fig.savefig(figures / 'endpoints.png'); plt.close(fig)
    lines = ['# Результаты вычислительных экспериментов', '',
             f'Запусков: {len(runs)}; сценариев: {len(scenarios)}. '
             f'Версия модели: {runs[0]["model_version"]}. Java: {runs[0]["java_version"]}.', '',
             '## Методика', '',
             'Сценарии используют одинаковые seed; различия с baseline анализируются попарно по seed. '
             'Единица наблюдения — независимый запуск, а не агент или поколение. '
             'Оценка при G использует последнее запланированное поколение. После вымирания доля выживших равна нулю; '
             'сотрудничество, энергия и кластеризация не определены и исключаются с явным указанием n. '
             'Средние и 95% интервалы рассчитаны по t-распределению. Интервалы на графиках поточечные. '
             'Для всех сравнений четырёх конечных метрик с baseline выполнена общая поправка Holm; '
             'интервалы разностей остаются нескорректированными. При малом n выводы предварительные.', '',
             '## Наблюдения при G', '',
             '| Сценарий | Запусков | Вымираний | n для C | Средняя доля C [95% ДИ] | Доля выживших [95% ДИ] |',
             '|---|---:|---:|---:|---|---|']
    for s in scenarios:
        rows = [r for r in runs if r['scenario'] == s]
        c = next(r for r in summary if r['scenario'] == s and r['metric'] == 'cooperation_fraction')
        v = next(r for r in summary if r['scenario'] == s and r['metric'] == 'survival_fraction')
        lines.append(f'| {s} | {len(rows)} | {sum(r["status"] == "extinct" for r in rows)} | {c["n"]} | '
                     f'{fmt(c["mean"])} [{fmt(c["ci_low"])}, {fmt(c["ci_high"])}] | '
                     f'{fmt(v["mean"])} [{fmt(v["ci_low"])}, {fmt(v["ci_high"])}] |')
    lines += ['', '## Статистические сравнения с baseline', '',
              '| Сценарий | Метрика | Пар | Разность средних [95% ДИ] | p (Holm) |', '|---|---|---:|---|---:|']
    for r in comparisons:
        lines.append(f'| {r["scenario"]} | {METRICS[r["metric"]]} | {r["n_pairs"]} | '
                     f'{fmt(r["mean_difference"])} [{fmt(r["ci_low"])}, {fmt(r["ci_high"])}] | {fmt(r.get("p_holm", np.nan))} |')
    lines += ['', '## Интерпретация и ограничения', '',
              'Доля C описывает действия и сама по себе не доказывает распространение условных стратегий. '
              'Для этого нужно сопоставлять частоты генотипов и их поведение. '
              'При изменении K одновременно меняется число игровых раундов и суммарный энергетический вклад игры; '
              'эффект нельзя приписать только памяти. Глобальное спаривание может менять и число встреч. '
              'Полная случайная рассадка потомков разрушает пространственную наследственность кластеров. '
              'Сравнения поведения среди невыживших запусков не определены, а оценки среди выживших могут иметь смещение отбора. '
              'Одинаковые seed обеспечивают повторяемость, но после расхождения траекторий не означают одинаковую среду.', '',
              'Отклонение от baseline считается статистически различимым при p(Holm) < 0,05; '
              'это не эквивалентно подтверждению механистической гипотезы. Все исходные параметры — в runs.csv. '
              'Полные оценки и сравнения — в summary.csv и comparisons.csv.', '', '## Графики', '']
    for name in ['rounds', 'weight', 'locality', 'mutation', 'resources', 'genotypes', 'endpoints']:
        if (figures / f'{name}.png').exists():
            lines += [f'![{name}](figures/{name}.png)', '']
    (directory / 'report.md').write_text('\n'.join(lines), encoding='utf-8')
    write_demo(directory)
    print(directory / 'report.md')


def write_demo(directory):
    import html
    import json
    images = sorted((directory / 'snapshots').glob('baseline-*.svg'),
                    key=lambda path: int(path.stem.split('-')[-1]))
    frames = [dict(generation=int(p.stem.split('-')[-1]), image=p.read_text(encoding='utf-8')) for p in images]
    data = json.dumps(frames).replace('<', '\u003c')
    charts = ''.join(f'<figure><img src="figures/{name}.png" alt="{html.escape(name)}"></figure>'
                     for name in ['rounds', 'weight', 'locality', 'mutation', 'resources', 'genotypes', 'endpoints']
                     if (directory / f'figures/{name}.png').exists())
    document = r"""<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>Эволюция сотрудничества</title><style>
body{margin:0;background:#10232d;color:#e6f0f4;font:17px/1.6 system-ui}main{max-width:1100px;margin:auto;padding:40px 24px}
h1{font-size:clamp(32px,5vw,60px);line-height:1.1}h2{margin-top:48px}p{max-width:850px;color:#bcd0d9}
a{color:#7ae2d0}figure{margin:24px 0}figure img{width:100%;border-radius:10px;background:white}
#world{max-width:620px}#world svg{width:100%;height:auto}input{width:min(100%,620px)}button{background:#7ae2d0;color:#10232d;border:0;border-radius:6px;padding:9px 18px;font:inherit;cursor:pointer}
.card{background:#193642;border-radius:16px;padding:24px;margin:24px 0}label{display:block}
</style><main><p>Вычислительный эксперимент · генетический алгоритм</p><h1>Эволюция сотрудничества</h1>
<p>Агенты добывают ресурсы, играют с соседями и передают стратегии следующему поколению.
Генотип задаёт первый ход и четыре ответа на предыдущий раунд.</p>
<div class="card"><h2 style="margin:0">Карта мира</h2><p>Baseline, seed 1. Снимки конца поколений; ползунок показывает смену поколений.
Цвет соответствует одному из 32 генотипов, жёлтый квадрат — ресурсу. Наведите на агента, чтобы увидеть генотип и энергию.</p>
<div id="world"></div><label for="frame">Поколение: <output id="generation"></output></label>
<input id="frame" type="range" min="0" value="0" step="1"><p><button id="play">Воспроизвести</button></p></div>
<h2>Сравнение сценариев</h2><p>Линии — средние по независимым запускам, области — поточечные 95% доверительные интервалы.
Доля действий C не равна доле условных стратегий. Изменение числа раундов также меняет энергетический вклад игры.</p>
CHARTS<p><a href="report.md">Полный отчёт и ограничения</a> · <a href="summary.csv">Сводные оценки CSV</a> · <a href="comparisons.csv">Сравнения CSV</a></p>
</main><script>const frames=FRAMES;const slider=document.getElementById('frame');slider.max=Math.max(0,frames.length-1);
function show(){const f=frames[Number(slider.value)];document.getElementById('world').innerHTML=f?f.image:'Снимки не созданы. Используйте --snapshots.';document.getElementById('generation').value=f?f.generation:'—'}
slider.oninput=show;show();let timer;document.getElementById('play').onclick=()=>{if(timer){clearInterval(timer);timer=null;document.getElementById('play').textContent='Воспроизвести'}else{document.getElementById('play').textContent='Пауза';timer=setInterval(()=>{slider.value=(Number(slider.value)+1)%Math.max(1,frames.length);show()},1000)}};</script></html>"""
    (directory / 'demo.html').write_text(document.replace('CHARTS', charts).replace('FRAMES', data), encoding='utf-8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    analyze(parser.parse_args().directory)
