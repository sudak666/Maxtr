package ua.rytm.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Entity(tableName = "sync_revisions", primaryKeys = ["ownerUid", "profileId", "domain", "entityId"])
data class SyncRevisionEntity(
    val ownerUid: String,
    val profileId: String,
    val domain: String,
    val entityId: String,
    val revision: Long,
)

@Dao
interface SyncRevisionDao {
    @Query("SELECT revision FROM sync_revisions WHERE ownerUid=:ownerUid AND profileId=:profileId AND domain=:domain AND entityId=:entityId LIMIT 1")
    suspend fun get(ownerUid: String, profileId: String, domain: String, entityId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: SyncRevisionEntity)

    @Query("DELETE FROM sync_revisions WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun clearScope(ownerUid: String, profileId: String)
}
