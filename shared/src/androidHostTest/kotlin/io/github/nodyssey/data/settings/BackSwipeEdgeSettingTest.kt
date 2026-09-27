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
 * A stored 返回手势 value this build cannot read falls back to the default.
 *
 * The stored form is the enum name — [EinkModeSettingTest] and [ReportFormatSettingTest] test the
 * same shape for their own settings — so a constant renamed without a migration, or a write from a
 * build that had a third value, would otherwise reach `BackSwipeEdge.valueOf` and throw *inside the
 * settings flow*. That is the failure worth pinning: the reader does not lose one preference, they
 * lose every screen that collects `settings`, at launch, with no way back to 设置 to fix it.
 *
 * The fallback is asserted to be [BackSwipeEdge.START] specifically rather than "not the bad value":
 * START is the platform's own behaviour, so the degraded outcome is an app that behaves like every
 * other iOS app rather than one that silently grew a second back edge nobody asked for.
 */
class BackSwipeEdgeSettingTest {
    @Test
    fun `a value this build does not know falls back to the platform's own edge`() =
        runTest {
            assertEquals(BackSwipeEdge.START, storedEdge("NEITHER"))
        }

    @Test
    fun `a value stored by a build that had a third option still reads`() =
        runTest {
            assertEquals(BackSwipeEdge.START, storedEdge("END_ONLY"))
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
