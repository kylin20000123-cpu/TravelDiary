package com.example.traveldiary.data
import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="trips")
data class Trip(@PrimaryKey(autoGenerate=true) val id:Long=0, val name:String, val start:String, val end:String, val note:String="")

@Entity(tableName="records")
data class Record(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val tripId:Long,
    val kind:String,
    val title:String,
    val detail:String="",
    val amount:Double=0.0,
    val date:String=""
)

@Dao
interface Dao {
    @Query("SELECT * FROM trips ORDER BY id DESC") fun trips():Flow<List<Trip>>
    @Insert suspend fun addTrip(x:Trip):Long
    @Query("SELECT * FROM records WHERE tripId=:id ORDER BY id DESC") fun records(id:Long):Flow<List<Record>>
    @Insert suspend fun addRecord(x:Record)
}

@Database(entities=[Trip::class,Record::class],version=1,exportSchema=false)
abstract class Db:RoomDatabase(){
    abstract fun dao():Dao
    companion object {
        @Volatile private var instance:Db?=null
        fun get(c:Context):Db = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(c.applicationContext,Db::class.java,"travel.db").build().also{instance=it}
        }
    }
}
