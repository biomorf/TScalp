<!-- SECTION_BEGIN: issues-ci -->
## Ci










<!-- SUBSECTION_BEGIN: i-5 -->
___
### [DEFER] I-5 Gradle 10: устаревшие API в сторонних плагинах
**Статус:** отложен. Не наш код.

**Симптом:** при сборке (Gradle 9.8.1) в конце выводится
предупреждение:

    Deprecated Gradle features were used in this build, making it
    incompatible with Gradle 10.

**Причина:** один или несколько сторонних плагинов (Hilt, KSP,
AGP) используют API, запланированные к удалению в Gradle 10.
`--warning-mode all` не показывает источников из наших скриптов —
значит, предупреждение приходит от плагинов.

**Решение:** ожидать обновлений плагинов. Отслеживать через
release notes Hilt, KSP, AGP. Перед апгрейдом до Gradle 10 —
проверить, что все плагины поддерживают.

**Когда вернуться:** при подготовке к апгрейду Gradle 10
(ожидается в 2027+), либо если предупреждение начнёт
накапливаться с новыми source-локациями.

---
<!-- SUBSECTION_END: i-5 -->










<!-- SUBSECTION_BEGIN: i-22 -->
___
### [DEFER] I-22 Strip warnings в debug APK
**Статус:** отложен. Не наш код.

**Симптом:** при `./gradlew :app:assembleDebug` (или
`:app:installDebug`) в выводе:

    > Task :app:stripDebugDebugSymbols
    Unable to strip the following libraries, packaging them as
    they are: libandroidx.graphics.path.so, libconscrypt_jni.so,
    libdatastore_shared_counter.so.

**Причина:** `stripDebugDebugSymbols` пытается удалить отладочные
символы из `.so` библиотек для уменьшения APK. Часть библиотек
не содержит strip-таблиц (собраны без них), поэтому strip не
может их обработать и оставляет как есть. Сборка не падает —
`.so` попадают в APK в исходном виде.

**Последствия:** нет. Единственное — debug APK чуть больше.
В release-сборке (`minifyEnabled = false`) strip выполняется,
на маленьких библиотеках разница незначительна.

**Решение:** не наша проблема. Все три — сторонние:
- `libandroidx.graphics.path.so` — AndroidX (core).
- `libconscrypt_jni.so` — Conscrypt (SSL на Android).
- `libdatastore_shared_counter.so` — DataStore (Preferences).

Если понадобится убрать warning — `packaging { jniLibs {
keepDebugSymbols += "..." } }` или `doNotStrip`. Лишний конфиг
ради косметики.

**Когда вернуться:** если warning начнёт накапливаться новыми
библиотеками (5+) или размер debug APK станет проблемой.

---
<!-- SUBSECTION_END: i-22 -->





<!-- SECTION_END: issues-ci -->
