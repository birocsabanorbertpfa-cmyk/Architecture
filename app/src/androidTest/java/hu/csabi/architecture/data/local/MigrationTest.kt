package hu.csabi.architecture.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lesson 08 — the test that makes a migration trustworthy.
 *
 * [MigrationTestHelper] creates a database at the *old* schema straight from the exported
 * `1.json`, lets the migration run, and then validates the result against `2.json`. That is
 * why the schema files are committed: without them this test cannot exist, and a migration
 * nobody tested is a crash report waiting for a release.
 *
 * It needs a device or emulator, because it uses real SQLite — hence `androidTest`.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ArchitectureDatabase::class.java,
    )

    @Test
    fun migrate1To2_keepsExistingRowsAndDefaultsFetchedAt() {
        // 1. A version 1 database with one row, written with raw SQL — the entity class has
        //    moved on, so it cannot be used to insert into the old schema.
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO repos (id, name, owner, description, stars, language)
                VALUES (1, 'retrofit', 'square', 'A type-safe HTTP client', 43000, 'Java')
                """.trimIndent(),
            )
        }

        // 2. Run the migration and let Room validate the resulting schema against 2.json.
        //    validateDroppedTables = true also catches tables a migration forgot to remove.
        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            ArchitectureDatabase.MIGRATION_1_2,
        )

        // 3. The data survived, and the new column got its default rather than null.
        db.query("SELECT id, name, fetched_at FROM repos").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals("retrofit", cursor.getString(1))
            assertEquals(0L, cursor.getLong(2))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
