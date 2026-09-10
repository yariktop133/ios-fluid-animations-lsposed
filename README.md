# iOS Fluid Animations (LSPosed Module)
### Высокопроизводительные анимации в стиле iOS для Poco X6 Pro 5G (LineageOS 23.2 / Android 16)

[![GitHub Release](https://img.shields.io/badge/Release-v1.2.1-blue.svg)](https://github.com/yariktop133/ios-fluid-animations-lsposed/releases)
[![Platform](https://img.shields.io/badge/Platform-Android%2016%20(API%2036)-green.svg)](https://android.com)
[![Target](https://img.shields.io/badge/Device-Poco%20X6%20Pro%205G%20(120Hz)-orange.svg)]()
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed%20Vector%202.2%2B-purple.svg)]()

Продвинутый LSPosed-модуль, переносящий естественную физику анимаций iOS (Spring Physics, Fluid Transitions, Icon Morphing и Landing Bounce) в прошивку **LineageOS 23.2 (Android 16)** на смартфоне **Poco X6 Pro 5G** (MediaTek Dimensity 8300-Ultra, экран 120 Гц).

---

## 🚀 Ключевые возможности

1. **Аутентичная физика пружин (iOS Spring Engine)**:
   - Аналитическая модель затухающего гармонического осциллятора (Damped Harmonic Oscillator), полностью идентичная `CASpringAnimation` в iOS.
   - 1024-точечная таблица предрасчета (Look-Up Table / LUT) с линейной интерполяцией — **0 аллокаций памяти и 0 наносекунд GC-пауз** на частоте 120 FPS.

2. **Интеграция с Trebuchet Launcher (Quickstep)**:
   - **Launch from Icon**: плавное расширение окна из центра иконки с динамической трансформацией формы.
   - **Swipe-to-Home Gesture**: бесшовный возврат окна в иконку с эффектом легкого упругого отскока (*iOS Landing Bounce*).
   - **FloatingIconView Morphing**: адаптивное скругление углов (от ~38dp дисплея Poco X6 Pro до формы иконки лаунчера).
   - **Wallpaper Depth Zoom**: мягкий параллакс и отдаление обоев при переходе.

3. **SystemUI WMShell Transitions (App-to-App)**:
   - Перехват переходов между приложениями в WMShell: плавное масштабирование ($0.93 \to 1.0$) с кривой Cubic Bezier `(0.32, 0.72, 0.0, 1.0)`.
   - Оптимизированные тайминги открытия приложений из Центра Уведомлений и экрана блокировки.

4. **Архитектура нулевых задержек (Zero-IPC)**:
   - Настройки передаются между модулем, лаунчером и SystemUI через кастомный `ContentProvider` и обновляются в реальном времени через `ContentObserver`.
   - В цикле рендера кадра обращение идет напрямую к разделяемой памяти процесса без IPC и блокировок потока рендеринга.

5. **Панель управления (Material You UI)**:
   - Готовые пресеты: *iOS Fluid (Default)*, *iOS Bouncy*, *Snappy 120Hz*.
   - Ползунки жесткости (*stiffness*), затухания (*damping ratio*), длительностей и радиуса углов.
   - Встроенный интерактивный симулятор пружины для оценки тактильного отклика прямо в приложении.

---

## 📱 Требования

- **Устройство**: Poco X6 Pro 5G (`duchamp`) или аналогичные устройства с экраном 120 Гц.
- **ОС**: LineageOS 23.2 (Android 16 / API 36).
- **Root**: Magisk 26+, KernelSU или APatch.
- **Xposed Framework**: LSPosed (ветка Vector 2.2+ / Zygisk LSPosed с поддержкой Android 15/16).

---

## 🛠️ Установка

1. Скачайте последнюю версию APK из раздела [Releases](https://github.com/yariktop133/ios-fluid-animations-lsposed/releases).
2. Установите APK на смартфон.
3. Откройте приложение **LSPosed** $\to$ вкладка «Модули».
4. Включите модуль **iOS Fluid Animations**.
5. Убедитесь, что в скоупе модуля отмечены:
   - `Trebuchet` (`org.lineageos.trebuchet` или `com.android.launcher3`)
   - `Системный интерфейс` (`com.android.systemui`)
   - `Система Android` (`android`)
6. Перезагрузите смартфон.
7. Откройте приложение модуля для настройки пресетов и параметров под свои предпочтения.

---

## ⚙️ Пресеты физики

| Параметр | iOS Fluid (Default) | iOS Bouncy | Snappy 120Hz |
|---|---|---|---|
| **Stiffness** | 340 | 290 | 450 |
| **Damping Ratio** | 0.84 | 0.78 | 0.88 |
| **Время открытия (Open)** | 360 ms | 380 ms | 260 ms |
| **Время закрытия (Close)** | 410 ms | 450 ms | 290 ms |
| **App $\leftrightarrow$ App** | 320 ms | 340 ms | 260 ms |
| **Landing Bounce** | Включен | Включен | Выключен |

---

## 🏗️ Сборка из исходников

```bash
# Клонирование репозитория
git clone https://github.com/yariktop133/ios-fluid-animations-lsposed.git
cd ios-fluid-animations-lsposed

# Сборка APK (требуется JDK 21 и Android SDK)
./gradlew assembleRelease
```
Собранный APK будет находиться в `app/build/outputs/apk/release/app-release-unsigned.apk`.

---

## 📄 Лицензия
Private Repository • All rights reserved • 2026
