package dev.legendsayantan.extendroid.lib

import android.annotation.SuppressLint
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method


/**
 * @author legendsayantan
 */
class DisplayHelper {
    @SuppressLint("BlockedPrivateApi", "PrivateApi")
    companion object{

        val SurfaceControlClass = Class.forName("android.view.SurfaceControl")
        var DisplayControlClass: Class<*>? = null

        init {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val classLoaderFactoryClass =
                    Class.forName("com.android.internal.os.ClassLoaderFactory")
                val createClassLoaderMethod = classLoaderFactoryClass.getDeclaredMethod(
                    "createClassLoader",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    ClassLoader::class.java,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    String::class.java
                )
                val classLoader = createClassLoaderMethod.invoke(
                    null,
                    "/system/framework/services.jar",
                    null,
                    null,
                    ClassLoader.getSystemClassLoader(),
                    0,
                    true,
                    null
                ) as ClassLoader
                DisplayControlClass =
                    classLoader.loadClass("com.android.server.display.DisplayControl")

                val loadLibraryMethod = Runtime::class.java.getDeclaredMethod(
                    "loadLibrary0",
                    Class::class.java,
                    String::class.java
                )
                loadLibraryMethod.isAccessible = true
                loadLibraryMethod.invoke(
                    Runtime.getRuntime(),
                    DisplayControlClass,
                    "android_servers"
                )
            }
        }

        @SuppressLint("PrivateApi", "BlockedPrivateApi")
        fun injectKeyEventToDisplay(
            displayId: Int,
            action: Int,
            keyCode: Int,
            metaState: Int
        ) {
            // 1) Create KeyEvent
            val now = SystemClock.uptimeMillis()
            val event = KeyEvent(
                now, now,
                action, keyCode, /*repeat*/0,
                metaState, /*deviceId*/0,
                /*scancode*/0, /*flags*/0, InputDevice.SOURCE_TOUCHSCREEN
            )

            // 2) Reflectively set its displayId (hidden API)
            KeyEvent::class.java.getDeclaredMethod("setDisplayId", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(event, displayId)

            // 3) Get IInputManager from ServiceManager
            val svcMgr = Class.forName("android.os.ServiceManager")
            val getService: Method = svcMgr.getMethod("getService", String::class.java)

            @Suppress("UNCHECKED_CAST")
            val imBinder = getService.invoke(null, "input") as IBinder

            val imStub = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asIface = imStub.getMethod("asInterface", IBinder::class.java)
            val im = asIface.invoke(null, imBinder)

            // 4) Inject the event (mode 0 = ASYNC)
            val injectMethod = im.javaClass.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            // InputManager.INJECT_INPUT_EVENT_MODE_ASYNC == 0
            injectMethod.invoke(im, event, 0)
        }

        fun injectMotionEvent(
            event: MotionEvent,
            displayId: Int
        ) {
            // 1) Build the MotionEvent
            event.apply {
                // set the target display
                // API 30+ has setDisplayId; pre‑30 we reflect
                val m =
                    MotionEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
                m.invoke(this, displayId)
            }


            // 2) Get the IInputManager binder
            val smClass = Class.forName("android.os.ServiceManager")
            val getService: Method = smClass.getMethod("getService", String::class.java)

            @Suppress("UNCHECKED_CAST")
            val inputBinder = getService.invoke(null, "input") as IBinder

            // 3) Obtain IInputManager interface
            val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asInterface: Method = stubClass.getMethod("asInterface", IBinder::class.java)
            val inputManager = asInterface.invoke(null, inputBinder)

            // 4) Call hidden injectInputEvent(InputEvent, int mode)
            val injectMethod = inputManager.javaClass.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            // mode: 0 = INJECT_INPUT_EVENT_MODE_ASYNC
            injectMethod.invoke(inputManager, event, 0)

            // 5) Recycle the event
            event.recycle()
        }

        @SuppressLint("PrivateApi")
        fun listenForInputEvents(displayId: Int) {
            try {
                val smClass = Class.forName("android.os.ServiceManager")
                val getService = smClass.getDeclaredMethod("getService", String::class.java)
                val binder = getService.invoke(null, "input") as android.os.IBinder

                val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
                val asInterface = stubClass.getDeclaredMethod("asInterface", android.os.IBinder::class.java)
                val iInputManager = asInterface.invoke(null, binder)

                val method = iInputManager.javaClass.methods.firstOrNull { it.name == "monitorGestureInput" }
                    ?: throw NoSuchMethodException("monitorGestureInput not found")

                val args = when (method.parameterTypes.size) {
                    2 -> arrayOf("ShizukuInputListener",displayId) // old signature
                    3 -> arrayOf(android.os.Binder(), "ShizukuInputListener", displayId) // new signature
                    else -> throw IllegalStateException("Unexpected monitorGestureInput signature: ${method.parameterTypes.toList()}")
                }

                val inputMonitor = method.invoke(iInputManager, *args)

                val getChannel = inputMonitor.javaClass.getDeclaredMethod("getInputChannel")
                val inputChannel = getChannel.invoke(inputMonitor)

                val ierClass = Class.forName("android.view.InputEventReceiver")
                val ctor = ierClass.getDeclaredConstructor(
                    Class.forName("android.view.InputChannel"),
                    android.os.Looper::class.java
                )
                ctor.isAccessible = true
                val receiver = ctor.newInstance(inputChannel, android.os.Looper.getMainLooper())

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        val dispose = ierClass.getDeclaredMethod("dispose")
                        dispose.isAccessible = true
                        dispose.invoke(receiver)
                        Log.d("ShizukuService", "Receiver disposed after 60s")
                    } catch (e: Exception) {
                        Log.e("ShizukuService", "Dispose failed", e)
                    }
                }, 60_000)

                Log.d("ShizukuService", "Listening for input events on display $displayId (method params=${method.parameterTypes.size})")

            } catch (e: Exception) {
                Log.e("ShizukuService", "Failed to listen for input events", e)
            }
        }









        fun getBuiltInDisplay(): IBinder? {
            val method = SurfaceControlClass.getMethod("getInternalDisplayToken")
            return method.invoke(null) as IBinder?
        }

        fun setDisplayPowerMode(mode:Int){
            val method: Method = SurfaceControlClass.getMethod(
                "setDisplayPowerMode",
                IBinder::class.java,
                Int::class.javaPrimitiveType
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && DisplayControlClass != null) {
                val getPhysicalDisplayIdsMethod: Method =
                    DisplayControlClass!!.getMethod("getPhysicalDisplayIds")
                val getPhysicalDisplayTokenMethod: Method = DisplayControlClass!!.getMethod(
                    "getPhysicalDisplayToken",
                    Long::class.javaPrimitiveType
                )

                val displayIds = getPhysicalDisplayIdsMethod.invoke(null) as LongArray?
                if (displayIds != null) {
                    for (displayId in displayIds) {
                        val token =
                            getPhysicalDisplayTokenMethod.invoke(null, displayId) as IBinder?
                        method.invoke(null,token, mode)
                    }
                }
            }else{
                method.invoke(null, getBuiltInDisplay(), mode)
            }
        }
    }
}