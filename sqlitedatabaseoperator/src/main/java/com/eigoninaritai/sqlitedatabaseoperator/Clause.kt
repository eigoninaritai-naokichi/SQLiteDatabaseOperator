package com.eigoninaritai.sqlitedatabaseoperator

import kotlin.reflect.KProperty1

/**
 * SQLiteの句を表す基底クラス。
 *
 * @property clauseName 句の名前。
 */
abstract class Clause(private val clauseName: String) {
    /**
     * 句を作成する。
     *
     * デフォルトでは、作成する句に句の名前は含まれない。
     * 作成する句に句の名前を含めたい場合、shouldUseClauseNameをtrueにする必要がある。
     *
     * @param table 句を作成する際に使用するテーブルクラス。
     * @param tableAlias 句内のカラムに使用するテーブルエイリアス名。
     * nullだった場合、エイリアスは使用しない。
     * @param shouldUseClauseName 作成する句に句の名前を含めるかどうかを表す。
     * @return 作成した句。
     */
    fun makeClause(table: Any, tableAlias: String?, shouldUseClauseName: Boolean = false): String = if (shouldUseClauseName) "$clauseName ${makeClause(table, tableAlias)}" else makeClause(table, tableAlias)

    /**
     * 句を作成する。
     *
     * @param table 句を作成する際に使用するテーブルクラス。
     * @param tableAlias 句内のカラムに使用するテーブルエイリアス名。
     * nullだった場合、エイリアスは使用しない。
     * @return 作成した句。
     */
    protected abstract fun makeClause(table: Any, tableAlias: String?): String

    /**
     * 句の途中に文を追加する。
     *
     * 句の作成途中に追加したい文がある場合に使用する。
     *
     * @return 句の途中に追加したい文。
     */
    protected open fun appendSentenceIntoClause(): String = ""
}

/**
 * 渡されたColumnアノテーションが付与されたプロパティを使用して、SQLiteの句を表す基底クラス。
 *
 * @param clauseName 句の名前。
 * @property columnAnnotationProperties 句で使用するColumnアノテーションが付与されたプロパティのリスト。
 */
abstract class ClauseWithProperties(clauseName: String, protected val columnAnnotationProperties: List<KProperty1<*, *>>) : Clause(clauseName) {
    override fun makeClause(table: Any, tableAlias: String?): String {
        var clause = ""
        columnAnnotationProperties.forEachIndexed { i, columnAnnotationProperty ->
            if (i > 0) clause += ", "
            clause += SQLiteTableOperator.addTableAlias(tableAlias, SQLiteTableOperator.getColumnName(table, columnAnnotationProperty))
            if (appendSentenceIntoClause() != "") clause += " ${appendSentenceIntoClause()}"
        }
        return clause
    }
}

/**
 * 渡されたカラム名を使用して、SQLiteの句を表す基底クラス。
 *
 * @param clauseName 句の名前。
 * @property columns 句で使用するカラム名。
 * @property shouldUseTableAlias 渡されたカラム名にテーブルエイリアスを使用するかどうかを表す。
 */
abstract class ClauseWithString(clauseName: String, private val columns: List<String>, private val shouldUseTableAlias: Boolean) : Clause(clauseName) {
    override fun makeClause(table: Any, tableAlias: String?): String {
        var clause = ""
        columns.forEachIndexed { i, column ->
            if (i > 0) clause += ", "
            clause += if (shouldUseTableAlias) SQLiteTableOperator.addTableAlias(tableAlias, column) else column
            if (appendSentenceIntoClause() != "") clause += " ${appendSentenceIntoClause()}"
        }
        return clause
    }
}

/**
 * GROUP BY句を表す。
 *
 * @property columnAnnotationProperties 句で使用するColumnアノテーションが付与されたプロパティのリスト。
 */
class GroupBy(columnAnnotationProperties: List<KProperty1<*, *>>) : ClauseWithProperties("GROUP BY", columnAnnotationProperties)

/**
 * 渡されたカラム名を使用して、GROUP BY句を表す。
 *
 * @property columns 句で使用するカラム名。
 * @property shouldUseTableAlias 渡されたカラム名にテーブルエイリアスを使用するかどうかを表す。
 */
class GroupByWithString(columns: List<String>, shouldUseTableAlias: Boolean = false) : ClauseWithString("GROUP BY", columns, shouldUseTableAlias)

/**
 * HAVING句を表す。
 *
 * @property whereConditions 句で使用する条件。
 */
class Having(private val whereConditions: List<WhereCondition>) : Clause("HAVING") {
    override fun makeClause(table: Any, tableAlias: String?): String = WhereCondition.makeWhereString(table, tableAlias, whereConditions)
}

/**
 * 順序を表す。
 */
enum class Order {
    /**
     * 昇順を表す。
     */
    ASC,

    /**
     * 降順を表す。
     */
    DESC
}

/**
 * ORDER BY句を表す。
 *
 * @property columnAnnotationProperties 句で使用するColumnアノテーションが付与されたプロパティのリスト。
 */
class OrderBy(columnAnnotationProperties: List<KProperty1<*, *>>, private val order: Order) : ClauseWithProperties("ORDER BY", columnAnnotationProperties) {
    override fun appendSentenceIntoClause(): String = when (order) {
        Order.ASC -> "ASC"
        Order.DESC -> "DESC"
    }
}

/**
 * 渡されたカラム名を使用して、ORDER BY句を表す。
 *
 * @property columns 句で使用するカラム名。
 * @property shouldUseTableAlias 渡されたカラム名にテーブルエイリアスを使用するかどうかを表す。
 */
class OrderByWithString(columns: List<String>, private val order: Order, shouldUseTableAlias: Boolean = false) : ClauseWithString("ORDER BY", columns, shouldUseTableAlias) {
    override fun appendSentenceIntoClause(): String = when (order) {
        Order.ASC -> "ASC"
        Order.DESC -> "DESC"
    }
}