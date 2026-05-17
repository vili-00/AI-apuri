package com.aiapuri.core.util

import android.content.Context
import androidx.fragment.app.FragmentActivity

/**
 * Recursively walk the context wrapper chain to find a [FragmentActivity].
 *
 * Returns null if no FragmentActivity is found (e.g. called from a
 * non-Activity context). Callers should handle the null case gracefully.
 */
fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx != null) {
        if (ctx is FragmentActivity) return ctx
        ctx = if (ctx is android.content.ContextWrapper) ctx.baseContext else null
    }
    return null
}
