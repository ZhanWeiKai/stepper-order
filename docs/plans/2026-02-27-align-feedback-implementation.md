# Stepper Feedback 机制对齐实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 stepper-feedback-demo 的 feedback 实现与 android-material-stepper 完全对齐

**Architecture:** 使用组合模式 + 工厂模式，通过位运算标志组合多种反馈类型

**Tech Stack:** Android Java, AndroidX annotations

---

## 差异分析

| 组件 | android-material-stepper | stepper-feedback-demo | 需要修改 |
|------|-------------------------|----------------------|---------|
| StepperFeedbackType.java | 有详细注释 | 无注释 | ✅ |
| StepperFeedbackTypeFactory.java | 有 License + 注释 | 无 | ✅ |
| StepperFeedbackTypeComposite.java | 有 License + 注释 | 无 | ✅ |
| TabsStepperFeedbackType.java | 有 License + 注释 | 无 | ✅ |
| ContentProgressStepperFeedbackType.java | 有 License + 注释 | 无 | ✅ |
| ContentFadeStepperFeedbackType.java | 有 License + 注释 | 无 | ✅ |
| ContentOverlayStepperFeedbackType.java | 有 License + 注释 | 无 | ✅ |
| DisabledBottomNavigationStepperFeedbackType.java | 有 License + 注释 | 无 | ✅ |
| DisabledContentInteractionStepperFeedbackType.java | 有 License + 注释 | 无 | ✅ |
| AnimationUtil.java | 有 License + 注释 | 无 | ✅ |
| StepViewPager.java | 有 License + 注释 | 无 | ✅ |

---

## Task 1: 更新 StepperFeedbackType.java

**Files:**
- Modify: `app/src/main/java/com/example/stepperfeedback/feedback/StepperFeedbackType.java`

**Step 1: 更新接口，添加详细注释**

```java
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

/**
 * An interface to be implemented by all support stepper feedback types.
 * It contains methods which allow to show feedback for the duration of some executed operation.
 *
 * @author Piotr Zawadzki
 */
public interface StepperFeedbackType {

    /**
     * No changes during operation.
     */
    int NONE = 1;

    /**
     * Show a progress message instead of the tabs during operation.
     * @see TabsStepperFeedbackType
     */
    int TABS = 1 << 1;

    /**
     * Shows a progress bar on top of the steps' content.
     * @see ContentProgressStepperFeedbackType
     */
    int CONTENT_PROGRESS = 1 << 2;

    /**
     * Disables the buttons in the bottom navigation during operation.
     * @see DisabledBottomNavigationStepperFeedbackType
     */
    int DISABLED_BOTTOM_NAVIGATION = 1 << 3;

    /**
     * Disables content interaction during operation i.e. stops step views from receiving touch events.
     * @see DisabledContentInteractionStepperFeedbackType
     */
    int DISABLED_CONTENT_INTERACTION = 1 << 4;

    /**
     * Partially fades the content out during operation.
     * @see ContentFadeStepperFeedbackType
     */
    int CONTENT_FADE = 1 << 5;

    /**
     * Shows a dimmed overlay over the content during operation.
     * @see ContentOverlayStepperFeedbackType
     */
    int CONTENT_OVERLAY = 1 << 6;

    /**
     * Animation duration for progress show/hide operations in milliseconds.
     */
    int PROGRESS_ANIMATION_DURATION = 200;

    /**
     * Shows a progress indicator. This does not have to be a progress bar and it depends on chosen stepper feedback types.
     * @param progressMessage optional progress message if supported by the selected types
     */
    void showProgress(@NonNull String progressMessage);

    /**
     * Hides the progress indicator.
     */
    void hideProgress();

}
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/stepperfeedback/feedback/StepperFeedbackType.java
git commit -m "docs: add license header and documentation to StepperFeedbackType"
```

---

## Task 2: 更新 StepperFeedbackTypeFactory.java

**Files:**
- Modify: `app/src/main/java/com/example/stepperfeedback/feedback/StepperFeedbackTypeFactory.java`

**Step 1: 添加 License 和详细注释**

