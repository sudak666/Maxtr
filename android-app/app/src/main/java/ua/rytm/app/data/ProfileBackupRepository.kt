package ua.rytm.app.data

import android.content.ContentValues
import android.database.Cursor
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import ua.rytm.app.data.local.PROFILE_TABLES
import ua.rytm.app.data.local.RoomProfileScope
import ua.rytm.app.data.local.RytmDatabase

private const val BACKUP_FORMAT = 1

class ProfileBackupRepository(private val db: RytmDatabase) {
    suspend fun export(password: CharArray): ByteArray {
        val ownerUid = RoomProfileScope.ownerUid
        val profileId = RoomProfileScope.profileId
        val root = JSONObject().put("format", BACKUP_FORMAT).put("schema", 16).put("tables", JSONObject())
        val tables = root.getJSONObject("tables")
        db.withTransaction {
            PROFILE_TABLES.forEach { table ->
                val rows = JSONArray()
                db.openHelper.readableDatabase.query(
                    "SELECT * FROM `$table` WHERE ownerUid = ? AND profileId = ?",
                    arrayOf(ownerUid, profileId),
                ).use { cursor -> while (cursor.moveToNext()) rows.put(cursor.toJsonRow()) }
                tables.put(table, rows)
            }
        }
        return BackupCrypto.encrypt(root.toString().toByteArray(Charsets.UTF_8), password)
    }

    suspend fun restore(payload: ByteArray, password: CharArray): Int {
        val plaintext = BackupCrypto.decrypt(payload, password)
        return try {
            restorePlaintext(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    private suspend fun restorePlaintext(plaintext: ByteArray): Int {
        val root = try { JSONObject(plaintext.toString(Charsets.UTF_8)) }
        catch (error: Exception) { throw InvalidBackupException("Invalid backup JSON", error) }
        if (root.optInt("format") != BACKUP_FORMAT || root.optInt("schema") != 16) {
            throw InvalidBackupException("Unsupported backup format")
        }
        val tables = root.optJSONObject("tables") ?: throw InvalidBackupException("Missing backup tables")
        if (tables.keys().asSequence().toSet() != PROFILE_TABLES.toSet()) throw InvalidBackupException("Incomplete backup")
        val scopeOwner = RoomProfileScope.ownerUid
        val scopeProfile = RoomProfileScope.profileId
        var restored = 0
        db.withTransaction {
            PROFILE_TABLES.forEach { table ->
                val allowed = db.openHelper.readableDatabase.query("PRAGMA table_info(`$table`)").use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
                }
                val rows = tables.optJSONArray(table) ?: throw InvalidBackupException("Missing table: $table")
                val prepared = (0 until rows.length()).map { index ->
                    val row = rows.optJSONObject(index) ?: throw InvalidBackupException("Invalid row in $table")
                    if (!allowed.containsAll(row.keys().asSequence().toSet())) throw InvalidBackupException("Unknown column in $table")
                    ContentValues().apply {
                        row.keys().forEach { key -> putJsonValue(key, row.get(key)) }
                        put("ownerUid", scopeOwner)
                        put("profileId", scopeProfile)
                    }
                }
                db.openHelper.writableDatabase.execSQL(
                    "DELETE FROM `$table` WHERE ownerUid = ? AND profileId = ?",
                    arrayOf<Any>(scopeOwner, scopeProfile),
                )
                prepared.forEach { values ->
                    if (db.openHelper.writableDatabase.insert(table, 0, values) == -1L) {
                        throw InvalidBackupException("Could not restore $table")
                    }
                    restored++
                }
            }
        }
        return restored
    }
}

private fun Cursor.toJsonRow(): JSONObject = JSONObject().also { row ->
    columnNames.forEachIndexed { index, name ->
        if (name == "ownerUid" || name == "profileId") return@forEachIndexed
        row.put(name, when (getType(index)) {
            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
            Cursor.FIELD_TYPE_INTEGER -> getLong(index)
            Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
            Cursor.FIELD_TYPE_STRING -> getString(index)
            Cursor.FIELD_TYPE_BLOB -> throw InvalidBackupException("Blob columns are not supported")
            else -> throw InvalidBackupException("Unknown SQLite value")
        })
    }
}

private fun ContentValues.putJsonValue(key: String, value: Any) {
    when (value) {
        JSONObject.NULL -> putNull(key)
        is String -> put(key, value)
        is Int -> put(key, value)
        is Long -> put(key, value)
        is Double -> put(key, value)
        is Boolean -> put(key, value)
        else -> throw InvalidBackupException("Unsupported value in $key")
    }
}
