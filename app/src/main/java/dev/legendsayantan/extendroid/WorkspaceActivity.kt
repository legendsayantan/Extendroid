package dev.legendsayantan.extendroid

import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WorkspaceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workspace)

        val name = intent.getStringExtra(EXTRA_WORKSPACE_NAME) ?: "Workspace"
        findViewById<TextView>(R.id.workspaceTitle).text = name
        title = name
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val taskDescription = ActivityManager.TaskDescription.Builder()
                .setLabel(name)
                .build()
            setTaskDescription(taskDescription)
        } else {
            @Suppress("DEPRECATION")
            val taskDescription = ActivityManager.TaskDescription(name)
            setTaskDescription(taskDescription)
        }
    }

    companion object {
        const val EXTRA_WORKSPACE_NAME = "dev.legendsayantan.extendroid.extra.WORKSPACE_NAME"
    }
}