```java
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
import androidx.annotation.VisibleForTesting;

import com.example.stepperfeedback.StepperLayout;

/**
 * Factory class for creating feedback stepper types.
 */
public final class StepperFeedbackTypeFactory {

    private StepperFeedbackTypeFactory() {}

    /**
     * Creates a stepper feedback type for provided arguments.
     * It can be a composition of several feedback types depending on the provided flags.
     *
     * @param feedbackTypeMask step feedback type mask, should contain one or more from {@link StepperFeedbackType}
     * @param stepperLayout    stepper layout to use with the chosen stepper feedback type(s)
     * @return a stepper feedback type
     */
    @NonNull
    public static StepperFeedbackType createType(int feedbackTypeMask, @NonNull StepperLayout stepperLayout) {

        StepperFeedbackTypeComposite stepperFeedbackTypeComposite = new StepperFeedbackTypeComposite();

        if ((feedbackTypeMask & StepperFeedbackType.NONE) != 0) {
            //Add no more components if NONE type is selected
            return stepperFeedbackTypeComposite;
        }

        if ((feedbackTypeMask & StepperFeedbackType.TABS) != 0) {
            stepperFeedbackTypeComposite.addComponent(new TabsStepperFeedbackType(stepperLayout));
        }

        if ((feedbackTypeMask & StepperFeedbackType.CONTENT_PROGRESS) != 0) {
            stepperFeedbackTypeComposite.addComponent(new ContentProgressStepperFeedbackType(stepperLayout));
        }

        if ((feedbackTypeMask & StepperFeedbackType.CONTENT_FADE) != 0) {
            stepperFeedbackTypeComposite.addComponent(new ContentFadeStepperFeedbackType(stepperLayout));
        }

        if ((feedbackTypeMask & StepperFeedbackType.CONTENT_OVERLAY) != 0) {
            stepperFeedbackTypeComposite.addComponent(new ContentOverlayStepperFeedbackType(stepperLayout));
        }

        if ((feedbackTypeMask & StepperFeedbackType.DISABLED_BOTTOM_NAVIGATION) != 0) {
            stepperFeedbackTypeComposite.addComponent(new DisabledBottomNavigationStepperFeedbackType(stepperLayout));
        }

        if ((feedbackTypeMask & StepperFeedbackType.DISABLED_CONTENT_INTERACTION) != 0) {
            stepperFeedbackTypeComposite.addComponent(new DisabledContentInteractionStepperFeedbackType(stepperLayout));
        }

        return stepperFeedbackTypeComposite;
    }
}
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/stepperfeedback/feedback/StepperFeedbackTypeFactory.java
git commit -m "docs: add license header and documentation to StepperFeedbackTypeFactory"
```

---

## Task 3: 更新 StepperFeedbackTypeComposite.java

**Files:**
- Modify: `app/src/main/java/com/example/stepperfeedback/feedback/StepperFeedbackTypeComposite.java`

**Step 1: 添加 License 和详细注释**

```java
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
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * A stepper feedback type which is a composition of other feedback type, which allows to select only a group of feedback types.
 * See Stepper feedback section in https://material.io/guidelines/components/steppers.html#steppers-types-of-steppers
 */
public class StepperFeedbackTypeComposite implements StepperFeedbackType {

    @NonNull
    private List<StepperFeedbackType> mChildren = new ArrayList<>();

    @Override
    public void showProgress(@NonNull String progressMessage) {
        for (StepperFeedbackType child : mChildren) {
            child.showProgress(progressMessage);
        }
    }

    @Override
    public void hideProgress() {
        for (StepperFeedbackType child : mChildren) {
            child.hideProgress();
        }
    }

    /**
     * Adds a child component to this composite.
     * @param component child to add
     */
    public void addComponent(@NonNull StepperFeedbackType component) {
        mChildren.add(component);
    }

    /**
     * Returns the list of child feedback components.
     * @return list of child components
     */
    @VisibleForTesting
    public List<StepperFeedbackType> getChildComponents() {
        return mChildren;
    }
}
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/stepperfeedback/feedback/StepperFeedbackTypeComposite.java
git commit -m "docs: add license header and documentation to StepperFeedbackTypeComposite"
```

---

## Task 4: 更新 TabsStepperFeedbackType.java

**Files:**
- Modify: `app/src/main/java/com/example/stepperfeedback/feedback/TabsStepperFeedbackType.java`

