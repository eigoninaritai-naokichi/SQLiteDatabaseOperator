package com.eigoninaritai.sqlitedatabaseoperator

import java.util.*
import kotlin.reflect.KProperty1

/**
 * SQLiteテーブルからデータを取得するための条件を表す基底クラス。
 *
 * このクラスを継承したクラスに条件作成に必要な要素を渡すことで、データの更新、削除、取得の条件として使用される。
 *
 * @property isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
abstract class WhereCondition(private val isAnd: Boolean = true) {
    companion object {
        /**
         * 渡されたデータ取得条件のリストからWHERE句を作成する。
         *
         * @param table WHERE句を作成する際に使用するテーブルクラス。
         * @param tableAlias 条件に使用するテーブルエイリアス名。
         * nullだった場合、エイリアスは使用しない。
         * @param whereConditions データ取得条件のリスト。
         * リストにある条件を連結し、1つのWHERE句を作成する。
         * @return WHERE句とWHERE句で条件に指定する値。
         */
        fun makeWhere(table: Any, tableAlias: String?, whereConditions: List<WhereCondition>): Pair<String, Array<String>> {
            var resultWhereClause = ""
            var resultWhereArgs = arrayOf<String>()
            whereConditions.forEach {
                if (resultWhereClause != "") resultWhereClause += if (it.isAnd) "AND " else "OR "
                val (whereClause, whereArgs) = it.makeWhere(table, tableAlias)
                resultWhereClause += "$whereClause\n"
                if (!whereArgs.isEmpty()) {
                    resultWhereArgs = resultWhereArgs.plus(whereArgs)
                }
            }
            return Pair(resultWhereClause, resultWhereArgs)
        }

        /**
         * 渡されたデータ取得条件のリストからWHERE句を文字列として作成する。
         *
         * @param table WHERE句を作成する際に使用するテーブルクラス。
         * @param tableAlias 条件に使用するテーブルエイリアス名。
         * nullだった場合、エイリアスは使用しない。
         * @param whereConditions データ取得条件のリスト。
         * リストにある条件を連結し、1つのWHERE句を作成する。
         * @return 渡されたデータ取得条件のリストから文字列化したWHERE句。
         */
        fun makeWhereString(table: Any, tableAlias: String?, whereConditions: List<WhereCondition>): String {
            var (whereClause, whereArgs) = makeWhere(table, tableAlias, whereConditions)
            whereArgs.forEach { whereClause = whereClause.replaceFirst("?", "'$it'") }
            return whereClause
        }
    }

    /**
     * WHERE句を作成する。
     *
     * @param table WHERE句を作成する際に使用するテーブルクラス。
     * @param tableAlias 条件に使用するテーブルエイリアス名。
     * nullだった場合、エイリアスは使用しない。
     * @return WHERE句とWHERE句で条件に指定する値。
     */
    abstract fun makeWhere(table: Any, tableAlias: String?): Pair<String, Array<String>>

    /**
     * 渡された値をString型に変換する。
     *
     * @param value String型に変換したい値。
     * @return 渡された値をString型に変換した値。
     */
    protected fun convertValueToValueString(value: Any?): String = when (value) {
        is String -> "'$value'"
        is Boolean -> if (value) ColumnConstant.BOOLEAN_TRUE.toString() else ColumnConstant.BOOLEAN_FALSE.toString()
        is Calendar -> value.timeInMillis.toString()
        else -> value?.toString() ?: "NULL"
    }
}

/**
 * 渡された値で、SQLiteテーブルからデータを取得するための条件を表す基底クラス。
 *
 * このクラスを継承したクラスに条件作成に必要な要素と値を渡すことで、データの更新、削除、取得の条件として使用される。
 * WhereConditionを継承したクラスとは異なり、任意の値で指定したい場合にこのクラスを継承したクラスを使用する。
 *
 * @property value 条件に使用する値。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
abstract class WhereConditionWithValue(protected val value: Any?, isAnd: Boolean = true) : WhereCondition(isAnd) {
    /**
     * 渡された値をString型に変換した値。
     *
     * 渡された値がBooleanの場合、ColumnConstant.BOOLEAN_TRUE、もしくはColumnConstant.BOOLEAN_FALSEをString型に変換する。
     * 渡された値がCalendarの場合、Long型に変換した値をString型に変換する。
     * 値がnullだった場合、文字列のNULLを返す。
     */
    protected val valueString: String
        get () = convertValueToValueString(value)
}

