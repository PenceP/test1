Project Goal: Modernize an Android TV app UI (using Leanback/Compose) to a "Next-Gen" aesthetic (2026 era). Interaction Model: D-Pad Navigation. No on-screen CTA buttons in Hero (Click = Details; Long Press = Context Menu).

1. Global Layout & Background (The "Ambient" Effect)
Dynamic Background:

Implement a Palette library extraction on the currently focused item's poster.

Extract the vibrantSwatch or darkVibrantSwatch.

Requirement: Create a large, diffused mesh gradient or radial gradient overlay behind the Hero section that transitions smoothly when the user scrolls to a new movie.

Visual: The background should not be black; it should be a dark, deep version of the movie's dominant color (e.g., Deep Emerald for The Grinch, Deep Crimson for The Roses).

Z-Layering: ensure the background image has a gradient overlay (black to transparent vertical) to ensure text readability at the top left.

2. The Navigation Dock (Left Sidebar)
Style: Floating "Pill" or "Glass" Dock.

Modification: Instead of the full-height solid sidebar standard in Leanback:

Create a floating vertical container with rounded corners (cornerRadius = 24dp).

Background: Semi-transparent surface (#1AFFFFFF) with a background blur (Frosted Glass / Glassmorphism) if supported by the target API, otherwise use a high-alpha dark grey.

Interaction:

Idle: Icons are grey/white (alpha = 0.6).

Active/Focus: Icon scales up (1.2x), turns pure white, and gains a subtle glow shadow.

This is some css for a button and window, but i like it and we should use the blur radius and other values as an example for the popout nav bar:
```
.glass-card {
  width: 240px;
  height: 360px;
  background: rgba(255, 255, 255, 0.14);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow:
    0 8px 32px rgba(0, 0, 0, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.5),
    inset 0 -1px 0 rgba(255, 255, 255, 0.1),
    inset 0 0 14px 7px rgba(255, 255, 255, 0.7);
  position: relative;
  overflow: hidden;
}

.glass-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.8),
    transparent
  );
}

.glass-card::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 1px;
  height: 100%;
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.8),
    transparent,
    rgba(255, 255, 255, 0.3)
  );
}
```

3. The Hero Section (Top Left Content)
Typography Hierarchy:

Title: Increase font size drastically. Use a bold, custom font.

Description: Increase line-height (1.4) for readability. Limit to 3 lines with an ellipsis (...) overflow.

Metadata Row (The "Match" System):

Logic: Remove individual provider logos (IMDb, RT).

New Element: "Match Score."

Visual: A bold percentage text (e.g., "98% Match") in a distinct color (Neon Green or Brand Primary) placed first in the metadata row.

Separators: Use bullet points (•) or vertical bars (|) with consistent padding between Year, Rating, and Duration.

Cast List:

Constraint: Single line only.

Format: "Starring: [Actor 1], [Actor 2], [Actor 3]"

Overflow: If the list is too long, truncate with "..." or fade out.

Genre Tags:

Style: Remove the heavy pill borders. Use text-only tags with a very subtle background (Surface color with 10% opacity) and rounded corners (4dp).

4. Content Rows (The "Bento" Grid)
Card Presenters (General):

Focus State: When a card is focused:

Scale Up: 1.1x or 1.15x.

Border: Add a focused border stroke (2dp, pure white or brand color).

Elevation: Increase shadow intensity to pop the card off the background.

Aspect Ratios (Mixed Rows):

Standard Rows (Trending): Use Vertical Portrait (2:3 aspect ratio).

"Continue Watching" Row: Use Horizontal Landscape (16:9 aspect ratio) to differentiate it visually.

Transitions:

Ensure SharedElementTransition is enabled so when a user clicks a card, the poster "flies" into the Details screen.

5. Interaction & Feedback
Click Action: Navigate to DetailsFragment / Details Screen.

Long Press Action: Trigger a Dialog or Context Menu (options: "Play Immediately", "Mark Watched", "Add to List").

Sound: Add subtle "tick" sounds on traversal to enhance the "tactile" feel of the UI.