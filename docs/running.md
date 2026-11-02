# Эволюция сотрудничества: исследование с генетическим алгоритмом

Готовая вычислительная работа: проверенная пространственная модель, зафиксированный протокол, **480 реально выполненных запусков** (12 сценариев × 40 seed), статистические таблицы, графики, JavaFX и материалы защиты. Основная серия: `genetic-v2-controls`, 60 поколений, seed 10001–10040. Исходники и материалы сохранены в [PR #2](https://github.com/meynenaeidethea/evolution-of-cooperation/pull/2); проверенный архив находится в [отдельной ветке данных](https://github.com/meynenaeidethea/evolution-of-cooperation/releases/tag/research-data-20261102).

## Текущее состояние

Исправлены повторный запуск произвольного профиля, генерация численных выводов и восстановление прерванных серий. Проверки нового состояния: [отчет о доработке](development-status.md). Дополнительная проверка размеров мира, плотностей и горизонта проведена по [отдельному протоколу](robustness-protocol.md); ее [результаты и графики](https://github.com/meynenaeidethea/evolution-of-cooperation/releases/download/research-data-20261102/robustness-study-20261102.tar.gz) отделены от исходной основной серии.

## Материалы

- [Пояснительная записка с результатами и источниками](research-paper.md).
- [Локальный HTML-отчёт с графиками и таблицами](https://github.com/meynenaeidethea/evolution-of-cooperation/releases/download/research-data-20261102/cooperation-study-20261020.tar.gz), [численные результаты](https://github.com/meynenaeidethea/evolution-of-cooperation/releases/download/research-data-20261102/cooperation-study-20261020.tar.gz).
- [Протокол, зафиксированный до основного расчёта](research-protocol.md), [исходный SHA256 протокола](https://github.com/meynenaeidethea/evolution-of-cooperation/releases/download/research-data-20261102/cooperation-study-20261020.tar.gz).
- [Краткое резюме](research-summary.md), [выступление, слайды, демонстрация и вопросы комиссии](defense.md).
- [Проверки реализации и воспроизводимости](verification.md), [каталог всех 32 стратегий](https://github.com/meynenaeidethea/evolution-of-cooperation/releases/download/research-data-20261102/cooperation-study-20261020.tar.gz).
- [Полные параметры и окружение основной серии](https://github.com/meynenaeidethea/evolution-of-cooperation/releases/download/research-data-20261102/cooperation-study-20261020.tar.gz), [первичные CSV](https://github.com/meynenaeidethea/evolution-of-cooperation/releases/download/research-data-20261102/cooperation-study-20261020.tar.gz), [иллюстративные карты](https://github.com/meynenaeidethea/evolution-of-cooperation/releases/download/research-data-20261102/cooperation-study-20261020.tar.gz).
- Локальный архив: [cooperation-study-20261020.tar.gz](https://github.com/meynenaeidethea/evolution-of-cooperation/releases/download/research-data-20261102/cooperation-study-20261020.tar.gz), [SHA256](https://github.com/meynenaeidethea/evolution-of-cooperation/releases/download/research-data-20261102/cooperation-study-20261020.tar.gz).

Архив и первичные результаты находятся локально в `archives/` и `results/`, исключённых из Git. Копия исходного архива также сохранена в GitHub Release research-data-20261102, независимо от CI artifacts. Если передаёте проект другому человеку, передайте также архив; один checkout основной ветки не содержит исходные 480 запусков. Открывайте `research/study-20261020/report.html` обычным браузером: сервер и интернет не нужны, папка с рисунками должна оставаться рядом.

## Что установлено

При выравнивании масштаба игровых выплат αK=1 средняя конечная C равна .004 при K=1, .487 при K=5, .797 при K=10. В baseline результаты часто близки к противоположным крайним состояниям. Без мутации C=.050; при отключённом игровом доходе высокая C не сопровождается такой же долей условных стратегий. Сравнения локальности и ресурсов имеют широкие интервалы: уверенного преимущества этих условий по C не установлено. Полного вымирания не наблюдалось; верхняя Wilson-граница риска на сценарий — .0876 до 60 поколений. Подробные размеры эффектов и ограничения — в записке.

## Сборка и короткий пример

Нужен JDK 25. Gradle wrapper входит в проект; при первом запуске скачивает зависимости. Python нужен только для расширенного исследовательского анализа; Java-анализатор работает отдельно.

```sh
./gradlew :simulation:build
./gradlew :simulation:runStudy --args='--profile profiles/demo.properties --output results/quick-example --repetitions 2 --seed-start 800001 --threads 2 --scenarios baseline,normalized-1,global-matched'
./gradlew :simulation:analyzeExperiments --args='--input results/quick-example --output results/quick-java-report'
```

Открыть `results/quick-java-report/report.html`. Два повтора — только проверка работоспособности, не основное исследование. Каждый каталог должен быть новым: runner и анализаторы отказываются перезаписывать существующие серии/отчёты.

Для всех научных графиков и каталога стратегий:

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r tools/requirements-analysis.txt
.venv/bin/python tools/analyze_study.py --input results/quick-example --output results/quick-python-report
```

В последующих командах `python3` означает Python с установленными версиями из requirements; можно заменить на `.venv/bin/python` или активировать окружение.

## Интерактивное окно

```sh
./gradlew :simulation:runVisual
# Сразу загрузить короткий профиль:
./gradlew :simulation:runVisual --args='--profile=profiles/demo.properties'
```

Выберите встроенный профиль или нажмите «Открыть профиль…», выберите режим пар и seed, затем нажмите «Новый запуск». Произвольный профиль сохраняется при повторных запусках. Фактически активные параметры показаны над картой. Круг — агент, цвет — генотип, золотой квадрат — ресурс, в том числе под агентом. Доступны шаг, назад, начало/конец доступной истории, пауза, скорости до 50×, графики и экспорт выбранного кадра в PNG/SVG с легендой и seed. Неопределённые показатели отображаются как «—».

История хранит **последние 300 снимков**, что показано в окне; более ранние удаляются. Перемотка и графики согласованы с выбранным снимком. На границе поколения показаны родители после последнего такта; следующий шаг создаёт потомков и выполняет их первый такт. Пакетные эксперименты сохраняют только поколенные метрики, не снимки всей серии.

IntelliJ: импортировать Gradle-проект, выбрать JDK 25, рабочую директорию — корень репозитория; точка входа `evolution.ui.SimulationVisualMain`. Команда Gradle уже задаёт нужные classpath и рабочую директорию. Для окна требуется графическая сессия; автоматическая проверка на CI использует Xvfb.

## Три разных конфигурации

| Профиль | L | N | T | G | E₀ | q | pc | Назначение |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| `profiles/user.properties` | 100 | 300 | 1000 | 20 | 100 | .01 | .90 | Сохранённые пользовательские параметры и текущий `baseline()` |
| `profiles/research.properties` | 20 | 160 | 50 | 60 | 10 | .05 | .80 | Зафиксированное новое исследование |
| `profiles/demo.properties` | 20 | 160 | 30 | 10 | 10 | .05 | .80 | Короткая демонстрация |

Общие исходные K=5, α=.20, R=4, c=1, pm=.01, турнир=3. Энергия в `.properties` задаётся целыми **сотыми**; все 13 полей обязательны, неизвестные имена запрещены. Для своей конфигурации скопируйте профиль в новый файл и передайте `--profile <путь>` пакетному runner или `--profile=<путь>` окну. Java-код менять не требуется. Сценарии StudyMain задают конкретные исследовательские вмешательства (например K=1/10); для фактических параметров смотрите runs.csv.

Предлагаемые параметры из [первоначальной спецификации](model-specification.md) и параметры старых наблюдений **не являются** параметрами новой серии. Старые сведения сохранены и помечены как исторические; упомянутых там исходных каталогов при начале этой работы в checkout не было. Удалённая ветка не использовалась.

## Полное исследование и анализ из исходных CSV

```sh
./gradlew :simulation:runStudy --args='--profile profiles/research.properties --output results/reproduction --repetitions 40 --seed-start 10001 --threads 2'
python3 tools/analyze_study.py --input results/reproduction --output results/reproduction-report --strict-main
./gradlew :simulation:analyzeExperiments --args='--input results/reproduction --output results/reproduction-java'
./gradlew :simulation:exportStudyFrames --args='profiles/research.properties results/reproduction-frames'
python3 tools/allele_diagnostics.py --input results/reproduction --output results/reproduction-alleles.json
```

Оценка времени основной серии на машине подготовки — 4.3 минуты внутри Java при двух потоках; скорость на другой машине не гарантируется. `--threads` 1…32, отдельная сессия и PRNG на запуск. Число seed задаётся `--repetitions`, начало — `--seed-start`; порядок CSV стабилен. `--scenarios` ограничивает IDs через запятую и предназначен также для коротких проверок. Пилотные seed — 900001–900003, основные — 10001–10040, CI — 800001 и далее.

Полный повторный конвейер со своим новым именем, включая отчёт, карты и локальный архив:

```sh
bash tools/reproduce.sh my-reproduction
```

Стандартный устаревший интерфейс `runExperiments --series ...` сохранён для совместимости: он использует пользовательский `baseline()` и seed 1…N. Он не заменяет зафиксированное исследование `runStudy`.

## Воспроизводимость и контроль качества

```sh
./gradlew :simulation:test
STUDY_TEST_INPUT=results/quick-example python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/verify_artifacts.py --output results/verification-repeat.json
```

Последняя команда сверяет сохранённые исходные основную/пилотную серии, Java/Python оценки и две короткие серии; она рассчитана на комплект локальных материалов этой работы. Для произвольной новой серии всю научную валидацию выполняет `analyze_study.py`.

Основные файлы: `runs.csv`, `generations.csv`, `genotype-frequencies.csv`. Неопределённые значения — пустые поля, не нули. Схема 2 добавляет agent-ticks, охват встреч и обращения к генам. Старые CSV читает прежний Java-анализатор; Python-конвейер требует новую схему. [Подробное описание полей](csv-results.md).

Каждый завершённый запуск записывается в `checkpoints/<scenario>-<repetition>/` вместе с длительностью и фактическими тактами. Серия сохраняет профиль, revision/dirty, Java/PRNG, сценарии, точные исходники и SHA256. Сбой не уничтожает завершённые checkpoints, но неполная серия не считается готовым исследованием.

Для новых серий повторите исходную команду с теми же параметрами и `--output`, добавив `--resume`. Можно изменить число потоков. Runner проверяет план, Java, исходники и SHA256 каждого завершенного checkpoint; неполный текущий запуск вычисляется заново. Итоговые CSV собираются в стабильном порядке, параллельный доступ к одному каталогу блокируется. Завершенные серии не перезаписываются. Старые checkpoints основной серии не содержат новых полей восстановления и автоматически не мигрируются.

Проверить архив после передачи:

```sh
cd archives
sha256sum -c cooperation-study-20261020.tar.gz.sha256
# Распаковать в отдельный каталог; внутри cooperation-study:
# sha256sum -c bundle-SHA256SUMS
```

Архив формируется командой `tools/archive_study.py`; gzip/tar метаданные фиксированы, пути отсортированы, каждый файл имеет внутреннюю контрольную сумму. Исключены кэши, build и данные Git. Финальные исходники находятся в корне архива, точный исходник основной серии — также внутри её `source/`.

## CI и ограничения

[Workflow](../.github/workflows/research.yml): сборка/тесты, короткая серия, проверки статистики и CSV, генерация отчёта, реальное окно JavaFX под Xvfb. [Hosted GitHub Actions](https://github.com/meynenaeidethea/evolution-of-cooperation/actions/runs/41470593088) успешно выполнил эти проверки на чистом runner. После слияния workflow в `main` ручной запуск с `full_study=true` выполняет полный исходный конвейер и сохраняет архив на 30 дней.

Исходная основная серия ограничена одним размером мира и горизонтом; генотип имеет фиксированную память, пространственное положение не наследуется. Исследование не доказывает устойчивость к вторжению, эквивалентность локальных/глобальных режимов или универсальный оптимум мутации. Эти границы являются частью выводов, а не незавершённой реализацией.

## Дополнительная проверка и резервные копии

```sh
python3 tools/run_robustness.py --label my-robustness
# Если конвейер прерван, повторить с --resume:
python3 tools/run_robustness.py --label my-robustness --resume
python3 tools/summarize_robustness.py --input results/my-robustness --output research/my-robustness
python3 tools/verify_archive.py archives/cooperation-study-20261020.tar.gz
```

`run_robustness.py` выполняет 1400 запусков по шести фиксированным профилям. Полные данные находятся в `results/`, краткие материалы — в `research/robustness-20261102/`. `verify_archive.py` проверяет внешнюю SHA256 и каждый файл внутри архива без распаковки.
