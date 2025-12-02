# Phase 4 Manual Testing Guide: "Layout & Rows" Settings

## Navigation Path
1. Launch the app
2. Navigate to **Settings** (check how you normally access Settings in your app)
3. Look for **"Layout & Rows"** menu item with a **grid icon** (4-square layout icon)
4. Select it to open the row customization screen

---

## 1. Initial Screen Verification

**What You Should See:**
- **Three tabs at the top:** `Home`, `Movies`, `TV Shows`
- **Home tab active by default** (should have full opacity/highlighted)
- **List of 6 rows** for Home screen (using VerticalGridView):
  - Continue Watching
  - Networks
  - Franchises
  - Directors
  - Trending Movies
  - Trending Shows
- **"Reset to Defaults" button** at the bottom
- Each row item displays:
  - Row title (large text)
  - Row type and content type info (smaller text below)
  - "SYSTEM ROW" indicator (if applicable - appears on some rows)
  - Move Up button (↑)
  - Move Down button (↓)
  - Visibility toggle switch (ON/OFF)

**TV Remote Navigation:**
- All controls should be **focusable with D-pad**
- Focus indicators should be **clear and visible**
- Cards should have focus animations

---

## 2. Tab Switching Test

**Test Steps:**
1. Navigate to **Movies** tab
2. Navigate to **TV Shows** tab
3. Navigate back to **Home** tab

**Expected Behavior:**
- **Movies tab:** Shows 3 rows (Trending Movies, Popular Movies, Latest 4K Releases)
- **TV Shows tab:** Shows 2 rows (Trending Shows, Popular Shows)
- **Home tab:** Shows 6 rows again
- **Tab highlighting:** Selected tab should have full opacity (1.0f), others dimmed (0.6f)
- **List updates immediately** when switching tabs (no delay)

---

## 3. Visibility Toggle Test

**Test Steps:**
1. On **Home** tab, toggle OFF the **"Networks"** row
2. Exit Settings and go to **Home screen**
3. Verify "Networks" row is **no longer visible**
4. Return to Settings → Layout & Rows
5. Toggle "Networks" back ON
6. Exit Settings and verify "Networks" row **reappears**

**Expected Behavior:**
- Switch animates smoothly when toggled
- Changes persist immediately (no "Save" button needed)
- Changes are **reactive** - home screen updates when you navigate back
- Toast notifications may appear (optional)

**Additional Tests:**
- Try toggling multiple rows off
- Switch tabs and toggle rows on different screens
- Verify disabled rows are hidden but not deleted from database

---

## 4. Row Reordering Test (Move Up/Down)

**Test Steps:**
1. On **Home** tab, select **"Trending Movies"** (should be position 4)
2. Press **Move Up button** twice
3. Verify "Trending Movies" moves to position 2 (after "Networks")
4. Exit Settings and go to **Home screen**
5. Verify row order changed on actual home screen
6. Close and **restart the app**
7. Verify row order **persisted** after restart

**Expected Behavior:**
- Move Up button **disabled/dimmed** for first row (can't move higher)
- Move Down button **disabled/dimmed** for last row (can't move lower)
- Rows **swap positions** smoothly
- List updates immediately after move
- Changes persist across app restarts
- Changes sync to the actual Home/Movies/TV Shows screens

**Edge Cases to Test:**
- Try moving first row up (button should be disabled)
- Try moving last row down (button should be disabled)
- Move multiple rows in sequence
- Switch tabs after reordering to verify changes saved

---

## 5. Reset to Defaults Test

**Test Steps:**
1. Make several changes:
   - Toggle off 2-3 rows
   - Reorder several rows
2. Press **"Reset to Defaults"** button
3. **Confirmation dialog** should appear with:
   - Title: "Reset to Defaults"
   - Message: "This will reset all row settings for [Screen Name] to their defaults. Continue?"
   - Buttons: "Reset" and "Cancel"
4. Press **"Cancel"** - verify nothing changes
5. Press **"Reset to Defaults"** again
6. Press **"Reset"** - verify all changes reverted
7. Verify a **Toast message** appears: "[Screen Name] rows reset to defaults"

**Expected Behavior:**
- All rows return to **original enabled state** (all enabled)
- All rows return to **original positions**:
  - Home: Continue Watching (0), Networks (1), Franchises (2), Directors (3), Trending Movies (4), Trending Shows (5)
  - Movies: Trending Movies (0), Popular Movies (1), Latest 4K Releases (2)
  - TV Shows: Trending Shows (0), Popular Shows (1)
- Reset applies **only to current tab** (doesn't affect other screens)
- Confirmation dialog prevents accidental resets

---

## 6. TV Remote Focus & Navigation Test

**D-Pad Navigation Tests:**
1. **Up/Down arrows:** Navigate between row items in the list
2. **Left/Right arrows:** Switch between tabs, navigate between buttons within a row item
3. **Enter/Center button:** Activate toggles, press buttons
4. **Back button:** Exit settings (should return to previous screen)

**Focus Behavior to Check:**
- Focus states are **visually clear** (highlighted borders, color changes)
- Focus follows **logical order** (top to bottom, left to right)
- Can reach **all interactive elements** with D-pad
- No "focus traps" (getting stuck on an element)
- Pressing Back exits cleanly

---

## 7. Edge Cases & Error Scenarios

**Test These Edge Cases:**

1. **All rows disabled:**
   - Disable all rows on Home screen
   - Navigate to Home screen
   - What happens? (Empty state? Message?)

2. **Rapid changes:**
   - Quickly toggle multiple switches on/off
   - Rapidly press move buttons
   - App should handle without crashes or freezes

3. **Screen rotation:** (if applicable)
   - Make changes
   - Rotate device/change orientation
   - Verify changes persist

4. **Memory/state preservation:**
   - Make changes
   - Press Home button (minimize app)
   - Return to app
   - Verify settings screen state preserved

5. **Multiple screens:**
   - Customize Home rows
   - Customize Movies rows
   - Customize TV Shows rows
   - Verify each screen maintains its own configuration independently

---

## 8. Visual & UX Checks

**Design Consistency:**
- Colors match existing Settings design
- Fonts/text sizes consistent with other settings
- Spacing and padding looks clean
- Material Design 3 components used correctly
- Blur effect on background (if applicable to Settings)

**Animations:**
- Tab switching feels smooth
- Toggle switches animate properly
- Row reordering doesn't flicker
- Focus animations are smooth

**Accessibility:**
- Text is readable
- Focus indicators are clear
- Buttons have adequate size for TV remote
- No UI clipping or overflow

---

## What Could Go Wrong (Bugs to Watch For)

🚨 **Critical Bugs:**
- App crashes when opening Layout & Rows
- Changes don't persist (reset on app restart)
- Toggle changes don't reflect on actual screens
- Row order changes don't reflect on actual screens

⚠️ **Medium Priority:**
- Focus gets stuck on certain elements
- Move buttons don't disable at list boundaries
- Reset dialog doesn't appear
- Toast notifications don't show
- Can't navigate with D-pad

⚙️ **Minor Issues:**
- Visual glitches (flickering, layout shifts)
- Slow performance with many rows
- Focus indicators unclear
- Text truncation on long row names

---

## Success Criteria

Phase 4 is **fully functional** if:
- ✅ All 3 tabs work correctly
- ✅ Visibility toggles work and persist
- ✅ Row reordering works and persists
- ✅ Changes reflect on actual Home/Movies/TV Shows screens immediately
- ✅ Reset to defaults works correctly
- ✅ All D-pad navigation works smoothly
- ✅ No crashes or errors
- ✅ Changes persist across app restarts
