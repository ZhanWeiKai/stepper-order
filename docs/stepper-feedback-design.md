# Stepper Feedback 机制设计文档

> 基于 android-material-stepper 项目学习

## 1. 概述

Stepper Feedback 机制用于在步骤执行异步操作时提供用户反馈，防止用户重复操作并显示进度状态。

## 2. 核心设计模式

### 2.1 位运算标志 (Bitwise Flags)

使用位运算组合多种反馈类型：

```java
public interface StepperFeedbackType {

    // 位运算常量
    int NONE = 1;
    int TABS = 1 << 1;
    int CONTENT_PROGRESS = 1 << 2;
    int DISABLED_BOTTOM_NAVIGATION = 1 << 3;
    int DISABLED_CONTENT_INTERACTION = 1 << 4;
    int CONTENT_FADE = 1 << 5;
    int CONTENT_OVERLAY = 1 << 6;

    // 核心方法
    void showProgress(@NonNull String progressMessage);
    void hideProgress();
}
```

**组合示例：**
```java
int feedbackMask = TABS | CONTENT_PROGRESS | CONTENT_FADE;
stepperLayout.setFeedbackType(feedbackMask);
```

### 2.2 组合模式 (Composite Pattern)

`StepperFeedbackTypeComposite` 管理多个反馈类型实例：

```java
public class StepperFeedbackTypeComposite implements StepperFeedbackType {

    @NonNull
    private final List<StepperFeedbackType> feedbackTypes = new ArrayList<>();

    public void addFeedback(@NonNull StepperFeedbackType feedbackType) {
        feedbackTypes.add(feedbackType);
    }

    @Override
    public void showProgress(@NonNull String progressMessage) {
        for (StepperFeedbackType feedbackType : feedbackTypes) {
            feedbackType.showProgress(progressMessage);
        }
    }

    @Override
    public void hideProgress() {
        for (StepperFeedbackType feedbackType : feedbackTypes) {
            feedbackType.hideProgress();
        }
    }
}
```

### 2.3 工厂模式 (Factory Pattern)

`StepperFeedbackTypeFactory` 根据位掩码创建反馈实例：

```java
public class StepperFeedbackTypeFactory {

    @NonNull
    public static StepperFeedbackType createType(
            int feedbackMask,
            @NonNull StepperLayout stepperLayout) {

        if (feedbackMask == StepperFeedbackType.NONE) {
            return new NoneStepperFeedbackType();
        }

        StepperFeedbackTypeComposite composite = new StepperFeedbackTypeComposite();

        if ((feedbackMask & TABS) != 0) {
            composite.addFeedback(new TabsStepperFeedbackType(stepperLayout));
        }
        if ((feedbackMask & CONTENT_PROGRESS) != 0) {
            composite.addFeedback(new ContentProgressStepperFeedbackType(stepperLayout));
        }
        if ((feedbackMask & DISABLED_BOTTOM_NAVIGATION) != 0) {
            composite.addFeedback(new DisabledBottomNavigationStepperFeedbackType(stepperLayout));
        }
        if ((feedbackMask & DISABLED_CONTENT_INTERACTION) != 0) {
            composite.addFeedback(new DisabledContentInteractionStepperFeedbackType(stepperLayout));
        }
        if ((feedbackMask & CONTENT_FADE) != 0) {
            composite.addFeedback(new ContentFadeStepperFeedbackType(stepperLayout));
        }
        if ((feedbackMask & CONTENT_OVERLAY) != 0) {
            composite.addFeedback(new ContentOverlayStepperFeedbackType(stepperLayout));
        }

        return composite;
    }
}
```

## 3. 反馈类型详解

### 3.1 NoneStepperFeedbackType

空实现，不做任何操作。

```java
public class NoneStepperFeedbackType implements StepperFeedbackType {
    @Override
    public void showProgress(@NonNull String progressMessage) {}

    @Override
    public void hideProgress() {}
}
```

### 3.2 TabsStepperFeedbackType

隐藏 Tabs 容器，显示进度消息。

