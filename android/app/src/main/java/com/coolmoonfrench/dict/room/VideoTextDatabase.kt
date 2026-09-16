package com.coolmoonfrench.dict.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 应用数据库：当前包含视频转文字记录表。
 * 通过单例暴露，避免重复建库。
 */
@Database(
    entities = [VideoTextRecord::class],
    version = 1,
    exportSchema = false
)
abstract class VideoTextDatabase : RoomDatabase() {

    abstract fun videoTextDao(): VideoTextDao

    companion object {
        private const val DB_NAME = "video_text.db"
        private const val DB_VERSION = 1

        @Volatile
        private var instance: VideoTextDatabase? = null

        fun get(context: Context): VideoTextDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VideoTextDatabase::class.java,
                    DB_NAME
                ).setJournalMode(JournalMode.TRUNCATE)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
