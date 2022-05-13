package com.example.cardRecognition;

import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.example.cardRecognition.R;

public class Splashscreen extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splashscreen);



        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent MainActivity = new Intent(Splashscreen.this, com.example.cardRecognition.MainActivity.class);
                startActivity(MainActivity);
                finish();

            }
        }, 3000);
    }
}