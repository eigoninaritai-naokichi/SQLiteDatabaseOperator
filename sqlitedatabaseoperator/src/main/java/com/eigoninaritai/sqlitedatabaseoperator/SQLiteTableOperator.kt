package com.eigoninaritai.sqlitedatabaseoperator

import android.content.ContentValues
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.Closeable
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure

/**
 * SQLiteデータベースのテーブルを操作するためのクラス。
 *
 * テーブルの作成をすることができる。
 * テーブル内のレコードの挿入、更新、削除をすることができる。
 *
 * @param T SQLiteOpenHelperを継承したクラス。
 * @property sqliteOpenHelper SQLiteOpenHelperを継承したクラスのインスタンス。
 * このプロパティを使用し、内部でデータベースの操作を行う。
 */
class SQLiteTableOperator<out T : SQLiteOpenHelper>(private val sqliteOpenHelper: T) : Closeable {
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
                foreignKeyDefineColumns.forEachIndexed { i, columnDefine ->
                    if (columnDefine.foreignKeyDefine != null) {
                        if (i > 0) {
                            columnNames += ", "
                            referenceColumnNames += ", "
                        }
                        columnNames += columnDefine.columnName
                        referenceColumnNames += columnDefine.foreignKeyDefine.columnName
                    }
                }
                createTableQuery += "    FOREIGN KEY($columnNames) REFERENCES $tableName($referenceColumnNames)"
                val foreignKeyActionDefine = sqliteTableDefine.foreignKeyActionDefines?.find { it.tableName == tableName }
                if (foreignKeyActionDefine != null) {
                    createTableQuery += when (foreignKeyActionDefine.updateAction) {
                        SQLiteForeignKeyAction.NO_ACTION -> " ON UPDATE NO ACTION"
                        SQLiteForeignKeyAction.RESTRICT -> " ON UPDATE RESTRICT"
                        SQLiteForeignKeyAction.SET_NULL -> " ON UPDATE SET NULL"
                        SQLiteForeignKeyAction.SET_DEFAULT -> " ON UPDATE SET DEFAULT"
                        SQLiteForeignKeyAction.CASCADE -> " ON UPDATE CASCADE"
                    }
                    createTableQuery += when (foreignKeyActionDefine.deleteAction) {
                        SQLiteForeignKeyAction.NO_ACTION -> " ON DELETE NO ACTION"
                        SQLiteForeignKeyAction.RESTRICT -> " ON DELETE RESTRICT"
                        SQLiteForeignKeyAction.SET_NULL -> " ON DELETE SET NULL"
                        SQLiteForeignKeyAction.SET_DEFAULT -> " ON DELETE SET DEFAULT"
                        SQLiteForeignKeyAction.CASCADE -> " ON DELETE CASCADE"
                    }
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
         * 指定されたテーブルクラスからContentValuesを取得する。
         *
         * Columnアノテーションが付与されたプロパティにnullが代入されている場合、そのプロパティの値はContentValuesに代入されない。
         *
         * @param isInsert インサート用のContentValuesかどうかを表す。
         * trueの場合、インサート用のContentValuesの取得を表す。
         * falseの場合、アップデート用のContentValuesの取得を表す。
         * Columnアノテーションには、Columnアノテーションが付与されたプロパティの値をインサート時、アップデート時に使用するかどうかの指定がある。
         * その指定を適切に使用するために、このフラグでインサート用かアップデート用かを判断する。
         * @param table ContentValuesに値を設定したいテーブルクラス。
         * @param eliminationColumns ContentValuesへの代入から除外するColumnアノテーションが付与されたプロパティのリスト。
         * @return 指定されたテーブルクラスから取得したContentValues。
         */
        private fun getContentValuesFromTable(isInsert: Boolean, table: Any, eliminationColumns: List<KProperty1<*, *>> = listOf()) = ContentValues().apply {
            val sqliteTableDefine = getSQLiteTableDefine(table::class)
            sqliteTableDefine.columnDefines.forEach { columnDefine ->
                // 値に使用しない設定がされているカラムの場合、次のカラム定義を参照する
                if (
                    (isInsert && !columnDefine.shouldUseInInsert) ||
                    (!isInsert && !columnDefine.shouldUseInUpdate) ||
                    eliminationColumns.contains(columnDefine.columnAnnotationProperty)
                ) return@forEach

                // カラムの値がnullでない場合、値を取得
                val currentColumnProperty = table::class.memberProperties.first { it == columnDefine.columnAnnotationProperty }
                val columnValue = getColumnValue(table, currentColumnProperty)
                when (columnValue) {
                    is String -> put(columnDefine.columnName, columnValue)
                    is Short -> put(columnDefine.columnName, columnValue)
                    is Int -> put(columnDefine.columnName, columnValue)
                    is Long -> put(columnDefine.columnName, columnValue)
                    is Float -> put(columnDefine.columnName, columnValue)
                    is Double -> put(columnDefine.columnName, columnValue)
                }
            }
        }

        /**
         * 指定された句の頭にテーブルエイリアスを追加する。
         *
         * テーブルエイリアスがnullの場合、指定された句をそのまま返す。
         *
         * @param tableAlias 句に追加するテーブルエイリアス名。
         * @param clause テーブルエイリアスを追加する句。
         */
        fun addTableAlias(tableAlias: String?, clause: String) = if (tableAlias != null) "$tableAlias.$clause" else clause

        /**
         * 指定されたテーブルクラスと指定されたColumnアノテーションが付与されたプロパティからカラム名を取得する。
         *
         * @param table 指定されたColumnアノテーションが付与されたプロパティを持つテーブルクラス。
         * @param columnAnnotationProperty 指定されたColumnアノテーションが付与されたプロパティ。
         * @return 指定されたテーブルクラスと指定されたColumnアノテーションが付与されたプロパティから取得したカラム名。
         * @throws SQLiteColumnNotFoundException 指定されたテーブルクラスに指定されたColumnアノテーションが付与されたプロパティが存在しない場合、実行時に発生する。
         */
        fun getColumnName(table: Any, columnAnnotationProperty: KProperty1<*, *>): String = getSQLiteTableDefine(table::class).columnDefines.find { it.columnAnnotationProperty == columnAnnotationProperty }?.columnName ?: throw SQLiteColumnNotFoundException("指定されたテーブルクラスに指定されたColumnアノテーションが付与されたプロパティが存在しません。\nテーブルクラス:${getSQLiteTableDefine(table::class).tableName}")

        /**
         * 指定されたテーブルクラスと指定されたColumnアノテーションが付与されたプロパティからそのプロパティの値を取得する。
         *
         * 指定されたプロパティの型がBooleanの場合、ColumnConstant.BOOLEAN_TRUE、もしくはColumnConstant.BOOLEAN_FALSEを返す。
         * 指定されたプロパティの型がCalendarの場合、Long型に変換して返す。
         *
         * @param table 指定されたColumnアノテーションが付与されたプロパティを持つテーブルクラス。
         * @param columnAnnotationProperty 指定されたColumnアノテーションが付与されたプロパティ。
         * @return 指定されたテーブルクラスと指定されたColumnアノテーションが付与されたプロパティから取得した値。
         */
        fun getColumnValue(table: Any, columnAnnotationProperty: KProperty1<*, *>): Any? = when (columnAnnotationProperty.returnType.jvmErasure) {
            Boolean::class -> {
                val boolean = columnAnnotationProperty.getter.call(table) as Boolean
                if (boolean) ColumnConstant.BOOLEAN_TRUE else ColumnConstant.BOOLEAN_FALSE
            }
            Calendar::class -> {
                val calendar = columnAnnotationProperty.getter.call(table) as Calendar
                calendar.timeInMillis
            }
            else -> columnAnnotationProperty.getter.call(table)
        }

        /**
         * 指定されたテーブルクラスと指定されたColumnアノテーションが付与されたプロパティからそのプロパティの値をString型で返す。
         *
         * 値がnullだった場合、文字列のNULLを返す。
         *
         * @param table 指定されたColumnアノテーションが付与されたプロパティを持つテーブルクラス。
         * @param columnAnnotationProperty 指定されたColumnアノテーションが付与されたプロパティ。
         * @return 指定されたテーブルクラスと指定されたColumnアノテーションが付与されたプロパティから取得した値をString型に変換した値。
         */
        fun getColumnValueString(table: Any, columnAnnotationProperty: KProperty1<*, *>): String {
            val columnValue = getColumnValue(table, columnAnnotationProperty)
            return columnValue?.toString() ?: "NULL"
        }

        /**
         * 指定されたテーブルクラスとColumnアノテーションが付与されたプロパティのリストからカラム名を連結した文字列を取得する。
         *
         * @param table 指定されたColumnアノテーションが付与されたプロパティを持つテーブルクラス。
         * @param tableAlias カラム名に使用するテーブルエイリアス名。
         * nullだった場合、エイリアスは使用しない。
         * @param columnAnnotationProperties 指定されたColumnアノテーションが付与されたプロパティのリスト。
         * @return 指定されたテーブルクラスと指定されたColumnアノテーションが付与されたプロパティのリストから取得したカラム名を連結した文字列。
         */
        fun getColumnNames(table: Any, tableAlias: String?, columnAnnotationProperties: List<KProperty1<*, *>>): Array<String >{
            val columns = mutableListOf<String>()
            columnAnnotationProperties.forEach { columnAnnotationProperty ->
                columns.add(addTableAlias(tableAlias, SQLiteTableOperator.getColumnName(table, columnAnnotationProperty)))
            }
            return columns.toTypedArray()
        }

        /**
         * 指定されたテーブルクラスとCursorのインスタンスから指定されたテーブルクラスのインスタンスを作成する。
         *
         * @param T インスタンスを作成するテーブルクラスを表す。
         * @param tableClass インスタンスを作成するテーブルクラス。
         * @param cursor テーブルクラスのColumnアノテーションが付与されたプロパティと一致するカラムの値を持つCursorインスタンス。
         * このCursorのインスタンスを使用し、テーブルクラス内のColumnアノテーションが付与されたプロパティに値を設定する。
         * nullが指定されている場合、指定されたテーブルクラスの空のインスタンスを作成する。
         * @return 指定されたテーブルクラスとCursorのインスタンスから作成した指定されたテーブルクラスのインスタンス。
         * cursorのインスタンスがnullの場合、指定されたテーブルクラスの空のインスタンスを作成する。
         */
        @PublishedApi internal inline fun <reified T> makeInstanceFromCursor(tableClass: KClass<*>, cursor: Cursor? = null): T {
            // コンストラクタに渡すパラメータと値を取得
            val sqliteTableDefine = getSQLiteTableDefine(tableClass)
            val constructor = tableClass.constructors.first()
            val constructorArgs = mutableMapOf<KParameter, Any?>()
            constructor.parameters.forEach { parameter ->
                // Columnアノテーションが付与されたプロパティ名とテーブルクラスのコンストラクタのパラメータ名が一致するカラム定義を取得
                val targetColumnDefine = sqliteTableDefine.columnDefines.find { it.columnAnnotationProperty.name == parameter.name }

                // Columnアノテーションが付与されたプロパティ名とテーブルクラスのコンストラクタのパラメータ名が一致するカラム定義が存在しないかつ、パラメータがデフォルト引数の場合、現在のパラメータを設定しない
                if (
                    targetColumnDefine == null &&
                    parameter.isOptional
                ) return@forEach

                // Columnアノテーションが付与されたプロパティ名とテーブルクラスのコンストラクタのパラメータ名が一致するカラム定義が存在しない場合、エラー
                // Columnアノテーションが付与されたプロパティの型とテーブルクラスのコンストラクタのパラメータの型が一致しない場合、エラー
                if (
                    targetColumnDefine == null ||
                    targetColumnDefine.columnAnnotationProperty.returnType != parameter.type
                ) throw IllegalArgumentException("テーブルクラスのコンストラクタにColumnアノテーションが付与されたプロパティと一致するパラメータがないため、テーブルクラスのインスタンス作成に失敗しました。")

                // コンストラクタに渡す値を作成
                constructorArgs[parameter] = getParameterValueFromCursor(targetColumnDefine.columnName, parameter.type, cursor)
            }

            // インスタンスを作成し、カラム定義と一致するミュータブルなプロパティに値を設定
            val madeInstance = constructor.callBy(constructorArgs)
            if (madeInstance is T)
            {
                sqliteTableDefine.columnDefines.forEach { targetColumnDefine ->
                    val targetMutableProperty = tableClass.memberProperties.filterIsInstance<KMutableProperty1<T, Any?>>().find { it == targetColumnDefine.columnAnnotationProperty }
                    targetMutableProperty?.set(madeInstance, getParameterValueFromCursor(targetColumnDefine.columnName, targetColumnDefine.columnAnnotationProperty.returnType, cursor))
                }
            }
            else throw IllegalStateException("テーブルクラスのインスタンス作成に失敗しました。")
            return madeInstance
        }

        /**
         * 指定されたカラム名に一致するカラムの値を指定されたCursorから取得する。
         *
         * @param columnName Cursorから取得するカラム名。
         * @param type Cursorから取得するカラムの型。
         * @param cursor 指定されたカラムを持つCursor。
         * nullが指定されている場合、指定されたカラムの型のデフォルト値を返す。
         * @return 指定されたカラム名に一致するカラムの値。
         * cursorがnullの場合、指定された絡むの型のデフォルト値を返す。
         * @throws IllegalArgumentException テーブルクラスで扱えない型が指定されている場合、実行時に発生する。
         */
        @PublishedApi internal fun getParameterValueFromCursor(columnName: String, type: KType, cursor: Cursor? = null): Any? {
            val parameterValue: Any?
            val columnIndex = cursor?.getColumnIndex(columnName) ?: -1
            if (
                cursor != null &&
                columnIndex >= 0
            ) {
                parameterValue = when (type.jvmErasure) {
                    String::class -> cursor.getString(columnIndex)
                    Short::class -> cursor.getShort(columnIndex)
                    Int::class -> cursor.getInt(columnIndex)
                    Long::class -> cursor.getLong(columnIndex)
                    Float::class -> cursor.getFloat(columnIndex)
                    Double::class -> cursor.getDouble(columnIndex)
                    Boolean::class -> cursor.getInt(columnIndex) == ColumnConstant.BOOLEAN_TRUE
                    Calendar::class -> Calendar.getInstance().apply { timeInMillis = cursor.getLong(columnIndex) }
                    else -> throw IllegalArgumentException("テーブルクラスで扱えない型が使用されています。")
                }
            }
            else {
                parameterValue = if (type.isMarkedNullable) {
                    null
                }
                else {
                    when (type.jvmErasure) {
                        String::class -> ""
                        Short::class, Int::class, Long::class, Float::class, Double::class -> 0
                        Boolean::class -> false
                        Calendar::class -> Calendar.getInstance()
                        else -> throw IllegalArgumentException("テーブルクラスで扱えない型が使用されています。")
                    }
                }
            }
            return parameterValue
        }
    }

