package com.wood.pair.data.model

import java.security.SecureRandom

/**
 * Room identity.
 *
 * Rules the brief set, and how they are met:
 *  - "short, random, non-sequential, uppercase alphanumeric" -> generated from
 *    [SecureRandom], never a counter, UUID, timestamp or hash of anything ordered.
 *  - "approximately 6 characters" -> exactly [LENGTH].
 *  - "Do not use UUIDs as the user-facing room ID" -> the UUID is never involved.
 *
 * The alphabet deliberately drops `I`, `O`, `0` and `1`. Those are the four characters
 * people most often mistype or misread when a room ID is spoken or retyped, and leaving
 * them out removes the entire class of "room not found" mistakes that a hand-typed ID
 * would otherwise cause. What remains is 32 symbols, so each character carries exactly
 * 5 bits and a 6-character ID is a 30-bit space (~1.07 billion rooms).
 */
object RoomId {

    const val LENGTH: Int = 6

    /** 32 unambiguous uppercase alphanumerics: 2-9 and A-Z minus I, O. */
    const val ALPHABET: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private val random = SecureRandom()

    /**
     * Normalises free-form user input: trims, uppercases, and strips separators people
     * commonly paste in (spaces, dashes, the word "room", a leading "#").
     *
     * Returns `null` when the result is not a legal room ID, so callers can show a
     * specific "invalid room ID" message rather than a misleading "not found".
     */
    fun normalize(input: String): String? {
        val cleaned = input
            .trim()
            .removePrefix("#")
            .replace(Regex("[\\s\\-_]"), "")
            .uppercase()
        if (cleaned.length != LENGTH) return null
        if (!cleaned.all { it in ALPHABET }) return null
        return cleaned
    }

    /** True if [value] is already a canonical room ID. */
    fun isValid(value: String): Boolean = normalize(value) == value

    /** Generates a new random room ID. */
    fun generate(): String = buildString(LENGTH) {
        repeat(LENGTH) {
            append(ALPHABET[random.nextInt(ALPHABET.length)])
        }
    }

    /**
     * Generates an ID that is not already taken, given a suspending check.
     *
     * Collisions are astronomically unlikely at 10^9 rooms, but retrying costs nothing
     * and removes the failure mode entirely. [isTaken] is expected to be a cheap
     * transactional existence check.
     */
    suspend fun generateUnique(isTaken: suspend (String) -> Boolean): String {
        repeat(MAX_GENERATION_ATTEMPTS) {
            val candidate = generate()
            if (!isTaken(candidate)) return candidate
        }
        return generate()
    }

    private const val MAX_GENERATION_ATTEMPTS = 8
}
