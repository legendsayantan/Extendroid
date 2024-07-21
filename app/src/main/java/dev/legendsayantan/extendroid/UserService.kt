package dev.legendsayantan.extendroid

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.UserHandle
import android.view.Display
import rikka.shizuku.Shizuku

/**
 * @author legendsayantan
 */
class UserService : IUserService.Stub {


    constructor() {
    }

    override fun destroy() {
        //Shizuku wants the service to be killed. Clean up and exit.
        System.exit(0)
    }

    override fun launchIntentOnDisplay(intent: Intent,display: Display){
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId)
        val contextReflect = Class.forName("android.content.Context")
        val userHandleReflect = Class.forName("android.os.UserHandle")
        val startActivityAsUserMethod = contextReflect.getMethod("startActivityAsUser",Intent::class.java,
            Bundle::class.java,
            UserHandle::class.java)
        val userHandleConstructor = userHandleReflect.getConstructor(Int::class.java)
        startActivityAsUserMethod.invoke(this,intent,options.toBundle(),userHandleConstructor.newInstance(
            Shizuku.getUid()))
    }
}