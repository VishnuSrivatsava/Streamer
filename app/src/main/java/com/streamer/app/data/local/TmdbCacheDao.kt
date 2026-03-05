package com.streamer.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TmdbCacheDao {
    @Query("SELECT * FROM tmdb_cache WHERE cacheKey = :key AND cachedAt > :minTimestamp")
    suspend fun get(key: String, minTimestamp: Long): TmdbCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TmdbCacheEntity)

    @Query("DELETE FROM tmdb_cache WHERE cachedAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long)
}
