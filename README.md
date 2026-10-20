# Эволюция сотрудничества

Курсовая работа по генетическим алгоритмам: агенты на тороидальной решётке добывают ресурсы, играют в повторяющуюся дилемму заключённого и передают пятигенные стратегии следующим поколениям.

## Запуск

Нужен JDK 25. Python 3.12 нужен для анализа и графиков.

```bash
./gradlew test installDist
simulation/build/install/simulation/bin/simulation --output results/baseline --snapshots
python3 -m pip install -r scripts/requirements.txt
python3 scripts/analyze.py results/baseline
```

Откройте `results/baseline/demo.html`: карта мира с переключением поколений и графики. Численные результаты и ограничения — в `report.md` той же папки. На Windows используйте `gradlew.bat` и `simulation.bat`.

Полная серия: 18 сценариев × 30 seed × максимум 100 поколений.

```bash
simulation/build/install/simulation/bin/simulation --scenario all --threads 4 --snapshots --output results/full
python3 scripts/analyze.py results/full
python3 scripts/package_results.py results/full docs/results
```

Короткая проверка конвейера:

```bash
./gradlew :simulation:run --args='--scenario all --repetitions 2 --generations 2 --threads 2 --snapshots --output results/smoke'
python3 scripts/analyze.py results/smoke
```

CLI: `--scenario baseline|all|NAME`, `--repetitions N`, `--generations G`, `--threads N`, `--output DIR`, `--snapshots`, `--help`. По умолчанию: baseline, 30 повторов, 100 поколений, один поток. Seed равен номеру повтора начиная с 1. Каждый запуск использует собственный генератор; число потоков не меняет CSV. Используйте отдельную папку для каждой серии: CSV перезаписываются.

## Готовность базовой модели

- [x] Решётка, агенты, возобновляемые ресурсы и синхронное движение.
- [x] Повторяющаяся игра, пятигенная стратегия, энергия и смерть.
- [x] Турнирный отбор, скрещивание, мутация и смена поколений.
- [x] Локальный и глобальный выбор партнёров.
- [x] Полные метрики сотрудничества, исходов, энергии, генотипов и кластеризации.
- [x] CSV с параметрами, seed, PRNG, версиями и статусом запуска.
- [x] Тесты и CI; 18 сценариев для полного исследования.
- [x] SVG-снимки мира, HTML-демонстрация, графики и статистический отчёт.
- [x] Полная серия из 540 запусков; [наблюдения и доступ к результатам](docs/results/report.md).

Культурная передача, наследуемый поиск ресурсов, ошибки действий и длинная память остаются необязательными расширениями.

## Структура

| Пакет / папка | Назначение |
|---|---|
| evolution.game | Генотип, матрица выигрышей, статистика встречи |
| evolution.world | Тор, агенты и ресурсный слой |
| evolution.simulation | Фазы такта, генетика, сценарии, метрики, CSV и SVG |
| scripts | Анализ, графики и демонстрация |
| docs | Спецификация, план и исследование |

[Спецификация](docs/model-specification.md) · [План](docs/experiment-plan.md) · [Воспроизведение](docs/reproduction.md) · [Текст исследования](docs/study.md)

Доля действий C сама по себе не доказывает распространение условных стратегий. Изменение K меняет также энергетический вклад игры. Эти ограничения отражены в отчёте.
