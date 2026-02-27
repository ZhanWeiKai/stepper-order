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

package com.example.stepperfeedback.util;

import android.animation.Animator;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import android.view.View;
import android.view.ViewPropertyAnimator;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Util class containing static methods to simplify animation.
 */
public final class AnimationUtil {

    @Retention(SOURCE)
    @IntDef({View.VISIBLE, View.INVISIBLE, View.GONE})
    @interface Visibility {
    }

    public static final float ALPHA_OPAQUE = 1.0f;
    public static final float ALPHA_INVISIBLE = 0.0f;
    public static final float ALPHA_HALF = 0.5f;

    private static final int DEFAULT_DURATION = 300;

    private AnimationUtil() {
        throw new AssertionError("Please do not instantiate this class");
    }

    /**
     * Animate the View's visibility using a fade animation.
     * @param view The View to be animated
     * @param visibility View visibility constant, can be either View.VISIBLE, View.INVISIBLE or View.GONE
     * @param animate true if the visibility should be changed with an animation, false if instantaneously
     */
    public static void fadeViewVisibility(@NonNull final View view, @Visibility final int visibility, boolean animate) {
        ViewPropertyAnimator animator = view.animate();
        animator.cancel();
        animator.alpha(visibility == View.VISIBLE ? ALPHA_OPAQUE : ALPHA_INVISIBLE)
                .setDuration(animate ? DEFAULT_DURATION : 0)
                .setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(@NonNull Animator animation) {
                        if (visibility == View.VISIBLE) {
                            view.setVisibility(visibility);
                        }
                    }

                    @Override
                    public void onAnimationEnd(@NonNull Animator animation) {
                        if (visibility == View.INVISIBLE || visibility == View.GONE) {
                            view.setVisibility(visibility);
                        }
                    }

                    @Override
                    public void onAnimationCancel(@NonNull Animator animation) {
                        if (visibility == View.INVISIBLE || visibility == View.GONE) {
                            view.setVisibility(visibility);
                        }
                    }

                    @Override
                    public void onAnimationRepeat(@NonNull Animator animation) {
                    }
                }).start();
    }
}
