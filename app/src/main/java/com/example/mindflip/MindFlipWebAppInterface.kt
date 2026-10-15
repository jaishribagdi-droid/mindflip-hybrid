package com.example.mindflip

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.google.gson.Gson

class MindFlipWebAppInterface(private val activity: Activity) {

    private val prefs: SharedPreferences = activity.getSharedPreferences("mindflip_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    data class SelectedApps(val packages: List<String>)

    @JavascriptInterface
    fun getSelectedApps(): String {
        val json = prefs.getString("selected_apps", null)
        return json ?: gson.toJson(SelectedApps(emptyList()))
    }

    @JavascriptInterface
    fun setSelectedApps(json: String) {
        prefs.edit().putString("selected_apps", json).apply()
    }

    @JavascriptInterface
    fun openAppSelection() {
        activity.runOnUiThread {
            val i = Intent(activity, AppSelectionActivity::class.java)
            activity.startActivity(i)
        }
    }

    @JavascriptInterface
    fun toast(message: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Start a native "focus session" (mock implementation)
    @JavascriptInterface
    fun startNativeTimer(seconds: Int, reason: String) {
        activity.runOnUiThread {
            val now = System.currentTimeMillis()
            val end = now + seconds * 1000L
            prefs.edit().putLong("session_end_ts", end).putString("session_reason", reason).putLong("session_started_ts", now).apply()
            Toast.makeText(activity, "Native timer started: ${seconds}s — reason=$reason", Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun isSessionActive(): Boolean {
        val end = prefs.getLong("session_end_ts", 0L)
        return end > System.currentTimeMillis()
    }

    @JavascriptInterface
    fun endNativeSession() {
        prefs.edit().remove("session_end_ts").remove("session_reason").remove("session_started_ts").apply()
        activity.runOnUiThread {
            Toast.makeText(activity, "Native session ended", Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun isEnforcementEnabled(): Boolean {
        return prefs.getBoolean("enforcement_enabled", false)
    }

    @JavascriptInterface
    fun setEnforcementEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enforcement_enabled", enabled).apply()
        activity.runOnUiThread {
            Toast.makeText(activity, "Enforcement: ${if (enabled) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Mock-opening an app from the web prototype. If enforcement is enabled and a session is active
     * and the app is in the selected list, show the native LockActivity to simulate blocking.
     * Otherwise attempt to launch the package on device if it's installed.
     */
    @JavascriptInterface
    fun openMockApp(packageName: String) {
        activity.runOnUiThread {
            val enforcement = prefs.getBoolean("enforcement_enabled", false)
            val end = prefs.getLong("session_end_ts", 0L)
            val sessionActive = end > System.currentTimeMillis()

            val selectedJson = prefs.getString("selected_apps", null)
            val selected = if (selectedJson != null) {
                try {
                    gson.fromJson(selectedJson, SelectedApps::class.java).packages
                } catch (e: Exception) {
                    emptyList<String>()
                }
            } else emptyList()

            if (enforcement && sessionActive && selected.contains(packageName)) {
                val i = Intent(activity, LockActivity::class.java)
                i.putExtra("package_name", packageName)
                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                activity.startActivity(i)
            } else {
                // Try to open the real app if installed
                try {
                    val pm = activity.packageManager
                    val launch = pm.getLaunchIntentForPackage(packageName)
                    if (launch != null) {
                        activity.startActivity(launch)
                    } else {
                        Toast.makeText(activity, "App not installed: $packageName", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(activity, "Cannot open app: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
