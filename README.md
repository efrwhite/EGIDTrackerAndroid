# EGID Tracker

## Overview

EGID Tracker is an Android application developed to assist parents and caregivers in managing and coordinating care for children with Eosinophilic Gastrointestinal Disorders (EGID), primarily Eosinophilic Esophagitis (EoE). This app empowers users to monitor symptoms, medications, and dietary requirements, while also providing educational resources to better understand and manage this complex condition.

### Research Basis

The development of EGID Tracker is grounded in the need for effective care coordination tools for children with EoE, as identified in previous studies. Research highlights the importance of information management systems in improving follow-up visits, increasing referrals, and ensuring access to accurate and up-to-date information for families. Mobile health (mHealth) applications have been proven effective in care coordination for chronic conditions like asthma and diabetes, demonstrating their potential for EoE care.

## Key Features

- Patient Profiles: Create and manage multiple profiles for children, caregivers, and patients.

- Symptom Tracker: Log daily symptoms and their severity to monitor trends over time.

- Medication Management: Track prescribed medications, dosages, and schedules.

- Dietary Management: Maintain a record of daily diets, avoid accidental exposures, and access a comprehensive list of restricted foods.

- Surveys: Complete quality-of-life and symptom checker surveys to monitor patient well-being.

- Reports and Endoscopy Results: Save and organize important documents, including endoscopy results and other medical reports.

- Educational Resources: Access a detailed page with information about EoE, including causes, symptoms, affected populations, and treatment options.

## Future Expansion

While the current version of EGID Tracker is designed for parents and caregivers, future iterations aim to include functionality for older patients to manage their own care.

## Installation

1. Clone the repository:

   ```
   git clone https://github.com/efrwhite/EGIDTrackerAndroid.git
   ```

2. Get the testing project's `google-services.json` from the Firebase console (**Project settings → Your apps**). Save it to `app/src/debug/google-services.json`. It's gitignored, so it isn't in the repository, and the app won't build without it.
3. Open the project in Android Studio and sync Gradle.
4. In **Build Variants**, select **debug**. Run it on an emulator or a physical device with Android 7.0 (API 24) or newer.

## Firebase

The app uses Firebase Authentication (Email/Password) and Cloud Firestore. Each build type connects to its own Firebase project, depending on which `google-services.json` it finds:

| Build | Firebase project | Config file |
|---|---|---|
| Debug | `egidproject-68665`, the testing database | `app/src/debug/google-services.json` |
| Release | Production project (not created yet) | `app/src/release/google-services.json` |

Debug builds write to the real testing database, so use clearly fake test accounts.

Release builds fail with "google-services.json is missing" until a production project exists. To set one up:

1. Create a new Firebase project.
2. Enable Email/Password sign-in and create a Firestore database in production mode.
3. Register the Android app (`com.elizabethwhitebaker.egidtracker`) and save its `google-services.json` to `app/src/release/`.
4. Deploy the security rules to it (see below).

### Firebase files

| File | Purpose |
|---|---|
| `app/src/debug/google-services.json`, `app/src/release/google-services.json` | Connect each build type to its Firebase project. Not in git. |
| `firestore.rules` | Security rules. Each user can reach only their own data, and a username can be looked up for login but not listed. |
| `firebase.json` | Tells the Firebase CLI where the rules file is. |
| `.firebaserc` | Sets the CLI's default project, the testing project. |

### Deploying the security rules

After changing `firestore.rules`, install the Firebase CLI (`npm install -g firebase-tools`) and run `firebase login` once. Then:

```
firebase deploy --only firestore:rules                      # testing project
firebase deploy --only firestore:rules -P <production-id>   # production, once it exists
```

## Release signing

Release builds are signed with your key if a `keystore.properties` file exists in the repository root. It's gitignored and contains `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. Without it, release builds fall back to the debug key, which is fine for running locally but can't be published. Create a key in Android Studio under **Build → Generate Signed App Bundle / APK** and keep it backed up: you need the same key for every update.
