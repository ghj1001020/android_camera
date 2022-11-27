package com.ghj.camera;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import com.ghj.camera.camera2.Camera2Activity;

public class MainActivity extends AppCompatActivity {

    Button btnCamera2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnCamera2 = findViewById(R.id.btnCamera2);
        btnCamera2.setOnClickListener(v -> {
            Intent intent = new Intent(this, Camera2Activity.class);
            startActivity(intent);
        });
    }
}