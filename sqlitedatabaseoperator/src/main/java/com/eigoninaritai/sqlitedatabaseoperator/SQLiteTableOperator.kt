package com.eigoninaritai.sqlitedatabaseoperator

import kotlin.reflect.KClass

/**
 * SQLiteデータベースのテーブルを操作するためのクラス。
 *
 * テーブルの作成をすることができる。
 * テーブル内のレコードの挿入、更新、削除をすることができる。
 *
 * @param T SQLiteOpenHelperを継承したクラス。
 * このパラメータを使用し、データベースの操作を行う。
 * @property context SQLiteOpenHelperを使用するために必要なインスタンス。
 */
class SQLiteTableOperator {
    companion object {
        /**
         * 各テーブルのテーブル定義のキャッシュ。
         *
         * キャッシュしたテーブル定義は以下の動作で使用される。
         * テーブル作成
         * レコード挿入、更新、削除
         * レコード挿入、更新時の入力値確認
         */
        private val sqliteTableDefineMap: MutableMap<KClass<*>, SQLiteTableDefine> = mutableMapOf()

        /**
         * 指定されたテーブルクラスからそのテーブルの定義を取得する。
         *
         * キャッシュしたテーブル定義を取得する。
         * テーブル定義のキャッシュがない場合、指定されたテーブルクラスのアノテーションからテーブル定義を作成する。
         */
        @PublishedApi internal fun getSQLiteTableDefine(tableClass: KClass<*>): SQLiteTableDefine {
            val sqliteTableDefine: SQLiteTableDefine
            val sqliteTableDefineTemp = sqliteTableDefineMap[tableClass]
            if (sqliteTableDefineTemp != null) {
                sqliteTableDefine = sqliteTableDefineTemp
            }
            else {
                sqliteTableDefine = SQLiteAnnotationOperator.createTableDefine(tableClass)
                sqliteTableDefineMap[tableClass] = sqliteTableDefine
            }
            return sqliteTableDefine
        }

        /**
         * CREATE TABLE文を返す。
         *
         * Tableアノテーションが付与されたクラスからCREATE TABLE文を作成する。
         *
         * @param T Tableアノテーションが付与されたクラス。
         * @return CREATE TABLE文。
         */
        inline fun <reified T> makeCreateTableQuery(): String {
            // テーブル定義を取得
            val sqliteTableDefine = getSQLiteTableDefine(T::class)

            // CREATE TABLE文作成開始
            var createTableQuery = "CREATE TABLE ${sqliteTableDefine.tableName} (\n"

            // 各カラム定義からカラム名、型、長さ、オートインクリメント、デフォルト値、NOT NULLかどうかを定義
            sqliteTableDefine.columnDefines.forEachIndexed { i, columnDefine ->
                if (i > 0) createTableQuery += ",\n"
                val columnType = when (columnDefine.columnType) {
                    SQLiteType.TEXT -> "TEXT"
                    SQLiteType.INTEGER, SQLiteType.BOOLEAN, SQLiteType.TIMESTAMP -> "INTEGER"
                    SQLiteType.REAL -> "REAL"
                }
                createTableQuery += "    ${columnDefine.columnName}"
                createTableQuery += " $columnType"
                if (columnDefine.columnLength != ColumnConstant.LENGTH_NOT_LIMIT) createTableQuery += "(${columnDefine.columnLength})"
                if (columnDefine.isAutoIncrement) createTableQuery += " AUTOINCREMENT"
                if (columnDefine.defaultValue != null) createTableQuery += " DEFAULT ${columnDefine.defaultValue}"
                if (columnDefine.isNotNull) createTableQuery += " NOT NULL"
            }

            // プライマリキーを定義
            if (sqliteTableDefine.primaryKeyColumnNames != null) {
                createTableQuery += ",\n"
                createTableQuery += "    PRIMARY KEY("
                sqliteTableDefine.primaryKeyColumnNames.forEachIndexed { i, columnName ->
                    if (i > 0) createTableQuery += ", "
                    createTableQuery += columnName
                }
                createTableQuery += ")"
            }

            // ユニークを定義
            if (sqliteTableDefine.uniqueColumnNames != null) {
                createTableQuery += ",\n"
                createTableQuery += "    UNIQUE("
                sqliteTableDefine.uniqueColumnNames.forEachIndexed { i, columnName ->
                    if (i > 0) createTableQuery += ", "
                    createTableQuery += columnName
                }
                createTableQuery += ")"
            }

            // 外部キー制約を定義
            val foreignKeyDefineColumnsMap = getForeignKeyDefineColumnsMap(sqliteTableDefine.columnDefines)
            foreignKeyDefineColumnsMap.forEach { (tableName, foreignKeyDefineColumns) ->
                createTableQuery += ",\n"
                var columnNames = ""
                var referenceColumnNames = ""
                var isDeleteCascade = false
                foreignKeyDefineColumns.forEachIndexed { i, columnDefine ->
                    if (columnDefine.foreignKeyDefine != null) {
                        if (i > 0) {
                            columnNames += ", "
                            referenceColumnNames += ", "
                        }
                        columnNames += columnDefine.columnName
                        referenceColumnNames += columnDefine.foreignKeyDefine.columnName
                        if(columnDefine.foreignKeyDefine.isDeleteCascade) {
                            isDeleteCascade = true
                        }
                    }
                }
                createTableQuery += "    FOREIGN KEY($columnNames) REFERENCES $tableName($referenceColumnNames)"
                if (isDeleteCascade) {
                    createTableQuery += " ON DELETE CASCADE"
                }
            }
            createTableQuery += "\n);"

            // インデックスを定義
            sqliteTableDefine.indexColumnNamesList?.forEach { indexColumnNames ->
                createTableQuery += "\n"
                var indexNames = ""
                var targetColumnNames = ""
                indexColumnNames.forEachIndexed { i, columnName ->
                    if (i > 0) targetColumnNames += ", "
                    indexNames += "_$columnName"
                    targetColumnNames += columnName
                }
                createTableQuery += "CREATE INDEX ${sqliteTableDefine.tableName}${indexNames}_index ON ${sqliteTableDefine.tableName}($targetColumnNames);"
            }

            // 特殊な処理を定義
            sqliteTableDefine.specifiedQueries?.forEach {
                createTableQuery += "\n"
                createTableQuery += it
            }
            return createTableQuery
        }

        /**
         * 外部キー制約が設定されているカラム定義を外部キー制約で使用されるテーブル名ごとにまとめたマップを返す。
         *
         * @param columnDefines 指定されたカラム定義。
         * @return 外部キーが設定されているカラム定義のリストを外部キー制約で使用されるテーブル名ごとにまとめたマップ。
         */
        @PublishedApi internal fun getForeignKeyDefineColumnsMap(columnDefines: List<SQLiteColumnDefine>): Map<String, List<SQLiteColumnDefine>> = columnDefines.filter { it.foreignKeyDefine != null }.groupBy { it.foreignKeyDefine!!.tableName }

        /**
         * 読み込み専用のSQLiteデータベース操作インスタンスを返す。
         *
         * @return 読み込み専用のSQLiteデータベース操作インスタンス。
         */
        private fun getReadableDatabase() {
        }

        /**
         * 読み書き可能なSQLiteデータベース操作インスタンスを返す。
         *
         * @return 読み書き可能なSQLiteデータベース操作インスタンス。
         */
        private fun getWritableDatabase() {
        }
    }
}
