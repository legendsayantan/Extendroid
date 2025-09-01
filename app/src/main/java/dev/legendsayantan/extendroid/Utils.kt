package dev.legendsayantan.extendroid


import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.provider.Settings
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.createBitmap
import androidx.core.view.setPadding
import androidx.palette.graphics.Palette
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import rikka.shizuku.Shizuku
import java.util.Locale
import androidx.core.net.toUri

/**
 * @author legendsayantan
 */
class Utils {
    companion object {

        fun isShizukuSetup(): Boolean {
            return Shizuku.pingBinder()
        }

        fun isShizukuAllowed(): Boolean {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }

        fun isSafeForUI(ctx: Context): Boolean {
            return isShizukuSetup() && isShizukuAllowed() && Settings.canDrawOverlays(ctx)
        }

        fun whenSafeForUI(ctx: Context, then: () -> Unit) {
            if (isShizukuSetup() && isShizukuAllowed()) {
                if (!Settings.canDrawOverlays(ctx)) {
                    Toast.makeText(ctx, "Please wait, starting soon!", Toast.LENGTH_LONG).show()
                    Handler(ctx.mainLooper).postDelayed({
                        whenSafeForUI(ctx, then)
                    }, 2000)
                }
                then()
            } else {
                Toast.makeText(ctx, "Failure: Insufficient Setup!", Toast.LENGTH_LONG).show()
            }
        }

        fun getColorOf(drawable: Drawable): Int {
            val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            val palette = Palette.from(bitmap).generate()
            return palette.getDominantColor(0xFFFFFF) // default to white if not found
        }

        fun mixColors(c1: Int = Color.TRANSPARENT, c2: Int, ratio: Float): Int {
            val inverseRatio = 1f - ratio
            val a = (Color.alpha(c1) * inverseRatio + Color.alpha(c2) * ratio).toInt()
            val r = (Color.red(c1) * inverseRatio + Color.red(c2) * ratio).toInt()
            val g = (Color.green(c1) * inverseRatio + Color.green(c2) * ratio).toInt()
            val b = (Color.blue(c1) * inverseRatio + Color.blue(c2) * ratio).toInt()
            return Color.argb(a, r, g, b)
        }

        fun isDarkModeEnabled(context: Context): Boolean {
            val currentNightMode =
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return currentNightMode == Configuration.UI_MODE_NIGHT_YES
        }

        fun Context.dpToPx(dp: Float): Float {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics
            )
        }

        fun Context.miuiRequirements() {
            if ("xiaomi" == Build.MANUFACTURER.lowercase(Locale.ROOT)) {
                Toast.makeText(
                    this,
                    "Please allow the required miui permissions!",
                    Toast.LENGTH_SHORT
                ).show()
                try {
                    val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                    intent.setClassName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"
                    )
                    intent.putExtra("extra_pkgname", packageName)
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                }
            }
        }

        fun String.toJsonSanitized(): String {
            // Matches control chars (U+0000–U+001F), double-quote, or backslash
            val jsonEscapeRegex = Regex("""[\u0000-\u001F"\\]""")
            return jsonEscapeRegex.replace(this) { matchResult ->
                when (val ch = matchResult.value[0]) {
                    '\b' -> "\\b"
                    '\u000C' -> "\\f"   // form feed
                    '\n' -> "\\n"
                    '\r' -> "\\r"
                    '\t' -> "\\t"
                    '"' -> "\\\""  // double-quote
                    '\\' -> "\\\\"  // backslash
                    else -> String.format("\\u%04X", ch.code)  // other control chars
                }
            }
        }

        fun minuteToMilliseconds(minutes: Int): Long {
            return minutes * 60 * 1000L
        }

        /** Linear interpolate between a and b */
        fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt()

        fun showInfoDialog(themedCtx: Context, title: String, content: String, then: () -> Unit) {
            lateinit var dialog: AlertDialog
            val builder = MaterialAlertDialogBuilder(themedCtx)
            val container = MaterialCardView(themedCtx)
            container.radius = 10f
            container.setCardBackgroundColor(themedCtx.resources.getColor(R.color.theme4, null))
            container.strokeWidth = 0
            val heading = TextView(themedCtx)
            val contents = TextView(themedCtx)
            heading.text = title
            heading.textSize = 20f
            heading.setTextColor(themedCtx.resources.getColor(R.color.theme0, null))
            contents.text = content
            contents.textSize = 16f
            contents.setPadding(0, 10, 0, 10)
            contents.setTextColor(themedCtx.resources.getColor(R.color.theme1, null))
            container.addView(LinearLayout(themedCtx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40)
                addView(heading)
                addView(contents)
                addView(MaterialButton(themedCtx).apply {
                    text = "Understood"
                    textSize = 20f
                    setBackgroundColor(resources.getColor(R.color.theme3, null))
                    setTextColor(resources.getColor(R.color.theme0, null))
                    setOnClickListener {
                        dialog.dismiss()
                        then()
                    }
                })
            })
            builder.setView(container)
            dialog = builder.show()
        }

        fun Context.launchOnDefaultBrowser(url: String) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        /**
         * Returns a base62 (alphanumeric) encoding of:
         *   (current Unix epoch seconds) - (Unix epoch seconds at 2025-01-01 00:00:00 UTC)
         */
        fun uniqueID(): String {
            val epoch2025 = 1756709400L // initial seconds
            val nowSeconds = System.currentTimeMillis() / 1000L
            val num = nowSeconds - epoch2025
            return toBase62(num)
        }

        private fun toBase62(value: Long): String {
            if (value == 0L) return "0"
            // handle negative values (if clock is before 2025-01-01)
            var n = value
            val negative = n < 0
            if (negative) n = -n

            val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
            val base = alphabet.length.toLong() // 62

            val sb = StringBuilder()
            var x = n
            while (x > 0) {
                val rem = (x % base).toInt()
                sb.append(alphabet[rem])
                x /= base
            }
            if (negative) sb.append('-')
            return sb.reverse().toString()
        }

    }
}