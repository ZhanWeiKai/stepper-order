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

package com.example.stepperfeedback.adapter;

import android.view.View;

import com.example.stepperfeedback.viewmodel.StepViewModel;

import androidx.annotation.IntRange;

/**
 * An interface to be implemented by adapters for {@link com.example.stepperfeedback.StepperLayout}.
 */
public interface StepAdapter {

    /**
     * Returns the ViewModel for the step at the given position.
     *
     * @param position step position
     * @return ViewModel for the step
     */
    StepViewModel getViewModel(@IntRange(from = 0) int position);

    /**
     * Returns the View for the step at the given position.
     *
     * @param position step position
     * @return View for the step content
     */
    View getContentView(@IntRange(from = 0) int position);

    /**
     * Get the number of steps.
     *
     * @return number of steps
     */
    int getCount();

}
