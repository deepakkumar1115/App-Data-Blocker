package com.example

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val isExempted: Boolean,
    val isBlocked: Boolean,
    val uid: Int
)