```java
public class TabsStepperFeedbackType implements StepperFeedbackType {

    @NonNull private final TabsContainer mTabsContainer;
    @NonNull private final TextView mProgressMessage;

    @Override
    public void showProgress(@NonNull String progressMessage) {
        mProgressMessage.setText(progressMessage);
        mProgressMessage.setVisibility(View.VISIBLE);
        mTabsContainer.setVisibility(View.GONE);
    }

    @Override
    public void hideProgress() {
        mProgressMessage.setVisibility(View.GONE);
        mTabsContainer.setVisibility(View.VISIBLE);
    }
}
```

### 3.3 ContentProgressStepperFeedbackType

在内容区域显示进度条。

```java
public class ContentProgressStepperFeedbackType implements StepperFeedbackType {

    @NonNull private final ProgressBar mProgressBar;

    @Override
    public void showProgress(@NonNull String progressMessage) {
        mProgressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideProgress() {
        mProgressBar.setVisibility(View.GONE);
    }
}
```

### 3.4 DisabledBottomNavigationStepperFeedbackType

禁用底部导航按钮。

```java
public class DisabledBottomNavigationStepperFeedbackType implements StepperFeedbackType {

    @NonNull private final StepperLayout mStepperLayout;

    @Override
    public void showProgress(@NonNull String progressMessage) {
        mStepperLayout.setNextButtonEnabled(false);
        mStepperLayout.setCompleteButtonEnabled(false);
        mStepperLayout.setBackButtonEnabled(false);
    }

    @Override
    public void hideProgress() {
        mStepperLayout.setNextButtonEnabled(true);
        mStepperLayout.setCompleteButtonEnabled(true);
        mStepperLayout.setBackButtonEnabled(true);
    }
}
```

### 3.5 DisabledContentInteractionStepperFeedbackType

禁用内容区域触摸事件。

```java
public class DisabledContentInteractionStepperFeedbackType implements StepperFeedbackType {

    @NonNull private final StepViewPager mPager;

    @Override
    public void showProgress(@NonNull String progressMessage) {
        mPager.setBlockTouchEventsFromChildrenEnabled(true);
    }

    @Override
    public void hideProgress() {
        mPager.setBlockTouchEventsFromChildrenEnabled(false);
    }
}
```

**StepViewPager 实现：**

```java
public class StepViewPager extends ViewPager {

    private boolean mBlockTouchEvents;

    public void setBlockTouchEventsFromChildrenEnabled(boolean enabled) {
        mBlockTouchEvents = enabled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return mBlockTouchEvents || super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mBlockTouchEvents || super.onTouchEvent(ev);
    }
}
```

### 3.6 ContentFadeStepperFeedbackType

内容区域淡出效果。

```java
public class ContentFadeStepperFeedbackType implements StepperFeedbackType {

    private static final int FADE_DURATION = 200;

    @NonNull private final ViewPager mPager;
    private final float mContentFadeAlpha;

    @Override
    public void showProgress(@NonNull String progressMessage) {
        mPager.animate()
                .alpha(mContentFadeAlpha)
                .setDuration(FADE_DURATION);
    }

    @Override
    public void hideProgress() {
        mPager.animate()
                .alpha(1.0f)
                .setDuration(FADE_DURATION);
    }
}
```

### 3.7 ContentOverlayStepperFeedbackType

在内容区域显示遮罩层。

```java
public class ContentOverlayStepperFeedbackType implements StepperFeedbackType {

    private static final int FADE_DURATION = 200;

    @NonNull private final View mOverlayView;

    @Override
    public void showProgress(@NonNull String progressMessage) {
        mOverlayView.setVisibility(View.VISIBLE);
        mOverlayView.animate()
                .alpha(1.0f)
                .setDuration(FADE_DURATION);
    }

    @Override
    public void hideProgress() {
        mOverlayView.animate()
                .alpha(0.0f)
                .setDuration(FADE_DURATION)
                .withEndAction(() -> mOverlayView.setVisibility(View.GONE));
    }
}
```

## 4. 动画常量

