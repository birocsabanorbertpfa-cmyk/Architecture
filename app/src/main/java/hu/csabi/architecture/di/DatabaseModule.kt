package hu.csabi.architecture.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import hu.csabi.architecture.data.local.ArchitectureDatabase
import hu.csabi.architecture.data.local.RepoDao
import javax.inject.Singleton

/**
 * Lesson 08 — the database is the textbook `@Singleton`.
 *
 * A `RoomDatabase` owns a connection pool and a write lock. Building two of them for the
 * same file means two pools fighting over one lock, which shows up as `SQLITE_BUSY` under
 * load rather than as an obvious bug. One instance per process, with a real lifetime
 * attached to it, is the whole reason this module exists.
 *
 * `@ApplicationContext` is Hilt's built-in qualifier. Taking an Activity context here would
 * leak it for the life of the process.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ArchitectureDatabase =
        Room.databaseBuilder(context, ArchitectureDatabase::class.java, ArchitectureDatabase.NAME)
            // Registering the migration is what makes the schema change survivable. Without
            // this line Room throws IllegalStateException on upgrade, which is at least
            // honest — unlike fallbackToDestructiveMigration(), which deletes the data.
            .addMigrations(ArchitectureDatabase.MIGRATION_1_2)
            .build()

    /**
     * Providing the DAO separately means consumers depend on the narrow interface they use,
     * not on the whole database. It also keeps the repository's constructor honest about
     * what it actually touches.
     */
    @Provides
    @Singleton
    fun repoDao(database: ArchitectureDatabase): RepoDao = database.repoDao()
}
