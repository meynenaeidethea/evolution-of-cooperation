#!/usr/bin/env python3
"""Verify and summarize all six robustness series without pooling their observations."""
import argparse
import csv
import hashlib
import html
import json
import os
from pathlib import Path
os.environ.setdefault('MPLCONFIGDIR', '/tmp/cooperation-matplotlib')
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from verify_artifacts import checksums
from run_robustness import PROFILES

SCENARIOS = ('baseline', 'normalized-1', 'normalized-10', 'global', 'global-matched',
             'resource-poor', 'resource-rich')


def rows(path):
    with path.open(newline='', encoding='utf-8') as h:
        return list(csv.DictReader(h))


def summarize(source, output):
    summaries, effects, counts = [], [], {}
    protocol = Path('docs/robustness-protocol.md').read_bytes()
    protocol_digest = hashlib.sha256(protocol).hexdigest()
    for profile in PROFILES:
        data, report = (source / (profile + suffix) for suffix in ('-data', '-report'))
        if not (data / 'completed.txt').exists():
            raise ValueError('Incomplete series: ' + profile)
        counts[profile] = checksums(data)
        if (data / 'source/docs/robustness-protocol.md').read_bytes() != protocol:
            raise ValueError('Protocol changed since computation started: ' + profile)
        if (data / 'profile.properties').read_bytes() != Path(f'profiles/robustness/{profile}.properties').read_bytes():
            raise ValueError('Archived profile differs: ' + profile)
        info = json.loads((report / 'analysis.json').read_text())
        for name, digest in info['input_sha256'].items():
            if hashlib.sha256((data / name).read_bytes()).hexdigest() != digest:
                raise ValueError('Report input differs: ' + profile + '/' + name)
        metadata = rows(data / 'runs.csv')
        expected_scenarios = ({'baseline','global','global-matched','resource-poor','resource-rich'}
                              if profile == 'reference' else
                              {'baseline','normalized-1','normalized-10','global','global-matched'})
        expected_seeds = range(20001, 20081 if profile == 'reference' else 20041)
        if {(r['scenario_id'],int(r['seed'])) for r in metadata} != {
                (scenario,seed) for scenario in expected_scenarios for seed in expected_seeds}:
            raise ValueError('Seeds/scenarios differ: ' + profile)
        if len(metadata) != (400 if profile == 'reference' else 200):
            raise ValueError('Unexpected number of runs: ' + profile)
        summaries.extend(dict(profile=profile, **r) for r in rows(report / 'summary.csv'))
        effects.extend(dict(profile=profile, **r) for r in rows(report / 'effects.csv'))
    output.mkdir(parents=True, exist_ok=False)
    for name, data in [('summary.csv', summaries), ('effects.csv', effects)]:
        with (output / name).open('w', newline='', encoding='utf-8') as h:
            writer = csv.DictWriter(h, fieldnames=list(data[0]))
            writer.writeheader()
            writer.writerows(data)
    means = {(r['profile'], r['scenario'], r['metric']): r for r in summaries}
    def fmt(r):
        return f'{float(r["mean"]):.3f} [{float(r["low"]):.3f}; {float(r["high"]):.3f}]'
    lines = ['# Дополнительная проверка устойчивости', '',
             '1400 новых запусков. Протокол: [robustness-protocol.md](../../docs/robustness-protocol.md).',
             'Seed 20001–20080; для пяти измененных профилей 20001–20040. Исходные 480 запусков не изменены.',
             'Таблица показывает конечную долю действий C: среднее и описательный bootstrap 95% CI.',
             '', '| Профиль | baseline | normalized-1 | normalized-10 | global | global-matched | resource-poor | resource-rich |',
             '|---|---|---|---|---|---|---|---|']
    for profile in PROFILES:
        lines.append('| ' + profile + ' | ' + ' | '.join(
            fmt(means[profile, scenario, 'cooperation']) if (profile, scenario, 'cooperation') in means else '—'
            for scenario in SCENARIOS) + ' |')
    lines += ['', 'В reference по 80 запусков на сценарий, в остальных профилях по 40.',
              'Эффекты ниже — парные разности C относительно baseline. Интервалы описательные, не общее семейство для всех профилей.',
              '', '| Профиль | Сценарий | ΔC [95% CI] | Пар |', '|---|---|---|---|']
    for r in effects:
        if r['reference'] == 'baseline' and r['metric'] == 'cooperation':
            lines.append(f'| {r["profile"]} | {r["scenario"]} | {fmt(r)} | {r["n"]} |')
    directions = {}
    for scenario in ['normalized-1', 'normalized-10', 'global-matched']:
        selected = [r for r in effects if r['scenario'] == scenario and r['reference'] == 'baseline'
                    and r['metric'] == 'cooperation']
        directions[scenario] = dict(profiles=len(selected),
            negative=sum(float(r['mean']) < 0 for r in selected),
            positive=sum(float(r['mean']) > 0 for r in selected),
            ci_below_zero=sum(float(r['high']) < 0 for r in selected),
            ci_above_zero=sum(float(r['low']) > 0 for r in selected))
    lines += ['', '## Интерпретация', '']
    for scenario, d in directions.items():
        lines.append(f'- {scenario}: положительная оценка в {d["positive"]}/{d["profiles"]} профилях, '
                     f'отрицательная в {d["negative"]}/{d["profiles"]}; описательный CI целиком выше нуля '
                     f'в {d["ci_above_zero"]}, ниже нуля в {d["ci_below_zero"]}.')
    original = rows(Path('research/study-20261020/summary.csv'))
    old = next(r for r in original if r['scenario'] == 'baseline' and r['metric'] == 'cooperation')
    new = means['reference', 'baseline', 'cooperation']
    lines += ['', f'Новая независимая группа baseline: {fmt(new)}, прежняя группа: {fmt(old)}. '
              'Различие средних иллюстрирует неопределенность при мультимодальных исходах; две исходные '
              'траектории seed 10001–10002 отдельно воспроизведены новым кодом без изменения CSV.',
              'Оценки разных профилей и исходной/новой групп здесь не объединяются. Проверка не доказывает '
              'эволюционную устойчивость, отсутствие эффектов или эквивалентность режимов.',
              '', 'Полные оценки: [summary.csv](summary.csv), [effects.csv](effects.csv).',
              'Первичные CSV, точные исходники и подробные отчеты входят в архив robustness-study-20261102.tar.gz '
              'в отдельной ветке datasets/research-20261102 на GitHub.']
    (output / 'results.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    plt.rcParams.update({'font.family':'DejaVu Sans', 'font.size':10})
    fig, axes = plt.subplots(1, 2, figsize=(13, 6), layout='constrained')
    colors = {'baseline':'#1b4965', 'normalized-1':'#b54b4b', 'normalized-10':'#3f7e44'}
    for scenario, offset in [('baseline', -.2), ('normalized-1', 0), ('normalized-10', .2)]:
        for i, profile in enumerate(PROFILES):
            r = means.get((profile, scenario, 'cooperation'))
            if r is None: continue
            mean, lo, hi = (float(r[k]) for k in ['mean', 'low', 'high'])
            axes[0].errorbar(mean, i + offset, xerr=[[mean-lo], [hi-mean]], fmt='o',
                color=colors[scenario], capsize=3, label=scenario if i == 1 else None)
    axes[0].set(yticks=range(len(PROFILES)), yticklabels=PROFILES, xlim=(-.03, 1.03),
                xlabel='Конечная C, среднее и 95% CI', title='Повторяемость в разных условиях')
    axes[0].legend()
    for i, profile in enumerate(PROFILES):
        r = next(r for r in effects if r['profile'] == profile and r['scenario'] == 'global-matched'
                 and r['reference'] == 'baseline' and r['metric'] == 'cooperation')
        mean, lo, hi = (float(r[k]) for k in ['mean', 'low', 'high'])
        axes[1].errorbar(mean, i, xerr=[[mean-lo], [hi-mean]], fmt='o', capsize=3, color='#795ca4')
    axes[1].axvline(0, color='black', lw=1)
    axes[1].set(yticks=range(len(PROFILES)), yticklabels=PROFILES,
                xlabel='ΔC (global-matched − baseline), 95% CI', title='Локальность: оценки по профилям')
    for ax in axes:
        ax.invert_yaxis()
        ax.grid(axis='x', alpha=.2)
    for ext in ['png', 'svg', 'pdf']:
        fig.savefig(output / ('robustness.' + ext), dpi=180)
    plt.close(fig)
    # Markdown stays the readable source; the offline HTML has tables and the exportable figure.
    table_rows = []
    for profile in PROFILES:
        cells = [profile] + [fmt(means[profile, scenario, 'cooperation'])
                 if (profile, scenario, 'cooperation') in means else '—' for scenario in SCENARIOS]
        table_rows.append('<tr>' + ''.join('<td>' + html.escape(c) + '</td>' for c in cells) + '</tr>')
    table = '<table><tr>' + ''.join('<th>' + c + '</th>' for c in ('profile',) + SCENARIOS) + '</tr>' + ''.join(table_rows) + '</table>'
    page = '<!doctype html><html lang="ru"><meta charset="utf-8"><title>Устойчивость результатов</title>' \
           '<style>body{max-width:1250px;margin:40px auto;padding:0 24px;font:16px/1.6 sans-serif;color:#183447}' \
           'img{width:100%}table{border-collapse:collapse;font-size:13px}td,th{padding:8px;border:1px solid #ddd}' \
           '.table{overflow:auto}</style><h1>Дополнительная проверка устойчивости</h1>' \
           '<p>1400 запусков; seed — независимая единица. Средние и описательные bootstrap 95% CI.</p>' \
           '<img src="robustness.svg" alt="Результаты по шести профилям"><div class="table">' + table + '</div>'
    page += '<h2>Интерпретация</h2>' + ''.join('<p>' + html.escape(line.lstrip('- ')) + '</p>'
            for line in lines[lines.index('## Интерпретация') + 2:] if line)
    page += '<p><a href="results.md">Полный текст</a> · <a href="summary.csv">Оценки</a> · <a href="effects.csv">Парные эффекты</a></p></html>'
    (output / 'report.html').write_text(page, encoding='utf-8')
    (output / 'verification.json').write_text(json.dumps(dict(runs=1400, checksum_counts=counts,
        directions=directions, protocol_sha256=protocol_digest, analysis_source_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()), indent=2) + '\n')
    (output / 'protocol.sha256').write_text(protocol_digest + '  docs/robustness-protocol.md\\n')
    print(output / 'report.html')


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--input', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    summarize(a.input, a.output)
