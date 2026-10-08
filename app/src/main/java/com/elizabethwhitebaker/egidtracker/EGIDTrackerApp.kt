package com.elizabethwhitebaker.egidtracker

import android.app.Application
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * In debug builds, points Firebase Auth and Firestore at the local Firebase
 * Emulator Suite (see firebase.json / README) instead of a real backend, so
 * sign-up/sign-in and Firestore reads work without a real google-services.json.
 * 10.0.2.2 is the Android emulator's alias for the host machine's localhost;
 * on a physical device set EMULATOR_HOST to your machine's LAN IP instead.
 */
class EGIDTrackerApp : Application() {

    companion object {
        private const val EMULATOR_HOST = "10.0.2.2"
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            try {
                FirebaseAuth.getInstance().useEmulator(EMULATOR_HOST, 9099)
                FirebaseFirestore.getInstance().useEmulator(EMULATOR_HOST, 8080)
            } catch (e: IllegalStateException) {
                // useEmulator() throws if called after the instance already made a call;
                // safe to ignore on warm restarts within the same process.
                Log.w("EGIDTrackerApp", "Firebase emulator already configured", e)
            }
        }
    }
}
