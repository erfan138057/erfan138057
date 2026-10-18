package com.personal.wifibridge

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var password: EditText
    private lateinit var status: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(28, 24, 28, 24) }
        layout.addView(TextView(this).apply { text = "Wi-Fi Bridge A36"; textSize = 26f })
        layout.addView(TextView(this).apply { text = "پروکسی HTTP/HTTPS آزمایشی؛ این برنامه هات‌اسپات یا NAT ایجاد نمی‌کند."; textSize = 16f; setPadding(0,16,0,16) })
        password = EditText(this).apply { hint = "رمز حداقل ۱۲ نویسه‌ای" }
        layout.addView(password)
        layout.addView(Button(this).apply { text = "شروع پروکسی"; setOnClickListener {
            val p = password.text.toString()
            if (p.length < 12) { Toast.makeText(this@MainActivity, "رمز باید حداقل ۱۲ نویسه باشد", Toast.LENGTH_LONG).show(); return@setOnClickListener }
            val i = Intent(this@MainActivity, ProxyService::class.java).putExtra("password", p)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            status.text = "در حال راه‌اندازی پروکسی روی پورت 8787. لپ‌تاپ باید به IP گوشی دسترسی داشته باشد."
        } })
        layout.addView(Button(this).apply { text = "توقف"; setOnClickListener { stopService(Intent(this@MainActivity, ProxyService::class.java)); status.text = "متوقف شد." } })
        status = TextView(this).apply { text = "آماده"; textSize = 15f; setPadding(0,16,0,0) }
        layout.addView(status)
        setContentView(layout)
    }
}
