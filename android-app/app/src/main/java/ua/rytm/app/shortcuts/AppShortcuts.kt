package ua.rytm.app.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import ua.rytm.app.MainActivity
import ua.rytm.app.R

/**
 * Long-press launcher shortcuts. Dynamic, not res/xml/shortcuts.xml: a static
 * shortcut's intent needs a literal targetPackage, which would break the
 * `.qa` applicationIdSuffix build. Republished from MainActivity so the labels
 * follow the in-app language.
 */
object AppShortcuts {
    fun publish(context: Context) {
        fun shortcut(id: String, action: String, label: Int, icon: Int) = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(context.getString(label))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(MainActivity.EXTRA_LAUNCH_ACTION, action),
            )
            .build()
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(
                context,
                listOf(
                    shortcut("expense", MainActivity.ACTION_NEW_EXPENSE, R.string.shortcut_expense, R.drawable.ic_shortcut_expense),
                    shortcut("income", MainActivity.ACTION_NEW_INCOME, R.string.shortcut_income, R.drawable.ic_shortcut_income),
                    shortcut("quick", MainActivity.ACTION_QUICK_ENTRY, R.string.quick_entry_title, R.drawable.ic_shortcut_quick),
                ),
            )
        }
    }
}
