package com.notisync

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class PhoneNotificationTest {

    private val gson = Gson()

    @Test
    fun `test create notification with all fields`() {
        val notification = PhoneNotification(
            id = "test-id",
            app_package = "com.example.app",
            app_name = "Example App",
            title = "Test Title",
            text = "Test Text",
            timestamp = 1234567890L,
            icon = "base64icon"
        )

        assertEquals("test-id", notification.id)
        assertEquals("com.example.app", notification.app_package)
        assertEquals("Example App", notification.app_name)
        assertEquals("Test Title", notification.title)
        assertEquals("Test Text", notification.text)
        assertEquals(1234567890L, notification.timestamp)
        assertEquals("base64icon", notification.icon)
    }

    @Test
    fun `test create notification with null optional fields`() {
        val notification = PhoneNotification(
            id = "notif-1",
            app_package = "com.test",
            app_name = "Test",
            title = null,
            text = null,
            timestamp = 0L
        )

        assertEquals("notif-1", notification.id)
        assertNull(notification.title)
        assertNull(notification.text)
        assertNull(notification.icon)
    }

    @Test
    fun `test notification equality`() {
        val notif1 = PhoneNotification(
            id = "id1",
            app_package = "pkg",
            app_name = "App",
            title = "Title",
            text = "Text",
            timestamp = 100L
        )

        val notif2 = PhoneNotification(
            id = "id1",
            app_package = "pkg",
            app_name = "App",
            title = "Title",
            text = "Text",
            timestamp = 100L
        )

        assertEquals(notif1, notif2)
    }

    @Test
    fun `test notification inequality`() {
        val notif1 = PhoneNotification(
            id = "id1",
            app_package = "pkg",
            app_name = "App",
            title = "Title",
            text = "Text",
            timestamp = 100L
        )

        val notif2 = PhoneNotification(
            id = "id2",
            app_package = "pkg",
            app_name = "App",
            title = "Title",
            text = "Text",
            timestamp = 100L
        )

        assertNotEquals(notif1, notif2)
    }

    @Test
    fun `test notification copy`() {
        val original = PhoneNotification(
            id = "orig-id",
            app_package = "com.original",
            app_name = "Original",
            title = "Original Title",
            text = "Original Text",
            timestamp = 999L
        )

        val copy = original.copy(title = "Modified Title")

        assertEquals("orig-id", copy.id)
        assertEquals("Modified Title", copy.title)
        assertEquals("Original Text", copy.text)
    }

    @Test
    fun `test serialize notification to json`() {
        val notification = PhoneNotification(
            id = "json-test",
            app_package = "com.json",
            app_name = "JSON App",
            title = "JSON Title",
            text = "JSON Text",
            timestamp = 12345L
        )

        val json = gson.toJson(notification)

        assertTrue(json.contains("\"id\":\"json-test\""))
        assertTrue(json.contains("\"app_package\":\"com.json\""))
        assertTrue(json.contains("\"app_name\":\"JSON App\""))
        assertTrue(json.contains("\"title\":\"JSON Title\""))
        assertTrue(json.contains("\"text\":\"JSON Text\""))
        assertTrue(json.contains("\"timestamp\":12345"))
    }

    @Test
    fun `test deserialize notification from json`() {
        val json = """
            {
                "id": "deser-test",
                "app_package": "com.deserialize",
                "app_name": "Deser App",
                "title": "Deser Title",
                "text": "Deser Text",
                "timestamp": 54321,
                "icon": "icondata"
            }
        """.trimIndent()

        val notification = gson.fromJson(json, PhoneNotification::class.java)

        assertEquals("deser-test", notification.id)
        assertEquals("com.deserialize", notification.app_package)
        assertEquals("Deser App", notification.app_name)
        assertEquals("Deser Title", notification.title)
        assertEquals("Deser Text", notification.text)
        assertEquals(54321L, notification.timestamp)
        assertEquals("icondata", notification.icon)
    }

    @Test
    fun `test deserialize notification without optional icon`() {
        val json = """
            {
                "id": "no-icon",
                "app_package": "com.noicon",
                "app_name": "No Icon",
                "title": "Title",
                "text": "Text",
                "timestamp": 0
            }
        """.trimIndent()

        val notification = gson.fromJson(json, PhoneNotification::class.java)

        assertEquals("no-icon", notification.id)
        assertNull(notification.icon)
    }

    @Test
    fun `test deserialize notification with null title and text`() {
        val json = """
            {
                "id": "null-fields",
                "app_package": "com.nulls",
                "app_name": "Nulls",
                "title": null,
                "text": null,
                "timestamp": 0
            }
        """.trimIndent()

        val notification = gson.fromJson(json, PhoneNotification::class.java)

        assertEquals("null-fields", notification.id)
        assertNull(notification.title)
        assertNull(notification.text)
    }

    @Test
    fun `test notification toString`() {
        val notification = PhoneNotification(
            id = "str-test",
            app_package = "com.string",
            app_name = "String App",
            title = "Title",
            text = "Text",
            timestamp = 111L
        )

        val str = notification.toString()

        assertTrue(str.contains("PhoneNotification"))
        assertTrue(str.contains("str-test"))
        assertTrue(str.contains("String App"))
    }

    @Test
    fun `test notification hashCode consistency`() {
        val notif1 = PhoneNotification(
            id = "hash-test",
            app_package = "com.hash",
            app_name = "Hash",
            title = "T",
            text = "X",
            timestamp = 1L
        )

        val notif2 = PhoneNotification(
            id = "hash-test",
            app_package = "com.hash",
            app_name = "Hash",
            title = "T",
            text = "X",
            timestamp = 1L
        )

        assertEquals(notif1.hashCode(), notif2.hashCode())
    }

    @Test
    fun `test serialize and deserialize roundtrip`() {
        val original = PhoneNotification(
            id = "roundtrip-id",
            app_package = "com.roundtrip",
            app_name = "Roundtrip App",
            title = "RT Title",
            text = "RT Text",
            timestamp = 987654321L,
            icon = "rt-icon-data"
        )

        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, PhoneNotification::class.java)

        assertEquals(original, deserialized)
    }

    @Test
    fun `test notification with special characters in text`() {
        val notification = PhoneNotification(
            id = "special-chars",
            app_package = "com.special",
            app_name = "Special \"App\"",
            title = "Title with\nnewline",
            text = "Text with emoji \uD83D\uDE00",
            timestamp = 0L
        )

        val json = gson.toJson(notification)
        val deserialized = gson.fromJson(json, PhoneNotification::class.java)

        assertEquals("Special \"App\"", deserialized.app_name)
        assertEquals("Title with\nnewline", deserialized.title)
        assertEquals("Text with emoji \uD83D\uDE00", deserialized.text)
    }

    @Test
    fun `test notification with empty strings`() {
        val notification = PhoneNotification(
            id = "",
            app_package = "",
            app_name = "",
            title = "",
            text = "",
            timestamp = 0L
        )

        assertEquals("", notification.id)
        assertEquals("", notification.app_package)
        assertEquals("", notification.app_name)
        assertEquals("", notification.title)
        assertEquals("", notification.text)
    }

    @Test
    fun `test notification with max long timestamp`() {
        val notification = PhoneNotification(
            id = "max-ts",
            app_package = "com.max",
            app_name = "Max",
            title = null,
            text = null,
            timestamp = Long.MAX_VALUE
        )

        assertEquals(Long.MAX_VALUE, notification.timestamp)
    }

    @Test
    fun `test notification default icon is null`() {
        val notification = PhoneNotification(
            id = "default-icon",
            app_package = "com.default",
            app_name = "Default",
            title = "T",
            text = "X",
            timestamp = 0L
        )

        assertNull(notification.icon)
    }

    @Test
    fun `test component properties via destructuring`() {
        val notification = PhoneNotification(
            id = "destruct",
            app_package = "com.destruct",
            app_name = "Destruct",
            title = "DT",
            text = "DX",
            timestamp = 42L,
            icon = "icon"
        )

        val (id, pkg, name, title, text, ts, icon) = notification

        assertEquals("destruct", id)
        assertEquals("com.destruct", pkg)
        assertEquals("Destruct", name)
        assertEquals("DT", title)
        assertEquals("DX", text)
        assertEquals(42L, ts)
        assertEquals("icon", icon)
    }
}
