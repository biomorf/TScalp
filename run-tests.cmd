@echo off
setlocal

if "%JAVA_HOME%"=="" (
    set "FALLBACK_JBR=%LOCALAPPDATA%\Programs\Android Studio\jbr"
    if exist "%FALLBACK_JBR%\bin\java.exe" (
        set "JAVA_HOME=%FALLBACK_JBR%"
        echo JAVA_HOME не задан, использую JBR: %JAVA_HOME%
    ) else (
        echo ERROR: JAVA_HOME не задан, и JBR Android Studio не найден.>&2
        echo Установите JAVA_HOME или поправьте путь в этом скрипте.>&2
        exit /b 1
    )
)

if "%~1"=="" (
    echo ==^> Запуск всех unit-тестов
    call gradlew.bat :app:testDebugUnitTest
) else (
    echo ==^> Запуск тестов, matching "%~1"
    call gradlew.bat :app:testDebugUnitTest --tests "*%~1*"
)

endlocal
