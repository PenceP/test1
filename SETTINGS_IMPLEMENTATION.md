# Android TV Glassmorphism Settings Implementation

## Overview

This is a custom Android TV settings implementation that replicates a glassmorphism UI design from React, featuring:

- **Floating glass panels** with blur effects (Android 12+)
- **Yellow accent colors** (#FACC15) for focus states
- **Cinematic dark background** with gradient overlays
- **D-pad navigation** optimized for TV
- **Custom focus animations** (scale, elevation, glow)
- **NO standard PreferenceFragment** - fully custom using VerticalGridView

## Architecture

```
com.test1.tv.ui.settings/
├── SettingsActivity.kt           # Main activity with sidebar & submenu
├── adapter/
│   ├── SidebarAdapter.kt          # Sidebar icon navigation
│   ├── SubmenuAdapter.kt          # Left panel submenu items
│   └── SettingsAdapter.kt         # Settings items (toggle, cards, etc.)
├── fragments/
│   └── AccountsFragment.kt        # Example accounts screen
├── model/
│   ├── SettingsItem.kt            # Sealed class for setting types
│   └── SubmenuItem.kt             # Submenu data model
└── util/
    └── FocusUtils.kt              # Focus animation extensions
```

## Key Components

### 1. Glass Effect Drawables

**bg_glass_panel.xml** - Base glass panel:
- 10% white background (#1AFFFFFF)
- 24dp corner radius
- 1dp subtle border

**bg_glass_focused.xml** - Focused state:
- Yellow border (#FACC15)
- Glow layer with gradient
- Used when items gain focus

### 2. Focus Animations

```kotlin
// Apply to any view
view.setupFocusEffect(scale = 1.1f, applyBackgroundChange = true)

// Card-specific focus
view.setupCardFocusEffect() // scale = 1.05f

// Sidebar-specific focus
view.setupSidebarFocusEffect() // scale = 1.15f
```

### 3. Settings Item Types

```kotlin
sealed class SettingsItem {
    data class Toggle       // On/off switches
    data class AccountCard  // Service account cards
    data class Select       // Dropdown selections
    data class Slider       // Range sliders
    data class Input        // Text input fields
}
```

### 4. Blur Effect (Android 12+)

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val blurEffect = RenderEffect.createBlurEffect(
        25f, 25f, Shader.TileMode.CLAMP
    )
    blurContainer.setRenderEffect(blurEffect)
}
```

For Android 11 and below, the UI falls back to semi-transparent backgrounds.

## How to Use

### 1. Launch the Settings Activity

```kotlin
val intent = Intent(context, SettingsActivity::class.java)
startActivity(intent)
```

### 2. Add New Submenu Screens

1. Create a new Fragment:
```kotlin
class MyNewFragment : Fragment() {
    override fun onCreateView(...): View? {
        return inflater.inflate(R.layout.fragment_my_new, container, false)
    }
}
```

2. Add to `SettingsActivity.kt`:
```kotlin
private val submenuItems = listOf(
    // ... existing items
    SubmenuItem("mynew", "My Feature", R.drawable.ic_custom, "Description")
)

// In loadFragment():
"mynew" -> MyNewFragment()
```

### 3. Create Custom Settings Items

```kotlin
// In your fragment
private fun buildSettingsItems(): List<SettingsItem> {
    return listOf(
        // Toggle example
        SettingsItem.Toggle(
            id = "autoplay",
            label = "Autoplay Next Episode",
            isEnabled = viewModel.autoplayEnabled,
            onToggle = { enabled -> viewModel.setAutoplay(enabled) }
        ),

        // Account card example
        SettingsItem.AccountCard(
            id = "trakt",
            serviceName = "Trakt.tv",
            serviceDescription = "Sync watch history",
            iconText = "T",
            iconBackgroundColor = Color.parseColor("#DC2626"),
            isConnected = viewModel.traktConnected,
            userName = viewModel.traktUser,
            additionalInfo = viewModel.traktScrobbles,
            onAction = { action -> handleTraktAction(action) }
        )
    )
}
```

## Customization

### Colors

All colors follow the React design:

- **Yellow accent**: `#FACC15`
- **White glass**: `#1AFFFFFF` (10% alpha)
- **Dark overlay**: `#CC000000` (80% alpha)
- **Zinc-400**: `#A1A1AA` (secondary text)
- **Zinc-500**: `#71717A` (inactive icons)

### Focus Scale Values

- **Sidebar icons**: 1.15x
- **Cards**: 1.05x
- **Buttons/Toggles**: 1.1x

### Blur Intensity

Adjust in `SettingsActivity.kt`:
```kotlin
val blurEffect = RenderEffect.createBlurEffect(
    25f, 25f, // Increase for more blur
    Shader.TileMode.CLAMP
)
```

## Example Screens

### Accounts Screen (Implemented)
- Trakt.tv account card with OAuth flow
- Premiumize account card with API key
- Connected/Disconnected states
- Sync and logout actions

### Additional Screens (To Implement)
- **Resolving**: Link resolution settings, auto-select, min links
- **Filtering**: Quality filters, bitrate slider, exclude phrases
- **Playback**: Autoplay toggle, player engine selection
- **Display**: UI customization options
- **About**: App info, version, credits

## D-pad Navigation

The UI is fully navigable with TV remote:

1. **Sidebar** → Navigate between app sections
2. **Submenu** → Select settings category
3. **Content** → Interact with settings items

Focus automatically moves between panels using `setOnFocusChangeListener`.

## Android Manifest

Add to `AndroidManifest.xml`:

```xml
<activity
    android:name=".ui.settings.SettingsActivity"
    android:theme="@style/Theme.Test1.NoActionBar"
    android:exported="false" />
```

## Dependencies Required

The implementation uses:
- AndroidX ConstraintLayout
- Leanback Library (VerticalGridView)
- CardView
- Fragment
- Material Components (for elevation)

All should already be in your `build.gradle`.

## Future Enhancements

1. **ViewModel integration** - Replace fragment state with proper ViewModel
2. **Settings persistence** - Save settings to DataStore/SharedPreferences
3. **API integration** - Connect to real Trakt/Premiumize OAuth
4. **Animations** - Add slide-in/slide-out for submenu transitions
5. **Testing** - Add UI tests for navigation and interactions

## Troubleshooting

**Blur not working?**
- Ensure device is Android 12+ (API 31+)
- Check hardware acceleration is enabled
- Fallback is automatic (semi-transparent background)

**Focus not highlighting?**
- Verify `android:focusable="true"` on interactive views
- Check selector drawables are properly applied
- Ensure `setupFocusEffect()` is called

**Layout not responsive?**
- Test on different TV screen sizes
- Adjust margin/padding values in layouts
- Use `0dp` with `layout_weight` for flexible widths

## Credits

Based on the React glassmorphism design for PLUR1BUS TV app.
Implemented for Android TV with custom Leanback components.
