package com.example.parental_control; // Use your package name

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class IntroSlideFragment extends Fragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_DESCRIPTION = "description";
    private static final String ARG_IMAGE_RES = "image_res";

    public static IntroSlideFragment newInstance(String title, String description, int imageRes) {
        IntroSlideFragment fragment = new IntroSlideFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_DESCRIPTION, description);
        args.putInt(ARG_IMAGE_RES, imageRes);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_intro_slide_layout, container, false);

        TextView titleTextView = view.findViewById(R.id.intro_title);
        TextView descriptionTextView = view.findViewById(R.id.intro_description);
        ImageView imageView = view.findViewById(R.id.intro_image);

        if (getArguments() != null) {
            titleTextView.setText(getArguments().getString(ARG_TITLE));
            descriptionTextView.setText(getArguments().getString(ARG_DESCRIPTION));
            imageView.setImageResource(getArguments().getInt(ARG_IMAGE_RES));
        }
        return view;
    }
}