package `in`.qaida.app.ui.common

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The UI language is the learner's choice, independent of the system locale: a context whose
 * configuration carries [tag]. Malayalam is left to right, so layout direction follows the locale.
 */
fun Context.withLocale(tag: String): Context {
    val locale = Locale(tag)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return createConfigurationContext(config)
}
