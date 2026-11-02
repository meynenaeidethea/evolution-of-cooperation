#!/usr/bin/env python3
"""Assemble a portable report; every numerical statement comes from its supplied data."""
import argparse
import csv
import hashlib
import html
import json
import math
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path


def read(path):
    with path.open(newline='', encoding='utf-8') as handle:
        return list(csv.DictReader(handle))


def number(value):
    return f'{float(value):.3f}' if value and math.isfinite(float(value)) else '—'


def assemble(report, data, frames, output):
    info = json.loads((report / 'analysis.json').read_text())
    expected = {'runs.csv', 'generations.csv', 'genotype-frequencies.csv'}
    if set(info['input_sha256']) != expected:
        raise ValueError('Analysis must identify exactly the three scientific input CSV')
    for name, digest in info['input_sha256'].items():
        if hashlib.sha256((data / name).read_bytes()).hexdigest() != digest:
            raise ValueError('Input changed: ' + name)
    metadata = read(data / 'runs.csv')
    summary = read(report / 'summary.csv')
    effects = read(report / 'effects.csv')
    extinction = read(report / 'extinction.csv')
    scenarios = {}
    for row in metadata:
        scenarios.setdefault(row['scenario_id'], []).append(row)
    if not scenarios or set(scenarios) != {r['scenario'] for r in summary}:
        raise ValueError('Analysis scenarios do not match input runs')
    shutil.copytree(report, output)  # Refuse overwriting any existing report.
    (output / 'inputs').mkdir()
    for name in ['manifest.txt', 'runs.csv']:
        shutil.copy2(data / name, output / 'inputs' / name)
    text = (output / 'report.html').read_text()
    text = text.replace('../' + html.escape(data.name) + '/manifest.txt', 'inputs/manifest.txt')
    cols = list(metadata[0])
    table = '<details><summary>Параметры и метаданные сценариев</summary><div style="overflow:auto"><table><tr>'
    table += ''.join('<th>' + html.escape(c) + '</th>' for c in cols) + '</tr>'
    for rows in scenarios.values():
        table += '<tr>' + ''.join('<td>' + html.escape(rows[0][c]) + '</td>' for c in cols) + '</tr>'
    table += '</table></div><p>Фактические seed и число запусков: '
    table += '; '.join(f'{html.escape(s)}: n={len(rows)}, seed {min(int(r["seed"]) for r in rows)}–'
                       f'{max(int(r["seed"]) for r in rows)}' for s, rows in scenarios.items())
    table += '. Полный список: <a href="inputs/runs.csv">runs.csv</a>.</p></details>'
    text = text.replace('<h2>Конечная точка</h2>', table + '<h2>Конечная точка</h2>')

    interpretation = '<h2>Результаты переданной серии</h2><p>Числа ниже вычислены из этой серии. ' \
                     'Они не заменяют научную интерпретацию и не переносят выводы исходного исследования.</p><ul>'
    for r in summary:
        if r['metric'] in ['cooperation', 'conditional', 'survival']:
            interpretation += (f'<li>{html.escape(r["scenario"])} / {html.escape(r["metric"])}: '
                               f'{number(r["mean"])}, 95% CI [{number(r["low"])}; {number(r["high"])}], '
                               f'n={int(r["n"])}.</li>')
    interpretation += '</ul><p>Парные разности относительно baseline (описательные 95% CI):</p><ul>'
    for r in effects:
        if r['reference'] == 'baseline' and r['metric'] == 'cooperation':
            interpretation += (f'<li>{html.escape(r["scenario"])}: {number(r["mean"])} '
                               f'[{number(r["low"])}; {number(r["high"])}], n={int(r["n"])}.</li>')
    interpretation += '</ul><p>Вымирание к концу наблюдения:</p><ul>'
    for r in extinction:
        interpretation += (f'<li>{html.escape(r["scenario"])}: {int(r["extinctions"])}/{int(r["n"])}, '
                           f'верхняя 95% Wilson-граница риска {number(r["high"])}.</li>')
    interpretation += '</ul>'
    text = text.replace('<h2>Методика и ограничения</h2>', interpretation + '<h2>Методика и ограничения</h2>')

    if frames is not None:
        shutil.copytree(frames, output / 'maps')
        maps = '<h2>Иллюстративные карты</h2><p>Отдельные траектории; seed и такт указаны на каждой карте.</p>'
        for image in sorted((output / 'maps').glob('*.png')):
            svg = Path(str(image) + '.svg')
            caption = image.stem
            if svg.exists():
                root = ET.parse(svg).getroot()
                caption = next(root.iter('{http://www.w3.org/2000/svg}text')).text or caption
            filename = svg.name if svg.exists() else image.name
            maps += (f'<figure><img src="maps/{html.escape(filename)}" alt="{html.escape(caption)}">'
                     f'<figcaption>{html.escape(caption)}</figcaption></figure>')
        text = text.replace('<h2>Прослеживаемость</h2>', maps + '<h2>Прослеживаемость</h2>')
    (output / 'report.html').write_text(text, encoding='utf-8')

    lines = ['# Все генотипы и стандартные встречи', '',
             'Порядок генов: первый ход, CC, CD, DC, DD. «Условный» означает ровно CCD??.',
             'Стандартная C — доля собственных действий C против 32 оппонентов по 10 раундов; это отдельный стенд.', '',
             '| Генотип | CCD?? | Непостоянные реакции | Стандартная C | Средняя выплата/раунд |',
             '|---|---|---|---|---|']
    for r in read(output / 'strategy-catalogue.csv'):
        lines.append(f'| {r["genotype"]} | {r["conditional"]} | {r["nonconstant"]} | '
                     f'{number(r["standard_C"])} | {number(r["standard_payoff"])} |')
    lines += ['', '[Все встречи](standard-matches.csv). [Классы эквивалентности](behavioral-equivalence.csv).']
    (output / 'strategy-catalogue.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    return output / 'report.html'


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--data', type=Path, required=True)
    parser.add_argument('--frames', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print(assemble(args.report, args.data, args.frames, args.output))