**Step 1: 添加 License 和完整注释**

```java
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
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import com.example.stepperfeedback.R;
import com.example.stepperfeedback.StepperLayout;

import static com.example.stepperfeedback.util.AnimationUtil.ALPHA_INVISIBLE;
import static com.example.stepperfeedback.util.AnimationUtil.ALPHA_OPAQUE;

/**
 * Feedback stepper type which displays a progress message instead of the tabs.
 */
public class TabsStepperFeedbackType implements StepperFeedbackType {

    private final float mProgressMessageTranslationWhenHidden;

    private boolean mTabNavigationEnabled;

    @NonNull
    private TextView mProgressMessageTextView;

    @NonNull
    private View mTabsScrollingContainer;

    @NonNull
    private StepperLayout mStepperLayout;

    public TabsStepperFeedbackType(@NonNull StepperLayout stepperLayout) {
        mProgressMessageTranslationWhenHidden = stepperLayout.getResources().getDimension(R.dimen.ms_progress_message_translation_when_hidden);
        mProgressMessageTextView = (TextView) stepperLayout.findViewById(R.id.ms_stepTabsProgressMessage);
        mTabsScrollingContainer = stepperLayout.findViewById(R.id.ms_stepTabsScrollView);
        mStepperLayout = stepperLayout;
        mProgressMessageTextView.setVisibility(View.VISIBLE);
    }

    @Override
    public void showProgress(@NonNull String progressMessage) {
        mTabNavigationEnabled = mStepperLayout.isTabNavigationEnabled();
        setTabNavigationEnabled(false);
        mProgressMessageTextView.setText(progressMessage);
        mProgressMessageTextView.animate()
                .setStartDelay(PROGRESS_ANIMATION_DURATION)
                .alpha(ALPHA_OPAQUE)
                .translationY(0.0f)
                .setDuration(PROGRESS_ANIMATION_DURATION);
        mTabsScrollingContainer.animate()
                .alpha(ALPHA_INVISIBLE)
                .setStartDelay(0)
                .setInterpolator(new LinearInterpolator())
                .setDuration(PROGRESS_ANIMATION_DURATION);
    }

    @Override
    public void hideProgress() {
        setTabNavigationEnabled(mTabNavigationEnabled);

        mProgressMessageTextView.animate()
                .setStartDelay(0)
                .alpha(ALPHA_INVISIBLE)
                .translationY(mProgressMessageTranslationWhenHidden)
                .setDuration(PROGRESS_ANIMATION_DURATION);
        mTabsScrollingContainer.animate()
                .alpha(ALPHA_OPAQUE)
                .setStartDelay(PROGRESS_ANIMATION_DURATION)
                .setInterpolator(new AccelerateInterpolator())
                .setDuration(PROGRESS_ANIMATION_DURATION);
    }

    private void setTabNavigationEnabled(boolean tabNavigationEnabled) {
        mStepperLayout.setTabNavigationEnabled(tabNavigationEnabled);
    }
}
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/stepperfeedback/feedback/TabsStepperFeedbackType.java
git commit -m "docs: add license header and documentation to TabsStepperFeedbackType"
```

---

## Task 5: 更新 ContentProgressStepperFeedbackType.java

**Files:**
- Modify: `app/src/main/java/com/example/stepperfeedback/feedback/ContentProgressStepperFeedbackType.java`

**Step 1: 添加 License 和注释**

```java
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
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/stepperfeedback/feedback/ContentProgressStepperFeedbackType.java
git commit -m "docs: add license header and documentation to ContentProgressStepperFeedbackType"
```

---

## Task 6: 更新 ContentFadeStepperFeedbackType.java

**Files:**
- Modify: `app/src/main/java/com/example/stepperfeedback/feedback/ContentFadeStepperFeedbackType.java`

**Step 1: 添加 License 和注释**

