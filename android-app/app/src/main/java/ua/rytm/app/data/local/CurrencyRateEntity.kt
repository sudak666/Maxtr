package ua.rytm.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// 1:1 with AppState.currencyRates (js/core.js), `Record<currencyCode,
// rateToUAH>` — e.g. {"USD": 41.5}. Same "Map<String,X> field on the
// finance doc" shape as CategoryIconEntity, same treatment. Seed values
// mirror js/core.js's SEED_RATES exactly (used as the fallback when a rate
// is genuinely absent, same as convertCurrency()'s `?? SEED_RATES[code]`).
@Entity(tableName = "currency_rates", primaryKeys = ["ownerUid", "profileId", "code"])
data class CurrencyRateEntity(
    val code: String,
    val rateToUah: Double,
    val ownerUid: String = RoomProfileScope.ownerUid,
    val profileId: String = RoomProfileScope.profileId,
)

@Dao
interface CurrencyRateDao {
    @Query("SELECT * FROM currency_rates WHERE ownerUid=:ownerUid AND profileId=:profileId")
    fun observeAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Flow<List<CurrencyRateEntity>>

    @Query("SELECT * FROM currency_rates WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun getAllOnce(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): List<CurrencyRateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CurrencyRateEntity>)

    @Query("DELETE FROM currency_rates WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun clearAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    @Transaction
    suspend fun replaceAll(entities: List<CurrencyRateEntity>) {
        clearAll()
        insertAll(entities)
    }
}
