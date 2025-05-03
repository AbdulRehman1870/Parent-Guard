package com.example.parental_control;

import android.os.Bundle;
import androidx.appcompat.app.ActionBar; // ActionBar import karein
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.parental_control.databinding.ActivityDashboardBinding;

public class dashboard extends AppCompatActivity {

    private ActivityDashboardBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.materialToolbar);

        // Default ActionBar title ko disable karein
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false); // Yeh line default title ko hide kar degi
            // actionBar.setTitle(""); // Yeh bhi kar sakte hain, lekin setDisplayShowTitleEnabled(false) behtar hai
        }

        if (savedInstanceState == null) {
            loadFragment(new Dasboard_Fragment());
            binding.bottomNavigationView.setSelectedItemId(R.id.home);
        }

        binding.bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.home) {
                selectedFragment = new Dasboard_Fragment();
            } else if (itemId == R.id.about) {
                selectedFragment = new Features_Fragment();
            } else {
                return false;
            }

            if (selectedFragment != null) {
                loadFragment(selectedFragment);
            }
            return true;
        });
    }

    private void loadFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }
}