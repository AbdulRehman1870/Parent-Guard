package com.example.parental_control;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link Features_Fragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class Features_Fragment extends Fragment {

    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private String mParam1;
    private String mParam2;

    public Features_Fragment() {
        // Required empty public constructor
    }

    public static Features_Fragment newInstance(String param1, String param2) {
        Features_Fragment fragment = new Features_Fragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_features_, container, false);

        // Find the CardView for Set Screen Time
        CardView setScreenTimeLayout = view.findViewById(R.id.set_screen_time);

        // Set an OnClickListener for Set Screen Time
        setScreenTimeLayout.setOnClickListener(v -> {
            // Navigate to parent_set_screen_time activity
            Intent intent = new Intent(getActivity(), DisplayInstalledApps.class);
            startActivity(intent);
        });

        // Find the CardView for Set Time
        CardView setScreenTimeButtonLayout = view.findViewById(R.id.set_time);

        // Set an OnClickListener for Set Screen Time Button
        setScreenTimeButtonLayout.setOnClickListener(v -> {
            // Navigate to SetTimeActivity
            Intent intent = new Intent(getActivity(), set_time.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        // Find the CardView for Location
        CardView locationLayout = view.findViewById(R.id.location);

        // Set an OnClickListener for Location
        locationLayout.setOnClickListener(v -> {
            // Navigate to LocationActivity
            Intent intent = new Intent(getActivity(), MapsActivity.class);
            startActivity(intent);
        });

        // Find the CardView for Geofencing
        CardView setLocationLayout = view.findViewById(R.id.geofence);

        // Set an OnClickListener for Geofencing
        setLocationLayout.setOnClickListener(v -> {
            // Navigate to GeofencingActivity
            Intent intent = new Intent(getActivity(), MapsGeofence.class);
            startActivity(intent);
        });

        // Find the CardView for Child Mistakes
        CardView childMistakesLayout = view.findViewById(R.id.child_mistakes);

        // Set an OnClickListener for Child Mistakes
        childMistakesLayout.setOnClickListener(v -> {
            // Navigate to ResponseActivity
            Intent intent = new Intent(getActivity(), output_Activity.class);
            startActivity(intent);
        });

        // Find the CardView for Child Sentiments
        CardView childSentimentsLayout = view.findViewById(R.id.child_Sentiments);

        // Set an OnClickListener for Child Sentiments
        childSentimentsLayout.setOnClickListener(v -> {
            // Navigate to ResponseActivity
            Intent intent = new Intent(getActivity(), Display_Sentiments.class);
            startActivity(intent);
        });

        // Find the CardView for Personality Traits
        CardView personalityTraitsLayout = view.findViewById(R.id.personality_traits);

        // Set an OnClickListener for Personality Traits
        personalityTraitsLayout.setOnClickListener(v -> {
            // Navigate to PersonalityTraitsActivity
            Intent intent = new Intent(getActivity(), Personality_Traits.class);
            startActivity(intent);
        });

        return view;
    }
}
