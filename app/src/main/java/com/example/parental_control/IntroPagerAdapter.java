package com.example.parental_control; // Use your package name

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.List;

public class IntroPagerAdapter extends FragmentStateAdapter {

    private List<IntroSlideItem> introSlides;

    public IntroPagerAdapter(@NonNull FragmentActivity fragmentActivity, List<IntroSlideItem> introSlides) {
        super(fragmentActivity);
        this.introSlides = introSlides;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        IntroSlideItem slide = introSlides.get(position);
        return IntroSlideFragment.newInstance(slide.getTitle(), slide.getDescription(), slide.getImageResource());
    }

    @Override
    public int getItemCount() {
        return introSlides.size();
    }
}