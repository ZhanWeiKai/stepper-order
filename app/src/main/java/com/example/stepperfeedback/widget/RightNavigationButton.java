package com.example.stepperfeedback.widget;

import android.content.Context;
import android.util.AttributeSet;
import com.google.android.material.button.MaterialButton;

public class RightNavigationButton extends MaterialButton {

    private boolean mVerificationFailed;

    public RightNavigationButton(Context context) {
        this(context, null);
    }

    public RightNavigationButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setVerificationFailed(boolean verificationFailed) {
        mVerificationFailed = verificationFailed;
        refreshDrawableState();
    }

    public boolean isVerificationFailed() {
        return mVerificationFailed;
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (mVerificationFailed) {
            drawableState[drawableState.length - 1] = com.example.stepperfeedback.R.attr.state_verification_failed;
        }
        return drawableState;
    }
}