    /**
     * 読み取り専用のSQLiteデータベース操作インスタンスを返す。
     */
    val readableDatabase: SQLiteDatabase
        get() = sqliteOpenHelper.readableDatabase!!

    /**
     * 読み書き可能なSQLiteデータベース操作インスタンスを保持する。
     */
    private var _writableDatabase: SQLiteDatabase? = null

    /**
     * 読み書き可能なSQLiteデータベース操作インスタンスを返す。
     */
    val writableDatabase: SQLiteDatabase
        get() {
            if (_writableDatabase == null) {
                _writableDatabase = sqliteOpenHelper.writableDatabase!!
            }
            return _writableDatabase!!
        }

    /**
     * BEGIN TRANSACTIONを行う。
     */
    fun beginTransaction() = writableDatabase.beginTransaction()

    /**
     * TRANSACTIONが成功したことを通知する。
     */
    fun setTransactionSuccessful() = writableDatabase.setTransactionSuccessful()

    /**
     * TRANSACTIONを終了する。
     */
    fun endTransaction() = writableDatabase.endTransaction()

    /**
     * 指定されたテーブルクラスを使用し、SQLiteテーブルにインサートする。
     *
     * @param table Tableアノテーションが付与されたクラス。
     * @param eliminationColumns インサートに使用しないColumnアノテーションが付与されたプロパティのリスト。
     * 指定されたプロパティはINSERT文に含められず除外される。
     * @return 新しくインサートされた行のID。
     * @throws SQLiteAnnotationNotAttachedException 渡されたテーブルクラスにTableアノテーションが付与されていない場合、実行時に発生する。
     * 渡されたテーブルクラスの1つ以上のプロパティにColumnアノテーションが付与されていない場合、実行時に発生する。
     * @throws SQLException インサート時に何らかの問題が発生した場合、実行時に発生する。
     */
    fun insert(table: Any, eliminationColumns: List<KProperty1<*, *>> = listOf()): Long {
        val contentValues = SQLiteTableOperator.getContentValuesFromTable(true, table, eliminationColumns)
        val sqliteTableDefine = SQLiteTableOperator.getSQLiteTableDefine(table::class)
        val result = writableDatabase.insert(sqliteTableDefine.tableName, null, contentValues)
        if (result <= -1) throw SQLException("インサート時に問題が発生しました。")
        return result
    }

