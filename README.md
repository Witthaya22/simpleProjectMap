# ระบบนำทางเดินเท้า Offline (Demo)

---

## โปรเจคนี้คืออะไร

แอป Android สำหรับ **จำลองการนำทางเดินเท้าแบบ Offline** โดยไม่ต้องพึ่ง Google Maps หรืออินเทอร์เน็ต (ยกเว้นครั้งแรกที่โหลดแผนที่)

แอปจะแสดงแผนที่พร้อมเส้นทางตัวอย่าง 5 จุดที่ฝังอยู่ในโค้ด และนำทางด้วย GPS จริงจากมือถือ โดยมีลูกศรบอกทิศทางและข้อความบอกระยะทางที่เหลือเป็นภาษาไทย

**เหมาะสำหรับ:** นักพัฒนาที่ต้องการเรียนรู้การสร้างระบบนำทางเบื้องต้น หรือต้องการต่อยอดเป็นแอปนำทางป่า/ทางเดินจริง

---

## ใช้เทคโนโลยีอะไรบ้าง และทำไมถึงเลือกใช้

| เทคโนโลยี | เวอร์ชัน | เหตุผลที่เลือก |
|---|---|---|
| **Kotlin** | 2.2.10 | ภาษาหลักของ Android ยุคใหม่ โค้ดสั้นและปลอดภัยกว่า Java |
| **OSMDroid** | 6.1.18 | แผนที่ OpenStreetMap ฟรี ไม่ต้องใช้ API Key ทำงาน Offline ได้ |
| **FusedLocationProviderClient** | Play Services 21 | อ่านค่า GPS จากมือถือ รวม GPS + เครือข่าย ให้ความแม่นยำสูง |
| **Android Views (XML)** | - | เข้าใจง่าย เหมาะกับการฝัง OSMDroid ซึ่งเป็น View-based library |

### ทำไมไม่ใช้ Google Maps?
- Google Maps ต้องใช้ API Key และเชื่อมต่ออินเทอร์เน็ตเสมอ
- OSMDroid ดาวน์โหลดแผนที่แล้วเก็บในมือถือได้ ใช้งาน Offline ได้จริง
- ข้อมูลแผนที่มาจาก OpenStreetMap ซึ่งเป็น Open Source ฟรีตลอดกาล

---

## วิธีติดตั้งและรันแอป ทีละขั้นตอน

### สิ่งที่ต้องมี
- Android Studio (Hedgehog หรือใหม่กว่า)
- JDK 17 ขึ้นไป
- มือถือ Android API 26 (Android 8.0) ขึ้นไป หรือ Emulator

### ขั้นตอน

**ขั้นที่ 1: เปิดโปรเจค**
```
เปิด Android Studio → File → Open → เลือกโฟลเดอร์ simpleProjectMap
```

**ขั้นที่ 2: รอ Gradle Sync**
```
Android Studio จะ sync โปรเจคอัตโนมัติ รอจนเสร็จ (1-3 นาที)
ถ้าไม่ sync ให้กด: File → Sync Project with Gradle Files
```

**ขั้นที่ 3: เชื่อมต่อมือถือ**
```
เปิด Developer Options บนมือถือ → เปิด USB Debugging
เสียบสาย USB กับคอมพิวเตอร์
```

**ขั้นที่ 4: รันแอป**
```
กดปุ่ม ▶ Run (Shift+F10) ใน Android Studio
เลือกมือถือที่ต้องการ → กด OK
```

**ขั้นที่ 5: ใช้งานแอป**
```
1. เปิดแอป → กด "อนุญาต" เมื่อมือถือขอสิทธิ์ GPS
2. แผนที่จะแสดงเส้นทางตัวอย่างสีแดง (บริเวณสยามสแควร์ กรุงเทพฯ)
3. กดปุ่ม "เริ่มนำทาง"
4. เดินตามลูกศร จนถึงจุดหมาย
```

> **หมายเหตุ:** ครั้งแรกที่เปิดแอปต้องมีอินเทอร์เน็ตเพื่อโหลดแผนที่มาเก็บไว้ก่อน หลังจากนั้นใช้งาน Offline ได้เลย

---

## โครงสร้างไฟล์ในโปรเจค

