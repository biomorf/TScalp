#!/usr/bin/env bash
# run-tests.sh — запуск unit-тестов с фильтром по классу.
#
# Использование:
#   ./run-tests.sh                          — все unit-тесты
#   ./run-tests.sh PairOrderMapperTest      — только один класс
#   ./run-tests.sh usages                   — все тесты в пакете usages

set -e

# JAVA_HOME: берём из окружения, если не задан — используем JBR Android Studio.
# Это удобно для локальной разработки в Bluefin/Toolbox, но не переносится
# на другие машины без правки пути.
if [ -z "$JAVA_HOME" ]; then
    FALLBACK_JBR="$HOME/.local/share/JetBrains/Toolbox/apps/android-studio/jbr"
    if [ -x "$FALLBACK_JBR/bin/java" ]; then
        export JAVA_HOME="$FALLBACK_JBR"
        echo "JAVA_HOME не задан, использую JBR: $JAVA_HOME"
    else
        echo "ERROR: JAVA_HOME не задан, и JBR Android Studio не найден." >&2
        echo "Установите JAVA_HOME или поправьте путь в этом скрипте." >&2
        exit 1
    fi
fi

if [ -z "$1" ]; then
    echo "==> Запуск всех unit-тестов"
    ./gradlew :app:testDebugUnitTest
else
    echo "==> Запуск тестов, matching '$1'"
    ./gradlew :app:testDebugUnitTest --tests "*$1*"
fi