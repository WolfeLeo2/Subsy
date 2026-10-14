# Product Requirement Document (PRD): Suby 💳

**App Name**: Suby
**Tagline**: *Smart Subscription, Free Trial & Recurring Expense Manager*
**Platform Target**: Android 13+ (`minSdk = 33`, `targetSdk = 37`)
**Design System**: Material 3 Expressive (M3 Expressive)
**Architecture**: Jetpack Compose + MVVM + Room + WorkManager

---

## 1. Executive Summary & Vision

**Suby** is a clean, modern Android application designed to help users track subscriptions, free trials, and recurring payments without friction. It eliminates forgotten auto-renewals, gives complete visibility over monthly/yearly burn rates, and provides optional automated transaction detection via SMS and notifications.

Built with **Material 3 Expressive UI** and targeting **minSdk 33**, Suby leverages top-tier Android 13+ native capabilities, including predictive back gestures, quick settings tiles, precise notifications, and dynamic color integration.

---

## 2. Core Problems & Solution Strategy

| Problem | Suby Solution |
| :--- | :--- |
| **Forgotten Free Trials** converting to unwanted charges. | Smart pre-renewal reminders (3 days, 1 day, and day-of) + direct "Cancel Subscription" links. |
| **High Friction in Manual Entry**. | Pre-loaded 50+ service catalog (*Netflix, ChatGPT, Spotify, iCloud, Gym*) for 2-tap creation. |
| **Scattered Payment Records**. | Automated payment ledger with scheduled auto-logging via `WorkManager`. |
| **Missed Cash Flow Visibility**. | Instant monthly burn rate calculations, category breakdowns, and yearly projections. |
| **Manual Logging Fatigue**. | **Opt-in Transaction Auto-Detection** reading bank SMS and push notifications to prompt 1-tap logging. |

---

## 3. Key Features & Functional Specifications

### Feature 1: Subscription Management & Preset Catalog
- **CRUD Operations**: Create, edit, pause, resume, and delete subscriptions or free trials.
- **Service Preset Catalog**: Pre-populated catalog of 50+ popular services with official icons, dominant brand colors, default categories, and renewal URL links.
- **Custom Service Support**: Add custom subscriptions with custom names, custom category icons, and notes.
- **Billing Cycles Supported**:
  - Weekly
  - Monthly
  - Quarterly
  - Bi-Annually
  - Yearly
  - Custom Interval (e.g., every X days)
- **Multi-Currency Support**: Primary currency selection (USD `$`, EUR `€`, GBP `£`, NGN `₦`, JPY `¥`, etc.) with manual exchange rate conversion.
- **Status States**:
  - `ACTIVE`: Currently ongoing paid subscription.
  - `TRIAL`: Active free trial with urgent renewal alert timers.
  - `PAUSED`: Temporarily inactive subscription.
  - `CANCELLED`: Archived subscription for historical tracking.

---

### Feature 2: Free Trial Engine & Pre-Renewal Alerts
- **Trial Duration Presets**: 3-day, 7-day, 14-day, 30-day, or custom trial length.
- **Smart Notification Scheduler**:
  - Uses `AlarmManager` + `NotificationManager` (API 33 `POST_NOTIFICATIONS` permission).
  - Sends alerts at **3 days before**, **1 day before**, and **morning of renewal (8:00 AM)**.
  - Alert notification contains a direct button: **"Jump to Cancel Page"** using the service's stored management URL.

---

### Feature 3: Opt-in Automated Transaction Detection (SMS & Notifications)
> [!IMPORTANT]
> **Privacy First Guarantee**: This feature is **DISABLED by default**. Users must explicitly enable it in Settings with clear runtime permission explanations.

- **SMS Auto-Detector (`RECEIVE_SMS` / `READ_SMS`)**:
  - Listens for incoming SMS messages from bank sender IDs (e.g., bank alerts, card transaction alerts).
  - Regex Parser identifies:
    - Keywords: `charged`, `debited`, `paid`, `subscription`, `auto-renewed`, `transaction`.
    - Currency & Amount: `$XX.XX` / `USD XX.XX`.
    - Merchant/Service Name: Cross-referenced with subscription names and preset keywords.
