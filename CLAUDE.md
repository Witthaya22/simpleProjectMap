# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## โปรเจคนี้คืออะไร

**ระบบนำทางเดินเท้า Offline Demo** — แอป Android สำหรับทดสอบระบบนำทาง GPS แบบ offline  
ใช้ OSMDroid (OpenStreetMap) แสดงแผนที่ และ FusedLocationProviderClient รับ GPS จากมือถือ  
มี waypoints ฝังไว้ใน code ที่ มจพ. วิทยาเขตปราจีนบุรี (14.1610, 101.3472)

## Build & Run

```bash
# Build debug APK (Windows)
gradlew.bat assembleDebug

# Install ผ่าน adb โดยตรง (แนะนำ — ข้ามปัญหา INSTALL_FAILED_USER_RESTRICTED)
adb install -r app\build\outputs\apk\debug\app-debug.apk

# หรือ install ผ่าน Gradle
gradlew.bat installDebug

# Run unit tests
gradlew.bat test

# Lint
gradlew.bat lint
```

> **หมายเหตุ:** ถ้า `adb` ไม่เจอให้ใช้ full path:
> `C:\Users\Acer\AppData\Local\Android\Sdk\platform-tools\adb.exe`

## Architecture

Single-Activity Android app — ใช้ traditional View system (XML layout) ไม่ใช่ Compose

- **Entry point:** `MainActivity.kt` — logic ทั้งหมดอยู่ที่นี่ (GPS, แผนที่, นำทาง)
- **Layout:** `res/layout/activity_main.xml` — MapView + panel ด้านล่าง
- **Drawables:** `ic_arrow.xml` (ลูกศรนำทาง), `ic_user_location.xml` (จุดสีน้ำเงิน)
- **Theme:** `res/values/themes.xml` — `android:Theme.Material.Light.NoActionBar`
- **Package:** `com.example.simpleprojectmap`
- **minSdk:** 26, **targetSdk/compileSdk:** 36
- **AGP:** 9.2.1, **Kotlin:** 2.2.10

## Dependencies หลัก

```
org.osmdroid:osmdroid-android:6.1.18       ← แผนที่ OpenStreetMap
com.google.android.gms:play-services-location:21.0.1  ← GPS FusedLocation
```

ส่วน Compose BOM ยังอยู่ใน build.gradle.kts แต่ไม่ได้ใช้ใน MainActivity

## UX การใช้งาน (2 โหมด)

### โหมด 1: Pick Mode (เลือกจุด)
- แผนที่เปิดมาที่ประตูหลัก มจพ. ปราจีนบุรี (14.1610, 101.3472)
- **แตะบนแผนที่** → เพิ่ม waypoint ทีละจุด (Marker + เส้นสีแดงเชื่อมอัตโนมัติ)
- ปุ่ม "↩ ลบล่าสุด" — ลบจุดที่เพิ่งแตะ
- ปุ่ม "🗑 ล้างทั้งหมด" — เริ่มใหม่ (มี dialog confirm)
- ปุ่ม "เริ่มนำทาง →" — active เมื่อมี ≥ 2 จุด

### โหมด 2: Nav Mode (นำทาง)
- ลูกศรหมุน + ระยะทางที่เหลือ
- แสดง "มุ่งหน้า: จุดที่ X" ด้านบน
- เมื่อถึงจุด (≤ 15 เมตร) → ข้ามไปจุดถัดไปอัตโนมัติ
- ปุ่ม "■ หยุดนำทาง" → กลับ Pick Mode (waypoints ยังอยู่)

## Navigation Logic

- GPS อัปเดตทุก **2 วินาที** (min interval 1 วินาที)
- เมื่อเข้าใกล้ waypoint **≤ 15 เมตร** → ข้ามไปจุดถัดไปอัตโนมัติ
- ลูกศรหมุนด้วย `view.rotation = bearing.toFloat()` (Haversine bearing formula)
- ระยะทางที่เหลือ = ระยะถึง waypoint ปัจจุบัน + ผลรวมระยะระหว่าง waypoints ที่เหลือ

## Overlay ordering (สำคัญ)

OSMDroid processes overlays **forward** (index 0 first). เพื่อให้ Marker รับ tap ก่อน MapEventsOverlay:
- `MapEventsOverlay` ต้องอยู่ **ท้ายสุด** ของ overlays list เสมอ
- ใช้ `insertBeforeEventsOverlay()` ทุกครั้งที่เพิ่ม Marker/Polyline ใหม่
- ถ้า tap ตรง Marker → Marker handles it (ไม่เพิ่ม waypoint)
- ถ้า tap แผนที่ว่าง → MapEventsOverlay เพิ่ม waypoint

## ปัญหาที่เคยเจอ

| ปัญหา | สาเหตุ | วิธีแก้ |
|---|---|---|
| `Array<out String>` compile error | Kotlin 2.x strict type variance | ใช้ `Array<String>` ใน `onRequestPermissionsResult` |
| `INSTALL_FAILED_USER_RESTRICTED` | MIUI block install via Gradle | ใช้ `adb install -r <apk>` แทน |
| แผนที่ไม่แสดง (gray tiles) | ยังไม่ได้โหลด tile | ต้องมีอินเทอร์เน็ตครั้งแรก แล้วซูมไปพื้นที่ มจพ.ปราจีน |
| RenderInspector QueueBuffer timeout | MIUI warning ปกติ | ไม่ใช่ crash — ปกติสำหรับ Xiaomi |
| `Unable to create base path at null` (OsmDroid debug log) | Configuration ไม่มี context ก่อน inflate XML | เรียก `Configuration.getInstance().load(applicationContext, ...)` ก่อน `setContentView` |

## วิธีใช้งานแอป

1. เปิดแอป → กด **"อนุญาต"** เมื่อมือถือขอสิทธิ์ GPS
2. แผนที่แสดงเส้นทางสีแดง (บริเวณ มจพ. ปราจีนบุรี)
3. กดปุ่ม **"เริ่มนำทาง"** → ลูกศรจะหมุนชี้ทิศทาง
4. เดินตามลูกศร → ระยะทางลดลงเรื่อยๆ
5. เมื่อเข้าใกล้แต่ละจุด → ข้ามไปจุดถัดไปอัตโนมัติ
6. ถึงจุดสุดท้าย → Dialog "ถึงจุดหมายแล้ว!"
7. กด **"หยุด"** ได้ตลอดเวลา

## Permissions ที่ใช้

- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — GPS
- `INTERNET` — โหลด OSM tile แผนที่
- `WRITE_EXTERNAL_STORAGE` — cache tile (maxSdkVersion=28, API 29+ ใช้ internal cache)

## All dependency versions

รวมอยู่ใน `gradle/libs.versions.toml`  
Dependencies ใหม่ (osmdroid, play-services) ถูกเพิ่มตรงใน `app/build.gradle.kts` เป็น string literal
