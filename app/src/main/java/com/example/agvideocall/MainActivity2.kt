package com.example.agvideocall

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity2 : AppCompatActivity() {
    private lateinit var start :Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        start = findViewById(R.id.button1)
        start.setOnClickListener {
            startActivity(Intent(baseContext,MainActivity::class.java))
        }
    }
}