# Step 2 Box Placement Animation Design

## Overview

A 2D animation for Step 2 that shows a box being placed on a designated surface, looping automatically to guide users where to place their box.

## Visual Layout

```
┌─────────────────────────┐
│                         │
│         📦              │  ← Box falls from above
│         ↓               │
│                         │
│   ┌───────────────┐     │
│   │               │     │  ← Target placement zone
│   │   ■ ■ ■ ■ ■   │     │    (dashed border)
│   │               │     │
│   └───────────────┘     │
│   ═════════════════     │  ← Surface/Ground (perspective)
│                         │
│  "请将箱子放置到指定位置"  │
└─────────────────────────┘
```

## Components

### 1. Surface (Ground/Platform)
- A rectangular surface with slight perspective effect
- Color: Light gray or subtle gradient
- Draws attention as the "placement area"

### 2. Target Zone
- Dashed border rectangle on the surface
- Indicates where box should be placed
- Semi-transparent fill

### 3. Box
- Simple cube shape (can use emoji 📦 or custom drawn)
- Size: ~80x80 dp
- Has drop shadow when landed

### 4. Instruction Text
- Below the surface
- Text: "请将箱子放置到指定位置"
- Font: 16-18sp, center aligned

## Animation Sequence (Loop)

| Phase | Duration | Description |
|-------|----------|-------------|
| 1. Appear | 200ms | Box fades in at top, slightly above view |
| 2. Fall | 600ms | Box accelerates downward (ease-in) |
| 3. Land | 200ms | Box lands with slight bounce effect |
| 4. Shadow | 100ms | Shadow appears/grows under box |
| 5. Hold | 1000ms | Box stays in place |
| 6. Fade | 400ms | Box and shadow fade out together |
| 7. Reset | 100ms | Ready for next loop |

Total loop: ~2.5 seconds

## Technical Implementation

### Approach
- Custom View with `onDraw()` and `ValueAnimator`
- Use `ArgbEvaluator` for fade effects
- Use `AccelerateInterpolator` for falling motion
- Use `OvershootInterpolator` for bounce effect

### Files to Create/Modify
- `BoxPlacementView.java` - Custom animation view
- `MainActivity.java` - Update Step 2 content view

### Animation Properties
```java
// Box position
float boxY; // Animated from top to landing position

// Box alpha
float boxAlpha; // 0 -> 1 -> 1 -> 0

// Shadow alpha
float shadowAlpha; // 0 -> 1 -> 1 -> 0

// Bounce scale
float bounceScale; // 1.0 -> 1.1 -> 1.0
```

## Color Palette

| Element | Color |
|---------|-------|
| Background | #E8F5E9 (Light Green) |
| Surface | #F5F5F5 with gradient |
| Target Zone Border | #9E9E9E dashed |
| Box | Brown/Orange cube |
| Shadow | #40000000 (semi-transparent black) |
| Text | #424242 (Dark Gray) |

## Success Criteria

- [ ] Animation plays automatically when entering Step 2
- [ ] Animation loops smoothly without jarring transitions
- [ ] Visual clearly communicates "place box here"
- [ ] Performance is smooth (60fps)
- [ ] Works on different screen sizes
