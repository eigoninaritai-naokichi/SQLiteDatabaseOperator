package com.eigoninaritai.sqlitedatabaseoperator

import kotlin.reflect.KClass

/**
 * SQLiteのテーブルカラムを表すアノテーション。
 *
 * Tableアノテーションが付与されているクラスのプロパティにこのアノテーションを付与することでテーブルのカラムとしてマッピングされる。
 * 付与されるプロパティの型によってテーブルのカラムとして定義される型が決まる。
 * テーブルのカラムの型に変換される型は以下になる。
 * String -> TEXT
 * Short, Int, Long -> INTEGER
 * Float, Double -> REAL
 * Boolean -> BOOLEAN(INTEGER) falseを0 trueを1で保存する。
 * Calendar -> TIMESTAMP(INTEGER) 時間値をミリ秒で保存する。
 *
 * このアノテーションのプロパティは、マッピングするカラムに対してのオプションを設定できる。
 *
 * @property name カラムの名前を表す。
 * 指定された文字列がSQLiteで使用するカラムの名前になる。
 * デフォルト値の空文字が指定されている場合、このアノテーションが付与されているプロパティの名前をカラム名として使用する。
 * @property length 定義されるカラムの長さを表す。
 * ColumnConstant.LENGTH_NOT_LIMITを指定すると型の長さは無制限となる。
 * 長さが指定されている場合、カラム挿入時、更新時に長さが超えていないかの確認が行われる。
 * デフォルトは無制限となる。
 * @property defaultValue 定義されるカラムの初期値を表す。
 * Boolean型には初期値としてColumnConstant.BOOLEAN_FALSE、ColumnConstant.BOOLEAN_TRUEが指定できる。
 * Calendar型にはColumnConstant.CURRENT_TIME_AS_DEFAULTで初期値として現在時刻を指定できる。
 * デフォルト値の空文字が指定されている場合、初期値を設定しない。
 * このアノテーションが付与されたプロパティがNull許容型かつ挿入する値として指定されない場合、エラーが発生する。
 * @property isAutoIncrement 定義されるカラムがオートインクリメントするかどうかを表す。
 * プライマリキーかつInt型の場合にのみ設定できる。
 * falseの場合、オートインクリメントを設定しない。
 * trueの場合、オートインクリメントを設定する。
 * デフォルト値はfalse。
 * @property triggers 定義されるカラムに対して使用したいカラムのトリガーを表す。
 * 指定されたトリガーがそのテーブル定義の最後に設定される。
 */
@Target(AnnotationTarget.PROPERTY)
annotation class Column(
    val name: String = "",
    val length: Int = ColumnConstant.LENGTH_NOT_LIMIT,
    val defaultValue: String = "",
    val isAutoIncrement: Boolean = false,
    vararg val triggers: SQLiteTrigger
)

/**
 * Columnアノテーションで使用するための定数。
 */
object ColumnConstant {
    /**
     * 型の長さを無制限に指定する場合に使用する。
     */
    const val LENGTH_NOT_LIMIT = 0

    /**
     * Boolean型のfalseを表す。
     */
    const val BOOLEAN_FALSE = 0

    /**
     * Boolean型のtrueを表す。
     */
    const val BOOLEAN_TRUE = 1

    /**
     * 初期値に現在時刻を指定する場合に使用する。
     */
    const val CURRENT_TIME_AS_DEFAULT = "(DATETIME('now', 'localtime'))"
}

/**
 * 標準で用意しているトリガーを表す。
 *
 * Columnアノテーションに渡すと指定されたトリガーがそのテーブル定義の最後に設定される。
 */
enum class SQLiteTrigger {
    /**
     * 更新した日時を指定したカラムに自動で書き込むトリガー。
     */
    UPDATED_TIME_TRIGGER
}

/**
 * SQLiteの外部キー制約を表すアノテーション。
 *
 * Columnアノテーションが付与されているプロパティにこのアノテーションを付与することで外部キー制約を設定することができる。
 *
 * @property tableClass 外部キー制約で参照するテーブルクラス。
 * @property columnName 外部キー制約で参照するテーブルクラスに定義されているカラム名。
 * @property isDeleteCascade ON DELETE CASCADEを設定するかどうかを表す。
 * falseの場合、設定をしない。
 * trueの場合、参照している外部キーが削除された場合、この外部キーを参照している行も削除される。
 * デフォルト値はfalse。
 * isDeleteCascadeがtrueの場合、参照テーブルが同じ他のカラムのisDeleteCascadeがfalseでもON DELETE CASCADEが設定される。
 */
@Target(AnnotationTarget.PROPERTY)
annotation class ForeignKey(
    val tableClass: KClass<*>,
    val columnName: String,
    val isDeleteCascade: Boolean = false
)

