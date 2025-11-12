package com.example.fitnesstracker.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import androidx.room.Room

@Database(
    entities = [User::class, Exercise::class, WorkoutSession::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create users table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        email TEXT NOT NULL,
                        passwordHash TEXT,
                        createdAt INTEGER NOT NULL,
                        lastLoginAt INTEGER,
                        profileImageUri TEXT
                    )
                """.trimIndent())

                // Create exercises table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS exercises (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT,
                        muscleGroups TEXT,
                        difficulty TEXT,
                        iconResId TEXT,
                        isActive INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())

                // Add new columns to workout_sessions table
                database.execSQL("ALTER TABLE workout_sessions ADD COLUMN userId INTEGER")
                database.execSQL("ALTER TABLE workout_sessions ADD COLUMN exerciseId INTEGER")
                database.execSQL("ALTER TABLE workout_sessions ADD COLUMN avgElbowAngle REAL")
                database.execSQL("ALTER TABLE workout_sessions ADD COLUMN avgHipAngle REAL")
                database.execSQL("ALTER TABLE workout_sessions ADD COLUMN goodFormPercentage REAL")
                database.execSQL("ALTER TABLE workout_sessions ADD COLUMN notes TEXT")

                // Create indices
                database.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_userId ON workout_sessions(userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_exerciseId ON workout_sessions(exerciseId)")

                // Insert default exercises
                database.execSQL("""
                    INSERT INTO exercises (name, description, muscleGroups, difficulty, isActive) VALUES
                    ('Push-Ups', 'Upper body strength exercise', 'Chest, Triceps, Shoulders', 'Beginner', 1),
                    ('Squats', 'Lower body strength exercise', 'Quadriceps, Glutes, Hamstrings', 'Beginner', 1),
                    ('Pull-Ups', 'Upper body pulling exercise', 'Back, Biceps', 'Intermediate', 1),
                    ('Planks', 'Core strength exercise', 'Core, Shoulders', 'Beginner', 1)
                """)
            }
        }

        // Migration from version 2 to 3 - Add firebaseUid column
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add firebaseUid column to users table
                database.execSQL("ALTER TABLE users ADD COLUMN firebaseUid TEXT")
                // Create unique index on firebaseUid
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_firebaseUid ON users(firebaseUid)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fitness_tracker_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // Removed fallbackToDestructiveMigration for production stability
                    // If migration fails, app will crash rather than silently lose data
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}


