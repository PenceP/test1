package com.test1.tv.data

/**
 * A generic class that holds a value with its loading status.
 * Used to wrap data that comes from the repository layer.
 */
sealed class Resource<out T> {

    /**
     * Represents successful data retrieval.
     * @param data The retrieved data
     */
    data class Success<T>(val data: T) : Resource<T>()

    /**
     * Represents an error during data retrieval.
     * @param exception The exception that occurred
     * @param cachedData Optional cached data to show while error is displayed
     */
    data class Error<T>(
        val exception: Throwable,
        val cachedData: T? = null
    ) : Resource<T>()

    /**
     * Represents ongoing data retrieval.
     * @param cachedData Optional cached data to show while loading
     */
    data class Loading<T>(val cachedData: T? = null) : Resource<T>()

    /**
     * Returns the data if available (from Success, or cached from Error/Loading)
     */
    fun dataOrNull(): T? = when (this) {
        is Success -> data
        is Error -> cachedData
        is Loading -> cachedData
    }

    /**
     * Returns true if this is a Success
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * Returns true if this is Loading
     */
    fun isLoading(): Boolean = this is Loading

    /**
     * Returns true if this is Error
     */
    fun isError(): Boolean = this is Error
}
