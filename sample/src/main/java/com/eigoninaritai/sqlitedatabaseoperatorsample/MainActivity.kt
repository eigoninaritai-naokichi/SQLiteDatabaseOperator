package com.eigoninaritai.sqlitedatabaseoperatorsample

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.eigoninaritai.sqlitedatabaseoperator.*

/**
 * SQLiteTableOperatorサンプル メインアクティビティ。
 */
class MainActivity : AppCompatActivity() {
    /**
     * アクティビティを作成する。
     *
     * @param savedInstanceState 特定の状況下でアクティビティ廃棄直前に保存された情報。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // SQLiteデータベーステーブル操作クラスをしてテーブルを操作する
        SQLiteTableOperator(SampleDatabaseHelper(this)).use { sqliteTableOperator ->
            // 全てのデータを削除する
            try {
                sqliteTableOperator.beginTransaction()
                sqliteTableOperator.delete<SampleTable>()
                sqliteTableOperator.setTransactionSuccessful()
            }
            finally {
                sqliteTableOperator.endTransaction()
            }

            // SampleTableのデータを作成し、インサートする
            try {
                sqliteTableOperator.beginTransaction()
                val sampleTable1 = SampleTable(nullableInt = 2, limitedLengthString = "Foreign String", stringWithDefault = "Non Default String")
                val sampleTable2 = SampleTable(nullableInt = 2, limitedLengthString = "Hoge String", intWithDefault = 54321, stringWithDefault = "Non Default String")
                sqliteTableOperator.insert(sampleTable1)
                sqliteTableOperator.insert(sampleTable2)
                sqliteTableOperator.setTransactionSuccessful()
            }
            finally {
                sqliteTableOperator.endTransaction()
            }

            // インサートしたデータを取得し、データを表示する
            var sampleTableList = sqliteTableOperator.selectDataList<SampleTable>(listOf(EqualWithValue(SampleTable::nullableInt, 2)))
            sampleTableList.forEach { Log.println(Log.INFO, "Show inserted data.", it.toString()) }

            // インサートしたデータをCursorで取得し、データを表示する
            val select = Select(listOf(
                SelectColumnWithFunction("count(?)", SampleTable::limitedLengthString, "COUNT"),
                SelectColumn(SampleTable::id),
                SelectColumnWithString("*")
            ))
            val whereConditions = listOf(NotEqualWithValue(SampleTable::limitedLengthString, "Foreign String"))
            sqliteTableOperator.selectData<SampleTable>(select, whereConditions).use { cursor ->
                if (cursor.moveToFirst()){
                    Log.println(Log.INFO, "Show inserted data2.", "${SQLiteTableOperator.getColumnName<SampleTable>(SampleTable::nullableInt)}:${SQLiteTableOperator.getParameterValueFromCursor<SampleTable>(SampleTable::nullableInt, cursor)}")
                    do {
                        for (i in 0 until cursor.columnCount) Log.println(Log.INFO, "Show inserted data3.", "${cursor.getColumnName(i)}:${cursor.getString(i)}")
                    } while (cursor.moveToNext())
                }
            }

            // 取得した最初のデータを更新する
            if (sampleTableList.isNotEmpty()) {
                try {
                    sqliteTableOperator.beginTransaction()
                    val changedSampleTable1 = sampleTableList[0].copy(nullableInt = 100, limitedLengthString = "Changed String")
                    sqliteTableOperator.update(changedSampleTable1, listOf(Equal(SampleTable::id)))
                    sqliteTableOperator.setTransactionSuccessful()
                }
                finally {
                    sqliteTableOperator.endTransaction()
                }
            }

            // 更新したデータを取得し、データを表示する
            sampleTableList = sqliteTableOperator.selectDataList(listOf(EqualWithValue(SampleTable::nullableInt, 100)))
            sampleTableList.forEach { Log.println(Log.INFO, "Show updated data.", it.toString()) }

            // 全てのデータを表示する
            sampleTableList = sqliteTableOperator.selectDataList()
            sampleTableList.forEach { Log.println(Log.INFO, "Show All data.", it.toString()) }

            // データを削除する
            if (sampleTableList.isNotEmpty()) {
                try {
                    sqliteTableOperator.beginTransaction()
                    sqliteTableOperator.delete(sampleTableList[0], listOf(Equal(SampleTable::id)))
                    sqliteTableOperator.setTransactionSuccessful()
                }
                finally {
                    sqliteTableOperator.endTransaction()
                }
            }
        }
    }
}