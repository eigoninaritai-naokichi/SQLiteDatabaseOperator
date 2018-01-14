package com.eigoninaritai.sqlitedatabaseoperatorsample

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.eigoninaritai.sqlitedatabaseoperator.SQLiteTableOperator

/**
 *
 */
class MainActivity : AppCompatActivity() {
    /**
     *
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.println(Log.INFO, "TableInfo", SQLiteTableOperator.makeCreateTableQuery<SampleTable>())
        Log.println(Log.INFO, "TableInfo", SQLiteTableOperator.makeCreateTableQuery<SampleTable2>())
    }
}
