# PharmaOS — B2B Pharmaceutical Distribution & Stockist Platform

**PharmaOS** is an enterprise-grade Android application engineered for pharmaceutical stockists, distributors, and wholesale suppliers. Built using **Kotlin**, **Jetpack Compose**, and **Room Database**, it delivers real-time inventory management, batch-level cold-chain tracking, supplier order routing, and regulatory compliance monitoring (FEFO/FIFO, Drug Recall matching).

---

## 🚀 Key Features

### 1. Real-Time Inventory Dashboard & Critical Stock Alerts
- **Live Reactive Dashboard:** Powered by Room reactive `Flow` streams (`getAllBatchesFlow`, `getAllProductsFlow`, `getAllRecallsFlow`) with zero manual polling.
- **Critical Stock Flagging:** Dynamically highlights out-of-stock and low-stock SKUs with visual health progress bars.
- **Risk & Expiry Monitoring:** Flags batches expiring within 30, 60, or 90 days, as well as active regulatory recalls.
- **Quick Restock Action:** One-tap replenishment that instantly calculates MOQ and adds items to the active procurement basket.
- **Audit-Compliant Stock Adjustments:** Inward new batches, adjust quantities (+1/-1, +10/-10), and log reasons (Damage, Breakage, Audit Variance, Inward Transfer).

### 2. Purchase Order Pipeline & Supplier Split Routing
- **Supplier-Split Orders:** Automatically groups multi-product purchase orders by manufacturer/supplier with separate GST and delivery calculations.
- **Order Tracking:** Track active consignments, batch serialization, QA checks, cold-chain temperature ranges (2°C - 8°C), and live logistics ETA.
- **Full Order Lifecycle:** From pending PO generation to dispatch, transit, and delivery confirmation.

### 3. Comprehensive Batch & Expiry Management
- **FEFO (First-Expired, First-Out):** Prioritizes earliest expiring stock to minimize write-offs.
- **Cold-Chain Assurance:** Dedicated monitoring for insulin, vaccines, and sensitive biologicals requiring temperature-controlled logistics.
- **Active Drug Recall Warning:** Instant warning badges against batches matching regulatory recalls.

### 4. Enterprise Stockist Navigation & Theming
- **Modern Drawer Navigation:** Quick-access sidebar with user profile badge, catalog shortcuts, order history, and active basket count.
- **System-Wide Light & Dark Theme:** High-contrast, polished Material 3 design optimized for both bright warehouse floors and low-light operations.
- **Authentication & Multi-Account Switching:** Stockist/distributor profile with enterprise credentials and Google Sign-in integration.

---

## 🛠️ Tech Stack & Architecture

- **Language:** Kotlin (100%)
- **UI Framework:** Jetpack Compose + Material Design 3 (M3)
- **Local Persistence:** Android Room Database (Entities, DAOs, Reactive Flows)
- **Concurrency & Streams:** Kotlin Coroutines (`viewModelScope`) & StateFlow (`collectAsStateWithLifecycle`)
- **Architecture Pattern:** MVVM (Model - View - ViewModel) + Repository Pattern
- **Navigation:** Jetpack Navigation Compose with type-safe route management

---

## 📂 Project Structure

```text
app/src/main/java/com/example/
├── data/
│   ├── local/
│   │   ├── dao/             # Room DAOs (ProductDao, BatchDao, OrderDao, etc.)
│   │   ├── entity/          # Room Database Entities
│   │   └── PharmaDatabase.kt
│   └── repository/          # PharmaRepository implementation & reactive stream combiners
├── domain/
│   ├── model/               # Domain Models (InventoryStockItem, Order, Batch, etc.)
│   └── repository/          # PharmaRepository interfaces
├── ui/
│   ├── components/          # Reusable Compose components (Cards, Badges, Headers)
│   ├── navigation/          # AppNavigation & NavRoutes
│   ├── screens/
│   │   ├── auth/            # Login & Account selection screens
│   │   ├── catalog/         # Product search & Manufacturer catalog
│   │   ├── checkout/        # Supplier split checkout & review
│   │   ├── dashboard/       # Main overview & quick actions
│   │   ├── inventory/       # Real-Time Inventory Dashboard & Batch Expiry
│   │   ├── orders/          # Order Pipeline & Consignment Tracking
│   │   └── settings/        # Profile, Dark Theme toggle, & ERP sync
│   └── theme/               # Material 3 ColorScheme, Typography & Shapes
```

---

## 📦 Building & Running

### Requirements
- Android SDK 34+
- Java 17+
- Gradle 8.0+

### Build Commands
```bash
# Compile debug APK
gradle assembleDebug

# Run unit and local JVM tests
gradle testDebugUnitTest
```