    /**
     * 指定されたテーブルクラスを使用し、条件に従ってSQLiteテーブルを更新する。
     *
     * @param table Tableアノテーションが付与されたクラス。
     * @param whereConditions アップデートに使用する条件。
     * nullの場合、条件を指定しない。
     * @param eliminationColumns アップデートに使用しないColumnアノテーションが付与されたプロパティのリスト。
     * 指定されたプロパティはUPDATE文に含められず除外される。
     * @return 更新された行の数。
     * @throws SQLiteAnnotationNotAttachedException 渡されたテーブルクラスにTableアノテーションが付与されていない場合、実行時に発生する。
     * 渡されたテーブルクラスの1つ以上のプロパティにColumnアノテーションが付与されていない場合、実行時に発生する。
     */
    fun update(table: Any, whereConditions: List<WhereCondition>?, eliminationColumns: List<KProperty1<*, *>> = listOf()): Int {
        val contentValues = SQLiteTableOperator.getContentValuesFromTable(false, table, eliminationColumns)
        val (whereClause, whereArgs) = if (whereConditions != null) WhereCondition.makeWhere(table, null, whereConditions) else Pair(null, null)
        val sqliteTableDefine = SQLiteTableOperator.getSQLiteTableDefine(table::class)
        return writableDatabase.update(sqliteTableDefine.tableName, contentValues, whereClause, whereArgs)
    }

