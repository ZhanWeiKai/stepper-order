/*
Copyright 2016 StepStone Services

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package com.example.stepperfeedback;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.stepperfeedback.adapter.StepAdapter;
import com.example.stepperfeedback.feedback.StepperFeedbackType;
import com.example.stepperfeedback.viewmodel.StepViewModel;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements StepperLayout.StepperListener {

    private StepperLayout mStepperLayout;

    private static final int[] STEP_COLORS = {
            Color.parseColor("#E3F2FD"), // Light Blue
            Color.parseColor("#E8F5E9"), // Light Green
            Color.parseColor("#FFF3E0"), // Light Orange
            Color.parseColor("#F3E5F5")  // Light Purple
    };

    private static final String[] STEP_TITLES = {
            "Step 1",
            "Step 2",
            "Step 3",
            "Step 4"
    };

    private static final String[] STEP_DESCRIPTIONS = {
            "Welcome to the first step!\n\nThis demonstrates the stepper feedback mechanism.\n\nClick NEXT to continue.",
            "This is the second step.\n\nYou can see the tabs showing progress at the top.\n\nClick NEXT to continue.",
            "You're almost there!\n\nThis is the third step of the stepper.\n\nClick NEXT to finish.",
            "Congratulations!\n\nYou have completed all steps.\n\nClick COMPLETE to finish."
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupStepperLayout();
    }

    private void setupStepperLayout() {
        mStepperLayout = findViewById(R.id.stepper_layout);
        mStepperLayout.setListener(this);

        // Set up feedback type (tabs + disabled navigation during progress)
        int feedbackType = StepperFeedbackType.TABS |
                StepperFeedbackType.CONTENT_PROGRESS |
                StepperFeedbackType.DISABLED_BOTTOM_NAVIGATION;

        mStepperLayout.setFeedbackType(feedbackType);

        // Create and set adapter
        mStepperLayout.setAdapter(new DemoStepAdapter());
    }

    @Override
    public void onCompleted(View completeButton) {
        Toast.makeText(this, "Stepper completed!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onStepSelected(int newStepPosition) {
        // Optional: Add any logic when step is selected
    }

    @Override
    public void onReturn() {
        Toast.makeText(this, "Return button clicked", Toast.LENGTH_SHORT).show();
    }

    /**
     * Adapter for the demo stepper
     */
    private class DemoStepAdapter implements StepAdapter {

        @Override
        public StepViewModel getViewModel(int position) {
            return new StepViewModel.Builder(MainActivity.this)
                    .setTitle(STEP_TITLES[position])
                    .create();
        }

        @Override
        public View getContentView(int position) {
            // Create a ScrollView with content
            ScrollView scrollView = new ScrollView(MainActivity.this);
            scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            ));
            scrollView.setBackgroundColor(STEP_COLORS[position]);

            // Create content container
            LinearLayout contentLayout = new LinearLayout(MainActivity.this);
            contentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            contentLayout.setOrientation(LinearLayout.VERTICAL);
            contentLayout.setGravity(Gravity.CENTER);
            contentLayout.setPadding(48, 48, 48, 48);

            // Create title TextView
            TextView titleView = new TextView(MainActivity.this);
            titleView.setText(STEP_TITLES[position]);
            titleView.setTextSize(24);
            titleView.setTextColor(Color.BLACK);
            titleView.setGravity(Gravity.CENTER);
            titleView.setPadding(0, 0, 0, 32);

            // Create description TextView
            TextView descView = new TextView(MainActivity.this);
            descView.setText(STEP_DESCRIPTIONS[position]);
            descView.setTextSize(16);
            descView.setTextColor(Color.DKGRAY);
            descView.setGravity(Gravity.CENTER);
            descView.setLineSpacing(8, 1);

            contentLayout.addView(titleView);
            contentLayout.addView(descView);
            scrollView.addView(contentLayout);

            return scrollView;
        }

        @Override
        public int getCount() {
            return 4;
        }
    }
}
