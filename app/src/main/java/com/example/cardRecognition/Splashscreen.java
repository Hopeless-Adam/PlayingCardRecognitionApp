package com.example.cardRecognition;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;

public class Splashscreen extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splashscreen);



        new Handler().postDelayed(() -> {
            Intent MainActivity = new Intent(Splashscreen.this, com.example.cardRecognition.MainActivity.class);
            startActivity(MainActivity);
            finish();

        }, 3000);
    }
}