    /**
     * 指定されたテーブルクラスを使用し、条件に従ってSQLiteテーブルを更新する。
     *
     * @param table Tableアノテーションが付与されたクラス。
     * @param whereClause WHERE句。
     * @param whereArgs WHERE句内に設置した?を値に置き換えるための配列。
     * @param eliminationColumns アップデートに使用しないColumnアノテーションが付与されたプロパティのリスト。
     * 指定されたプロパティはUPDATE文に含められず除外される。
     * @return 更新された行の数。
     * @throws SQLiteAnnotationNotAttachedException 渡されたテーブルクラスにTableアノテーションが付与されていない場合、実行時に発生する。
     * 渡されたテーブルクラスの1つ以上のプロパティにColumnアノテーションが付与されていない場合、実行時に発生する。
     */
    fun update(table: Any, whereClause: String?, whereArgs: Array<String>?, eliminationColumns: List<KProperty1<*, *>> = listOf()): Int {
        val contentValues = SQLiteTableOperator.getContentValuesFromTable(false, table, eliminationColumns)
        val sqliteTableDefine = SQLiteTableOperator.getSQLiteTableDefine(table::class)
        return writableDatabase.update(sqliteTableDefine.tableName, contentValues, whereClause, whereArgs)
    }

    /**
     * 指定されたテーブルクラスを使用し、条件に従ってSQLiteテーブルから行を削除する。
     *
     * @param T Tableアノテーションが付与されたクラス。
     * @param table Tableアノテーションが付与されたクラス。
     * @param whereConditions 削除に使用する条件。
     * @return 削除された行の数。
     * @throws SQLiteAnnotationNotAttachedException 渡されたテーブルクラスにTableアノテーションが付与されていない場合、実行時に発生する。
     * 渡されたテーブルクラスの1つ以上のプロパティにColumnアノテーションが付与されていない場合、実行時に発生する。
     */
    inline fun <reified T> delete(table: T, whereConditions: List<WhereCondition>?): Int {
        val (whereClause, whereArgs) = if (whereConditions != null) WhereCondition.makeWhere(table as Any, null, whereConditions) else Pair(null, null)
        val sqliteTableDefine = SQLiteTableOperator.getSQLiteTableDefine(T::class)
        return writableDatabase.delete(sqliteTableDefine.tableName, whereClause, whereArgs)
    }

