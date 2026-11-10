# PharmaOS — B2B Pharmaceutical Distribution & Stockist Platform

**PharmaOS** is an enterprise-grade Android application engineered for pharmaceutical stockists, distributors, and wholesale suppliers. Built using **Kotlin**, **Jetpack Compose**, and **Room Database**, it delivers real-time inventory management, batch-level cold-chain tracking, supplier order routing, and regulatory compliance monitoring (FEFO/FIFO, Drug Recall matching).

---

## 🚀 Key Features

### 1. Real-Time Inventory Dashboard & Automated Depletion Monitoring Service
- **Continuous Inventory Monitoring Service (`InventoryMonitoringService` & `InventoryMonitorManager`):** Real-time background service observing Room reactive `Flow` streams to detect when critical medicines approach depletion thresholds.
- **Automated High-Priority Push Notifications:** Immediately dispatches system push alerts with sound and vibration via `NotificationHelper` when critical SKUs drop $\le$ depletion threshold (default 10 units) or hit stockout (0 units).
- **Anti-Spam Alert Cooldown & Deduplication:** Prevents repetitive alert flooding; only re-notifies when stock drops further or after a configurable cooldown.
- **Periodic Background Worker (`InventoryDepletionWorker`):** WorkManager-backed periodic checks ensuring offline/background vigilance.
- **Configurable Depletion Thresholds:** In-app settings to customize depletion thresholds (5, 10, 15, 20, 25, 30 units), toggle automated push alerts, and trigger manual "Scan Now" checks.
- **Critical Stock Flagging:** Dynamically highlights out-of-stock and low-stock SKUs with visual health progress bars and quick restock actions.
- **Risk & Expiry Monitoring:** Flags batches expiring within 30, 60, or 90 days, as well as active regulatory recalls.
- **Audit-Compliant Stock Adjustments:** Inward new batches, adjust quantities (+1/-1, +10/-10), and log reasons (Damage, Breakage, Audit Variance, Inward Transfer).

### 2. Purchase Order Pipeline & Multi-Method Payment Settlement
- **Supplier-Split Orders:** Automatically groups multi-product purchase orders by manufacturer/supplier with separate GST and delivery calculations.
- **Dedicated B2B Payment Screen:** Select from wholesale payment terms:
  - **Pharma Wholesale Credit Line:** Net-30 deferred settlement with 0% interest and live credit limit authorization.
  - **Instant UPI:** Direct payment via Google Pay, PhonePe, Paytm, BHIM, or dynamic QR code.
  - **Corporate Net Banking / RTGS:** Direct transfer into PharmaOS escrow accounts.
  - **Post-Dated Cheque (PDC):** 30-day verified PDC handover upon dispatch.
  - **Cash on Inward Delivery (COD):** Dock inspection and verification before payment.
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

## 📦 Environment Requirements & SDK Configuration

The project targets modern Android devices with backward compatibility down to Android 7.0:

| Property | Value | Notes |
| :--- | :--- | :--- |
| **`compileSdk`** | **36** (Android 15) | Configured in `app/build.gradle.kts` |
| **`targetSdk`** | **36** (Android 15) | Strict Android 15 edge-to-edge & privacy compliance |
| **`minSdk`** | **24** (Android 7.0 Nougat) | Broad market compatibility |
| **Java Version** | **Java 11 / 17** | `sourceCompatibility = JavaVersion.VERSION_11`, `targetCompatibility = JavaVersion.VERSION_11` |
| **Gradle / AGP** | **Gradle 8.13+**, AGP 8.9.0+ | Kotlin 2.0+ with Compose Compiler plugin |

---

## 🔑 First-Time Developer Setup (Cloning from GitHub)

### ⚠️ Resolving `debug.keystore` Missing Error
When opening the project in Android Studio after cloning, you might encounter:
```text
Execution failed for task ':app:validateSigningDebug'.
Keystore file: '.../debug.keystore' not found for signing config 'debugConfig'.
```

This happens because `.gitignore` intentionally excludes binary `debug.keystore` files from being pushed to Git repositories to prevent repo bloat and permission issues.

### 🛠️ One-Click Fix: Run Keystore Setup Script
Before running the first build in Android Studio or via Gradle, execute the appropriate setup script from the root directory:

#### On Windows:
Double-click or run from Command Prompt / PowerShell:
```cmd
setup-debug-keystore.bat
```

#### On macOS / Linux:
Run in terminal:
```bash
chmod +x setup-debug-keystore.sh
./setup-debug-keystore.sh
```

**What the script does:**
1. Verifies if `debug.keystore` already exists (leaves existing keystore untouched).
2. If `debug.keystore.base64` is present in the repository, it automatically decodes and restores the exact debug keystore.
3. If not, it automatically runs JDK `keytool` to generate a fresh `debug.keystore` using the exact alias (`androiddebugkey`) and password (`android`) expected by `debugConfig`.

---

## 🎨 Jetpack Compose Design Previews in Android Studio

All major screens and reusable UI components include isolated `@Preview` functions supporting both **Light Mode** and **Dark Mode**:

- **Login Screen:** `LoginScreenPreview()` in `LoginScreen.kt`
- **Registration Screen:** `RegisterScreenPreview()` in `RegisterScreen.kt`
- **Dashboard & KPIs:** `DashboardScreenPreview()` in `DashboardScreen.kt`
- **Real-Time Inventory Dashboard:** `InventoryDashboardScreenPreview()` in `InventoryDashboardScreen.kt`
- **Batch & Expiry Management:** `BatchExpiryScreenPreview()` in `BatchExpiryScreen.kt`
- **Order Pipeline & Tracking:** `MyOrdersScreenPreview()` in `MyOrdersScreen.kt`
- **Order Checkout & Review:** `CheckoutScreenPreview()` in `CheckoutScreen.kt`
- **B2B Payment & Settlement:** `PaymentScreenPreview()` in `PaymentScreen.kt`
- **Order Confirmation & PO Receipt:** `OrderConfirmationScreenPreview()` in `OrderConfirmationScreen.kt`
- **Medicine Catalogue:** `MedicineCatalogueScreenPreview()` in `MedicineCatalogueScreen.kt`
- **Settings & Profile:** `ProfileSettingsScreenPreview()` in `ProfileSettingsScreen.kt`
- **Design System Components:** `CommonComponentsPreview()` in `CommonComponents.kt`

### How to View Previews:
1. Open any of the files listed above in **Android Studio**.
2. Click the **"Split"** or **"Design"** button in the top right corner of the editor window.
3. The preview will render instantly using sample data and the official `PharmaOSTheme`, allowing real-time design inspection and UI tweaking without running an emulator or hitting real backend/database services.

---

## 🏗️ Build Commands

```bash
# Generate/verify debug keystore (first time only)
./setup-debug-keystore.sh   # On Linux/macOS
setup-debug-keystore.bat     # On Windows

# Compile debug APK
gradle assembleDebug

# Run unit and local JVM tests
gradle testDebugUnitTest
```

