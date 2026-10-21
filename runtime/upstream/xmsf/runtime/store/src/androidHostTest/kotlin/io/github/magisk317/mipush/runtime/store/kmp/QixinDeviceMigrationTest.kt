package io.github.magisk317.mipush.runtime.store.kmp

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

class QixinDeviceMigrationTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "QIXIN_MIGRATION_DB", matches = ".+")
    fun `device snapshot migrates without losing registrations or event payloads`() {
        val copy = Files.createTempFile("qixin-migration-", ".db")
        try {
            Files.copy(Path.of(System.getenv("QIXIN_MIGRATION_DB")), copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            val db = BundledSQLiteDriver().open(copy.toString())
            try {
                assertEquals(4L, scalar(db, "PRAGMA user_version"))
                val apps = fingerprint(db, "SELECT id,pkg,type,notification_on_register,blocked,island_enabled,island_focus_notification,registered_type,app_name FROM REGISTERED_APPLICATION ORDER BY id")
                val events = fingerprint(db, "SELECT id,pkg,type,date,result,dev_info,hex(payload),reg_sec FROM EVENT ORDER BY id")
                RuntimeStoreMigrations.ALL.filter { it.startVersion >= 4 }.forEach {
                    it.migrate(db)
                    db.execSQL("PRAGMA user_version=${it.endVersion}")
                }
                assertEquals(10L, scalar(db, "PRAGMA user_version"))
                assertEquals(apps, fingerprint(db, "SELECT id,pkg,type,notification_on_register,blocked,island_enabled,island_focus_notification,registered_type,app_name FROM REGISTERED_APPLICATION ORDER BY id"))
                assertEquals(events, fingerprint(db, "SELECT id,pkg,type,date,result,dev_info,hex(payload),reg_sec FROM EVENT ORDER BY id"))
                assertEquals(0L, scalar(db, "SELECT count(*) FROM REGISTERED_APPLICATION WHERE user_id!=0 OR click_fallback_enabled!=0"))
            } finally { db.close() }
        } finally { Files.deleteIfExists(copy) }
    }
    private fun scalar(db: SQLiteConnection, sql: String): Long = db.prepare(sql).use { it.step(); it.getLong(0) }
    private fun fingerprint(db: SQLiteConnection, sql: String): List<Byte> {
        val digest = MessageDigest.getInstance("SHA-256")
        db.prepare(sql).use { row ->
            while (row.step()) for (i in 0 until row.getColumnCount()) {
                val value = if (row.isNull(i)) "N" else "S" + row.getText(i)
                digest.update(value.length.toString().toByteArray())
                digest.update(0.toByte())
                digest.update(value.toByteArray())
            }
        }
        return digest.digest().toList()
    }
}
