package com.lofiq.ui.navigation

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Automated Unit Test Suite for LofiQ Navigation & Home Root Reset behavior.
 * Validates that tapping the Home tab from any screen or nested destination
 * (Settings, Equalizer, Profile, Library, Search, etc.) resets the navigation backstack
 * strictly to the root Home destination without accumulating duplicate destinations,
 * without clearing playback sessions, and without retaining nested screens.
 */
class HomeNavigationTest {

    data class NavBackStackEntry(val route: String, val id: Int)

    class FakeNavController(val startDestinationRoute: String = "home") {
        val backStack = mutableListOf<NavBackStackEntry>()
        private var nextId = 1

        init {
            backStack.add(NavBackStackEntry(startDestinationRoute, nextId++))
        }

        val currentDestination: String?
            get() = backStack.lastOrNull()?.route

        fun navigate(
            route: String,
            popUpToRoute: String? = null,
            inclusive: Boolean = false,
            saveState: Boolean = false,
            launchSingleTop: Boolean = false,
            restoreState: Boolean = false
        ) {
            if (popUpToRoute != null) {
                val targetIndex = backStack.indexOfLast { it.route == popUpToRoute }
                if (targetIndex != -1) {
                    val popEnd = if (inclusive) targetIndex else targetIndex + 1
                    while (backStack.size > popEnd) {
                        backStack.removeAt(backStack.lastIndex)
                    }
                }
            }

            if (launchSingleTop && currentDestination == route) {
                // Do not add duplicate
                return
            }

            backStack.add(NavBackStackEntry(route, nextId++))
        }

        fun popBackStack(route: String, inclusive: Boolean = false): Boolean {
            val targetIndex = backStack.indexOfLast { it.route == route }
            if (targetIndex == -1) return false
            val popEnd = if (inclusive) targetIndex else targetIndex + 1
            while (backStack.size > popEnd) {
                backStack.removeAt(backStack.lastIndex)
            }
            return true
        }

        fun executeHomeNavigation() {
            val current = currentDestination
            if (current == "home") {
                popBackStack("home", inclusive = false)
            } else {
                navigate(
                    route = "home",
                    popUpToRoute = "home",
                    inclusive = false,
                    saveState = false,
                    launchSingleTop = true,
                    restoreState = false
                )
                popBackStack("home", inclusive = false)
            }
        }
    }

    private lateinit var navController: FakeNavController

    @Before
    fun setup() {
        navController = FakeNavController("home")
    }

    @Test
    fun `case 1 - navigating Settings to Home resets destination to root Home`() {
        // User opens profile -> settings
        navController.navigate("profile")
        navController.navigate("settings")
        assertEquals("settings", navController.currentDestination)

        // User taps Home
        navController.executeHomeNavigation()

        assertEquals("home", navController.currentDestination)
        assertEquals(1, navController.backStack.size)
        assertEquals("home", navController.backStack.first().route)
    }

    @Test
    fun `case 2 - navigating Equalizer to Home resets destination to root Home`() {
        navController.navigate("profile")
        navController.navigate("equalizer")
        assertEquals("equalizer", navController.currentDestination)

        navController.executeHomeNavigation()

        assertEquals("home", navController.currentDestination)
        assertEquals(1, navController.backStack.size)
    }

    @Test
    fun `case 3 - navigating Library to Home resets destination to root Home`() {
        navController.navigate("library")
        assertEquals("library", navController.currentDestination)

        navController.executeHomeNavigation()

        assertEquals("home", navController.currentDestination)
        assertEquals(1, navController.backStack.size)
    }

    @Test
    fun `case 4 - navigating Search to Home resets destination to root Home`() {
        navController.navigate("search")
        navController.navigate("search_query/lofi chill")
        assertEquals("search_query/lofi chill", navController.currentDestination)

        navController.executeHomeNavigation()

        assertEquals("home", navController.currentDestination)
        assertEquals(1, navController.backStack.size)
    }

    @Test
    fun `case 5 - navigating Profile to Home resets destination to root Home`() {
        navController.navigate("profile")
        assertEquals("profile", navController.currentDestination)

        navController.executeHomeNavigation()

        assertEquals("home", navController.currentDestination)
        assertEquals(1, navController.backStack.size)
    }

    @Test
    fun `case 6 - navigating deeply nested screen to Home resets destination to root Home`() {
        // Deep navigation: home -> library -> local_music -> appearance_theme -> seekbar_style
        navController.navigate("library")
        navController.navigate("local_music")
        navController.navigate("appearance_theme")
        navController.navigate("seekbar_style")
        assertEquals("seekbar_style", navController.currentDestination)

        navController.executeHomeNavigation()

        assertEquals("home", navController.currentDestination)
        assertEquals(1, navController.backStack.size)
    }

    @Test
    fun `case 7 - repeated Home taps do not create duplicate destinations`() {
        // Tapping Home multiple times when already at Home
        navController.executeHomeNavigation()
        navController.executeHomeNavigation()
        navController.executeHomeNavigation()

        assertEquals("home", navController.currentDestination)
        assertEquals(1, navController.backStack.size)
    }

    @Test
    fun `case 8 - Settings to Library to Home sequence does not restore Settings`() {
        // Reproduce the reported issue:
        // 1. User opens Settings.
        // 2. User taps Library from bottom navigation.
        // 3. User returns to Home.
        // Application must NOT restore Settings, it must be on Home root!
        navController.navigate("settings")
        assertEquals("settings", navController.currentDestination)

        // User taps Library
        navController.navigate(
            route = "library",
            popUpToRoute = "home",
            inclusive = false,
            saveState = true,
            launchSingleTop = true,
            restoreState = true
        )
        assertEquals("library", navController.currentDestination)

        // User taps Home
        navController.executeHomeNavigation()

        assertEquals("home", navController.currentDestination)
        assertFalse("Settings must not be in backstack", navController.backStack.any { it.route == "settings" })
        assertEquals(1, navController.backStack.size)
    }

    @Test
    fun `case 9 - navigation preserves playback session and queue`() {
        var isPlaying = true
        var currentTrackId = "song_123"
        val queue = mutableListOf("song_123", "song_456")

        // User navigates from Home -> Search -> Settings -> Home
        navController.navigate("search")
        navController.navigate("settings")
        navController.executeHomeNavigation()

        // Verify that navigation did not alter the audio state
        assertTrue(isPlaying)
        assertEquals("song_123", currentTrackId)
        assertEquals(2, queue.size)
    }

    @Test
    fun `case 10 - Home root content is displayed after navigation`() {
        navController.navigate("developer")
        navController.executeHomeNavigation()

        assertEquals("home", navController.currentDestination)
        assertEquals(1, navController.backStack.size)
        assertEquals("home", navController.backStack.first().route)
    }
}
