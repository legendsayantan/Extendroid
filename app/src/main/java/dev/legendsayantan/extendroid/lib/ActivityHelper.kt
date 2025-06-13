package dev.legendsayantan.extendroid.lib

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import java.lang.reflect.Method


/**
 * @author legendsayantan
 */
class ActivityHelper {
    companion object {
        var SHELL_PACKAGE: String = "com.android.shell"

        fun getLauncherActivityComponentName(
            context: Context,
            packageName: String
        ): ComponentName? {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            intent.setPackage(packageName)
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            if (resolveInfos.isNotEmpty()) {
                val info = resolveInfos[0].activityInfo
                return ComponentName(info.packageName, info.name)
            }
            return null
        }

        @SuppressLint("PrivateApi")
        fun launchActivityOnDisplayID(component: ComponentName, displayId: Int): String {
            try {
                // 1) Build the Intent
                val intent = Intent()
                intent.setComponent(component)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // 2) Build ActivityOptions via reflection
                val aoClass = Class.forName("android.app.ActivityOptions")
                val makeBasic = aoClass.getMethod("makeBasic")
                val options = makeBasic.invoke(null)

                val setDisplay =
                    aoClass.getMethod("setLaunchDisplayId", Int::class.javaPrimitiveType)
                setDisplay.invoke(options, displayId)

                val toBundle = aoClass.getMethod("toBundle")
                val optsBundle = toBundle.invoke(options) as Bundle?

                // 3) Get IActivityManager instance
                val smClass = Class.forName("android.os.ServiceManager")
                val getSvc = smClass.getMethod("getService", String::class.java)
                val binder = getSvc.invoke(null, "activity")

                val stubClass = Class.forName("android.app.IActivityManager\$Stub")
                val asIface =
                    stubClass.getMethod("asInterface", Class.forName("android.os.IBinder"))
                val iam = asIface.invoke(null, binder)

                // 4) Find the startActivityAsUser method dynamically to match current API
                val iamClass = Class.forName("android.app.IActivityManager")
                var targetMethod: Method? = null
                for (m in iamClass.getMethods()) {
                    if (m.name != "startActivityAsUser") continue
                    val p = m.getParameterTypes().size
                    // look for either 10- or 11-arg variant
                    if (p == 10 || p == 11) {
                        targetMethod = m
                        break
                    }
                }
                if (targetMethod == null) {
                    throw NoSuchMethodException("startActivityAsUser with expected signature not found")
                }

                // Prepare common args
                val invokeArgs: Array<Any?>?
                if (targetMethod.getParameterTypes().size == 11) {
                    // signature with userId at end
                    val uhClass = Class.forName("android.os.UserHandle")
                    val userCurrent = uhClass.getField("USER_CURRENT").getInt(null)
                    invokeArgs = arrayOf<Any?>(
                        null,  // caller token
                        SHELL_PACKAGE,  // callingPackage
                        intent,  // intent
                        null,  // resolvedType
                        null,  // resultTo
                        null,  // resultWho
                        0,  // requestCode
                        Intent.FLAG_ACTIVITY_NEW_TASK,  // startFlags
                        null,  // profilerInfo
                        optsBundle,  // options
                        userCurrent // userId
                    )
                } else {
                    // 10-arg signature without userId
                    invokeArgs = arrayOf<Any?>(
                        null,
                        SHELL_PACKAGE,
                        intent,
                        null,
                        null,
                        null,
                        0,
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                        null,
                        optsBundle
                    )
                }

                // 5) Invoke
                targetMethod.invoke(iam, *invokeArgs)
                return "Launched on display $displayId"
            } catch (e: Exception) {
                return e.stackTraceToString()
            }
        }

        @SuppressLint("PrivateApi")
        fun removePackageTask(context: Context, pkg: String?) {
            // 1) Reflectively get the ActivityTaskManager service binder
            val sm = Class.forName("android.os.ServiceManager")
            val getService = sm.getMethod("getService", String::class.java)
            val b = getService.invoke(null, "activity_task") as IBinder?

            // 2) Get IActivityTaskManager
            val stub = Class.forName("android.app.IActivityTaskManager\$Stub")
            val asInterface = stub.getMethod("asInterface", IBinder::class.java)
            val atm = asInterface.invoke(null, b)

            // 3) Enumerate recent tasks to find task IDs for 'pkg'
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val recent = am.getRecentTasks(
                100,
                ActivityManager.RECENT_WITH_EXCLUDED
            )

            // 4) For each matching task, call removeTask(taskId)
            val removeTask = atm.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType)
            for (info in recent) {
                if (info.baseIntent.component!!.packageName == pkg) {
                    removeTask.invoke(atm, info.taskId)
                }
            }
        }


    }

}