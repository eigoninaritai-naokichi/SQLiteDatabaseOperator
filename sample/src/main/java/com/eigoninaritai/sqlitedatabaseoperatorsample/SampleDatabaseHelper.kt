package com.eigoninaritai.sqlitedatabaseoperatorsample

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import com.eigoninaritai.sqlitedatabaseoperator.SQLiteTableOperator

/**
 * サンプルデータベースのヘルパークラス。
 */
class SampleDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        /**
         *  データベースのバージョンを表す。
         */
        const val DATABASE_VERSION = 1

        /**
         * データベースの名前を表す。
         */
        const val DATABASE_NAME = "Sample.db"
    }

    /**
     * サンプルのテーブルを作成する。
     */
    override fun onCreate(database: SQLiteDatabase?) {
        if (database == null) return
        try {
            database.beginTransaction()
            database.execSQL(SQLiteTableOperator.makeCreateTableQuery<SampleTable>())
            database.execSQL(SQLiteTableOperator.makeCreateTableQuery<SampleTable2>())
            database.setTransactionSuccessful()
        }
        finally {
            database.endTransaction()
        }
    }

    /**
     * データベースの更新を行う。
     */
    override fun onUpgrade(database: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    }

    /**
     * SDKのバージョンがJelly Bean未満の場合、外部キー制約を有効にする。
     *
     * SDKのバージョンがJelly Bean以上の場合、onConfigureで外部キー制約を有効にする。
     */
    override fun onOpen(database: SQLiteDatabase?) {
        super.onOpen(database)
        if (database == null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            if (database.isReadOnly) database.execSQL("PRAGMA foreign_keys=ON;")
        }
    }

    /**
     * SDKのバージョンがJelly Bean以上の場合、外部キー制約を有効にする。
     *
     * SDKのバージョンがJelly Bean未満の場合、onOpenで外部キー制約を有効にする。
     */
    override fun onConfigure(database: SQLiteDatabase?) {
        super.onConfigure(database)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            database?.setForeignKeyConstraintsEnabled(true)
        }
    }
}