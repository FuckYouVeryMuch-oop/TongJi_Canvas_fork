package com.mln.tongji_canvas.data

import android.content.Context

class OnboardingStore(context: Context) {
    private val prefs = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)

    fun shouldShowAutomatically(): Boolean = prefs.getString("status", null) == null

    fun shouldShowToolbarTour(): Boolean = shouldShowAutomatically() && !prefs.getBoolean("toolbar_tour_seen", false)

    fun markToolbarTourSeen() {
        prefs.edit().putBoolean("toolbar_tour_seen", true).apply()
    }

    fun skip() {
        prefs.edit().putString("status", "skipped").apply()
    }

    fun complete() {
        prefs.edit().putString("status", "completed").apply()
    }
}