    /**
     * 指定されたテーブルクラスを使用し、条件に従ってSQLiteテーブルから行を削除する。
     *
     * @param T Tableアノテーションが付与されたクラス。
     * @param whereClause WHERE句。
     * @param whereArgs WHERE句内に設置した?を値に置き換えるための配列。
     * @return 削除された行の数。
     * @throws SQLiteAnnotationNotAttachedException 渡されたテーブルクラスにTableアノテーションが付与されていない場合、実行時に発生する。
     * 渡されたテーブルクラスの1つ以上のプロパティにColumnアノテーションが付与されていない場合、実行時に発生する。
     */
    inline fun <reified T> delete(whereClause: String? = null, whereArgs: Array<String>? = null): Int {
        val sqliteTableDefine = SQLiteTableOperator.getSQLiteTableDefine(T::class)
        return writableDatabase.delete(sqliteTableDefine.tableName, whereClause, whereArgs)
    }

    /**
     * 指定されたテーブルクラスのリストを指定された条件でSQLiteのテーブルから取得する。
     *
     * @param T SQLiteのテーブルから取得したいテーブルクラス。
     * @param whereConditions データ取得に使用する条件。
     * nullの場合、条件を指定しない。
     * @param groupBy GROUP BYを表す。
     * nullの場合、GROUP BYを指定しない。
     * @param having HAVINGを表す。
     * nullの場合、HAVINGを指定しない。
     * @param orderBy ORDER BYを表す。
     * nullの場合、ORDER BYを指定しない。
     * @param columnAnnotationProperties 取得したいカラムのリスト。
     * nullの場合、全てのカラムを取得する。
     * @param distinct DISTINCTを行うかどうかを表す。
     * @param limit 取得するデータの上限を表す。
     * nullの場合、上限を指定しない。
     * @return 指定された条件でSQLiteのテーブルから取得したテーブルクラスのリスト。
     */
    inline fun <reified T> selectDataList(whereConditions: List<WhereCondition>? = null, groupBy: GroupBy? = null, having: Having? = null, orderBy: OrderBy? = null, columnAnnotationProperties: List<KProperty1<*, *>>? = null, distinct: Boolean = false, limit: Int? = null): List<T> {
        val tableClass = T::class
        val emptyTableInstance: Any = SQLiteTableOperator.makeInstanceFromCursor(tableClass, null)

        // データ取得で使用する条件の準備
        val columns = if (columnAnnotationProperties != null) SQLiteTableOperator.getColumnNames(emptyTableInstance, null, columnAnnotationProperties) else arrayOf()
        val (whereClause, whereArgs) = if (whereConditions != null) WhereCondition.makeWhere(emptyTableInstance, null, whereConditions) else Pair(null, arrayOf<String>())
        val groupByClause = groupBy?.makeClause(emptyTableInstance, null)
        val havingClause = having?.makeClause(emptyTableInstance, null)
        val orderByClause = orderBy?.makeClause(emptyTableInstance, null)

        // データを取得し、取得したデータでテーブルクラスのインスタンスを作成し、リストにする
        val sqliteTableDefine = SQLiteTableOperator.getSQLiteTableDefine(tableClass)
        val selectList: MutableList<T> = mutableListOf()
        readableDatabase.query(distinct, sqliteTableDefine.tableName, columns, whereClause, whereArgs, groupByClause, havingClause, orderByClause, limit?.toString())!!.use { cursor ->
            if (!cursor.moveToFirst()) return selectList.toList()
            do {
                selectList.add(SQLiteTableOperator.makeInstanceFromCursor(tableClass, cursor))
            } while (cursor.moveToNext())
        }
        return selectList.toList()
    }

    /**
     * 保持している読み書き可能なSQLiteデータベース操作インスタンスをクローズする。
     */
    override fun close() {
        _writableDatabase?.close()
    }
}