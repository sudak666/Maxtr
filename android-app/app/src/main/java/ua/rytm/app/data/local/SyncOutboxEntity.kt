package ua.rytm.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.PrimaryKey
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["ownerUid", "profileId", "domain", "entityId"], unique = true)],
)
data class SyncOutboxEntity(
    @PrimaryKey
    val operationId: String,
    val ownerUid: String,
    val profileId: String,
    val domain: String,
    val entityId: String,
    val operation: String,
    val payload: String?,
    val createdAt: Long,
    val attemptCount: Int = 0,
    val lastErrorCode: String? = null,
)

@Dao
interface SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(operation: SyncOutboxEntity)

    @Query("SELECT * FROM sync_outbox WHERE domain = :domain ORDER BY createdAt LIMIT :limit")
    suspend fun oldestForDomain(domain: String, limit: Int): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE domain = :domain")
    suspend fun countForDomain(domain: String): Int

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE ownerUid = :ownerUid AND profileId = :profileId")
    suspend fun countForScope(ownerUid: String, profileId: String): Int

    @Query("SELECT * FROM sync_outbox WHERE ownerUid = :ownerUid AND profileId = :profileId AND domain = :domain")
    fun observe(ownerUid: String, profileId: String, domain: String): Flow<List<SyncOutboxEntity>>

    @Query("SELECT * FROM sync_outbox WHERE ownerUid = :ownerUid AND profileId = :profileId AND domain = :domain")
    suspend fun get(ownerUid: String, profileId: String, domain: String): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE ownerUid = :ownerUid AND profileId = :profileId AND domain = :domain AND entityId = :entityId LIMIT 1")
    suspend fun getForEntity(ownerUid: String, profileId: String, domain: String, entityId: String): SyncOutboxEntity?

    @Query("DELETE FROM sync_outbox WHERE operationId = :operationId")
    suspend fun delete(operationId: String)

    @Query("DELETE FROM sync_outbox WHERE ownerUid = :ownerUid AND profileId = :profileId")
    suspend fun clearScope(ownerUid: String, profileId: String)

    @Query("UPDATE sync_outbox SET attemptCount = attemptCount + 1, lastErrorCode = :code WHERE operationId = :operationId")
    suspend fun markFailed(operationId: String, code: String)
}
