package com.qlcom.hack.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatermarkDao {
    @Query("SELECT * FROM watermark_records ORDER BY timestamp DESC")
    fun getAllRecordsFlow(): Flow<List<WatermarkRecord>>

    @Query("SELECT * FROM watermark_records ORDER BY timestamp DESC")
    suspend fun getAllRecords(): List<WatermarkRecord>

    @Query("SELECT * FROM watermark_records WHERE filePath = :path LIMIT 1")
    suspend fun getRecordByPath(path: String): WatermarkRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: WatermarkRecord): Long

    @Delete
    suspend fun deleteRecord(record: WatermarkRecord)
}
