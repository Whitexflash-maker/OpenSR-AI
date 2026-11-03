package com.featherx.app.feature.gamespace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryLogicTest {

    private val chess = InstalledApp("com.chess", "Chess", isGameCategory = true)
    private val ludo = InstalledApp("com.ludo", "ludo King", isGameCategory = true)
    private val notes = InstalledApp("com.notes", "Notes", isGameCategory = false)
    private val camera = InstalledApp("com.camera", "Camera", isGameCategory = false)
    private val all = listOf(notes, ludo, camera, chess)

    private fun names(entries: List<LibraryEntry>) = entries.map { it.app.packageName }

    @Test
    fun detectedGamesAppearAutomaticallyAndAreSortedIgnoringCase() {
        val library = LibraryLogic.resolve(all, LibraryPrefs())
        assertEquals(listOf("com.chess", "com.ludo"), names(library))
        assertTrue(library.none { it.addedByUser })
    }

    @Test
    fun manuallyAddedNonGameAppIsIncludedAndMarkedAddedByUser() {
        val prefs = LibraryLogic.withAdded(LibraryPrefs(), "com.notes")
        val library = LibraryLogic.resolve(all, prefs)
        assertEquals(listOf("com.chess", "com.ludo", "com.notes"), names(library))
        assertTrue(library.first { it.app.packageName == "com.notes" }.addedByUser)
        assertFalse(library.first { it.app.packageName == "com.chess" }.addedByUser)
    }

    @Test
    fun removedDetectedGameStaysRemoved() {
        val prefs = LibraryLogic.withRemoved(LibraryPrefs(), "com.chess")
        assertEquals(listOf("com.ludo"), names(LibraryLogic.resolve(all, prefs)))
    }

    @Test
    fun removingAManuallyAddedAppRemovesIt() {
        var prefs = LibraryLogic.withAdded(LibraryPrefs(), "com.notes")
        prefs = LibraryLogic.withRemoved(prefs, "com.notes")
        assertFalse("com.notes" in names(LibraryLogic.resolve(all, prefs)))
    }

    @Test
    fun reAddingARemovedGameBringsItBack() {
        var prefs = LibraryLogic.withRemoved(LibraryPrefs(), "com.chess")
        prefs = LibraryLogic.withAdded(prefs, "com.chess")
        val library = LibraryLogic.resolve(all, prefs)
        assertTrue("com.chess" in names(library))
        assertTrue(library.first { it.app.packageName == "com.chess" }.addedByUser)
    }

    @Test
    fun uninstalledAppsDisappearEvenIfStillInPrefs() {
        val prefs = LibraryLogic.withAdded(LibraryPrefs(), "com.gone")
        assertFalse("com.gone" in names(LibraryLogic.resolve(all, prefs)))
    }

    @Test
    fun addCandidatesExcludeLibraryAndFilterByNameOrPackage() {
        val library = LibraryLogic.resolve(all, LibraryPrefs())
        assertEquals(
            listOf("com.camera", "com.notes"),
            LibraryLogic.addCandidates(all, library, "").map { it.packageName },
        )
        assertEquals(
            listOf("com.camera"),
            LibraryLogic.addCandidates(all, library, "  CAM ").map { it.packageName },
        )
        assertEquals(
            listOf("com.notes"),
            LibraryLogic.addCandidates(all, library, "com.no").map { it.packageName },
        )
        assertTrue(LibraryLogic.addCandidates(all, library, "zzz").isEmpty())
    }

    @Test
    fun recentsAreNewestFirstLimitedAndOnlyFromLibrary() {
        val prefs = LibraryLogic.withAdded(LibraryPrefs(), "com.notes")
        val library = LibraryLogic.resolve(all, prefs)
        val launches = mapOf(
            "com.chess" to 100L,
            "com.notes" to 300L,
            "com.ludo" to 200L,
            "com.camera" to 999L, // not in the library, must be ignored
            "com.gone" to 500L,   // not installed, must be ignored
        )
        assertEquals(
            listOf("com.notes", "com.ludo", "com.chess"),
            LibraryLogic.recents(library, launches).map { it.packageName },
        )
        assertEquals(
            listOf("com.notes"),
            LibraryLogic.recents(library, launches, limit = 1).map { it.packageName },
        )
        assertTrue(LibraryLogic.recents(library, emptyMap()).isEmpty())
    }
}
