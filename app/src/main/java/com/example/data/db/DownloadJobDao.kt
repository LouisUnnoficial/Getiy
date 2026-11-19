package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadJobDao {
    @Query("SELECT * FROM download_jobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadJobEntity>>

    @Query("SELECT * FROM download_jobs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadJobEntity?

    @Query("SELECT * FROM download_jobs ORDER BY createdAt ASC")
    suspend fun getAllAsc(): List<DownloadJobEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: DownloadJobEntity)

    @Update
    suspend fun update(job: DownloadJobEntity)

    @Query("DELETE FROM download_jobs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM download_jobs WHERE status IN ('DONE', 'FAILED', 'CANCELED')")
    suspend fun clearFinished()

    @Query("DELETE FROM download_jobs WHERE status IN ('FAILED', 'CANCELED')")
    suspend fun clearFailed()
}
