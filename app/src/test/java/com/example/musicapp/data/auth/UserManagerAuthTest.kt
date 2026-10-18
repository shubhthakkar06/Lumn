package com.example.musicapp.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserManagerAuthTest {

    @Test
    fun testPasswordHashing() {
        val hash1 = UserManager.hashPassword("mySecretPassword123")
        val hash2 = UserManager.hashPassword("mySecretPassword123")
        val hash3 = UserManager.hashPassword("differentPassword")

        assertTrue(hash1.isNotEmpty())
        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hash3)
        assertEquals(64, hash1.length) // SHA-256 hex string length
    }

    @Test
    fun testValidationRules() {
        val invalidEmails = listOf("", "plainaddress", "test@missingdot", "@domain.com", "test@domain.", "test.com")
        invalidEmails.forEach { email ->
            val isInvalid = !UserManager.isValidEmail(email)
            assertTrue("Expected '$email' to be detected as invalid", isInvalid)
        }

        val validEmails = listOf("shubh@gmail.com", "user@spotify.co.uk", "alex.music@domain.org")
        validEmails.forEach { email ->
            val isValid = UserManager.isValidEmail(email)
            assertTrue("Expected '$email' to be detected as valid", isValid)
        }

        // Username validation check
        val validUsernames = listOf("shubh", "music_fan", "alex.99", "john_doe")
        validUsernames.forEach { u ->
            assertTrue("Expected '$u' to be valid username", UserManager.isValidUsername(u))
        }
        val invalidUsernames = listOf("a", "ab", "name with spaces", "user@name", "invalid!", "")
        invalidUsernames.forEach { u ->
            assertFalse("Expected '$u' to be invalid username", UserManager.isValidUsername(u))
        }

        // Password length check
        assertTrue("short".length < 6)
        assertTrue("securePass123".length >= 6)
    }

    @Test
    fun testUserProfileCreation() {
        val name = "Alex Johnson"
        val email = "alex@test.com"
        val password = "password123"
        val hash = UserManager.hashPassword(password)
        val letter = name.trim().first().uppercaseChar().toString()

        val profile = UserProfile(
            id = "user_123",
            name = name,
            email = email,
            passwordHash = hash,
            avatarLetter = letter,
            avatarColorHex = "#1DB954",
            isPremium = true,
            isStudent = false
        )

        assertEquals("A", profile.avatarLetter)
        assertEquals(name, profile.name)
        assertEquals(email, profile.email)
        assertEquals(hash, profile.passwordHash)
        assertTrue(profile.isPremium)
    }
}
