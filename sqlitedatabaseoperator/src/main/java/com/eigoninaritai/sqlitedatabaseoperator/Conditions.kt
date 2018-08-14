package com.eigoninaritai.sqlitedatabaseoperator

import kotlin.reflect.KProperty1

/**
 * データ取得で使用する条件を表す基底クラス。
 *
 * @property whereConditions データ取得に使用する条件。
 * nullの場合、条件を指定しない。
 * @property groupBy GROUP BYを表す。
 * nullの場合、GROUP BYを指定しない。
 * @property having HAVINGを表す。
 * nullの場合、HAVINGを指定しない。
 * @property orderBy ORDER BYを表す。
 * nullの場合、ORDER BYを指定しない。
 * @property distinct DISTINCTを行うかどうかを表す。
 * @property limit 取得するデータの上限を表す。
 */
abstract class ConditionsBase(
    val whereConditions: List<WhereCondition>? = null,
    val groupBy: GroupBy? = null,
    val having: Having? = null,
    val orderBy: OrderBy? = null,
    val distinct: Boolean = false,
    val limit: Int? = null
)

/**
 * テーブルクラスのデータ取得で使用する条件を表す。
 *
 * SQLiteTableOperator.selectDataListのデータ取得の条件として使用する。
 *
 * @param whereConditions データ取得に使用する条件。
 * nullの場合、条件を指定しない。
 * @param groupBy GROUP BYを表す。
 * nullの場合、GROUP BYを指定しない。
 * @param having HAVINGを表す。
 * nullの場合、HAVINGを指定しない。
 * @param orderBy ORDER BYを表す。
 * nullの場合、ORDER BYを指定しない。
 * @property columnAnnotationProperties 取得したいカラムのリスト。
 * nullの場合、全てのカラムを取得する。
 * @param distinct DISTINCTを行うかどうかを表す。
 * @param limit 取得するデータの上限を表す。
 */
class Conditions(
    whereConditions: List<WhereCondition>? = null,
    groupBy: GroupBy? = null,
    having: Having? = null,
    orderBy: OrderBy? = null,
    val columnAnnotationProperties: List<KProperty1<*, *>>? = null,
    distinct: Boolean = false,
    limit: Int? = null
) : ConditionsBase(whereConditions, groupBy, having, orderBy, distinct, limit)