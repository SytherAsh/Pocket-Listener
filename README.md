# SpendWise Android App

## Overview
The SpendWise Android Application is the primary data ingestion point for the SpendWise personal finance platform. It operates silently in the background, listening for financial SMS messages and notifications from banks and payment apps, and securely forwards them to the Python backend for processing.

## Features
- **Real-time SMS Interception:** Watches for incoming financial SMS messages using a background observer.
- **Historical SMS Reading:** Scans the device's SMS inbox for past transaction messages and stores them.
- **Notification Listener:** Captures real-time push notifications from apps like GPay, PhonePe, Paytm, and banking applications.
- **Local Caching (Room DB):** Stores captured messages locally to prevent data loss when offline.
- **Background Sync:** Syncs captured data in bulk to the Python FastAPI backend.
- **Premium Dashboard:** A modern, dark-themed Jetpack Compose UI that provides a live view of captured records, pending syncs, and system permissions.

## Tech Stack
- **Language:** Kotlin
- **UI Toolkit:** Jetpack Compose (Material 3)
- **Database:** Room Database (SQLite)
- **Asynchronous Operations:** Kotlin Coroutines & Dispatchers
- **Networking:** Native `HttpURLConnection` for REST APIs

## Setup Instructions
1. Open the project in **Android Studio**.
2. Connect your Android device or start an emulator.
3. Build and Run the app.

## First-Time Configuration
1. **Grant Permissions:** Upon opening the app, you will see a "System Settings" panel. Tap on **Grant SMS Permission** and **Grant Notification Access**.
2. **Configure Backend URL:**
   - In the System Settings, find the "Backend URL" field.
   - Enter your local FastAPI server IP. Example: `http://192.168.1.100:8000/api/data`
   - Tap **Save & Test** to verify the connection. (The API status indicator at the top should turn green).
3. **Sync Data:** Once permissions are granted and the API is connected, the app will automatically start reading your messages and syncing them. You can manually push unsent records by clicking **Sync Bulk**.

## Architecture Flow
1. **Data Source:** User receives an SMS or Notification.
2. **Capture:** `SmsReader.kt` or `NotificationService.kt` captures the event.
3. **Storage:** The record is saved in the local Room Database (`AppDatabase`).
4. **Transmission:** `ApiSender.kt` converts the record to JSON and sends a `POST` request to the backend. If successful, the record is marked as synced.
