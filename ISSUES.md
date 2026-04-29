markdown

\# ISSUES: protobuf-field-descriptors



\*\*Контекст\*\*  

При реализации методов `getOrders()` и `getStopOrders()` в `TInvestService` возникли систематические проблемы с доступом к полям protobuf-сообщений:

\- `Unresolved reference` для геттеров `order.price`, `order.stopOrderType`, `order.createDate`

\- Конфликт типов `String!` (platform type) vs `kotlin.String`

\- Невозможность прямого вызова `addAllStatus()` из‑за отсутствия метода в конкретной версии SDK



\*\*Причина\*\*  

Сгенерированные Java-классы protobuf могут не экспонировать все поля через геттеры, особенно если используются разные версии контракта или обёртки. Кроме того, platform‑тип `String!` не всегда приводится к `kotlin.String` без явного `as String`.



\*\*Решение\*\*  

Использовать protobuf-дескрипторы полей (`FieldDescriptor`) для прямого доступа к любому полю сообщения, независимо от доступности геттеров.  

Шаблон (на примере поля `order\_type` в `StopOrder`):

```kotlin

val fieldDescriptor = order.descriptorForType.findFieldByName("order\_type")

val value = fieldDescriptor?.let { order.getField(it) }

// Приведение к нужному типу, например EnumValueDescriptor

val typeName = (value as? EnumValueDescriptor)?.name?.removePrefix("PREFIX\_")

Аналогично для create\_date, price и других полей.

Для строк: явное приведение as String после проверки на null.

Этот подход не зависит от версии SDK и гарантирует компиляцию.



Что нужно помнить при обновлении SDK



Проверять, не появились ли нативные геттеры для нужных полей – тогда можно упростить код.



Имена полей в дескрипторе соответствуют protobuf-определению (использовать snake\_case, как в .proto файле).



При падении в рантайме – включить логирование Log.d(TAG, "allFields: ${message.allFields}"), чтобы увидеть реальные имена полей.



Дата: 2026-04-30

Автор: совместная отладка









