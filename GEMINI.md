# GEMINI.md: Project Context for Gemini

This document provides a comprehensive overview of the "test1" Android TV application, intended to be used as a context for interacting with Gemini.

## Project Overview

This is an Android TV application designed for browsing and viewing movies and TV shows. It aggregates data from multiple sources (Trakt.tv, TMDB, OMDb) to provide a rich user experience. The project is written entirely in Kotlin and follows modern Android development best practices.

The core goal of the project, as outlined in other documents, is to modernize the UI to a "Next-Gen" aesthetic, focusing on a d-pad friendly experience with dynamic backgrounds, a floating navigation dock, and a rich "hero" section for content details.

### Key Technologies & Architecture

*   **Language:** Kotlin
*   **Platform:** Android TV (using the Leanback library)
*   **Architecture:**
    *   **Model-View-ViewModel (MVVM):** The UI is separated from the business logic using `ViewModel`s and `LiveData`.
    *   **Repository Pattern:** A `ContentRepository` abstracts the data sources, providing a clean API for the ViewModels.
    *   **Single-Activity Architecture:** The app uses a single `MainActivity` that hosts various `Fragment`s for different screens.
*   **Data Sources:**
    *   **Trakt.tv:** Provides lists of trending and popular movies/shows.
    *   **The Movie Database (TMDB):** Used to fetch detailed metadata, including posters, backdrops, cast, and crew.
    *   **OMDb API:** Used to fetch ratings from IMDb and Rotten Tomatoes.
*   **Asynchronous Programming:** Kotlin Coroutines are used extensively for managing background threads and network requests.
*   **Caching:** The application uses a Room database to cache network responses, providing a fast and offline-capable experience. `WorkManager` is used to schedule background refreshes of this cache.
*   **Image Loading:** Glide is used for efficiently loading and displaying images.
*   **Networking:** Retrofit and OkHttp are used for making API calls.

## Building and Running

This is a standard Gradle-based Android project.

### Prerequisites

*   Android Studio or the Android SDK.
*   A `secrets.properties` file in the root directory with the following keys:
    ```properties
    TRAKT_CLIENT_ID="..."
    TRAKT_CLIENT_SECRET="..."
    TMDB_API_KEY="..."
    TMDB_ACCESS_TOKEN="..."
    OMDB_API_KEY="..."
    ```

### Key Commands

*   **Build the project:**
    ```bash
    ./gradlew build
    ```
*   **Install the debug APK on a connected device/emulator:**
    ```bash
    ./gradlew installDebug
    ```
*   **Run unit tests:**
    ```bash
    ./gradlew test
    ```
*   **Run instrumented tests:**
    ```bash
    ./gradlew connectedCheck
    ```

## Development Conventions

*   **Code Style:** The code follows the standard Kotlin style guidelines.
*   **Nullability:** The project uses Kotlin's nullable types to prevent null pointer exceptions.
*   **View Binding/Data Binding:** Not explicitly used, views are accessed via `findViewById`. This could be a future improvement.
*   **Dependency Management:** Dependencies are managed in the `app/build.gradle.kts` file. Version catalogs (`gradle/libs.versions.toml`) are used to manage dependency versions.
