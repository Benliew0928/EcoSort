# Responsive Design System for Android

This guide explains how to implement Bootstrap-like responsive design in Android, specifically for phone devices.

## Overview

The responsive design system provides:
- **Screen size detection** (XS, SM, MD, LG)
- **Responsive dimensions** that scale based on screen size
- **Responsive layouts** using ConstraintLayout and flexible components
- **Responsive utilities** for dynamic adjustments
- **Responsive layout managers** for RecyclerView

## Screen Size Categories

Similar to Bootstrap breakpoints:

| Screen Size | Width Range | Description |
|-------------|-------------|-------------|
| XS | ≤ 320dp | Extra small phones |
| SM | 321-360dp | Small phones |
| MD | 361-400dp | Medium phones |
| LG | > 400dp | Large phones |

## Responsive Dimensions

### Dimension Resources

The system uses resource qualifiers to provide different dimensions for different screen sizes:

- `values/dimens.xml` - Base dimensions
- `values-sw320dp/dimens.xml` - Small phones
- `values-sw360dp/dimens.xml` - Medium phones  
- `values-sw400dp/dimens.xml` - Large phones

### Dimension Categories

```xml
<!-- Spacing System (8dp base unit) -->
<dimen name="spacing_xs">4dp</dimen>    <!-- 0.5x base -->
<dimen name="spacing_sm">8dp</dimen>    <!-- 1x base -->
<dimen name="spacing_md">16dp</dimen>   <!-- 2x base -->
<dimen name="spacing_lg">24dp</dimen>   <!-- 3x base -->
<dimen name="spacing_xl">32dp</dimen>   <!-- 4x base -->
<dimen name="spacing_xxl">40dp</dimen>  <!-- 5x base -->

<!-- Text Sizes -->
<dimen name="text_xs">10sp</dimen>      <!-- Caption -->
<dimen name="text_sm">12sp</dimen>      <!-- Small text -->
<dimen name="text_md">14sp</dimen>      <!-- Body text -->
<dimen name="text_lg">16sp</dimen>      <!-- Large body -->
<dimen name="text_xl">18sp</dimen>      <!-- Subheading -->
<dimen name="text_xxl">20sp</dimen>     <!-- Heading -->
<dimen name="text_xxxl">24sp</dimen>    <!-- Large heading -->
<dimen name="text_display">28sp</dimen> <!-- Display text -->

<!-- Button Heights -->
<dimen name="button_height_sm">36dp</dimen>
<dimen name="button_height_md">44dp</dimen>
<dimen name="button_height_lg">52dp</dimen>
<dimen name="button_height_xl">60dp</dimen>

<!-- Icon Sizes -->
<dimen name="icon_xs">16dp</dimen>
<dimen name="icon_sm">20dp</dimen>
<dimen name="icon_md">24dp</dimen>
<dimen name="icon_lg">32dp</dimen>
<dimen name="icon_xl">40dp</dimen>
<dimen name="icon_xxl">48dp</dimen>
```

## Responsive Styles

### Button Styles

```xml
<!-- Responsive Button -->
<style name="ButtonResponsive" parent="Widget.Material3.Button">
    <item name="android:background">@drawable/button_primary</item>
    <item name="android:textColor">@color/white</item>
    <item name="android:textSize">@dimen/text_md</item>
    <item name="android:textStyle">bold</item>
    <item name="android:fontFamily">@string/font_family_primary</item>
    <item name="android:padding">@dimen/padding_md</item>
    <item name="android:minHeight">@dimen/button_height_md</item>
    <item name="android:elevation">4dp</item>
</style>
```

### Text Styles

```xml
<!-- Responsive Heading -->
<style name="TextResponsiveHeading1">
    <item name="android:textSize">@dimen/text_display</item>
    <item name="android:textStyle">bold</item>
    <item name="android:textColor">@color/text_primary</item>
    <item name="android:fontFamily">@string/font_family_heading</item>
    <item name="android:lineSpacingExtra">@dimen/spacing_xs</item>
</style>
```

### Card Styles

```xml
<!-- Responsive Card -->
<style name="CardResponsive">
    <item name="android:background">@drawable/card_modern</item>
    <item name="android:elevation">4dp</item>
    <item name="android:layout_margin">@dimen/margin_lg</item>
    <item name="android:padding">@dimen/padding_lg</item>
</style>
```

## Responsive Utils

### Screen Size Detection

```kotlin
// Get current screen size
val screenSize = ResponsiveUtils.getScreenSize(context)
when (screenSize) {
    ResponsiveUtils.ScreenSize.XS -> // Small phone
    ResponsiveUtils.ScreenSize.SM -> // Medium phone
    ResponsiveUtils.ScreenSize.MD -> // Large phone
    ResponsiveUtils.ScreenSize.LG -> // Extra large phone
}

// Check orientation
val isLandscape = ResponsiveUtils.isLandscape(context)
```

### Dynamic Adjustments

```kotlin
// Get responsive padding
val padding = ResponsiveUtils.getResponsivePadding(context, 16)

// Get responsive text size
val textSize = ResponsiveUtils.getResponsiveTextSize(context, 14f)

// Get responsive button height
val buttonHeight = ResponsiveUtils.getResponsiveButtonHeight(context, 44)

// Get responsive icon size
val iconSize = ResponsiveUtils.getResponsiveIconSize(context, 24)

// Get responsive card width
val cardWidth = ResponsiveUtils.getResponsiveCardWidth(context)
```

