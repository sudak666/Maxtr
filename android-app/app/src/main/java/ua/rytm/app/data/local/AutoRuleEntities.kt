package ua.rytm.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "auto_rules")
data class AutoRuleEntity(@PrimaryKey val id: String, val type: String, val keyword: String, val category: String, val position: Int)

@Dao
interface AutoRuleDao {
    @Query("SELECT * FROM auto_rules ORDER BY position ASC") fun observeAll(): Flow<List<AutoRuleEntity>>
    @Query("SELECT * FROM auto_rules ORDER BY position ASC") suspend fun getAllOnce(): List<AutoRuleEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(rule: AutoRuleEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(rules: List<AutoRuleEntity>)
    @Query("DELETE FROM auto_rules WHERE id = :id") suspend fun deleteById(id: String)
    @Query("DELETE FROM auto_rules") suspend fun clearAll()
    @Transaction suspend fun replaceAll(rules: List<AutoRuleEntity>) { clearAll(); insertAll(rules) }
}
