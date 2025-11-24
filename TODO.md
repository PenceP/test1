## Backend & Data
- **Caching & API orchestration**  
  - After the initial foreground load, trigger smart background fetches that keep Trakt lists and TMDB metadata warm.  
  - Respect a 24h freshness window for lists; skip network calls when cached data meets this SLA.  
  - Introduce pagination (20 items per page). Fetch the next page when focus is within 10 posters of the current page’s end. Cache every page efficiently so subsequent sessions can reuse the data for “free” and only hit the network when the DB misses.

- **Dynamic row configuration**  
  - Move row definitions out of XML/Kotlin into a structured config (YAML). Each entry should include row title, Trakt source link(popular, trending, user list, etc.), orientation (portrait, landscape, square) and optional metadata such as genre tiles with square artwork that goes to another page with the same hero aera and list of media (so each genre item also has a trakt link), and another (level 2 type if thats what we want to call the "nested" pages) type where we can include trakt items Movie Collection & watchlist and TV shows collection and watchlist. Use this config to drive page composition at runtime so new rows/pages can be added without code changes.

## UI & UX
- **Details experience**  
  - Design two full-screen detail layouts (movie/show). Include: hero backdrop, Buttons: [Play, Play Trailer, mark watched, Trakt button (opens modal for add/remove to watchlist & collection if Trakt OAuth), Thumbsup (rate 9 Trakt), Thumbs side (6), Thumbs Down (3) [these ratings tell you to Trakt Oauth if not authorized yet]], actor portraits row (tap to open an actor page with two rows: movies & shows), Similar media (TMDB), Collection section (if applicable).  
  - For TV shows, support season/episode navigation and the same interaction affordances.

- **Settings page**  
  - Build a modern settings screen that groups accounts (Trakt OAuth, Premiumize API key entry, etc.), UI preferences, and other concerns with a TV-friendly layout.

- **Search**  
  - Implement a Trakt-backed search experience that renders three dedicated rows: Movies, Shows, People. Results should update quickly and match the look/feel of the rest of the app.

## Accounts & Integrations
- **Trakt OAuth flows**  
  - Support authenticated actions from the details page (watchlist/collection state) including add/remove toggles based on the user’s current Trakt state.

## Database & Persistence
- Ensure pagination results, row configs, and metadata are cached efficiently. Hitting the network should only happen when the DB lacks the requested slice or when the 24h TTL has expired.


## Modern polish ideas

1. Glassmorphism hero card – wrap the title/metadata stack in a rounded rectangle with blurred backdrop + 60% opacity. It keeps text readable over
    busy art while feeling very “future Netflix.”
2. Dynamic accent color – sample the hero backdrop’s dominant color and apply it to the rating-value text, focus ring, and CTA buttons for cohesive
    branding that changes with each show.
3. Hero controls bar – add slim Play / Watchlist buttons under the rating logos with icon+label chips (think Netflix’s “Play / More Info”), giving the
    user quick actions before they leave the hero.
4. Poster rail depth – apply a subtle Z-translation + drop shadow to the focused poster and tint unfocused ones; combined with a parallax scroll on
    the backdrop it sells the premium feel.
5. Sidebar glow states – show which section is active by animating a thin neon strip or soft glow behind the focused navigation icon, rather than just
    tint changes; it keeps the navigation minimal but modern.


## MICRO CHANGES
1. change "Similar" row on details page should pull from Trakt "related" api call instead of TMDB similar
2. add the scroll throttler from detailsfragment to EVERY row on Home, Movies, TV Show pages.