/**
 * 比較演算子を用いた条件を表す基底クラス。
 *
 * @property columnAnnotationProperty WHERE句で使用するColumnアノテーションが付与されたプロパティ。
 * このプロパティに関連するカラム名とプロパティの値を条件に使用する。
 * @property comparisonOperator 使用する比較演算子を表す。
 * 指定された比較演算子がWHERE句の中で使用される。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
abstract class ComparisonOperatorCondition(private val columnAnnotationProperty: KProperty1<*, *>, private val comparisonOperator: String, isAnd: Boolean = true) : WhereCondition(isAnd) {
    override fun makeWhere(table: Any, tableAlias: String?): Pair<String, Array<String>> {
        val columnName = SQLiteTableOperator.getColumnName(table, columnAnnotationProperty)
        val columnValue = SQLiteTableOperator.getColumnValue(table, columnAnnotationProperty)
        val columnValueString = convertValueToValueString(columnValue)
        val whereClause: String
        val whereArgs: Array<String>
        if (columnValue != null) {
            whereClause = SQLiteTableOperator.addTableAlias(tableAlias, "$columnName $comparisonOperator ?")
            whereArgs = arrayOf(columnValueString)
        }
        else {
            whereClause = SQLiteTableOperator.addTableAlias(tableAlias, "$columnName $comparisonOperator $columnValueString")
            whereArgs = arrayOf()
        }
        return Pair(whereClause, whereArgs)
    }
}

/**
 * 渡された値と比較演算子を用いた条件を表す基底クラス。
 *
 * @property columnAnnotationProperty WHERE句で使用するColumnアノテーションが付与されたプロパティ。
 * このプロパティに関連するカラム名を条件に使用する。
 * @property comparisonOperator 使用する比較演算子を表す。
 * 指定された比較演算子がWHERE句の中で使用される。
 * @param value 条件に使用する値。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
abstract class ComparisonOperatorConditionWithValue(private val columnAnnotationProperty: KProperty1<*, *>, private val comparisonOperator: String, value: Any?, isAnd: Boolean = true) : WhereConditionWithValue(value, isAnd) {
    override fun makeWhere(table: Any, tableAlias: String?): Pair<String, Array<String>> {
        val columnName = SQLiteTableOperator.getColumnName(table, columnAnnotationProperty)
        val whereClause: String
        val whereArgs: Array<String>
        if (value != null) {
            whereClause = SQLiteTableOperator.addTableAlias(tableAlias, "$columnName $comparisonOperator ?")
            whereArgs = arrayOf(valueString)
        }
        else {
            whereClause = SQLiteTableOperator.addTableAlias(tableAlias, "$columnName $comparisonOperator $valueString")
            whereArgs = arrayOf()
        }
        return Pair(whereClause, whereArgs)
    }
}

/**
 * 渡されたカラム名と値と比較演算子を用いた条件を表す基底クラス。
 *
 * @property columnName WHERE句で使用するカラム名。
 * @property comparisonOperator 使用する比較演算子を表す。
 * 指定された比較演算子がWHERE句の中で使用される。
 * @param value 条件に使用する値。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
abstract class ComparisonOperatorConditionWithString(private val columnName: String, private val comparisonOperator: String, value: Any?, isAnd: Boolean = true) : WhereConditionWithValue(value, isAnd) {
    override fun makeWhere(table: Any, tableAlias: String?): Pair<String, Array<String>> {
        val whereClause: String
        val whereArgs: Array<String>
        if (value != null) {
            whereClause = SQLiteTableOperator.addTableAlias(tableAlias, "$columnName $comparisonOperator ?")
            whereArgs = arrayOf(valueString)
        }
        else {
            whereClause = SQLiteTableOperator.addTableAlias(tableAlias, "$columnName $comparisonOperator $valueString")
            whereArgs = arrayOf()
        }
        return Pair(whereClause, whereArgs)
    }
}

/**
 * WHERE句での条件=を表す。
 *
 * @param columnAnnotationProperty WHERE句で使用するColumnアノテーションが付与されたプロパティ。
 * このプロパティに関連するカラム名とプロパティの値を条件に使用する。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class Equal(columnAnnotationProperty: KProperty1<*, *>, isAnd: Boolean = true) : ComparisonOperatorCondition(columnAnnotationProperty, "=", isAnd)

/**
 * 渡された値を使用して、WHERE句での条件=を表す。
 *
 * @param columnAnnotationProperty WHERE句で使用するColumnアノテーションが付与されたプロパティ。
 * このプロパティに関連するカラム名を条件に使用する。
 * @param value 条件に使用する値。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class EqualWithValue(columnAnnotationProperty: KProperty1<*, *>, value: Any?, isAnd: Boolean = true) : ComparisonOperatorConditionWithValue(columnAnnotationProperty, "=", value, isAnd)

/**
 * 渡されたカラム名と値を使用して、WHERE句での条件=を表す。
 *
 * @property columnName WHERE句で使用するカラム名。
 * @param value 条件に使用する値。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class EqualWithString(columnName: String, value: Any?, isAnd: Boolean = true) : ComparisonOperatorConditionWithString(columnName, "=", value, isAnd)

/**
 * WHERE句での条件<>を表す。
 *
 * @param columnAnnotationProperty WHERE句で使用するColumnアノテーションが付与されたプロパティ。
 * このプロパティに関連するカラム名とプロパティの値を条件に使用する。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class NotEqual(columnAnnotationProperty: KProperty1<*, *>, isAnd: Boolean = true) : ComparisonOperatorCondition(columnAnnotationProperty, "<>", isAnd)

/**
 * 渡された値を使用して、WHERE句での条件<>を表す。
 *
 * @param columnAnnotationProperty WHERE句で使用するColumnアノテーションが付与されたプロパティ。
 * このプロパティに関連するカラム名を条件に使用する。
 * @param value 条件に使用する値。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class NotEqualWithValue(columnAnnotationProperty: KProperty1<*, *>, value: Any?, isAnd: Boolean = true) : ComparisonOperatorConditionWithValue(columnAnnotationProperty, "<>", value, isAnd)

/**
 * 渡されたカラム名と値を使用して、WHERE句での条件<>を表す。
 *
 * @property columnName WHERE句で使用するカラム名。
 * @param value 条件に使用する値。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class NotEqualWithString(columnName: String, value: Any?, isAnd: Boolean = true) : ComparisonOperatorConditionWithString(columnName, "<>", value, isAnd)

/**
 * WHERE句での条件<を表す。
 *
 * @param columnAnnotationProperty WHERE句で使用するColumnアノテーションが付与されたプロパティ。
 * このプロパティに関連するカラム名とプロパティの値を条件に使用する。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class LessThan(columnAnnotationProperty: KProperty1<*, *>, isAnd: Boolean = true) : ComparisonOperatorCondition(columnAnnotationProperty, "<", isAnd)

/**
 * 渡された値を使用して、WHERE句での条件<を表す。
 *
 * @param columnAnnotationProperty WHERE句で使用するColumnアノテーションが付与されたプロパティ。
 * このプロパティに関連するカラム名を条件に使用する。
 * @param value 条件に使用する値。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class LessThanWithValue(columnAnnotationProperty: KProperty1<*, *>, value: Any?, isAnd: Boolean = true) : ComparisonOperatorConditionWithValue(columnAnnotationProperty, "<", value, isAnd)

/**
 * 渡されたカラム名と値を使用して、WHERE句での条件<を表す。
 *
 * @property columnName WHERE句で使用するカラム名。
 * @param value 条件に使用する値。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class LessThanWithString(columnName: String, value: Any?, isAnd: Boolean = true) : ComparisonOperatorConditionWithString(columnName, "<", value, isAnd)

/**
 * WHERE句での条件<=を表す。
 *
 * @param columnAnnotationProperty WHERE句で使用するColumnアノテーションが付与されたプロパティ。
 * このプロパティに関連するカラム名とプロパティの値を条件に使用する。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class LessThanOrEqual(columnAnnotationProperty: KProperty1<*, *>, isAnd: Boolean = true) : ComparisonOperatorCondition(columnAnnotationProperty, "<=", isAnd)

/**
 * 渡された値を使用して、WHERE句での条件<=を表す。
 *
 * @param columnAnnotationProperty WHERE句で使用するColumnアノテーションが付与されたプロパティ。
 * このプロパティに関連するカラム名を条件に使用する。
 * @param value 条件に使用する値。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class LessThanOrEqualWithValue(columnAnnotationProperty: KProperty1<*, *>, value: Any?, isAnd: Boolean = true) : ComparisonOperatorConditionWithValue(columnAnnotationProperty, "<=", value, isAnd)

/**
 * 渡されたカラム名と値を使用して、WHERE句での条件<=を表す。
 *
 * @property columnName WHERE句で使用するカラム名。
 * @param value 条件に使用する値。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class LessThanOrEqualWithString(columnName: String, value: Any?, isAnd: Boolean = true) : ComparisonOperatorConditionWithString(columnName, "<=", value, isAnd)

/**
 * WHERE句での条件>を表す。
 *
 * @param columnAnnotationProperty WHERE句で使用するColumnアノテーションが付与されたプロパティ。
 * このプロパティに関連するカラム名とプロパティの値を条件に使用する。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class GreaterThan(columnAnnotationProperty: KProperty1<*, *>, isAnd: Boolean = true) : ComparisonOperatorCondition(columnAnnotationProperty, ">", isAnd)

/**
 * 渡された値を使用して、WHERE句での条件>を表す。
 *
 * @param columnAnnotationProperty WHERE句で使用するColumnアノテーションが付与されたプロパティ。
 * このプロパティに関連するカラム名を条件に使用する。
 * @param value 条件に使用する値。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class GreaterThanWithValue(columnAnnotationProperty: KProperty1<*, *>, value: Any?, isAnd: Boolean = true) : ComparisonOperatorConditionWithValue(columnAnnotationProperty, ">", value, isAnd)

/**
 * 渡されたカラム名と値を使用して、WHERE句での条件>を表す。
 *
 * @property columnName WHERE句で使用するカラム名。
 * @param value 条件に使用する値。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class GreaterThanWithString(columnName: String, value: Any?, isAnd: Boolean = true) : ComparisonOperatorConditionWithString(columnName, ">", value, isAnd)

/**
 * WHERE句での条件>=を表す。
 *
 * @param columnAnnotationProperty WHERE句で使用するColumnアノテーションが付与されたプロパティ。
 * このプロパティに関連するカラム名とプロパティの値を条件に使用する。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class GreaterThanOrEqual(columnAnnotationProperty: KProperty1<*, *>, isAnd: Boolean = true) : ComparisonOperatorCondition(columnAnnotationProperty, ">=", isAnd)

/**
 * 渡された値を使用して、WHERE句での条件>=を表す。
 *
 * @param columnAnnotationProperty WHERE句で使用するColumnアノテーションが付与されたプロパティ。
 * このプロパティに関連するカラム名を条件に使用する。
 * @param value 条件に使用する値。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class GreaterThanOrEqualWithValue(columnAnnotationProperty: KProperty1<*, *>, value: Any?, isAnd: Boolean = true) : ComparisonOperatorConditionWithValue(columnAnnotationProperty, ">=", value, isAnd)

/**
 * 渡されたカラム名と値を使用して、WHERE句での条件>=を表す。
 *
 * @property columnName WHERE句で使用するカラム名。
 * @param value 条件に使用する値。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class GreaterThanOrEqualWithString(columnName: String, value: Any?, isAnd: Boolean = true) : ComparisonOperatorConditionWithString(columnName, ">=", value, isAnd)

/**
 * WHERE句での条件IS NULLを表す。
 *
 * @property columnAnnotationProperty WHERE句で使用するColumnアノテーションが付与されたプロパティ。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class IsNull(private val columnAnnotationProperty: KProperty1<*, *>, isAnd: Boolean = true) : WhereCondition(isAnd) {
    override fun makeWhere(table: Any, tableAlias: String?): Pair<String, Array<String>> {
        val columnName = SQLiteTableOperator.getColumnName(table, columnAnnotationProperty)
        val whereClause = SQLiteTableOperator.addTableAlias(tableAlias, "$columnName IS NULL")
        return Pair(whereClause, arrayOf())
    }
}

/**
 * WHERE句での条件IS NOT NULLを表す。
 *
 * @property columnAnnotationProperty WHERE句で使用するColumnアノテーションが付与されたプロパティ。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class IsNotNull(private val columnAnnotationProperty: KProperty1<*, *>, isAnd: Boolean = true) : WhereCondition(isAnd) {
    override fun makeWhere(table: Any, tableAlias: String?): Pair<String, Array<String>> {
        val columnName = SQLiteTableOperator.getColumnName(table, columnAnnotationProperty)
        val whereClause = SQLiteTableOperator.addTableAlias(tableAlias, "$columnName IS NOT NULL")
        return Pair(whereClause, arrayOf())
    }
}

/**
 * 渡された値を使用して、WHERE句での条件BETWEENを表す。
 *
 * @param columnAnnotationProperty WHERE句で使用するColumnアノテーションが付与されたプロパティ。
 * このプロパティに関連するカラム名を条件に使用する。
 * @param value1 BETWEENの左側に使用する値。
 * @property value2 BETWEENの右側に使用する値。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class Between(private val columnAnnotationProperty: KProperty1<*, *>, value1: Any?, private val value2: Any?, isAnd: Boolean = true) : WhereConditionWithValue(value1, isAnd) {
    override fun makeWhere(table: Any, tableAlias: String?): Pair<String, Array<String>> {
        val columnName = SQLiteTableOperator.getColumnName(table, columnAnnotationProperty)
        var whereClause = SQLiteTableOperator.addTableAlias(tableAlias, "$columnName BETWEEN ")
        var whereArgs = arrayOf<String>()
        if (value != null) {
            whereClause += "? AND "
            whereArgs = whereArgs.plus(valueString)
        }
        else {
            whereClause += "$valueString AND "
        }
        val valueString2 = convertValueToValueString(value2)
        if (value2 != null) {
            whereClause += "?"
            whereArgs = whereArgs.plus(valueString2)
        }
        else {
            whereClause += valueString2
        }
        return Pair(whereClause, whereArgs)
    }
}

/**
 * 渡されたカラム名と値を使用して、WHERE句での条件BETWEENを表す。
 *
 * @property columnName WHERE句で使用するカラム名。
 * @param value1 BETWEENの左側に使用する値。
 * @property value2 BETWEENの右側に使用する値。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class BetweenWithString(private val columnName: String, value1: Any?, private val value2: Any?, isAnd: Boolean = true) : WhereConditionWithValue(value1, isAnd) {
    override fun makeWhere(table: Any, tableAlias: String?): Pair<String, Array<String>> {
        var whereClause = SQLiteTableOperator.addTableAlias(tableAlias, "$columnName BETWEEN ")
        var whereArgs = arrayOf<String>()
        if (value != null) {
            whereClause += "? AND "
            whereArgs = whereArgs.plus(valueString)
        }
        else {
            whereClause += "$valueString AND "
        }
        val valueString2 = convertValueToValueString(value2)
        if (value2 != null) {
            whereClause += "?"
            whereArgs = whereArgs.plus(valueString2)
        }
        else {
            whereClause += valueString2
        }
        return Pair(whereClause, whereArgs)
    }
}

/**
 * 渡された値を使用して、WHERE句での条件LIKEを表す。
 *
 * @param columnAnnotationProperty WHERE句で使用するColumnアノテーションが付与されたプロパティ。
 * このプロパティに関連するカラム名を条件に使用する。
 * @param pattern LIKEに使用する文字列。
 * @property escape LIKEに使用するエスケープ文字列。
 * nullの場合、エスケープを使用しない。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class Like(private val columnAnnotationProperty: KProperty1<*, *>, pattern: String, private val escape: String? = null, isAnd: Boolean = true) : WhereConditionWithValue(pattern, isAnd) {
    override fun makeWhere(table: Any, tableAlias: String?): Pair<String, Array<String>> {
        val columnName = SQLiteTableOperator.getColumnName(table, columnAnnotationProperty)
        var whereClause = SQLiteTableOperator.addTableAlias(tableAlias, "$columnName LIKE ?")
        if (escape != null) whereClause += " ESCAPE ${convertValueToValueString(escape)}"
        val whereArgs = arrayOf(valueString)
        return Pair(whereClause, whereArgs)
    }
}

/**
 * 渡された条件を小括弧で囲んだ条件を表す。
 *
 * @param whereConditions WHERE句として作成されるWhereConditionクラス。
 * @param isAnd 条件作成時、直前の条件に対してANDで連結するか、ORで連結するかを表す。
 */
class Parenthesis(private val whereConditions: List<WhereCondition>, isAnd: Boolean = true) : WhereCondition(isAnd) {
    override fun makeWhere(table: Any, tableAlias: String?): Pair<String, Array<String>> {
        var (whereClause, whereArgs) = WhereCondition.makeWhere(table, tableAlias, whereConditions)
        whereClause = "($whereClause)"
        return Pair(whereClause, whereArgs)
    }
}