package ua.rytm.app.navigation

import android.content.Intent
import android.net.Uri
import android.content.Context
import android.content.pm.ShortcutManager
import androidx.test.core.app.ApplicationProvider
import ua.rytm.app.widget.quickActionIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchRequestTest {
    @Test fun parsesCustomAndHttpsRoutes() {
        assertEquals("shifts", parseLaunchRequest(Intent(Intent.ACTION_VIEW, Uri.parse("rytm://shifts")))?.route)
        assertEquals("debt", parseLaunchRequest(Intent(Intent.ACTION_VIEW, Uri.parse("https://maxtr-c238f.web.app/app/debt")))?.route)
        assertNull(parseLaunchRequest(Intent(Intent.ACTION_VIEW, Uri.parse("https://evil.example/app/debt"))))
        assertNull(parseLaunchRequest(Intent(Intent.ACTION_VIEW, Uri.parse("rytm://unknown"))))
    }

    @Test fun shortcutAndShareOpenSafeTransactionDraft() {
        val shortcut = parseLaunchRequest(Intent(Intent.ACTION_VIEW, Uri.parse("rytm://finance/new")))!!
        assertTrue(shortcut.openTransaction)
        val share = parseLaunchRequest(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "  receipt 42.50  ")
        })!!
        assertEquals("finance", share.route)
        assertTrue(share.openTransaction)
        assertEquals("receipt 42.50", share.sharedText)
    }

    @Test fun manifestExportsLinksShareAndThreeShortcuts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("rytm://shopping")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://maxtr-c238f.web.app/app/debt")),
            Intent(Intent.ACTION_SEND).apply { type = "text/plain" },
        ).forEach { intent ->
            intent.setPackage(context.packageName)
            assertTrue(packageManager.queryIntentActivities(intent, 0).isNotEmpty())
        }
        val shortcuts = context.getSystemService(ShortcutManager::class.java).manifestShortcuts.map { it.id }.toSet()
        assertEquals(setOf("new_transaction", "shifts", "shopping"), shortcuts)
    }

    @Test fun widgetActionsUseTheSameValidatedRoutes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf("finance/new", "shifts", "shopping").forEach { route ->
            val request = parseLaunchRequest(quickActionIntent(context, route))!!
            assertEquals(route.substringBefore('/'), request.route)
        }
    }
}
