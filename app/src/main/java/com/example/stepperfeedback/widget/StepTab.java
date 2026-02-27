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

package com.example.stepperfeedback.widget;

import android.content.Context;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.example.stepperfeedback.R;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * A widget for a single tab in the {@link TabsContainer}.
 */
public class StepTab extends RelativeLayout {

    private static final float ALPHA_INACTIVE_STEP_TITLE = 0.54f;
    private static final float ALPHA_ACTIVE_STEP_TITLE = 0.87f;

    final TextView mStepNumberTextView;
    final View mStepDivider;
    final TextView mStepTitleTextView;
    final TextView mStepSubtitleTextView;
    final ImageView mStepDoneIndicator;
    final ImageView mStepIconBackground;

    @ColorInt
    private int mUnselectedColor;

    @ColorInt
    private int mSelectedColor;

    public StepTab(Context context) {
        this(context, null);
    }

    public StepTab(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StepTab(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(getContext()).inflate(R.layout.ms_step_tab, this, true);

        mSelectedColor = ContextCompat.getColor(context, R.color.ms_selectedColor);
        mUnselectedColor = ContextCompat.getColor(context, R.color.ms_unselectedColor);

        mStepNumberTextView = findViewById(R.id.ms_stepNumber);
        mStepDoneIndicator = findViewById(R.id.ms_stepDoneIndicator);
        mStepIconBackground = findViewById(R.id.ms_stepIconBackground);
        mStepDivider = findViewById(R.id.ms_stepDivider);
        mStepTitleTextView = findViewById(R.id.ms_stepTitle);
        mStepSubtitleTextView = findViewById(R.id.ms_stepSubtitle);
    }

    /**
     * Changes the visibility of the horizontal line in the tab
     *
     * @param show true if the line should be shown, false otherwise
     */
    public void toggleDividerVisibility(boolean show) {
        mStepDivider.setVisibility(show ? VISIBLE : GONE);
    }

    /**
     * Updates the UI state of the tab.
     *
     * @param done    true the step was completed
     * @param current true if this is the currently selected step
     */
    public void updateState(final boolean done, final boolean current) {
        if (done) {
            changeToDone();
        } else if (current) {
            changeToActiveNumber();
        } else {
            changeToInactiveNumber();
        }
    }

    /**
     * Sets the name of the step
     *
     * @param title step name
     */
    public void setStepTitle(CharSequence title) {
        mStepTitleTextView.setText(title);
    }

    /**
     * Sets the optional step description.
     *
     * @param subtitle optional step description
     */
    public void setStepSubtitle(@Nullable CharSequence subtitle) {
        mStepSubtitleTextView.setText(subtitle);
        mStepSubtitleTextView.setVisibility(subtitle != null && !subtitle.toString().isEmpty() ? VISIBLE : GONE);
    }

    /**
     * Sets the position of the step
     *
     * @param number step position
     */
    public void setStepNumber(CharSequence number) {
        mStepNumberTextView.setText(number);
    }

    public void setUnselectedColor(int unselectedColor) {
        this.mUnselectedColor = unselectedColor;
    }

    public void setSelectedColor(int selectedColor) {
        this.mSelectedColor = selectedColor;
    }

    private void changeToInactiveNumber() {
        mStepIconBackground.setColorFilter(mUnselectedColor, PorterDuff.Mode.SRC_IN);
        mStepDoneIndicator.setVisibility(GONE);
        mStepNumberTextView.setVisibility(VISIBLE);
        mStepTitleTextView.setAlpha(ALPHA_INACTIVE_STEP_TITLE);
    }

    private void changeToActiveNumber() {
        mStepIconBackground.setColorFilter(mSelectedColor, PorterDuff.Mode.SRC_IN);
        mStepDoneIndicator.setVisibility(GONE);
        mStepNumberTextView.setVisibility(VISIBLE);
        mStepTitleTextView.setAlpha(ALPHA_ACTIVE_STEP_TITLE);
    }

    private void changeToDone() {
        mStepIconBackground.setColorFilter(mSelectedColor, PorterDuff.Mode.SRC_IN);
        mStepDoneIndicator.setVisibility(VISIBLE);
        mStepNumberTextView.setVisibility(GONE);
        mStepTitleTextView.setAlpha(ALPHA_ACTIVE_STEP_TITLE);
    }
}