- **Notification Listener Service (`NotificationListenerService`)**:
  - Reads push notifications from banking apps (e.g., Chase, Revolut, Monzo, Apple Card, Google Wallet).
- **In-App Prompt Banner**:
  - When a transaction matching a subscription is detected, Suby displays an in-app card or local prompt:
    > *"Spotted $14.99 charge to Spotify. Log this payment?"* `[Confirm Payment]` `[Ignore]`

---

### Feature 4: Payment Ledger & Scheduled Expense Engine
- **Automated Ledger (`WorkManager`)**:
  - A daily background worker updates subscription states on their billing date.
  - Automatically records a new `PaymentRecord` entry in the payment history log.
- **Payment History Log**: View month-by-month historical payments for every subscription with total spent over time.

---

### Feature 5: Analytics & Expense Burn Rate
- **Top Metrics Overview**:
  - **Total Monthly Burn Rate**: Combined normalized monthly cost of all active subscriptions.
  - **Total Yearly Cost Projection**.
  - **Active Free Trials Count**.
- **Category Distribution Chart**: Visual percentage and amount breakdown (Entertainment, Work/SaaS, Health & Fitness, Utilities, Personal).
- **Upcoming Renewals Timeline**: Chronological list of upcoming charges for the next 30 days.

---

### Feature 6: Android 13+ Platform Integrations
- **Quick Settings Tile (`SubyQuickTileService`)**: A system quick settings tile titled **"+ Log Trial"** allowing users to immediately trigger trial creation from anywhere in Android.
- **Predictive Back Gesture Support**: Smooth scale & corner-radius animated transitions between list, detail, and analytics views.
- **Per-App Language Preferences (API 33)**: Supports runtime locale changing.

---

## 4. Design System: Material 3 Expressive Guidelines

Suby adheres strictly to the **Material 3 Expressive** design specification:

1. **Expressive Color & Dynamic Themes**:
   - Uses M3 Dynamic Color (`dynamicLightColorScheme` / `dynamicDarkColorScheme`) derived from user wallpaper or custom Suby brand tones.
   - High-contrast expressive containers for urgent statuses (e.g., trials expiring soon use `errorContainer` or `tertiaryContainer`).
2. **Expressive Shapes & Badges**:
   - Status badges use pill shapes, super-ellipses, and expressive rounded polygons.
   - Subscription cards feature rounded corners (`24.dp`) with subtle surface tonal elevation.
3. **Floating Toolbars & Action Bars**:
   - Floating bottom navigation toolbar with integrated split FAB for adding subscriptions.
4. **Segmented Controls & Filter Chips**:
   - Smooth animated segmented buttons for toggling views (*All*, *Active*, *Free Trials*, *Paused*).
5. **Spring Motion & Transitions**:
   - All interactive elements utilize spring physics for touch feedback, list expansions, and swiping actions.

---

## 5. System Architecture & Tech Stack

```
com.wolfeleo2.app
├── data
│   ├── local
│   │   ├── dao (SubscriptionDao, PaymentRecordDao, DetectedTransactionDao)
│   │   ├── db (SubyDatabase)
│   │   └── entity (SubscriptionEntity, PaymentRecordEntity, DetectedTransactionEntity)
│   ├── model (Subscription, BillingCycle, Category, PresetService)
│   ├── repository (SubscriptionRepository, TransactionDetectorRepository)
│   └── service (SmsReceiver, BankNotificationListener, SubyQuickTileService)
├── ui
│   ├── analytics (AnalyticsScreen, AnalyticsViewModel)
│   ├── catalog (PresetCatalogSheet)
│   ├── detail (SubscriptionDetailScreen, SubscriptionDetailViewModel)
│   ├── home (HomeScreen, HomeViewModel)
│   ├── navigation (SubyNavGraph, ScreenRoutes)
│   ├── settings (SettingsScreen, SettingsViewModel)
│   └── theme (Color, Type, Shape, Theme)
└── worker (RenewalReminderWorker, PaymentSchedulerWorker)
```

