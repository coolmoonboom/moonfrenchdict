package com.coolmoonfrench.dict.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 视频转文字识别结果记录。
 *
 * 每一条对应一次「视频 → 法语文本」的识别：来源固定为 VIDEO，
 * 同时保存原始文件名与识别时间，供列表展示。
 */
@Entity(
    tableName = "video_text_records",
    indices = [Index(value = ["timestamp"])]
)
data class VideoTextRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 来源标识，当前固定为 VIDEO，预留扩展（如 AUDIO） */
    val source: String = "VIDEO",
    /** 所选视频的文件名 */
    val fileName: String,
    /** 识别出的法语文本 */
    val text: String,
    /** 识别完成时间（epoch 毫秒） */
    val timestamp: Long = System.currentTimeMillis()
)
