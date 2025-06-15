package dev.legendsayantan.extendroid.lib

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import dev.legendsayantan.extendroid.model.AppItem
import java.util.concurrent.TimeUnit

/**
 * @author legendsayantan
 */
class PackageManagerHelper {
    companion object {
        @SuppressLint("PrivateApi")
        fun grantPermissions(
            pkgName: String,
            perms: List<String>,
            packageManager: PackageManager
        ): String {
            val log = StringBuilder()
            try {
                // 1) Get IPackageManager binder
                val smClass = Class.forName("android.os.ServiceManager")
                val getService = smClass.getMethod("getService", String::class.java)
                val binder = getService.invoke(null, "package") as android.os.IBinder
                log.append("Obtained package binder\n")

                // 2) Get IPackageManager proxy
                val stub = Class.forName("android.content.pm.IPackageManager\$Stub")
                val asInterface = stub.getMethod("asInterface", IBinder::class.java)
                val ipm = asInterface.invoke(null, binder)
                log.append("Obtained IPackageManager interface\n")

                // 3) Find all grantRuntimePermission methods
                val allMethods =
                    ipm.javaClass.methods.filter { it.name == "grantRuntimePermission" }
                if (allMethods.isEmpty()) {
                    throw RuntimeException("No grantRuntimePermission methods found")
                }
                val grant = allMethods.first { m ->
                    val p = m.parameterTypes
                    p.size >= 3 && p[0] == String::class.java && p[1] == String::class.java
                }.apply { isAccessible = true }
                log.append("Using signature: ${grant.parameterTypes.joinToString { it.simpleName }}\n")

                // 4) Prepare userId/UserHandle
                val userHandleClz = Class.forName("android.os.UserHandle")
                val myUserId = userHandleClz.getMethod("myUserId").invoke(null) as Int
                val userHandle = try {
                    userHandleClz.getMethod("of", Int::class.javaPrimitiveType)
                        .invoke(null, myUserId)
                        .also { log.append("Created UserHandle via of($myUserId)\n") }
                } catch (e: NoSuchMethodException) {
                    userHandleClz.getMethod("getUserHandleForUid", Int::class.javaPrimitiveType)
                        .invoke(null, android.os.Process.myUid())
                        .also { log.append("Created UserHandle via getUserHandleForUid\n") }
                }

                // 5) Invoke for each permission
                perms.forEach { perm ->
                    if (arrayOf(Manifest.permission.SYSTEM_ALERT_WINDOW).contains(perm)) {
                        log.append(grantViaAppOps(packageManager, pkgName, perm))
                    } else if (arrayOf("PROJECT_MEDIA").contains(perm)) {
                        log.append(grantViaAdb(pkgName, perm))
                    } else {
                        try {
                            val params = grant.parameterTypes
                            val args: Array<Any> = when {
                                // (String, String, int)
                                params.size == 3 && params[2] == Int::class.javaPrimitiveType ->
                                    arrayOf(pkgName, perm, myUserId)

                                // (String, String, UserHandle)
                                params.size == 3 && params[2] == userHandleClz ->
                                    arrayOf(pkgName, perm, userHandle)

                                // (String, String, int, int)
                                params.size == 4 && params[2] == Int::class.javaPrimitiveType && params[3] == Int::class.javaPrimitiveType ->
                                    arrayOf(pkgName, perm, myUserId, 0x1)

                                // (String, String, UserHandle, boolean)
                                params.size == 4 && params[2] == userHandleClz && params[3] == Boolean::class.javaPrimitiveType ->
                                    arrayOf(pkgName, perm, userHandle, true)

                                else ->
                                    throw IllegalStateException("Unexpected signature: ${grant.parameterTypes.contentToString()}")
                            }
                            grant.invoke(ipm, *args)
                            log.append("Granted $perm to $pkgName\n")

                        } catch (inner: Exception) {
                            log.append("Error granting $perm: ${inner.stackTraceToString()}\n")
                        }
                    }

                }

                // If we reach here, consider overall success
                log.append("Success")
            } catch (e: Exception) {
                log.append("Fatal error: ${e.stackTraceToString()}")
            }
            return log.toString()
        }

        @SuppressLint("BlockedPrivateApi", "DiscouragedPrivateApi")
        fun grantViaAppOps(packageManager: PackageManager, pkgName: String, perm: String): String {
            val log = StringBuilder()
            try {
                log.append("Handling $perm via AppOpsManager service\n")

                val aoClz = Class.forName("android.app.AppOpsManager")
                val getAos = aoClz.getDeclaredMethod("getService").apply { isAccessible = true }
                val aos = getAos.invoke(null)!!

                val permToOp = aoClz.getDeclaredMethod("permissionToOpCode", String::class.java)
                val opCode = permToOp.invoke(null, perm) as Int
                log.append("-> OP code for $perm: $opCode\n")

                val modeField = aoClz.getDeclaredField("MODE_ALLOWED").apply { isAccessible = true }
                val modeAllowed = modeField.getInt(null)

                val setMode = aos.javaClass.getDeclaredMethod(
                    "setMode",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    Int::class.javaPrimitiveType
                ).apply { isAccessible = true }
                log.append("-> setMode signature ok\n")

                // 3️⃣ Loop through permissions
                val uid = packageManager.getPackageInfo(pkgName, 0).applicationInfo?.uid

                setMode.invoke(aos, opCode, uid, pkgName, modeAllowed)
                log.append("✔️ Allowed $perm\n")
            } catch (e: Exception) {
                log.append("Error: ${e.stackTraceToString()}\n")
            }
            return log.toString()
        }

        fun grantViaAdb(pkgName: String, perm: String): String {
            return try {
                val p1 = Runtime.getRuntime().exec("pm grant $pkgName $perm")
                val p2 = Runtime.getRuntime().exec("appops set $pkgName $perm allow")
                val output1 = "PM GRANT $perm: ${p1.inputStream.bufferedReader().readText() + p1.errorStream.bufferedReader().readText()}\n\n"
                val output2 = "APPOPS SET $perm: ${p2.inputStream.bufferedReader().readText() + p2.errorStream.bufferedReader().readText()}\n\n"
                output1 + output2
            } catch (e: Exception) {
                e.stackTraceToString()
            }
        }


        fun getLaunchableApps(pm: PackageManager): List<AppItem> {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfoList = pm.queryIntentActivities(intent, 0)
            return resolveInfoList.map { info ->
                val label = info.loadLabel(pm).toString()
                val pkgName = info.activityInfo.packageName
                val icon = info.activityInfo.loadIcon(pm)
                AppItem(
                    image = icon,
                    appName = label,
                    packageName = pkgName
                )
            }
        }

        fun queryRecentAndFrequentApps(applicationContext: Context): List<String> {
            val pm = applicationContext.packageManager
            val am =
                applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            // --- 1) Get 5 most recently used package names ---
            val smClass = Class.forName("android.os.ServiceManager")
            val getServiceM = smClass.getMethod("getService", String::class.java)

            @Suppress("UNCHECKED_CAST")
            val atmBinder = getServiceM.invoke(null, "activity_task") as IBinder

            val atmStub = Class.forName("android.app.IActivityTaskManager\$Stub")
            val asIface = atmStub.getMethod("asInterface", IBinder::class.java)
            val atm = asIface.invoke(null, atmBinder)

            // hidden removeTask lives on IActivityTaskManager, but we just need recentTasks
            val recentTasks = am.getRecentTasks(
                100,
                ActivityManager.RECENT_WITH_EXCLUDED or ActivityManager.RECENT_IGNORE_UNAVAILABLE
            )

            val recentPkgs = recentTasks
                .mapNotNull { it.baseIntent.component?.packageName }
                .distinct()
                .take(20)

            // --- 2) Get 5 most frequently used package names ---
            val now = System.currentTimeMillis()
            val weekAgo = now - TimeUnit.DAYS.toMillis(7)

            val usm = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager
            val usageStats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                weekAgo,
                now
            )

            val frequentPkgs = usageStats
                .filter { it.totalTimeInForeground > 0 }
                .sortedByDescending { it.totalTimeInForeground }
                .map { it.packageName }
                .distinct()
                .filterNot { recentPkgs.contains(it) }  // don't repeat
                .take(20)

            // --- 3) Build AppItem list ---
            val topPkgs = recentPkgs + frequentPkgs

            return topPkgs;
        }


    }
}