### Apply Responsive Layout

```kotlin
// Apply responsive layout to a view
ResponsiveUtils.applyResponsiveLayout(view, context)

// Apply responsive margins
ResponsiveUtils.applyResponsiveMargins(view, context)

// Apply responsive padding
ResponsiveUtils.applyResponsivePadding(view, context)
```

## Responsive Layout Managers

### RecyclerView Setup

```kotlin
// Use responsive layout manager
recyclerView.setResponsiveLayoutManager(
    context = this,
    baseColumns = 1,
    useStaggered = false
)

// For grid layout
recyclerView.setResponsiveLayoutManager(
    context = this,
    baseColumns = 2,
    useStaggered = true
)
```

### Grid Configuration

```kotlin
// Configure responsive grid
ResponsiveUtils.configureResponsiveGrid(
    container = linearLayout,
    context = this,
    itemCount = items.size,
    itemView = { index -> createItemView(items[index]) }
)
```

## Layout Examples

### Responsive Main Activity

```xml
<androidx.constraintlayout.widget.ConstraintLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- Header with responsive padding -->
    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:padding="@dimen/padding_lg"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <!-- Responsive text -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Welcome"
            android:textSize="@dimen/text_xxl"
            android:textStyle="bold"
            android:fontFamily="@string/font_family_heading" />

    </LinearLayout>

    <!-- Responsive buttons -->
    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        app:layout_constraintTop_toBottomOf="@+id/header"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <Button
            android:layout_width="0dp"
            android:layout_height="@dimen/button_height_md"
            android:layout_weight="1"
            android:text="Action 1"
            style="@style/ButtonResponsive" />

        <Button
            android:layout_width="0dp"
            android:layout_height="@dimen/button_height_md"
            android:layout_weight="1"
            android:text="Action 2"
            style="@style/ButtonResponsive" />

    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>
```

### Responsive Card Item

```xml
<androidx.cardview.widget.CardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="@dimen/margin_sm"
    app:cardCornerRadius="@dimen/card_radius_lg">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="@dimen/padding_md">

        <!-- Responsive content -->
        <TextView
            android:id="@+id/title"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:text="Card Title"
            android:textSize="@dimen/text_xl"
            android:textStyle="bold"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent" />

        <TextView
            android:id="@+id/content"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:text="Card content"
            android:textSize="@dimen/text_md"
            android:layout_marginTop="@dimen/spacing_sm"
            app:layout_constraintTop_toBottomOf="@+id/title"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent" />

    </androidx.constraintlayout.widget.ConstraintLayout>

</androidx.cardview.widget.CardView>
```

## Best Practices

### 1. Use Resource Qualifiers

Always use resource qualifiers for different screen sizes:

```
res/
├── values/
│   └── dimens.xml          # Base dimensions
├── values-sw320dp/
│   └── dimens.xml          # Small phones
├── values-sw360dp/
│   └── dimens.xml          # Medium phones
└── values-sw400dp/
    └── dimens.xml          # Large phones
```

### 2. Use ConstraintLayout

Prefer ConstraintLayout for responsive layouts:

```xml
<androidx.constraintlayout.widget.ConstraintLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- Use constraints for responsive positioning -->
    <View
        android:id="@+id/view1"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

### 3. Use Responsive Dimensions

Always use responsive dimensions in layouts:

```xml
<!-- Good -->
<TextView
    android:textSize="@dimen/text_md"
    android:padding="@dimen/padding_md" />

<!-- Avoid -->
<TextView
    android:textSize="14sp"
    android:padding="16dp" />
```

### 4. Use Responsive Styles

Apply responsive styles to components:

```xml
<!-- Good -->
<Button
    style="@style/ButtonResponsive"
    android:text="Click Me" />

<!-- Avoid -->
<Button
    android:textSize="16sp"
    android:padding="16dp"
    android:text="Click Me" />
```

### 5. Test on Different Screen Sizes

Test your app on different screen sizes:

- **Small phones** (320dp): Compact layout
- **Medium phones** (360dp): Standard layout
- **Large phones** (400dp+): Generous layout

## Implementation Checklist

- [ ] Create responsive dimension resources
- [ ] Create responsive styles
- [ ] Use ConstraintLayout for layouts
- [ ] Apply responsive dimensions
- [ ] Use responsive layout managers
- [ ] Test on different screen sizes
- [ ] Handle orientation changes
- [ ] Use responsive utilities for dynamic adjustments

## Example Implementation

See the following files for complete examples:

- `activity_main_responsive.xml` - Responsive main activity
- `item_community_post_responsive.xml` - Responsive card item
- `ResponsiveUtils.kt` - Responsive utilities
- `ResponsiveLayoutManager.kt` - Responsive layout managers
- `MainActivity.kt` - Usage examples
- `CommunityFeedActivity.kt` - RecyclerView setup

This responsive design system provides a Bootstrap-like experience for Android, ensuring your app looks great on all phone screen sizes.