```java
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

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import android.view.View;

import com.example.stepperfeedback.R;
import com.example.stepperfeedback.StepperLayout;

import static com.example.stepperfeedback.util.AnimationUtil.ALPHA_OPAQUE;

/**
 * Feedback stepper type which partially fades the content out.
 */
public class ContentFadeStepperFeedbackType implements StepperFeedbackType {

    @NonNull
    private final View mPager;

    @FloatRange(from = 0.0f, to = 1.0f)
    private final float mFadeOutAlpha;

    public ContentFadeStepperFeedbackType(@NonNull StepperLayout stepperLayout) {
        mPager = stepperLayout.findViewById(R.id.ms_stepPager);
        mFadeOutAlpha = stepperLayout.getContentFadeAlpha();
    }

    @Override
    public void showProgress(@NonNull String progressMessage) {
        mPager.animate()
                .alpha(mFadeOutAlpha)
                .setDuration(PROGRESS_ANIMATION_DURATION);
    }

    @Override
    public void hideProgress() {
        mPager.animate()
                .alpha(ALPHA_OPAQUE)
                .setDuration(PROGRESS_ANIMATION_DURATION);
    }
}
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/stepperfeedback/feedback/ContentFadeStepperFeedbackType.java
git commit -m "docs: add license header and documentation to ContentFadeStepperFeedbackType"
```

---

## Task 7: 更新 ContentOverlayStepperFeedbackType.java

**Files:**
- Modify: `app/src/main/java/com/example/stepperfeedback/feedback/ContentOverlayStepperFeedbackType.java`

**Step 1: 添加 License 和注释**

```java
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

import com.example.stepperfeedback.R;
import com.example.stepperfeedback.StepperLayout;

import static com.example.stepperfeedback.util.AnimationUtil.ALPHA_INVISIBLE;
import static com.example.stepperfeedback.util.AnimationUtil.ALPHA_OPAQUE;

/**
 * Feedback stepper type which shows a dimmed overlay over the content.
 */
public class ContentOverlayStepperFeedbackType implements StepperFeedbackType {

    @NonNull
    private final View mOverlayView;

    public ContentOverlayStepperFeedbackType(@NonNull StepperLayout stepperLayout) {
        mOverlayView = stepperLayout.findViewById(R.id.ms_stepPagerOverlay);
        mOverlayView.setVisibility(View.VISIBLE);
        mOverlayView.setAlpha(ALPHA_INVISIBLE);
        final int contentOverlayBackground = stepperLayout.getContentOverlayBackground();
        if (contentOverlayBackground != 0)  {
            mOverlayView.setBackgroundResource(contentOverlayBackground);
        }
    }

    @Override
    public void showProgress(@NonNull String progressMessage) {
        mOverlayView.animate()
                .alpha(ALPHA_OPAQUE)
                .setDuration(PROGRESS_ANIMATION_DURATION);
    }

    @Override
    public void hideProgress() {
        mOverlayView.animate()
                .alpha(ALPHA_INVISIBLE)
                .setDuration(PROGRESS_ANIMATION_DURATION);
    }
}
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/stepperfeedback/feedback/ContentOverlayStepperFeedbackType.java
git commit -m "docs: add license header and documentation to ContentOverlayStepperFeedbackType"
```

---

## Task 8: 更新 DisabledBottomNavigationStepperFeedbackType.java

**Files:**
- Modify: `app/src/main/java/com/example/stepperfeedback/feedback/DisabledBottomNavigationStepperFeedbackType.java`

**Step 1: 添加 License 和注释**

```java
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

import com.example.stepperfeedback.StepperLayout;

/**
 * Feedback stepper type which disables the buttons in the bottom navigation when an operation is in progress.
 */
public class DisabledBottomNavigationStepperFeedbackType implements StepperFeedbackType {

    @NonNull
    private StepperLayout mStepperLayout;

    public DisabledBottomNavigationStepperFeedbackType(@NonNull StepperLayout stepperLayout) {
        mStepperLayout = stepperLayout;
    }

    @Override
    public void showProgress(@NonNull String progressMessage) {
        setButtonsEnabled(false);
    }

    @Override
    public void hideProgress() {
        setButtonsEnabled(true);
    }

    private void setButtonsEnabled(boolean enabled) {
        mStepperLayout.setNextButtonEnabled(enabled);
        mStepperLayout.setCompleteButtonEnabled(enabled);
        mStepperLayout.setBackButtonEnabled(enabled);
    }

}
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/stepperfeedback/feedback/DisabledBottomNavigationStepperFeedbackType.java
git commit -m "docs: add license header and documentation to DisabledBottomNavigationStepperFeedbackType"
```

