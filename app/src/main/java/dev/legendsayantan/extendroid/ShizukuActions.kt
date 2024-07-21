package dev.legendsayantan.extendroid

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.UserHandle
import android.view.Display
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import rikka.shizuku.shared.BuildConfig

/**
 * @author legendsayantan
 */
class ShizukuActions {
    private var bndr: IUserService? = null

    fun Context.getServiceArgs(): Shizuku.UserServiceArgs? {
        return Shizuku.UserServiceArgs(
            ComponentName(packageName,UserService::class.java.name))
            .processNameSuffix("user_service")
            .debuggable(BuildConfig.DEBUG)
            .version(1)
    }

    init {

        val connection = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, binder: IBinder?) {
                if (binder != null && binder.pingBinder()) {
                    bndr = IUserService.Stub.asInterface(binder)
                }
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                bndr = null;
            }
        }
    }
    companion object {

        @SuppressLint("PrivateApi")
        fun Context.grantAudioRecordPerm() {
            val iPmClass = Class.forName("android.content.pm.IPackageManager")
            val iPmStub = Class.forName("android.content.pm.IPackageManager\$Stub")
            val asInterfaceMethod = iPmStub.getMethod("asInterface", IBinder::class.java)
            val grantRuntimePermissionMethod = iPmClass.getMethod(
                "grantRuntimePermission",
                String::class.java /* package name */,
                String::class.java /* permission name */,
                Int::class.java /* user ID */
            )

            val iPmInstance = asInterfaceMethod.invoke(
                null, ShizukuBinderWrapper(
                    SystemServiceHelper.getSystemService("package")
                )
            )

            grantRuntimePermissionMethod.invoke(
                iPmInstance,
                packageName,
                android.Manifest.permission.RECORD_AUDIO,
                0
            )
        }

        @SuppressLint("PrivateApi")
        fun Context.grantMediaProjectionPerm() {
            val iAppOps = Class.forName("android.app.AppOpsManager")
            val iAppOpsService = Class.forName("com.android.internal.app.IAppOpsService")
            val iAppOpsServiceStub = Class.forName("com.android.internal.app.IAppOpsService\$Stub")
            val asInterfaceMethod = iAppOpsServiceStub.getMethod("asInterface", IBinder::class.java)
            val startOpMethod = iAppOps.getMethod(
                "startOp",
                String::class.java
                /** op **/
                , Int::class.java
                /** uid **/
                , String::class.java
                /** packageName **/
                , String::class.java
                /** attributionTag **/
                , String::class.java
                /** message **/
            )
            val iAppOpsServiceInstance = asInterfaceMethod.invoke(
                null, ShizukuBinderWrapper(
                    SystemServiceHelper.getSystemService(Context.APP_OPS_SERVICE)
                )
            )
            val iAppOpsConstructor = iAppOps.getConstructor(Context::class.java,iAppOpsService)
            val iAppOpsInstance = iAppOpsConstructor.newInstance(this,iAppOpsServiceInstance)


            startOpMethod.invoke(
                iAppOpsInstance,
                "android:project_media",
                android.os.Process.myUid(),
                packageName,
                null,
                null
            )
        }

        fun Context.launchStarterOnDisplay(display: Display) {
            val intent = Intent(createDisplayContext(display), StarterActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId)
            val contextReflect = Class.forName("android.content.Context")
            val userHandleReflect = Class.forName("android.os.UserHandle")
            val startActivityAsUserMethod = contextReflect.getMethod("startActivityAsUser",Intent::class.java,Bundle::class.java,UserHandle::class.java)
            val userHandleConstructor = userHandleReflect.getConstructor(Int::class.java)
            startActivityAsUserMethod.invoke(this,intent,options.toBundle(),userHandleConstructor.newInstance(Shizuku.getUid()))
        }
    }
}