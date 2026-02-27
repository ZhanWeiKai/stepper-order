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
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import com.example.stepperfeedback.R;
import com.example.stepperfeedback.viewmodel.StepViewModel;

import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;

/**
 * Layout used for displaying tabs from the horizontal stepper.
 */
public class TabsContainer extends FrameLayout {

    /**
     * Listeners for actions on individual tabs of the horizontal stepper
     */
    public interface TabItemListener {

        /**
         * Called when a tab gets clicked
         *
         * @param position position of the tab/step
         */
        @UiThread
        void onTabClicked(int position);
    }

    @ColorInt
    private int mUnselectedColor;

    @ColorInt
    private int mSelectedColor;

    private int mContainerLateralPadding;

    private HorizontalScrollView mTabsScrollView;

    private LinearLayout mTabsInnerContainer;

    private TabItemListener mListener;

    private List<StepViewModel> mStepViewModels;

    public TabsContainer(Context context) {
        this(context, null);
    }

    public TabsContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TabsContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.ms_tabs_container, this, true);

        mSelectedColor = ContextCompat.getColor(context, R.color.ms_selectedColor);
        mUnselectedColor = ContextCompat.getColor(context, R.color.ms_unselectedColor);
        mContainerLateralPadding = context.getResources().getDimensionPixelOffset(R.dimen.ms_tabs_container_lateral_padding);

        mTabsInnerContainer = findViewById(R.id.ms_stepTabsInnerContainer);
        mTabsScrollView = findViewById(R.id.ms_stepTabsScrollView);
    }

    public void setUnselectedColor(@ColorInt int unselectedColor) {
        this.mUnselectedColor = unselectedColor;
    }

    public void setSelectedColor(@ColorInt int selectedColor) {
        this.mSelectedColor = selectedColor;
    }

    public void setListener(@NonNull TabItemListener listener) {
        this.mListener = listener;
    }

    /**
     * Sets the steps to display in the {@link TabsContainer}.
     *
     * @param stepViewModels a list of step info holders
     */
    public void setSteps(List<StepViewModel> stepViewModels) {
        this.mStepViewModels = stepViewModels;

        mTabsInnerContainer.removeAllViews();
        for (int i = 0; i < stepViewModels.size(); i++) {
            final View tab = createStepTab(i, stepViewModels.get(i));
            mTabsInnerContainer.addView(tab, tab.getLayoutParams());
        }
    }

    /**
     * Changes the position of the current step and updates the UI based on it.
     *
     * @param currentStepPosition new current step
     */
    public void updateSteps(int currentStepPosition) {
        if (mStepViewModels == null) {
            return;
        }
        int size = mStepViewModels.size();
        for (int i = 0; i < size; i++) {
            StepTab childTab = (StepTab) mTabsInnerContainer.getChildAt(i);
            boolean done = i < currentStepPosition;
            final boolean current = i == currentStepPosition;

            childTab.updateState(done, current);
            if (current) {
                mTabsScrollView.smoothScrollTo(childTab.getLeft() - mContainerLateralPadding, 0);
            }
        }
    }

    private View createStepTab(final int position, @NonNull StepViewModel stepViewModel) {
        StepTab view = (StepTab) LayoutInflater.from(getContext()).inflate(R.layout.ms_step_tab_container, mTabsInnerContainer, false);
        view.setStepNumber(String.valueOf(position + 1));
        view.toggleDividerVisibility(!isLastPosition(position));
        view.setStepTitle(stepViewModel.getTitle());
        view.setStepSubtitle(stepViewModel.getSubtitle());
        view.setSelectedColor(mSelectedColor);
        view.setUnselectedColor(mUnselectedColor);

        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener != null) {
                    mListener.onTabClicked(position);
                }
            }
        });

        return view;
    }

    private boolean isLastPosition(int position) {
        return mStepViewModels != null && position == mStepViewModels.size() - 1;
    }
}