```
simpleProjectMap/
├── app/
│   └── src/main/
│       ├── java/com/example/simpleprojectmap/
│       │   └── MainActivity.kt          ← โค้ดหลักทั้งหมด (GPS, นำทาง, แผนที่)
│       │
│       ├── res/
│       │   ├── layout/
│       │   │   └── activity_main.xml    ← หน้าตาแอป (แผนที่, ปุ่ม, ข้อความ)
│       │   ├── drawable/
│       │   │   ├── ic_arrow.xml         ← ไอคอนลูกศรนำทาง (Vector)
│       │   │   └── ic_user_location.xml ← ไอคอนจุดสีน้ำเงิน (Vector)
│       │   └── values/
│       │       ├── strings.xml          ← ชื่อแอป
│       │       └── themes.xml           ← สีและ Theme ของแอป
│       │
│       └── AndroidManifest.xml          ← ขอ permission GPS, INTERNET
│
├── app/build.gradle.kts                 ← dependencies ทั้งหมด (OSMDroid, GPS)
├── gradle/libs.versions.toml            ← เวอร์ชัน library กลาง
└── README.md                            ← ไฟล์นี้
```

### อธิบายแต่ละไฟล์สำคัญ

| ไฟล์ | หน้าที่ |
|---|---|
| `MainActivity.kt` | สมองของแอป — รับค่า GPS, คำนวณทิศทาง, ตัดสินใจว่าถึงจุดหรือยัง |
| `activity_main.xml` | หน้าตา UI — แผนที่, ลูกศร, ตัวเลขระยะทาง, ปุ่มเริ่ม/หยุด |
| `ic_arrow.xml` | ลูกศรสีส้ม-แดง ที่หมุนบอกทิศทางไปยัง waypoint ถัดไป |
| `ic_user_location.xml` | จุดกลมสีน้ำเงินแสดงตำแหน่งคุณบนแผนที่ |
| `AndroidManifest.xml` | ขอสิทธิ์ใช้ GPS และอินเทอร์เน็ตจากมือถือ |
| `build.gradle.kts` | บอก Android Studio ว่าใช้ library อะไรบ้าง |

---

## วิธีที่แอปทำงาน

### ภาพรวม Logic

```
[เปิดแอป]
    │
    ▼
[แสดงแผนที่ + เส้นทางสีแดง]
    │
    ▼
[กดปุ่ม "เริ่มนำทาง"]
    │
    ▼
[รับค่า GPS ทุก 2 วินาที]  ←──────────────────┐
    │                                           │
    ▼                                           │
[คำนวณ bearing (มุม) → หมุนลูกศร]             │
    │                                           │
    ▼                                           │
[คำนวณระยะทางรวมที่เหลือ → แสดงบนจอ]         │
    │                                           │
    ▼                                           │
[ห่างจาก waypoint ≤ 15 เมตร?]                 │
    │ ใช่                    │ ยัง              │
    ▼                        └──────────────────┘
[ข้ามไป waypoint ถัดไป]
    │
    ▼
[ถึง waypoint สุดท้ายแล้ว?]
    │ ใช่
    ▼
[แสดง Dialog "ถึงจุดหมายแล้ว!"]
```

### การคำนวณทิศทาง (Bearing)

แอปใช้สูตรคณิตศาสตร์ **Haversine-based bearing** คำนวณว่าต้องเดินไปทิศไหน:
- ผลลัพธ์เป็นองศา 0–360° (0° = เหนือ, 90° = ตะวันออก)
- ลูกศรบนจอจะหมุนตามองศานั้น

### การตรวจจุดหมาย (Waypoint Detection)

ทุกครั้งที่ GPS อัปเดต แอปจะตรวจว่าระยะห่างจากตำแหน่งปัจจุบันถึง waypoint ถัดไปน้อยกว่า **15 เมตร** หรือเปล่า ถ้าใช่ก็ถือว่าผ่านจุดนั้นแล้ว และข้ามไปจุดถัดไป

---

## ขั้นตอนต่อไปถ้าอยากพัฒนาต่อ

