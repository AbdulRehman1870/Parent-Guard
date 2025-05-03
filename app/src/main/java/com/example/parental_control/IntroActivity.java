package com.example.parental_control;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout; // Import this

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat; // Import this
import androidx.viewpager2.widget.ViewPager2;

// Remove TabLayout and TabLayoutMediator imports if not used elsewhere
// import com.google.android.material.tabs.TabLayout;
// import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

public class IntroActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private IntroPagerAdapter pagerAdapter;
    // private TabLayout tabLayoutDots; // Remove this
    private LinearLayout layoutDotsContainer; // Add this
    private Button buttonNext, buttonGetStarted;
    private List<IntroSlideItem> introSlides;
    private ImageView[] dots; // To hold references to dot ImageViews

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        viewPager = findViewById(R.id.view_pager_intro);
        // tabLayoutDots = findViewById(R.id.tab_layout_dots); // Remove this
        layoutDotsContainer = findViewById(R.id.layout_dots_container); // Initialize this
        buttonNext = findViewById(R.id.button_next_intro);
        buttonGetStarted = findViewById(R.id.button_get_started);

        setupIntroSlides();

        pagerAdapter = new IntroPagerAdapter(this, introSlides);
        viewPager.setAdapter(pagerAdapter);

        // Remove TabLayoutMediator
        // new TabLayoutMediator(tabLayoutDots, viewPager, (tab, position) -> {}).attach();

        addDotsIndicator(0); // Add dots initially

        buttonNext.setOnClickListener(v -> {
            int currentItem = viewPager.getCurrentItem();
            if (currentItem < introSlides.size() - 1) {
                viewPager.setCurrentItem(currentItem + 1);
            }
        });

        buttonGetStarted.setOnClickListener(v -> {
            Settings.getPreference(IntroActivity.this).saveBooleanState(Settings.INTRO_COMPLETED, true);
            Intent intent = new Intent(IntroActivity.this, parentorchild.class);
            startActivity(intent);
            finish();
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                addDotsIndicator(position); // Update dots on page change

                if (position == introSlides.size() - 1) {
                    buttonNext.setVisibility(View.GONE);
                    buttonGetStarted.setVisibility(View.VISIBLE);
                } else {
                    buttonNext.setVisibility(View.VISIBLE);
                    buttonGetStarted.setVisibility(View.GONE);
                }
            }
        });
        if (introSlides.isEmpty()){
            buttonNext.setVisibility(View.GONE);
            buttonGetStarted.setVisibility(View.GONE);
        } else if (introSlides.size() == 1) {
            buttonNext.setVisibility(View.GONE);
            buttonGetStarted.setVisibility(View.VISIBLE);
        } else {
            buttonNext.setVisibility(View.VISIBLE);
            buttonGetStarted.setVisibility(View.GONE);
        }
    }

    private void addDotsIndicator(int currentPage) {
        int numSlides = introSlides.size();
        if (numSlides <= 0) return;

        dots = new ImageView[numSlides];
        layoutDotsContainer.removeAllViews(); // Clear previous dots

        for (int i = 0; i < numSlides; i++) {
            dots[i] = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            // Adjust margin for spacing between dots
            int marginInDp = 4; // Adjust this value for spacing (e.g., 4dp)
            int marginInPx = (int) (marginInDp * getResources().getDisplayMetrics().density);
            params.setMargins(marginInPx, 0, marginInPx, 0);
            dots[i].setLayoutParams(params);

            if (i == currentPage) {
                dots[i].setImageDrawable(ContextCompat.getDrawable(this, R.drawable.dot_active));
            } else {
                dots[i].setImageDrawable(ContextCompat.getDrawable(this, R.drawable.dot_inactive));
            }
            layoutDotsContainer.addView(dots[i]);
        }
    }

    private void setupIntroSlides() {
        introSlides = new ArrayList<>();
        introSlides.add(new IntroSlideItem(
                getString(R.string.intro_title_personality),
                getString(R.string.intro_desc_personality),
                R.drawable.ic_intro_personality
        ));
        introSlides.add(new IntroSlideItem(
                getString(R.string.intro_title_sentiment),
                getString(R.string.intro_desc_sentiment),
                R.drawable.ic_intro_sentiment
        ));
        introSlides.add(new IntroSlideItem(
                getString(R.string.intro_title_toxicity),
                getString(R.string.intro_desc_toxicity),
                R.drawable.ic_intro_toxicity
        ));
        introSlides.add(new IntroSlideItem(
                getString(R.string.intro_title_screentime),
                getString(R.string.intro_desc_screentime),
                R.drawable.ic_intro_screentime
        ));
        introSlides.add(new IntroSlideItem(
                getString(R.string.intro_title_location_geofence),
                getString(R.string.intro_desc_location_geofence),
                R.drawable.ic_intro_location_geofence
        ));
    }
}