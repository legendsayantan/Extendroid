package dev.legendsayantan.extendroid

import android.os.Bundle
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import dev.legendsayantan.extendroid.adapters.LogAdapter
import dev.legendsayantan.extendroid.lib.Logging

class LogsActivity : AppCompatActivity() {
    val logging by lazy { Logging(applicationContext) }
    val daysLimit by lazy { findViewById<EditText>(R.id.days) }
    val ascending by lazy { findViewById<RadioButton>(R.id.ascending) }
    val descending by lazy { findViewById<RadioButton>(R.id.descending) }

    val clearLogsBtn by lazy { findViewById<MaterialButton>(R.id.clearLogs) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_logs)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        updateLogView()
        var lastValidDays = 1
        daysLimit.doOnTextChanged{ text,_,_,_ ->
            if(text.isNullOrEmpty() || text.toString().toIntOrNull() == null || text.toString().toInt() < 0){
                daysLimit.error = "Invalid number"
            }else{
                daysLimit.error = null
                lastValidDays = text.toString().toInt()
                updateLogView()
            }
        }
        ascending.setOnCheckedChangeListener { _, isChecked ->
            if(isChecked) updateLogView()
        }
        descending.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) updateLogView()
        }
        clearLogsBtn.setOnClickListener {
            Thread{
                logging.clearLogsOlderThan(lastValidDays)
                runOnUiThread {
                    Toast.makeText(applicationContext,"Logs older than $lastValidDays days were cleared.", Toast.LENGTH_LONG).show()
                    updateLogView()
                }
            }.start()
        }
    }

    fun updateLogView() {
        val recycler = findViewById<RecyclerView>(R.id.logsView);
        recycler.adapter = null
        Toast.makeText(applicationContext,"Loading...", Toast.LENGTH_SHORT).show()
        Thread {
            //fetch logs
            val logs = Logging(applicationContext).getLogsOf(daysLimit.text.toString().toInt(), descending.isChecked)
            runOnUiThread {
                recycler.adapter = LogAdapter(logs)
            }
        }.start()
    }
}