package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pihole_devices")
data class PiholeDeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val host: String,
    val port: Long,
    val useHttps: Boolean,
    val apiVersion: String,
    val basePath: String,
    val locationTag: String,
    val notes: String,
    val hasStoredCredential: Boolean,
    val lastConnectedAtMs: Long,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Entity(tableName = "management_audit_logs")
data class ManagementAuditLogEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val deviceName: String,
    val actionType: String,
    val target: String,
    val createdAtMs: Long
)

@Dao
interface PiholeDeviceDao {
    @Query("SELECT * FROM pihole_devices ORDER BY createdAtMs ASC")
    fun observeAllDevices(): Flow<List<PiholeDeviceEntity>>

    @Query("SELECT * FROM pihole_devices WHERE id = :deviceId LIMIT 1")
    suspend fun getDeviceById(deviceId: String): PiholeDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: PiholeDeviceEntity)

    @Update
    suspend fun updateDevice(device: PiholeDeviceEntity)

    @Query("UPDATE pihole_devices SET lastConnectedAtMs = :epochMs, updatedAtMs = :updatedAtMs WHERE id = :deviceId")
    suspend fun updateLastConnected(deviceId: String, epochMs: Long, updatedAtMs: Long)

    @Query("DELETE FROM pihole_devices WHERE id = :deviceId")
    suspend fun deleteDeviceById(deviceId: String)
}

@Dao
interface ManagementAuditLogDao {
    @Query("SELECT * FROM management_audit_logs ORDER BY createdAtMs DESC LIMIT 50")
    fun observeRecentLogs(): Flow<List<ManagementAuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ManagementAuditLogEntity)

    @Query("DELETE FROM management_audit_logs")
    suspend fun clearAllLogs()
}

@Database(
    entities = [PiholeDeviceEntity::class, ManagementAuditLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PiPulseDatabase : RoomDatabase() {
    abstract fun deviceDao(): PiholeDeviceDao
    abstract fun auditLogDao(): ManagementAuditLogDao

    companion object {
        @Volatile
        private var INSTANCE: PiPulseDatabase? = null

        fun getInstance(context: Context): PiPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PiPulseDatabase::class.java,
                    "pipulse_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
