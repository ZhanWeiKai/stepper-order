package com.example.stepperfeedback;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.viewpager.widget.ViewPager;
import com.example.stepperfeedback.adapter.StepAdapter;
import com.example.stepperfeedback.feedback.StepperFeedbackType;
import com.example.stepperfeedback.feedback.StepperFeedbackTypeFactory;
import com.example.stepperfeedback.util.AnimationUtil;
import com.example.stepperfeedback.widget.ColorableProgressBar;
import com.example.stepperfeedback.widget.DottedProgressBar;
import com.example.stepperfeedback.widget.RightNavigationButton;
import com.example.stepperfeedback.widget.TabsContainer;

import java.util.ArrayList;
import java.util.List;

public class StepperLayout extends LinearLayout implements TabsContainer.TabItemListener {

    public static final int DEFAULT_TAB_DIVIDER_WIDTH = -1;

    public interface StepperListener {

        void onCompleted(View completeButton);
        void onStepSelected(int newStepPosition);
        void onReturn();

        StepperListener NULL = new StepperListener() {
            @Override
            public void onCompleted(View complete) {}

            @Override
            public void onStepSelected(int stepPosition) {}

            @Override
            public void onReturn() {}
        };
    }

    private ViewPager mPager;
    private Button mBackNavigationButton;
    private RightNavigationButton mNextNavigationButton;
    private RightNavigationButton mCompleteNavigationButton;
    private ViewGroup mStepNavigation;
    private DottedProgressBar mDottedProgressBar;
    private ColorableProgressBar mProgressBar;
    private TabsContainer mTabsContainer;

    private ColorStateList mBackButtonColor;
    private ColorStateList mNextButtonColor;
    private ColorStateList mCompleteButtonColor;

    @ColorInt
    private int mUnselectedColor;
    @ColorInt
    private int mSelectedColor;
    @ColorInt
    private int mErrorColor;

    @DrawableRes
    private int mBottomNavigationBackground;
    @DrawableRes
    private int mBackButtonBackground;
    @DrawableRes
    private int mNextButtonBackground;
    @DrawableRes
    private int mCompleteButtonBackground;

    private int mTabStepDividerWidth = DEFAULT_TAB_DIVIDER_WIDTH;
    private String mBackButtonText;
    private String mNextButtonText;
    private String mCompleteButtonText;

    private boolean mShowBackButtonOnFirstStep;
    private boolean mShowBottomNavigation;
    private int mFeedbackTypeMask = StepperFeedbackType.NONE;

    private StepperFeedbackType mStepperFeedbackType;

    @FloatRange(from = 0.0f, to = 1.0f)
    private float mContentFadeAlpha = AnimationUtil.ALPHA_HALF;

    @DrawableRes
    private int mContentOverlayBackground;

    private int mCurrentStepPosition;
    private boolean mShowErrorStateEnabled;
    private boolean mShowErrorStateOnBackEnabled;
    private boolean mShowErrorMessageEnabled;
    private boolean mTabNavigationEnabled;
    private boolean mInProgress;

    @DrawableRes
    private int mStepperLayoutTheme;

    @NonNull
    private StepperListener mListener = StepperListener.NULL;

    private int mTotalSteps = 4;
    private StepAdapter mAdapter;
    private FrameLayout mContentContainer;
    private List<View> mStepViews = new ArrayList<>();

    public StepperLayout(Context context) {
        this(context, null);
    }

