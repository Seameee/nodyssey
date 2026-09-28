package io.github.nodyssey.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 返回手势 decodes the name an older build wrote, falls back on one it cannot read, and defaults to
 * both directions on a store with no answer.
 *
 * The stored form is the enum name — [EinkModeSettingTest] and [ReportFormatSettingTest] test the
 * same shape for their own settings — so a constant renamed without a migration, or a write from a
 * build that had a third value, would otherwise reach `BackSwipeEdge.valueOf` and throw *inside the
 * settings flow*. That is the failure worth pinning: the reader does not lose one preference, they
 * lose every screen that collects `settings`, at launch, with no way back to 设置 to fix it.
 *
 * The fallback is asserted to be [BackSwipeEdge.BOTH] specifically rather than "not the bad value".
 * Both is what a store with no answer means — a fresh install — and the whole reason this setting
 * defaults to it is that a second direction nobody has to discover is the feature; one that has to
 * be found before it exists is not. A regression to the narrow value here would be invisible on an
 * upgrading device and would take the gesture away again from every new one.
 *
 * The `START` case is the one this class exists for. The gesture used to be the platform's edge
 * recognizer and the setting named an *edge*; it is now the app's own drag and names a *direction*,
 * so the constant the old build wrote to disk is no longer the name of anything. Dropping it would
 * not throw — it would fall through to the default and silently hand every existing reader the wider
 * gesture, which is a change nobody asked for. `START` meant the narrower choice and
 * [BackSwipeEdge.RIGHT] is the narrower choice now, so that is what it has to decode to.
 */
class BackSwipeEdgeSettingTest {
    @Test
    fun `a value this build does not know falls back to both directions`() =
        runTest {
            assertEquals(BackSwipeEdge.BOTH, storedEdge("NEITHER"))
        }

    @Test
    fun `a value stored by a build that had a third option still reads`() =
        runTest {
            assertEquals(BackSwipeEdge.BOTH, storedEdge("END_ONLY"))
        }

    @Test
    fun `a store with no answer at all is both directions, not the platform's single one`() =
        runTest {
            val repository = repository()
            assertEquals(BackSwipeEdge.BOTH, repository.settings.first().backSwipeEdge)
        }

    @Test
    fun `the edge name an older build wrote decodes to the direction that means the same thing`() =
        runTest {
            assertEquals(BackSwipeEdge.RIGHT, storedEdge("START"))
        }

    @Test
    fun `the current names still round-trip`() =
        runTest {
            assertEquals(BackSwipeEdge.RIGHT, storedEdge("RIGHT"))
            assertEquals(BackSwipeEdge.BOTH, storedEdge("BOTH"))
        }

    private suspend fun CoroutineScope.storedEdge(raw: String): BackSwipeEdge {
        val repository = repository()
        dataStore.edit { it[KEY_BACK_SWIPE_EDGE] = raw }
        return repository.settings.first().backSwipeEdge
    }

    private lateinit var dataStore: DataStore<Preferences>

    private fun CoroutineScope.repository(): SettingsRepository {
        val directory =
            Files.createTempDirectory("nodyssey-settings").toFile().apply { deleteOnExit() }
        dataStore =
            PreferenceDataStoreFactory.create(scope = this) {
                File(directory, "settings.preferences_pb")
            }
        return SettingsRepository(dataStore)
    }

    private companion object {
        val KEY_BACK_SWIPE_EDGE = stringPreferencesKey("back_swipe_edge")
    }
}
