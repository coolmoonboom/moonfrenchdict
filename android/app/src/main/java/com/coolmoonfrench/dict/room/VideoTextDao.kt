package com.coolmoonfrench.dict.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 视频转文字记录的数据访问接口。 */
@Dao
interface VideoTextDao {

    /** 插入一条识别记录，返回新记录 id。 */
    @Insert
    suspend fun insert(record: VideoTextRecord): Long

    /** 按时间倒序返回全部识别记录。 */
    @Query("SELECT * FROM video_text_records ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<VideoTextRecord>>

    /** 删除指定记录。 */
    @Delete
    suspend fun delete(record: VideoTextRecord)

    /** 清空全部记录。 */
    @Query("DELETE FROM video_text_records")
    suspend fun clear()
}