    public StepperLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StepperLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs, defStyleAttr);
    }

    @Override
    public final void setOrientation(int orientation) {
        super.setOrientation(VERTICAL);
    }

    public void setListener(@NonNull StepperListener listener) {
        this.mListener = listener;
    }

    public int getSelectedColor() {
        return mSelectedColor;
    }

    public int getUnselectedColor() {
        return mUnselectedColor;
    }

    public int getErrorColor() {
        return mErrorColor;
    }

    public int getTabStepDividerWidth() {
        return mTabStepDividerWidth;
    }

    @Override
    public void onTabClicked(int position) {
        if (mTabNavigationEnabled) {
            if (position > mCurrentStepPosition) {
                onNext();
            } else if (position < mCurrentStepPosition) {
                setCurrentStepPosition(position);
            }
        }
    }

    public void proceed() {
        if (isLastPosition(mCurrentStepPosition)) {
            onComplete();
        } else {
            onNext();
        }
    }

    public void onBackClicked() {
        OnBackClickedCallback onBackClickedCallback = new OnBackClickedCallback();
        onBackClickedCallback.goToPrevStep();
    }

    public void setCurrentStepPosition(int currentStepPosition) {
        int previousStepPosition = mCurrentStepPosition;
        if (currentStepPosition < previousStepPosition) {
            updateErrorFlagWhenGoingBack();
        }
        mCurrentStepPosition = currentStepPosition;
        onUpdate(currentStepPosition, true);
    }

    public int getCurrentStepPosition() {
        return mCurrentStepPosition;
    }

    public void setNextButtonVerificationFailed(boolean verificationFailed) {
        mNextNavigationButton.setVerificationFailed(verificationFailed);
    }

    public void setCompleteButtonVerificationFailed(boolean verificationFailed) {
        mCompleteNavigationButton.setVerificationFailed(verificationFailed);
    }

    public void setNextButtonEnabled(boolean enabled) {
        mNextNavigationButton.setEnabled(enabled);
    }

    public void setCompleteButtonEnabled(boolean enabled) {
        mCompleteNavigationButton.setEnabled(enabled);
    }

    public void setBackButtonEnabled(boolean enabled) {
        mBackNavigationButton.setEnabled(enabled);
    }

    public void setShowBottomNavigation(boolean showBottomNavigation) {
        mStepNavigation.setVisibility(showBottomNavigation ? View.VISIBLE : View.GONE);
    }

    public void setShowErrorStateEnabled(boolean showErrorStateEnabled) {
        this.mShowErrorStateEnabled = showErrorStateEnabled;
    }

    public boolean isShowErrorStateEnabled() {
        return mShowErrorStateEnabled;
    }

    public boolean isShowErrorStateOnBackEnabled() {
        return mShowErrorStateOnBackEnabled;
    }

    public void setShowErrorMessageEnabled(boolean showErrorMessageEnabled) {
        this.mShowErrorMessageEnabled = showErrorMessageEnabled;
    }

    public boolean isShowErrorMessageEnabled() {
        return mShowErrorMessageEnabled;
    }

    public boolean isTabNavigationEnabled() {
        return mTabNavigationEnabled;
    }

    public void setTabNavigationEnabled(boolean tabNavigationEnabled) {
        mTabNavigationEnabled = tabNavigationEnabled;
    }

    public void showProgress(@NonNull String progressMessage) {
        if (!mInProgress) {
            mStepperFeedbackType.showProgress(progressMessage);
            mInProgress = true;
        }
    }

    public void hideProgress() {
        if (mInProgress) {
            mInProgress = false;
            mStepperFeedbackType.hideProgress();
        }
    }

    public boolean isInProgress() {
        return mInProgress;
    }

    public void setFeedbackType(int feedbackTypeMask) {
        mFeedbackTypeMask = feedbackTypeMask;
        mStepperFeedbackType = StepperFeedbackTypeFactory.createType(mFeedbackTypeMask, this);
    }

    @FloatRange(from = 0.0f, to = 1.0f)
    public float getContentFadeAlpha() {
        return mContentFadeAlpha;
    }

    @DrawableRes
    public int getContentOverlayBackground() {
        return mContentOverlayBackground;
    }

    public void setStepCount(int count) {
        mTotalSteps = count;
    }

    /**
     * Sets the adapter for the stepper.
     *
     * @param adapter the adapter to use
     */
    public void setAdapter(@NonNull StepAdapter adapter) {
        setAdapter(adapter, 0);
    }

    /**
     * Sets the adapter for the stepper with a starting position.
     *
     * @param adapter             the adapter to use
     * @param startingStepPosition the initial step position
     */
    public void setAdapter(@NonNull StepAdapter adapter, int startingStepPosition) {
        this.mAdapter = adapter;
        this.mTotalSteps = adapter.getCount();
        this.mCurrentStepPosition = startingStepPosition;

        // Setup tabs
        List<com.example.stepperfeedback.viewmodel.StepViewModel> viewModels = new ArrayList<>();
        for (int i = 0; i < mTotalSteps; i++) {
            viewModels.add(adapter.getViewModel(i));
        }
        mTabsContainer.setSteps(viewModels);
        mTabsContainer.setVisibility(VISIBLE);

        // Setup content views
        mStepViews.clear();
        for (int i = 0; i < mTotalSteps; i++) {
            View stepView = adapter.getContentView(i);
            mStepViews.add(stepView);
        }

        // Show first step
        showStep(startingStepPosition);
        onUpdate(startingStepPosition, false);
    }

    private void showStep(int position) {
        if (mContentContainer != null && position >= 0 && position < mStepViews.size()) {
            mContentContainer.removeAllViews();
            View stepView = mStepViews.get(position);
            if (stepView != null && stepView.getParent() != null) {
                ((ViewGroup) stepView.getParent()).removeView(stepView);
            }
            mContentContainer.addView(stepView);
        }
        mTabsContainer.updateSteps(position);
    }

    @SuppressWarnings("RestrictedApi")
    private void init(AttributeSet attrs, @AttrRes int defStyleAttr) {
        initDefaultValues();
        extractValuesFromAttributes(attrs, defStyleAttr);

        final Context context = getContext();
        ContextThemeWrapper contextThemeWrapper = new ContextThemeWrapper(context, context.getTheme());
        contextThemeWrapper.setTheme(mStepperLayoutTheme);

        LayoutInflater.from(contextThemeWrapper).inflate(R.layout.ms_stepper_layout, this, true);

        setOrientation(VERTICAL);

        bindViews();

        mPager.setOnTouchListener((view, motionEvent) -> true);

        initNavigation();

        mDottedProgressBar.setVisibility(GONE);
        mProgressBar.setVisibility(GONE);
        mTabsContainer.setVisibility(GONE);
        mStepNavigation.setVisibility(mShowBottomNavigation ? View.VISIBLE : View.GONE);

        mStepperFeedbackType = StepperFeedbackTypeFactory.createType(mFeedbackTypeMask, this);
    }

    private void initNavigation() {
        if (mBottomNavigationBackground != 0) {
            mStepNavigation.setBackgroundResource(mBottomNavigationBackground);
        }

        mBackNavigationButton.setText(mBackButtonText);
        mNextNavigationButton.setText(mNextButtonText);
        mCompleteNavigationButton.setText(mCompleteButtonText);

        setBackgroundIfPresent(mBackButtonBackground, mBackNavigationButton);
        setBackgroundIfPresent(mNextButtonBackground, mNextNavigationButton);
        setBackgroundIfPresent(mCompleteButtonBackground, mCompleteNavigationButton);

        mBackNavigationButton.setOnClickListener(v -> onBackClicked());
        mNextNavigationButton.setOnClickListener(v -> onNext());
        mCompleteNavigationButton.setOnClickListener(v -> onComplete());
    }

    private void setCompoundDrawablesForNavigationButtons(@DrawableRes int backDrawableResId, @DrawableRes int nextDrawableResId) {
    }

    private void setBackgroundIfPresent(@DrawableRes int backgroundRes, View view) {
        if (backgroundRes != 0) {
            view.setBackgroundResource(backgroundRes);
        }
    }

    private void bindViews() {
        mPager = findViewById(R.id.ms_stepPager);
        mContentContainer = findViewById(R.id.ms_stepContentContainer);
        mBackNavigationButton = findViewById(R.id.ms_stepPrevButton);
        mNextNavigationButton = findViewById(R.id.ms_stepNextButton);
        mCompleteNavigationButton = findViewById(R.id.ms_stepCompleteButton);
        mStepNavigation = findViewById(R.id.ms_bottomNavigation);
        mDottedProgressBar = findViewById(R.id.ms_stepDottedProgressBar);
        mProgressBar = findViewById(R.id.ms_stepProgressBar);
        mTabsContainer = findViewById(R.id.ms_stepTabsContainer);
        mTabsContainer.setListener(this);
    }

    private void extractValuesFromAttributes(AttributeSet attrs, @AttrRes int defStyleAttr) {
        if (attrs != null) {
            final TypedArray a = getContext().obtainStyledAttributes(
                    attrs, R.styleable.StepperLayout, defStyleAttr, 0);

            if (a.hasValue(R.styleable.StepperLayout_ms_backButtonColor)) {
                mBackButtonColor = a.getColorStateList(R.styleable.StepperLayout_ms_backButtonColor);
            }
            if (a.hasValue(R.styleable.StepperLayout_ms_nextButtonColor)) {
                mNextButtonColor = a.getColorStateList(R.styleable.StepperLayout_ms_nextButtonColor);
            }
            if (a.hasValue(R.styleable.StepperLayout_ms_completeButtonColor)) {
                mCompleteButtonColor = a.getColorStateList(R.styleable.StepperLayout_ms_completeButtonColor);
            }
            if (a.hasValue(R.styleable.StepperLayout_ms_bottomNavigationBackground)) {
                mBottomNavigationBackground = a.getResourceId(R.styleable.StepperLayout_ms_bottomNavigationBackground, 0);
            }
            if (a.hasValue(R.styleable.StepperLayout_ms_backButtonBackground)) {
                mBackButtonBackground = a.getResourceId(R.styleable.StepperLayout_ms_backButtonBackground, 0);
            }
            if (a.hasValue(R.styleable.StepperLayout_ms_nextButtonBackground)) {
                mNextButtonBackground = a.getResourceId(R.styleable.StepperLayout_ms_nextButtonBackground, 0);
            }
            if (a.hasValue(R.styleable.StepperLayout_ms_completeButtonBackground)) {
                mCompleteButtonBackground = a.getResourceId(R.styleable.StepperLayout_ms_completeButtonBackground, 0);
            }
            if (a.hasValue(R.styleable.StepperLayout_ms_backButtonText)) {
                mBackButtonText = a.getString(R.styleable.StepperLayout_ms_backButtonText);
            }
            if (a.hasValue(R.styleable.StepperLayout_ms_nextButtonText)) {
                mNextButtonText = a.getString(R.styleable.StepperLayout_ms_nextButtonText);
            }
            if (a.hasValue(R.styleable.StepperLayout_ms_completeButtonText)) {
                mCompleteButtonText = a.getString(R.styleable.StepperLayout_ms_completeButtonText);
            }
            mShowBackButtonOnFirstStep = a.getBoolean(R.styleable.StepperLayout_ms_showBackButtonOnFirstStep, false);
            mShowBottomNavigation = a.getBoolean(R.styleable.StepperLayout_ms_showBottomNavigation, true);
            mShowErrorStateEnabled = a.getBoolean(R.styleable.StepperLayout_ms_showErrorStateEnabled, false);
            mShowErrorStateOnBackEnabled = a.getBoolean(R.styleable.StepperLayout_ms_showErrorStateOnBackEnabled, false);
            mShowErrorMessageEnabled = a.getBoolean(R.styleable.StepperLayout_ms_showErrorMessageEnabled, false);
            mTabNavigationEnabled = a.getBoolean(R.styleable.StepperLayout_ms_tabNavigationEnabled, true);

            if (a.hasValue(R.styleable.StepperLayout_ms_stepperFeedbackType)) {
                mFeedbackTypeMask = a.getInt(R.styleable.StepperLayout_ms_stepperFeedbackType, StepperFeedbackType.NONE);
            }
            if (a.hasValue(R.styleable.StepperLayout_ms_stepperFeedback_contentFadeAlpha)) {
                mContentFadeAlpha = a.getFloat(R.styleable.StepperLayout_ms_stepperFeedback_contentFadeAlpha, AnimationUtil.ALPHA_HALF);
            }
            if (a.hasValue(R.styleable.StepperLayout_ms_stepperFeedback_contentOverlayBackground)) {
                mContentOverlayBackground = a.getResourceId(R.styleable.StepperLayout_ms_stepperFeedback_contentOverlayBackground, 0);
            }
            mStepperLayoutTheme = a.getResourceId(R.styleable.StepperLayout_ms_stepperLayoutTheme, R.style.MSDefaultStepperLayoutTheme);

            a.recycle();
        }
    }

    private void initDefaultValues() {
        mBackButtonColor = ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.ms_bottomNavigationButtonTextColor));
        mNextButtonColor = ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.ms_bottomNavigationButtonTextColor));
        mCompleteButtonColor = ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.ms_bottomNavigationButtonTextColor));
        mSelectedColor = ContextCompat.getColor(getContext(), R.color.ms_selectedColor);
        mUnselectedColor = ContextCompat.getColor(getContext(), R.color.ms_unselectedColor);
        mErrorColor = ContextCompat.getColor(getContext(), R.color.ms_errorColor);
        mBackButtonText = getContext().getString(R.string.ms_back);
        mNextButtonText = getContext().getString(R.string.ms_next);
        mCompleteButtonText = getContext().getString(R.string.ms_complete);
    }

    private boolean isLastPosition(int position) {
        return position == mTotalSteps - 1;
    }

    private void updateErrorFlagWhenGoingBack() {
    }

    private void onNext() {
        // Show progress with loading message
        showProgress("Loading...");

        // Simulate async operation with delay
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            hideProgress();
            OnNextClickedCallback onNextClickedCallback = new OnNextClickedCallback();
            onNextClickedCallback.goToNextStep();
        }, 1500);
    }

    private void onComplete() {
        OnCompleteClickedCallback onCompleteClickedCallback = new OnCompleteClickedCallback();
        onCompleteClickedCallback.complete();
    }

    private void onUpdate(int newStepPosition, boolean userTriggeredChange) {
        final boolean isLast = isLastPosition(newStepPosition);
        final boolean isFirst = newStepPosition == 0;

        int backButtonTargetVisibility = (isFirst && !mShowBackButtonOnFirstStep) ? View.GONE : View.VISIBLE;
        int nextButtonVisibility = isLast ? View.GONE : View.VISIBLE;
        int completeButtonVisibility = !isLast ? View.GONE : View.VISIBLE;

        AnimationUtil.fadeViewVisibility(mNextNavigationButton, nextButtonVisibility, userTriggeredChange);
        AnimationUtil.fadeViewVisibility(mCompleteNavigationButton, completeButtonVisibility, userTriggeredChange);
        AnimationUtil.fadeViewVisibility(mBackNavigationButton, backButtonTargetVisibility, userTriggeredChange);

        mListener.onStepSelected(newStepPosition);
    }

    public class OnNextClickedCallback {
        public StepperLayout getStepperLayout() {
            return StepperLayout.this;
        }

        public void goToNextStep() {
            if (mCurrentStepPosition >= mTotalSteps - 1) {
                return;
            }
            mCurrentStepPosition++;
            showStep(mCurrentStepPosition);
            onUpdate(mCurrentStepPosition, true);
        }
    }

    public class OnCompleteClickedCallback {
        public StepperLayout getStepperLayout() {
            return StepperLayout.this;
        }

        public void complete() {
            mListener.onCompleted(mCompleteNavigationButton);
        }
    }

    public class OnBackClickedCallback {
        public StepperLayout getStepperLayout() {
            return StepperLayout.this;
        }

        public void goToPrevStep() {
            if (mCurrentStepPosition <= 0) {
                if (mShowBackButtonOnFirstStep) {
                    mListener.onReturn();
                }
                return;
            }
            mCurrentStepPosition--;
            showStep(mCurrentStepPosition);
            onUpdate(mCurrentStepPosition, true);
        }
    }
}