### 1. เพิ่มระบบ Record เส้นทางเอง
**วิธีทำ:** เพิ่มปุ่ม "บันทึกเส้นทาง" แล้วเก็บพิกัด GPS ที่เดินผ่านลง Room Database (local database ใน Android) จากนั้นโหลดมาแสดงเป็นเส้นทางได้
```kotlin
// ตัวอย่าง: เพิ่ม dependency ใน build.gradle.kts
implementation("androidx.room:room-runtime:2.6.1")
```

### 2. ทำให้ Offline สมบูรณ์ 100%
**วิธีทำ:** ดาวน์โหลด Tile แผนที่ (ไฟล์ .mbtiles หรือ .sqlitedb) ล่วงหน้า แล้วบรรจุไว้ใน `assets/` ของแอป
```kotlin
// โหลด offline tile จาก assets
val tileSource = OfflineTileProvider(File(filesDir, "map.mbtiles"))
mapView.setTileSource(tileSource)
```

### 3. เพิ่มเส้นทางเดินป่าจริง
**วิธีทำ:** นำเข้าไฟล์ **GPX** (รูปแบบมาตรฐานของ GPS track) แล้วแปลงเป็น `List<GeoPoint>` แทนการ hardcode
```
tools แนะนำ: Garmin BaseCamp, GPX Editor, หรือ Google Earth (export เป็น KML แล้วแปลง)
```

### 4. เพิ่มเสียงนำทาง
**วิธีทำ:** ใช้ `TextToSpeech` ของ Android อ่านข้อความภาษาไทย เช่น "เลี้ยวขวา" หรือ "เดินตรงไป 30 เมตร"
```kotlin
val tts = TextToSpeech(context) { status -> }
tts.language = Locale("th", "TH")
tts.speak("เดินตรงไป", TextToSpeech.QUEUE_FLUSH, null, null)
```

### 5. แสดงความเร็วและเวลาที่คาดว่าจะถึง
**วิธีทำ:** คำนวณจาก `location.speed` (เมตร/วินาที) หารด้วยระยะทางที่เหลือ

---

## ปัญหาที่อาจเจอและวิธีแก้

### แผนที่แสดงแต่ตาราง/ไม่มีแผนที่
**สาเหตุ:** ยังไม่มีการดาวน์โหลด tile แผนที่
**วิธีแก้:** ต้องเปิดแอปพร้อมอินเทอร์เน็ตอย่างน้อยหนึ่งครั้ง แล้วซูมไปที่บริเวณที่ต้องการ รอแผนที่โหลดจนครบ จากนั้นใช้ Offline ได้

### ปุ่มเริ่มนำทางกดแล้วไม่ทำงาน
**สาเหตุ:** ยังไม่ได้อนุญาต GPS
**วิธีแก้:** ไปที่ Settings → Apps → นำทางเดินเท้า → Permissions → เปิด "Location" เป็น "Allow all the time" หรือ "Allow only while using the app"

### ลูกศรหมุนผิดทิศ
**สาเหตุ:** GPS ใหม่ๆ อาจมี error สูง โดยเฉพาะในอาคารหรือใต้ต้นไม้
**วิธีแก้:** ออกไปที่โล่งๆ รอ GPS lock สักครู่ (10-30 วินาที) จนตัวเลข accuracy ดีขึ้น

### Build Error: "Could not resolve org.osmdroid"
**สาเหตุ:** ไม่มีอินเทอร์เน็ตตอน build ครั้งแรก
**วิธีแก้:** เชื่อมต่ออินเทอร์เน็ต แล้ว sync Gradle ใหม่ (File → Sync Project with Gradle Files)

### แอปหยุดทำงาน (Crash) ตอนเปิด
**สาเหตุ:** OSMDroid ต้องการ user agent
**วิธีแก้:** ตรวจให้แน่ใจว่าใน `onCreate()` มีบรรทัดนี้ก่อน `setContentView()`:
```kotlin
Configuration.getInstance().userAgentValue = packageName
```

### GPS อัปเดตช้ามาก
**สาเหตุ:** ตั้งค่า interval ที่ 2000ms (2 วินาที)
**วิธีแก้:** ลด interval ใน `LocationRequest.Builder`:
```kotlin
// เปลี่ยนจาก 2000L เป็น 1000L สำหรับอัปเดตทุก 1 วินาที
LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
```

---

*สร้างด้วย Android Studio + Kotlin + OSMDroid — ระบบนำทางเดินเท้า Offline Demo*
