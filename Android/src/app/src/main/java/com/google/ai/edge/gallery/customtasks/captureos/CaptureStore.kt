package com.google.ai.edge.gallery.customtasks.captureos

import android.content.Context
import com.google.gson.Gson

data class SetupProfile(
  val teamCode: String = "",
  val name: String = "",
  val role: String = "", // "admin" | "employee"
) {
  val isComplete get() = teamCode.isNotBlank() && name.isNotBlank() && role.isNotBlank()
}

object CaptureStore {
  private const val PREFS = "captureos_profile"
  private const val KEY = "profile"
  private val gson = Gson()

  fun load(context: Context): SetupProfile {
    val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
    return if (json != null) gson.fromJson(json, SetupProfile::class.java) else SetupProfile()
  }

  fun save(context: Context, profile: SetupProfile) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .edit().putString(KEY, gson.toJson(profile)).apply()
  }
}