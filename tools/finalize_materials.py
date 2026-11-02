#!/usr/bin/env python3
"""Assemble portable figures/tables without changing any estimates or primary data."""
import argparse,csv,hashlib,html,json,shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--report',type=Path,required=True);p.add_argument('--data',type=Path,required=True)
p.add_argument('--frames',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
info=json.loads((a.report/'analysis.json').read_text())
for name,digest in info['input_sha256'].items():
    if hashlib.sha256((a.data/name).read_bytes()).hexdigest()!=digest:raise ValueError('Input changed: '+name)
shutil.copytree(a.report,a.output)
shutil.copytree(a.frames,a.output/'maps')
text=(a.output/'report.html').read_text()
metadata=list(csv.DictReader((a.data/'runs.csv').open()))
scenarios={}
for r in metadata:scenarios.setdefault(r['scenario_id'],r)
cols=list(next(iter(scenarios.values())))
table='<details><summary>Все параметры и метаданные типичного запуска каждого сценария</summary><div style="overflow:auto"><table><tr>'+''.join('<th>'+html.escape(c)+'</th>' for c in cols)+'</tr>'
for r in scenarios.values():table+='<tr>'+''.join('<td>'+html.escape(r[c])+'</td>' for c in cols)+'</tr>'
table+='</table></div><p>В каждой группе seed 10001–10040; все повторы: исходный runs.csv.</p></details>'
text=text.replace('<h2>Конечная точка</h2>',table+'<h2>Конечная точка</h2>')
text=text.replace('../'+a.data.name+'/manifest.txt','../../results/'+a.data.name+'/manifest.txt')
interpretation='''<h2>Интерпретация результатов</h2>
<p>Ключевой результат — сильная зависимость от повторяемости при фиксированной памяти одного раунда. В нормированном ряду при K=1 C=0.004, при K=5 C=0.487, при K=10 C=0.797. Конечные популяции часто разделяются на почти полностью сотрудничающие и почти полностью отказывающиеся; среднее не описывает типичную отдельную популяцию.</p>
<p>Без игрового дохода C=0.479 близка к baseline, но доля CCD?? лишь 0.129 против 0.434 и выживаемость 0.240 против 0.995. Поведенческая C сама по себе не доказывает отбора условной взаимности.</p>
<p>При нулевой мутации C=0.050: 38 из 40 запусков теряют аллель первого C. Это разведочная проверка механизма по уже сохранённым данным, не новая подтверждающая гипотеза. При мутации .05 средняя C ниже baseline, но одновременный интервал эффекта включает ноль. Утверждение «чем меньше мутация, тем лучше» данными отвергается.</p>
<p>Результаты local/global неточны: для global-matched разность C −0.202, описательный 95% CI [−0.407; 0.006], одновременный интервал ещё шире. Ресурсные режимы меняют энергетику, но направление влияния на C надёжно не установлено. Нет доказательства эквивалентности.</p>
<p>Ни одного вымирания за 40 запусков на сценарий; Wilson 95% верхняя граница риска 0.088. Горизонт 60 и один размер мира не позволяют утверждать бессрочную устойчивость.</p>
<p><a href="../../docs/research-paper.md">Полная пояснительная записка</a> · <a href="../../docs/research-protocol.md">Протокол</a> · <a href="../../docs/defense.md">Защита и демонстрация</a></p>
'''
text=text.replace('<h2>Методика и ограничения</h2>',interpretation+'<h2>Методика и ограничения</h2>')
maps='<h2>Карты заранее выбранного seed 10001</h2><p>Поколения 1, 30, 60: <a href="maps/">все карты PNG/SVG</a>. Выбор seed сделан до основной серии. Эти иллюстрации не представляют распределение всех 40 запусков.</p>'
for s in ['baseline','global','global-matched']:
    maps+=f'<figure><img src="maps/{s}-g60.png.svg" alt="{s}, seed 10001, generation 60"><figcaption>{s}, seed 10001, поколение 60; ресурс показан поверх агента.</figcaption></figure>'
text=text.replace('<h2>Прослеживаемость</h2>',maps+'<h2>Прослеживаемость</h2>')
(a.output/'report.html').write_text(text)
# A complete, explicit classification appendix for the defense.
lines=['# Все генотипы и стандартные встречи','', 'Порядок генов: первый ход, CC, CD, DC, DD. «Условный» означает ровно CCD??.',
       'Стандартная C — доля собственных действий C против равновзвешенных 32 оппонентов по 10 раундов. Это отдельный тестовый стенд, не среда эксперимента.',
       '', '| Генотип | CCD?? | Непостоянные реакции | Стандартная C | Средняя выплата/раунд |', '|---|---|---|---|---|']
for r in csv.DictReader((a.output/'strategy-catalogue.csv').open()):
    lines.append(f"| {r['genotype']} | {r['conditional']} | {r['nonconstant']} | {float(r['standard_C']):.3f} | {float(r['standard_payoff']):.3f} |")
lines+=['','Полные последовательности: [standard-matches.csv](standard-matches.csv). Классы эквивалентности: [behavioral-equivalence.csv](behavioral-equivalence.csv).']
(a.output/'strategy-catalogue.md').write_text('\n'.join(lines)+'\n')
print(a.output/'report.html')
