/*
Copyright 2017 StepStone Services

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

package com.example.stepperfeedback.feedback;

import androidx.annotation.NonNull;
import android.view.View;
import android.widget.ProgressBar;

import com.example.stepperfeedback.R;
import com.example.stepperfeedback.StepperLayout;

/**
 * Feedback stepper type which displays a progress bar on top of the steps' content.
 */
public class ContentProgressStepperFeedbackType implements StepperFeedbackType {

    @NonNull
    private final ProgressBar mPagerProgressBar;

    public ContentProgressStepperFeedbackType(@NonNull StepperLayout stepperLayout) {
        mPagerProgressBar = (ProgressBar) stepperLayout.findViewById(R.id.ms_stepPagerProgressBar);
    }

    @Override
    public void showProgress(@NonNull String progressMessage) {
        mPagerProgressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideProgress() {
        mPagerProgressBar.setVisibility(View.GONE);
    }
}
