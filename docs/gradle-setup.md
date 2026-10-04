###################################################
# Настройка Gradle в Android Studio

Документ описывает нюансы запуска приложения из Android Studio
и работы с APK-файлами в TScalp.

## Автоматический `clean assembleDebug` перед запуском

**Проблема:** при нажатии Shift+F10 Android Studio запускает
`installDebug` без предварительной очистки. Из-за этого:
- задачи `renameDebugApk` / `copyDebugApk` не отрабатывают
  (Gradle считает `assembleDebug` up-to-date);
- APK остаётся старым, если не сделать чистую сборку;
- приходится вручную запускать
  `./gradlew :app:clean :app:assembleDebug` из терминала.

**Решение:** настроить в Android Studio запуск задачи
`clean assembleDebug` в секции **Before launch** конфигурации
запуска.

### Пошагово

1. **Run → Edit Configurations…**
2. Выбрать конфигурацию `app`.
3. В разделе **Before launch** нажать **+**.
4. Выбрать **Run Gradle Task**.
5. В поле **Gradle project** указать директорию проекта.
6. В поле **Tasks** задать: `clean assembleDebug`
7. Нажать **OK**.

### Результат

При каждом Shift+F10:
1. Выполнится `clean assembleDebug` — полная пересборка APK с нуля.
2. Установится свежий APK.
3. Приложение запустится.

Красивое имя APK (`tscalp-debug-v1.XXX.apk`) появляется
автоматически через `finalizedBy` на `assembleDebug`.

## Замечания

- **Configuration cache:** флаг `--no-configuration-cache` нельзя
  передать через Run Gradle Task. Альтернатива — отключить
  configuration cache глобально в `gradle.properties` строкой
  `org.gradle.configuration-cache=false`.
- **Время сборки:** `clean` замедляет запуск. Если мешает —
  уберите `clean`, положившись на инкрементальную сборку.
- **Release:** аналогично можно создать отдельную конфигурацию
  с задачей `clean assembleRelease`.