### Core Libraries & Dependencies
- **UI**: Jetpack Compose, Material 3 Expressive (`androidx.compose.material3`), Compose Navigation.
- **State Management**: ViewModel, Kotlin `StateFlow`, `collectAsStateWithLifecycle`.
- **Database**: Room Database with Coroutines & Flow.
- **Preferences**: DataStore Preferences (for Opt-in SMS/Notification settings, Currency preferences).
- **Background Work**: AndroidX `WorkManager`, `AlarmManager`.

---

## 6. Database Schema

### `subscriptions` Table
```sql
CREATE TABLE subscriptions (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    category TEXT NOT NULL,
    amount REAL NOT NULL,
    currency TEXT NOT NULL,
    billing_cycle TEXT NOT NULL, -- WEEKLY, MONTHLY, YEARLY, etc.
    start_date INTEGER NOT NULL,
    next_billing_date INTEGER NOT NULL,
    is_free_trial INTEGER NOT NULL, -- 0 or 1
    trial_end_date INTEGER,
    status TEXT NOT NULL, -- ACTIVE, TRIAL, PAUSED, CANCELLED
    cancellation_url TEXT,
    icon_name TEXT,
    color_hex TEXT,
    notes TEXT,
    created_at INTEGER NOT NULL
);
```

### `payment_records` Table
```sql
CREATE TABLE payment_records (
    id TEXT PRIMARY KEY NOT NULL,
    subscription_id TEXT NOT NULL,
    amount REAL NOT NULL,
    currency TEXT NOT NULL,
    payment_date INTEGER NOT NULL,
    status TEXT NOT NULL, -- CONFIRMED, AUTO_LOGGED, MANUALLY_LOGGED
    FOREIGN KEY(subscription_id) REFERENCES subscriptions(id) ON DELETE CASCADE
);
```

### `detected_transactions` Table
```sql
CREATE TABLE detected_transactions (
    id TEXT PRIMARY KEY NOT NULL,
    merchant_name TEXT NOT NULL,
    amount REAL NOT NULL,
    raw_message TEXT NOT NULL,
    source TEXT NOT NULL, -- SMS or NOTIFICATION
    detected_at INTEGER NOT NULL,
    is_processed INTEGER NOT NULL -- 0 or 1
);
```

---

## 7. Security & Privacy Safeguards

1. **Local-Only Data Processing**: All SMS parsing and notification detection logic runs 100% on-device. Zero external network transmission of user messages or bank details.
2. **Opt-in Guardrail**: SMS (`RECEIVE_SMS`) and Notification Listener (`NotificationListenerService`) are turned OFF by default.
3. **Data Erasure**: Deleting a subscription or clearing app data permanently removes all associated ledger records and detected transactions.

---

## 8. Development Roadmap

- [x] **Phase 1: Foundation & Setup**
  - Project configuration with minSdk 33 and M3 Expressive theme tokens.
- [x] **Phase 2: Database & Core Models**
  - Implement Room Entities, DAOs, and Repositories.
  - Implement 50+ Preset Catalog data source.
- [x] **Phase 3: Subscription Management UI**
  - Build Home Screen with M3 Expressive cards, segmented filters, and burn rate banner.
  - Build Add/Edit Subscription Sheet with Preset Selector and Trial toggles.
- [x] **Phase 4: Reminders & Background Ledger**
  - Daily `WorkManager` worker sends renewal and trial-end alerts (3 days, 1 day, day of) and auto-logs payments on billing dates.
- [ ] **Phase 5: Automated Transaction Detection (Opt-in)**
  - Implement `SmsReceiver` regex parser and `BankNotificationListener`.
  - Build settings opt-in permission flow & transaction prompt card.
- [ ] **Phase 6: Analytics & Settings**
  - [x] Expense breakdown charts (category ring, monthly bars) and upcoming renewals timeline (next 30 days).
  - [ ] Quick Settings Tile ("+ Log Trial").
- [x] **Phase 7: All spending mode (opt-in expense and income tracker)**
  - [x] Mode chosen on first launch and in Settings; monthly budget with a budget ring on Spending.
  - [x] Transactions and custom categories (Room v2 via AutoMigration), Transactions tab, quick-add sheet, Categories screen.
  - [x] All-spending analytics (in/out/net, ring by spend category against the budget, in/out bars) and per-category budgets (Room v3).