```java
public final class AnimationUtil {
    public static final float ALPHA_OPAQUE = 1.0f;
    public static final float ALPHA_INVISIBLE = 0.0f;
    public static final float ALPHA_HALF = 0.5f;
    private static final int DEFAULT_DURATION = 300;
}
```

**反馈动画时长：** 200ms

## 5. StepperLayout 集成

```java
public class StepperLayout extends LinearLayout {

    private int mFeedbackTypeMask = StepperFeedbackType.NONE;
    private StepperFeedbackType mStepperFeedbackType;
    private boolean mInProgress = false;

    public void setFeedbackType(int feedbackTypeMask) {
        mFeedbackTypeMask = feedbackTypeMask;
        mStepperFeedbackType = StepperFeedbackTypeFactory.createType(feedbackTypeMask, this);
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
}
```

## 6. XML 属性配置

```xml
<declare-styleable name="StepperLayout">
    <attr name="ms_stepperFeedbackType" format="enum">
        <flag name="none" value="1" />
        <flag name="tabs" value="2" />
        <flag name="content_progress" value="4" />
        <flag name="disabled_bottom_navigation" value="8" />
        <flag name="disabled_content_interaction" value="16" />
        <flag name="content_fade" value="32" />
        <flag name="content_overlay" value="64" />
    </attr>
    <attr name="ms_stepperFeedback_contentFadeAlpha" format="float" />
    <attr name="ms_stepperFeedback_contentOverlayBackground" format="reference" />
</declare-styleable>
```

## 7. 使用示例

### XML 配置

```xml
<com.example.stepperfeedback.StepperLayout
    android:id="@+id/stepper_layout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:ms_stepperFeedbackType="tabs|content_progress|content_fade|disabled_bottom_navigation" />
```

### 代码调用

```java
// 设置反馈类型
int feedbackMask = StepperFeedbackType.TABS
    | StepperFeedbackType.CONTENT_PROGRESS
    | StepperFeedbackType.CONTENT_FADE;
stepperLayout.setFeedbackType(feedbackMask);

// 显示进度
stepperLayout.showProgress("正在处理...");

// 异步操作完成后隐藏进度
handler.postDelayed(() -> {
    stepperLayout.hideProgress();
}, 2000);
```

## 8. 架构图

```
┌─────────────────────────────────────────────────────────┐
│                    StepperLayout                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │              showProgress(message)               │    │
│  │              hideProgress()                      │    │
│  └─────────────────────────────────────────────────┘    │
│                          │                               │
│                          ▼                               │
│  ┌─────────────────────────────────────────────────┐    │
│  │        StepperFeedbackTypeFactory               │    │
│  │              createType(mask)                    │    │
│  └─────────────────────────────────────────────────┘    │
│                          │                               │
│                          ▼                               │
│  ┌─────────────────────────────────────────────────┐    │
│  │      StepperFeedbackTypeComposite               │    │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐           │    │
│  │  │  Tabs   │ │ Fade    │ │Disable  │  ...      │    │
│  │  │Feedback │ │Feedback │ │Nav Fb   │           │    │
│  │  └─────────┘ └─────────┘ └─────────┘           │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

## 9. 关键实现细节

1. **状态保护**: 使用 `mInProgress` 标志防止重复调用
2. **动画时长统一**: 所有反馈动画使用 200ms
3. **空实现模式**: `NoneStepperFeedbackType` 提供默认空操作
4. **触摸拦截**: `StepViewPager` 通过标志控制触摸事件拦截
5. **资源释放**: 动画结束回调中处理 View 可见性

## 10. 与当前 stepper-feedback-demo 的差异

| 项目 | android-material-stepper | stepper-feedback-demo |
|------|--------------------------|----------------------|
| NoneStepperFeedbackType | ✅ 有实现 | ❌ 缺失 |
| 动画时长 | 200ms | 300ms |
| 触摸拦截逻辑 | 更完善 | 需改进 |
| 状态管理 | mInProgress 保护 | 有，但需验证 |
| 代码注释 | 丰富 | 较少 |