---

## Task 9: 更新 DisabledContentInteractionStepperFeedbackType.java

**Files:**
- Modify: `app/src/main/java/com/example/stepperfeedback/feedback/DisabledContentInteractionStepperFeedbackType.java`

**Step 1: 添加 License 和注释**

```java
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

import com.example.stepperfeedback.R;
import com.example.stepperfeedback.StepperLayout;
import com.example.stepperfeedback.widget.StepViewPager;

/**
 * Feedback stepper type which intercepts touch events on the steps' content and ignores them.
 */
public class DisabledContentInteractionStepperFeedbackType implements StepperFeedbackType {

    @NonNull
    private final StepViewPager mStepPager;

    public DisabledContentInteractionStepperFeedbackType(@NonNull StepperLayout stepperLayout) {
        mStepPager = (StepViewPager) stepperLayout.findViewById(R.id.ms_stepPager);
    }

    @Override
    public void showProgress(@NonNull String progressMessage) {
        setContentInteractionEnabled(false);
    }

    @Override
    public void hideProgress() {
        setContentInteractionEnabled(true);
    }

    private void setContentInteractionEnabled(boolean enabled) {
        mStepPager.setBlockTouchEventsFromChildrenEnabled(!enabled);
    }

}
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/stepperfeedback/feedback/DisabledContentInteractionStepperFeedbackType.java
git commit -m "docs: add license header and documentation to DisabledContentInteractionStepperFeedbackType"
```

---

## Task 10: 更新 AnimationUtil.java

**Files:**
- Modify: `app/src/main/java/com/example/stepperfeedback/util/AnimationUtil.java`

**Step 1: 添加 License 和注释**

```java
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
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/stepperfeedback/util/AnimationUtil.java
git commit -m "docs: add license header and documentation to AnimationUtil"
```

---

## Task 11: 更新 StepViewPager.java

**Files:**
- Modify: `app/src/main/java/com/example/stepperfeedback/widget/StepViewPager.java`

**Step 1: 添加 License 和注释**

```java
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
import android.view.MotionEvent;

import androidx.viewpager.widget.ViewPager;

/**
 * A non-swipeable viewpager with RTL support.<br>
 * <a href="http://stackoverflow.com/questions/9650265/how-do-disable-paging-by-swiping-with-finger-in-viewpager-but-still-be-able-to-s">Source</a>
 */
public class StepViewPager extends ViewPager {

    private boolean mBlockTouchEventsFromChildrenEnabled;

    public StepViewPager(Context context) {
        this(context, null);
    }

    public StepViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // Never allow swiping to switch between pages
        return mBlockTouchEventsFromChildrenEnabled;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Never allow swiping to switch between pages
        return mBlockTouchEventsFromChildrenEnabled;
    }

    /**
     * @param blockTouchEventsFromChildrenEnabled true if children should not receive touch events
     */
    public void setBlockTouchEventsFromChildrenEnabled(boolean blockTouchEventsFromChildrenEnabled) {
        this.mBlockTouchEventsFromChildrenEnabled = blockTouchEventsFromChildrenEnabled;
    }
}
```

**Step 2: 验证编译**

Run: `./gradlew.bat compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/stepperfeedback/widget/StepViewPager.java
git commit -m "docs: add license header and documentation to StepViewPager"
```

---

## Task 12: 完整构建测试

**Step 1: 清理并完整构建**

Run: `./gradlew.bat clean assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: 验证 APK 生成**

Run: `ls -la app/build/outputs/apk/debug/`
Expected: app-debug.apk 文件存在

**Step 3: 最终 Commit**

```bash
git add .
git commit -m "chore: align stepper feedback implementation with android-material-stepper

- Add Apache 2.0 license headers to all feedback classes
- Add comprehensive JavaDoc documentation
- Standardize code style and formatting
- Update AnimationUtil and StepViewPager"
```

---

## 验证清单

| 检查项 | 状态 |
|--------|------|
| 所有 Java 文件有 License 头 | ☐ |
| 所有类有 JavaDoc 注释 | ☐ |
| 编译成功 (assembleDebug) | ☐ |
| APK 生成正常 | ☐ |
| 代码风格一致 | ☐ |
