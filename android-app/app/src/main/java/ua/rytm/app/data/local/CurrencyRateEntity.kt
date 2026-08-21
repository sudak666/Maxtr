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
@Entity(tableName = "currency_rates")
data class CurrencyRateEntity(
    @PrimaryKey val code: String,
    val rateToUah: Double,
)

@Dao
interface CurrencyRateDao {
    @Query("SELECT * FROM currency_rates")
    fun observeAll(): Flow<List<CurrencyRateEntity>>

    @Query("SELECT * FROM currency_rates")
    suspend fun getAllOnce(): List<CurrencyRateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CurrencyRateEntity>)

    @Query("DELETE FROM currency_rates")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(entities: List<CurrencyRateEntity>) {
        clearAll()
        insertAll(entities)
    }
}
