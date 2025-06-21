# Демонстрация библиотеки autocomplete на основе текстового редактора

Простой текстовый редактор написанный с помощью JavaFX для демонстрации работы библиотеки autocomplete.

## Фичи

- JavaFX GUI текстовый редактор
- Предложение завершения слов в реальном времени
- Встроенные словари

## Требования

- Java 21 или выше
- Gradle (присутсвует wrapper)

## Запуск приложения

```bash
./gradlew run
```

## Билд приложения

```bash
./gradlew build
```

## Структура

```
autocomplete-demo/
├── app/
│   ├── build.gradle.kts          # конфигурация Gradle
│   └── src/main/java/org/example/
│       └── App.java              # JavaFX приложение
├── lib/                          # Autocomplete JAR файлы
│   ├── autocomplete-1.0-SNAPSHOT.jar
│   ├── autocomplete-1.0-SNAPSHOT-sources.jar
│   └── autocomplete-1.0-SNAPSHOT-javadoc.jar
└── gradle/                       # Gradle wrapper
```
