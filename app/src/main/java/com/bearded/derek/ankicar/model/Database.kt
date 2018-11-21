package com.bearded.derek.ankicar.model

import android.arch.persistence.db.SimpleSQLiteQuery
import android.arch.persistence.room.*
import android.content.Context
import android.os.AsyncTask
import java.lang.ref.WeakReference
import java.util.*

@Database(entities = [DbCard::class, Review::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AnkiDatabase : RoomDatabase() {

    abstract fun cardDao(): CardDao

    abstract fun reviewDao(): ReviewDao

    companion object {
        private var INSTANCE: AnkiDatabase? = null

        fun getInstance(context: Context): AnkiDatabase? {
            return INSTANCE ?: synchronized(this) {
                INSTANCE
                        ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AnkiDatabase {
            return Room.databaseBuilder(context, AnkiDatabase::class.java, "db.db")
                    .fallbackToDestructiveMigration()
                    .build()
        }

        fun clearAndResetAllTables(): Boolean {
            val db = INSTANCE ?: return false

            // reset all auto-incrementValues
            val query = SimpleSQLiteQuery("DELETE FROM sqlite_sequence")

            db.beginTransaction()
            return try {
                db.clearAllTables()
                db.query(query)
                db.setTransactionSuccessful()
                true
            } catch (e: Exception) {
                false
            } finally {
                db.endTransaction()
            }
        }

    }
}

@Entity
data class DbCard (@PrimaryKey val noteId: Long,
              val cardOrd: Int,
              val buttonCount: Int,
              val question: String,
              val answer: String,
              val flagged: Boolean,
              val ease: Int,
              val time: Long,
              val date: Date)

@Entity
data class Review (val startDate: Date, val endDate: Date) {
    @PrimaryKey(autoGenerate = true) var reviewId: Long = 0
}

@Dao
interface CardDao {
    @Query("select * FROM DbCard")
    fun getAll(): List<DbCard>

    @Query("select * FROM DbCard WHERE date BETWEEN :from AND :to")
    fun getAllBetweenDates(from: Date, to: Date): List<DbCard>

    @Query("select * FROM DbCard WHERE ease <= 0")
    fun getSkips(): List<DbCard>

    @Query("select * FROM DbCard WHERE ease <= 0 AND date BETWEEN :from AND :to")
    fun getSkipsBetweenDates(from: Date, to: Date): List<DbCard>

    @Query("select * FROM DbCard WHERE flagged = 1")
    fun getFlagged(): List<DbCard>

    @Query("select * FROM DbCard WHERE flagged = 1 AND date BETWEEN :from AND :to")
    fun getFlaggedBetweenDates(from: Date, to: Date): List<DbCard>

    @Query("select * FROM DbCard WHERE ease IN (:ease) AND date BETWEEN :from AND :to")
    fun getEaseBetweenDates(from: Date, to: Date, vararg ease: Int): List<DbCard>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(card: DbCard): Long

    @Query("DELETE from DbCard")
    fun deleteAll()
}

@Dao
interface ReviewDao {
    @Query("select * FROM Review")
    fun getAll(): List<Review>

    @Query("select * FROM Review WHERE startDate >= :from AND endDate <= :to")
    fun getAllBetweenDates(from: Date, to: Date): List<Review>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(review: Review): Long

    @Query("DELETE from Review")
    fun deleteAll()
}

class Converters {
    @TypeConverter
    fun fromTimeStamp(value: Long): Date {
        return Date(value)
    }
    @TypeConverter
    fun dateToTimestamp(date: Date): Long {
        return date.time
    }
}

fun insertCard(db: AnkiDatabase, listener: TransactionListener?, card: DbCard) {
    val task = object : SimpleDbTransaction<DbCard>(db, listener) {
        override fun doInBackground(vararg params: DbCard?): Long {
            return this.db.cardDao().insert(params[0]!!)
        }
    }
    task.execute(card)
}

abstract class SimpleDbTransaction<T>(val db: AnkiDatabase, listener: TransactionListener?) : AsyncTask<T,
        Void, Long>() {

    private val weakReferenceListener: WeakReference<TransactionListener?> = WeakReference(listener)

    override fun onPostExecute(result: Long?) {
        weakReferenceListener.get()?.onComplete()
    }
}

interface TransactionListener {
    fun onComplete()
}