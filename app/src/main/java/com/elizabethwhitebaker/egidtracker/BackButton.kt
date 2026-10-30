package com.elizabethwhitebaker.egidtracker

import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import androidx.activity.ComponentActivity
import androidx.appcompat.widget.AppCompatImageButton

/**
 * Top-left back arrow shown in every screen header (style Widget.EGIDTracker.BackButton).
 * Behaves exactly like the system back gesture, so screens need no code of their own.
 */
class BackButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageButton(context, attrs, defStyleAttr) {

    init {
        setOnClickListener { findActivity()?.onBackPressedDispatcher?.onBackPressed() }
    }

    private fun findActivity(): ComponentActivity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is ComponentActivity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
