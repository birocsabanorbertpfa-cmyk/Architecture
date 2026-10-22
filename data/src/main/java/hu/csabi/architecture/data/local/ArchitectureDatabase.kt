package hu.csabi.architecture.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Lesson 08 — the database declaration, plus the part that usually gets skipped: migrations.
 *
 * `exportSchema = true` writes `app/schemas/<version>.json` on every build, and those files
 * are committed. They are the contract the next migration has to satisfy, they make the
 * schema change visible in a code review, and Room's migration tests read them to verify a
 * migration really produces the schema the next version expects.
 */
@Database(
    entities = [RepoEntity::class, RemoteKeyEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class ArchitectureDatabase : RoomDatabase() {

    abstract fun repoDao(): RepoDao

    abstract fun remoteKeyDao(): RemoteKeyDao

    companion object {
        const val NAME = "architecture.db"

        /**
         * A real migration, not an illustration: version 1 of this app had no `fetched_at`
         * and no indices, and version 2 needs both. Both schema JSONs are in `app/schemas`.
         *
         * Three details that make it correct:
         *  - `NOT NULL DEFAULT 0` — existing rows need a value, and Room's schema validation
         *    compares nullability and defaults, not just column names.
         *  - the indices are created explicitly, because adding `indices` to the entity
         *    changes the expected schema and Room rejects a database that lacks them.
         *  - the index names match what Room generates (`index_<table>_<column>`), which is
         *    what the validation actually compares against.
         *
         * Why not `fallbackToDestructiveMigration()`? Because it deletes the user's data on
         * every schema change. In an offline-first app that can mean losing the only copy of
         * something. It is fine in a throwaway prototype and nowhere else.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE repos ADD COLUMN fetched_at INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_repos_language ON repos (language)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_repos_stars ON repos (stars)")
            }
        }

        /**
         * Lesson 09 added the `remote_keys` table. A migration that creates a table has to
         * reproduce Room's own `CREATE TABLE` exactly — column order, types, nullability and
         * the primary key — or `runMigrationsAndValidate` fails. Copying it out of the
         * generated `3.json` is the reliable way to get it right.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `remote_keys` (
                        `query_key` TEXT NOT NULL,
                        `next_page` INTEGER,
                        `total_count` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`query_key`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
