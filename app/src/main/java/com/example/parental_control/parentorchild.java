package com.example.parental_control;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class parentorchild extends AppCompatActivity {



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parentorchild);

        ImageView parent_phone = findViewById(R.id.imageView);
        ImageView child_phone = findViewById(R.id.imageView2);


        parent_phone.setOnClickListener(v -> {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseUser user = auth.getCurrentUser();

            Intent intent;
            if (user != null) {
                intent = new Intent(parentorchild.this, dashboard.class);
            } else {
                intent = new Intent(parentorchild.this, signup.class);
            }

            startActivity(intent);


        });



        child_phone.setOnClickListener(v -> {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseUser user = auth.getCurrentUser();

            Intent intent;
            if (user != null) {
                intent = new Intent(parentorchild.this, paired_successfull.class);
            } else {
                intent = new Intent(parentorchild.this, login.class);
            }

            startActivity(intent);
        });
    }
}