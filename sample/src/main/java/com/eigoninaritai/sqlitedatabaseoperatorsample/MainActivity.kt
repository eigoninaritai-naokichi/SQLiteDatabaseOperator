package com.eigoninaritai.sqlitedatabaseoperatorsample

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.eigoninaritai.sqlitedatabaseoperator.Equal
import com.eigoninaritai.sqlitedatabaseoperator.EqualWithValue
import com.eigoninaritai.sqlitedatabaseoperator.SQLiteTableOperator

/**
 * SQLiteTableOperatorサンプル メインアクティビティ。
 */
class MainActivity : AppCompatActivity() {
    /**
     *
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
            sampleTableList.forEach { Log.println(Log.INFO, "Inserted shows.", it.toString()) }

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
            sampleTableList.forEach { Log.println(Log.INFO, "Updated shows.", it.toString()) }

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

            // 全てのデータを表示する
            sampleTableList = sqliteTableOperator.selectDataList()
            sampleTableList.forEach { Log.println(Log.INFO, "All shows.", it.toString()) }
        }
    }
}