/**
 * SQLiteのテーブルカラムを表す。
 *
 * このクラスは、SQLiteTableDefineクラスに保持され、データベースとやり取りするためにSQLiteTableOperaterクラスから使用される。
 *
 * @property columnName カラムの名前を表す。
 * @property columnType カラムの型を表す。
 * @property columnLength カラムの長さを表す。
 * 長さが指定されている場合、カラム挿入時、更新時に長さが超えていないかの確認が行われる。
 * @property isAutoIncrement 定義されるカラムがオートインクリメントするかどうかを表す。
 * @property defaultValue 定義されるカラムの初期値を表す。
 * @property isNotNull 定義されるカラムがNOT NULLどうかを表す。
 * falseの場合、NOT NULLを設定しない。
 * trueの場合、NOT NULLを設定する。
 * @property foreignKeyDefine カラムが参照する外部キーを表す。
 * @constructor 問題があった場合エラーを発生させる。
 * @throws SQLiteColumnDefaultValueTypeMismatchException カラムの型と初期値の値が一致しない場合、実行時に発生する。
 */
@PublishedApi internal data class SQLiteColumnDefine(
    val columnName: String,
    val columnType: SQLiteType,
    val columnLength: Int,
    val isAutoIncrement: Boolean,
    val defaultValue: String?,
    val isNotNull: Boolean,
    val foreignKeyDefine: ForeignKeyDefine?
) {
    init {
        // 数値型、BOOLEAN型、DATETIME型に変換できない値が設定されていた場合、エラー
        when (columnType) {
            SQLiteType.TEXT -> {
                // 何も行わない
            }
            SQLiteType.INTEGER -> {
                try {
                    defaultValue?.toLong()
                }
                catch (otherProblem: Exception) {
                    throw SQLiteColumnDefaultValueTypeMismatchException("カラムの初期値に整数型に変換できない値が渡されています。:$columnName")
                }
            }
            SQLiteType.REAL -> {
                try {
                    defaultValue?.toDouble()
                }
                catch (otherProblem: Exception) {
                    throw SQLiteColumnDefaultValueTypeMismatchException("カラムの初期値に浮動小数型に変換できない値が渡されています。:$columnName")
                }
            }
            SQLiteType.BOOLEAN -> {
                val convertedValue: Int?
                try {
                    convertedValue = defaultValue?.toInt()
                }
                catch (otherProblem: Exception) {
                    throw SQLiteColumnDefaultValueTypeMismatchException("カラムの初期値にBOOLEAN型に変換できない値が渡されています。:$columnName")
                }
                if (
                    convertedValue != null &&
                    convertedValue != ColumnConstant.BOOLEAN_FALSE &&
                    convertedValue != ColumnConstant.BOOLEAN_TRUE
                ) {
                    throw SQLiteColumnDefaultValueTypeMismatchException("カラムの初期値にBOOLEAN型に変換できない値が渡されています。:$columnName")
                }
            }
            SQLiteType.TIMESTAMP -> {
                if (
                    defaultValue != null &&
                    defaultValue != ColumnConstant.CURRENT_TIME_AS_DEFAULT
                ) {
                    throw SQLiteColumnDefaultValueTypeMismatchException("カラムの初期値にTIMESTAMP型に変換できない値が渡されています。:$columnName")
                }
            }
        }
    }
}

/**
 * SQLiteの外部キー制約を表す。
 *
 * このクラスは、SQLiteColumnDefineクラスに保持され、SQLiteの外部キー制約の定義に使用される。
 *
 * @property tableName 外部キー制約で参照するテーブル名。
 * @property columnName 外部キー制約で参照するテーブルクラスに定義されているカラム名。
 * @property isDeleteCascade ON DELETE CASCADEを設定するかどうかを表す。
 * falseの場合、設定をしない。
 * trueの場合、参照している外部キーが削除された場合、この外部キーを参照している行も削除される。
 */
@PublishedApi internal data class ForeignKeyDefine(
    val tableName: String,
    val columnName: String,
    val isDeleteCascade: Boolean
)

/**
 * SQLiteで定義できる型を表す。
 */
@PublishedApi internal enum class SQLiteType {
    /**
     * TEXT型
     */
    TEXT,

    /**
     * INTEGER型
     */
    INTEGER,

    /**
     * REAL型
     */
    REAL,

    /**
     * BOOLEAN型
     */
    BOOLEAN,

    /**
     * TIMESTAMP型
     */
    TIMESTAMP
}

/**
 * テーブルにカラムが存在しない場合に発生する。
 *
 * @param message エラーの内容。
 */
open class SQLiteColumnNotFoundException(message: String) : RuntimeException(message)

/**
 * カラムの初期値とカラムの型が異なる場合に発生する。
 *
 * @param message エラーの内容。
 */
open class SQLiteColumnDefaultValueTypeMismatchException(message: String) : RuntimeException(message)
