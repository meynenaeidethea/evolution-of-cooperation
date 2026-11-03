# Эволюция сотрудничества

Пространственная модель повторяющейся дилеммы заключенного с генетическим отбором.
Есть интерактивное окно JavaFX, пакетные эксперименты и анализ результатов.

[Состояние веток и история изменений](docs/development-status.md#согласование-истории-и-интерактивная-лаборатория).

**Что изменилось:** [CHANGELOG.md](CHANGELOG.md) — функции, изменения параметров и порядок просмотра кода.

## Запуск

Нужен JDK 25. Gradle wrapper скачает зависимости при первой сборке.

~~~sh
./gradlew :simulation:build
./gradlew :simulation:runVisual
~~~

Окно сразу открывает демонстрационный мир 20×20 с 160 агентами. Нажмите
«Воспроизвести»; «Параметры…» позволяет менять правила с понятными единицами
энергии и процентами. Показатели последнего такта отделены от сумм поколения.
Все рассчитанные кадры автоматически сохраняются в `results/replays/*.eoc`:
доступны полная перемотка, сохранение копии и открытие записи. Расчёт и файловые
операции выполняются в фоне.

[Руководство по окну, параметрам и полному просмотру](docs/interactive-lab.md).

## Эксперименты

~~~sh
./gradlew :simulation:runStudy --args='--profile profiles/demo.properties --output results/example --repetitions 2 --seed-start 800001 --threads 2 --scenarios baseline,normalized-1,global-matched'
./gradlew :simulation:analyzeExperiments --args='--input results/example --output results/example-report'
~~~

Отчет: results/example-report/report.html. Два повтора подходят для проверки запуска.
Нужен новый каталог; для прерванной серии повторите исходную команду с --resume.
Завершенные серии не перезаписываются.

| Профиль | Мир / агенты | Тактов / поколений | Назначение |
|---|---|---|---|
| profiles/demo.properties | 20×20 / 160 | 30 / 10 | Короткая демонстрация |
| profiles/research.properties | 20×20 / 160 | 50 / 60 | Исходное исследование |
| profiles/user.properties | 100×100 / 300 | 1000 / 20 | Сохраненные локальные настройки |

## Результаты и материалы

- [Краткие результаты](docs/research-summary.md), [пояснительная записка](docs/research-paper.md).
- [Основная серия: 480 запусков](docs/results/main-study.md).
- [Дополнительная проверка: 1400 запусков](docs/results/robustness-study.md).
- [Подробные команды, параметры и воспроизводимость](docs/running.md).
- [Правила модели](docs/model-specification.md), [CSV](docs/csv-results.md), [проверки](docs/development-status.md).

**Архивы скачиваются отдельно:** [GitHub Release: Research datasets](https://github.com/meynenaeidethea/evolution-of-cooperation/releases/tag/research-data-20261102).
Они содержат первичные CSV, полные отчеты, графики и контрольные суммы.
Git хранит код, документацию и небольшие итоговые таблицы. Для запуска модели архивы не нужны.
