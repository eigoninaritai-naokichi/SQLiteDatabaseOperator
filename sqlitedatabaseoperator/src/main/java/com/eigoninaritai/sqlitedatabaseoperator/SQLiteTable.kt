package com.eigoninaritai.sqlitedatabaseoperator

import java.util.*
import java.util.regex.Pattern
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.jvmErasure

/**
 * SQLiteで使用するテーブルを表すアノテーション。
 *
 * このアノテーションを付与されたクラスはSQLiteで使用するテーブルのマッピングクラスとして扱うことができる。
 *
 * @property name テーブルの名前を表す。
 * 指定された文字列がSQLiteで使用するテーブルの名前になる。
 * デフォルト値の空文字が指定されている場合、このアノテーションが付与されているクラスの名前をテーブル名として使用する。
 */
@Target(AnnotationTarget.CLASS)
annotation class Table(val name: String = "")

/**
 * SQLiteのテーブルのプライマリキーを表すアノテーション。
 *
 * このアノテーションが付与されたクラスにTableアノテーションが付与されている場合、このアノテーションに渡されたカラムがプライマリキーに設定される。
 *
 * @property columnNames プライマリキーとして設定されるカラムを表す。
 */
@Target(AnnotationTarget.CLASS)
annotation class PrimaryKey(vararg val columnNames: String)

/**
 * SQLiteのテーブルのユニークを表すアノテーション。
 *
 * このアノテーションが付与されたクラスにTableアノテーションが付与されている場合、このアノテーションに渡されたカラムがユニークに設定される。
 *
 * @property columnNames ユニークとして設定されるカラムを表す。
 */
@Target(AnnotationTarget.CLASS)
annotation class Unique(vararg val columnNames: String)

/**
 * SQLiteのテーブルのインデックスを表すアノテーション。
 *
 * このアノテーションが付与されたクラスにTableアノテーションが付与されている場合、このアノテーションに渡されたカラムがインデックスに設定される。
 * このアノテーションは一つのクラスに複数指定することができる。
 *
 * @property columnNames インデックスとして設定されるカラムを表す。
 */
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class Index(vararg val columnNames: String)

/**
 * トリガーなどの特殊な処理を設定するアノテーション。
 *
 * @property queries トリガーなどの特殊な処理を表すクエリ。
 * 指定されたクエリはテーブル定義の最後に設置される。
 */
@Target(AnnotationTarget.CLASS)
annotation class Query(vararg val queries: String)

/**
 * SQLiteのアノテーションに関する関数を持つクラス。
 */
internal object SQLiteAnnotationOperator {
    /**
     * 指定されたテーブルクラスからテーブル定義を作成する。
     *
     * @param tableClass テーブル定義を作成したいテーブルクラス。
     * @return 指定されたテーブルクラスから作成したテーブル定義。
     * @throws SQLiteAnnotationNotAttachedException 渡されたテーブルクラスにTableアノテーションが付与されていない場合、実行時に発生する。
     * 渡されたテーブルクラスの1つ以上のプロパティにColumnアノテーションが付与されていない場合、実行時に発生する。
     * @throws SQLiteNamingRulesViolationException SQLiteのテーブル名、カラム名の命名規則に違反している場合、実行時に発生する。
     * @throws SQLitePrimaryKeyNotSpecifiedException プライマリキーにカラムが指定されていない場合、実行時に発生する。
     * プライマリキーに指定されたカラム名が空文字で指定されている場合、実行時に発生する。
     * @throws SQLiteUniqueNotSpecifiedException ユニークにカラムが指定されていない場合、実行時に発生する。
     * ユニークに指定されたカラム名が空文字で指定されている場合、実行時に発生する。
     * @throws SQLiteIndexNotSpecifiedException インデックスにカラムが指定されていない場合、実行時に発生する。
     * インデックスに指定されたカラム名が空文字で指定されている場合、実行時に発生する。
     * @throws SQLiteSameColumnExistException プライマリキーに渡されたカラムが重複している場合、実行時に発生する。
     * ユニークに渡されたカラムが重複している場合、実行時に発生する。
     * インデックスに渡されたカラムが重複している場合、実行時に発生する。
     * @throws SQLiteColumnNotFoundException プライマリキーに指定されたカラムがテーブルクラスに存在しない場合、実行時に発生する。
     * ユニークに指定されたカラムがテーブルクラスに存在しない場合、実行時に発生する。
     * インデックスに指定されたカラムがテーブルクラスに存在しない場合、実行時に発生する。
     * 外部キー制約に指定されたカラムがテーブルクラスに存在しない場合、実行時に発生する。 
     * @throws SQLiteQueryNotSpecifiedException 特殊な処理を表すクエリが指定されていない場合、実行時に発生する。
     */
    fun createTableDefine(tableClass: KClass<*>): SQLiteTableDefine {
        // エラーの詳細表示用にテーブル名を取得
        val tableName = getTableName(tableClass)

        // カラム定義を保持するためのリストを作成
        val columnDefines = mutableListOf<SQLiteColumnDefine>()

        // カラム、外部キー制約アノテーションが付与されているプロパティのリストを取得
        val columnAnnotationProperties = findPropertiesAttachedTableColumnAnnotation<Column>(tableClass, true)
        val foreignKeyAnnotationProperties = findPropertiesAttachedTableColumnAnnotation<ForeignKey>(tableClass)

        // 取得したプロパティのリストからカラム定義クラスを作成し、カラム定義を保持するリストに追加
        columnAnnotationProperties.forEach { columnAnnotationProperty ->
            val column = columnAnnotationProperty.annotations.find { it is Column } as? Column
            if (column != null) {
                val columnName = getColumnName(columnAnnotationProperty)
                if (!checkName(columnName)) throw SQLiteNamingRulesViolationException("カラム名が命名規則に従われていません。:$tableName.columnName")
                val columnType = when (columnAnnotationProperty.returnType.jvmErasure) {
                    String::class -> SQLiteType.TEXT
                    Short::class, Int::class, Long::class -> SQLiteType.INTEGER
                    Float::class, Double::class -> SQLiteType.REAL
                    Boolean::class -> SQLiteType.BOOLEAN
                    Calendar::class -> SQLiteType.TIMESTAMP
                    else -> throw SQLiteColumnTypeMismatchException("使用できない型に${Column::class.simpleName}アノテーションが付与されています。")
                }
                val columnLength = column.length
                val isAutoIncrement = column.isAutoIncrement
                val defaultValue = if (column.defaultValue == "") null else if (columnType == SQLiteType.TEXT) "'${column.defaultValue}'" else column.defaultValue
                val isNotNull: Boolean = !columnAnnotationProperty.returnType.isMarkedNullable
                var foreignKeyDefine: ForeignKeyDefine? = null
                foreignKeyAnnotationProperties.forEach { foreignKeyAnnotationProperty ->
                    if (columnAnnotationProperty.name == foreignKeyAnnotationProperty.name) {
                        val foreignKey = foreignKeyAnnotationProperty.annotations.find { it is ForeignKey } as? ForeignKey
                        if (foreignKey != null) {
                            // 外部キー制約に指定されたテーブルに指定されたカラムが存在しない場合、エラー
                            if (!checkExistColumn(foreignKey.tableClass, foreignKey.columnName)) throw SQLiteColumnNotFoundException("${ForeignKey::class.simpleName}アノテーションに指定されたカラム名が存在しません。:${getTableName(foreignKey.tableClass)}.${foreignKey.columnName}")

                            // 外部キー制約定義を作成
                            foreignKeyDefine = ForeignKeyDefine(getTableName(foreignKey.tableClass), foreignKey.columnName, foreignKey.isDeleteCascade)
                        }
                    }
                }
                columnDefines.add(SQLiteColumnDefine(
                    columnName,
                    columnType,
                    columnLength,
                    isAutoIncrement,
                    defaultValue,
                    isNotNull,
                    foreignKeyDefine
                ))
            }
        }

        // テーブル名が命名規則に違反している場合、エラー
        if (!checkName(tableName)) throw SQLiteNamingRulesViolationException("テーブル名が命名規則に従われていません。:$tableName")

        // プライマリキーに設定されているカラムのリストを取得
        var primaryKeyColumnNames: List<String>? = null
        val primaryKeyAnnotation = findTableClassAnnotations<PrimaryKey>(tableClass)
        if (!primaryKeyAnnotation.isEmpty()) {
            val primaryKeyColumnNamesTemp = primaryKeyAnnotation.first().columnNames.toList()

            // プライマリキーにカラムが指定されていない場合、エラー
            if (primaryKeyColumnNamesTemp.isEmpty()) throw SQLitePrimaryKeyNotSpecifiedException("プライマリキーにカラムを指定してください。:$tableName")

            // プライマリキーに指定されたカラム名が空白の場合、エラー
            primaryKeyColumnNamesTemp.forEach {
                if (it == "") throw SQLitePrimaryKeyNotSpecifiedException("プライマリキーに指定するカラム名は空文字で指定することはできません。:$tableName.$it")
            }

            // プライマリキーに指定されたカラム名が重複している場合、エラー
            primaryKeyColumnNamesTemp.forEach { targetColumnName ->
                primaryKeyColumnNamesTemp.forEach { comparedColumnName ->
                    if (targetColumnName !== comparedColumnName) {
                        if (targetColumnName == comparedColumnName) throw SQLiteSameColumnExistException("プライマリキーに同一のカラムが指定されています。:$tableName.$targetColumnName")
                    }
                }
            }

            // プライマリキーに指定されたカラムが存在しない場合、エラー
            val problemColumnName = checkExistColumns(primaryKeyColumnNamesTemp, columnDefines)
            if (!problemColumnName.isNullOrEmpty()) throw SQLiteColumnNotFoundException("プライマリキーに指定されたカラムが見つかりませんでした。:$tableName.$problemColumnName")

            // プライマリキーに設定されているカラムのリストを代入
            primaryKeyColumnNames = primaryKeyColumnNamesTemp
        }

        // ユニークに設定されているカラムのリストを取得
        var uniqueColumnNames: List<String>? = null
        val uniqueAnnotation = findTableClassAnnotations<Unique>(tableClass)
        if (!uniqueAnnotation.isEmpty()) {
            val uniqueColumnNamesTemp = uniqueAnnotation.first().columnNames.toList()

            // ユニークにカラムが指定されていない場合、エラー
            if (uniqueColumnNamesTemp.isEmpty()) throw SQLiteUniqueNotSpecifiedException("ユニークにカラムを指定してください。:$tableName")

            // ユニークに指定されたカラム名が空白の場合、エラー
            uniqueColumnNamesTemp.forEach {
                if (it == "") throw SQLiteUniqueNotSpecifiedException("ユニークに指定するカラム名は空文字で指定することはできません。:$tableName.$it")
            }

            // ユニークに指定されたカラム名が重複している場合、エラー
            uniqueColumnNamesTemp.forEach { targetColumnName ->
                uniqueColumnNamesTemp.forEach { comparedColumnName ->
                    if (targetColumnName !== comparedColumnName) {
                        if (targetColumnName == comparedColumnName) throw SQLiteSameColumnExistException("ユニークに同一のカラムが指定されています。:$tableName.$targetColumnName")
                    }
                }
            }

            // ユニークに指定されたカラムが存在しない場合、エラー
            val problemColumnName = checkExistColumns(uniqueColumnNamesTemp, columnDefines)
            if (!problemColumnName.isNullOrEmpty()) throw SQLiteColumnNotFoundException("ユニークに指定されたカラムが見つかりませんでした。:$tableName.$problemColumnName")

            // ユニークに設定されているカラムのリストを代入
            uniqueColumnNames = uniqueColumnNamesTemp
        }

        // インデックスに設定されているカラムのリストを取得
        var indexColumnNamesList: MutableList<List<String>>? = null
        val indexAnnotations = findTableClassAnnotations<Index>(tableClass, isFindSuperClass = true)
        if (!indexAnnotations.isEmpty()) {
            indexColumnNamesList = mutableListOf()
            indexAnnotations.forEach {
                val indexColumnNamesTemp = it.columnNames.toList()

                // インデックスにカラムが指定されていない場合、エラー
                if (indexColumnNamesTemp.isEmpty()) throw SQLiteIndexNotSpecifiedException("インデックスにカラムを指定してください。:$tableName")

                // インデックスに指定されたカラム名が空白の場合、エラー
                indexColumnNamesTemp.forEach {
                    if (it == "") throw SQLiteIndexNotSpecifiedException("インデックスに指定するカラム名は空文字で指定することはできません。:$tableName.$it")
                }

                // インデックスに指定されたカラム名が重複している場合、エラー
                indexColumnNamesTemp.forEach { targetColumnName ->
                    indexColumnNamesTemp.forEach { comparedColumnName ->
                        if (targetColumnName !== comparedColumnName) {
                            if (targetColumnName == comparedColumnName) throw SQLiteSameColumnExistException("インデックスに同一のカラムが指定されています。:$tableName.$targetColumnName")
                        }
                    }
                }

                // インデックスに指定されたカラムが存在しない場合、エラー
                val problemColumnName = checkExistColumns(indexColumnNamesTemp, columnDefines)
                if (!problemColumnName.isNullOrEmpty()) throw SQLiteColumnNotFoundException("インデックスに指定されたカラムが見つかりませんでした。:$tableName.$problemColumnName")

                // インデックスに設定されているカラムのリストを追加
                indexColumnNamesList.add(indexColumnNamesTemp)
            }
        }

        // 特殊な処理のリストを取得
        val specifiedQueriesTemp = mutableListOf<String>()
        columnAnnotationProperties.forEach { columnAnnotationProperty ->
            val column = columnAnnotationProperty.annotations.find { it is Column } as? Column
            if (column != null) {
                if (!column.triggers.isEmpty()) {
                    column.triggers.forEach {
                        val trigger: String
                        when (it) {
                            SQLiteTrigger.UPDATED_TIME_TRIGGER -> trigger = getUpdatedTimeTrigger(tableClass, getColumnName(columnAnnotationProperty))
                        }
                        specifiedQueriesTemp.add(trigger)
                    }
                }
            }
        }
        val queryAnnotations = findTableClassAnnotations<Query>(tableClass, isFindSuperClass = true)
        if (!queryAnnotations.isEmpty()) {
            queryAnnotations.forEach {
                // 特殊な処理を表すクエリが指定されていない場合、エラー
                if (it.queries.isEmpty()) throw SQLiteQueryNotSpecifiedException("クエリを指定してください。:$tableName")

                // 特殊な処理のリストを追加
                specifiedQueriesTemp.addAll(it.queries)
            }
        }
        val specifiedQueries = if (specifiedQueriesTemp.isEmpty()) null else specifiedQueriesTemp

        // テーブル定義を作成
        return SQLiteTableDefine(
            tableClass,
            columnDefines,
            primaryKeyColumnNames,
            uniqueColumnNames,
            indexColumnNamesList?.toList(),
            specifiedQueries?.toList()
        )
    }

    /**
     * クラスに付与できる指定されたSQLite用のアノテーションのリストを指定されたクラスから取得する。
     *
     * パラメータのisEmptyがfalseかつ指定されたアノテーションがクラスに付与されていない場合、空のリストを返す。
     * パラメータのisEmptyがtrueかつ指定されたアノテーションがクラスに付与されていない場合、エラーが発生する。
     * パラメータのisFindSuperClassがtrueの場合、親クラスに付与されたアノテーションも取得する。
     *
     * @param T 取得したいSQLite用のアノテーションを表す。
     * @param tableClass SQLite用のアノテーションが付与されたクラス。
     * @param isNotEmpty 指定されたアノテーションがクラスに付与されていない場合に空のリストを返すかどうかを指定する。
     * 空のリストを返さない指定にしているかつリストが空の場合、エラーが発生する。
     * @param isFindSuperClass 指定されたアノテーションを親クラスからも取得するかどうかを指定する。
     * @return クラスに付与できる指定されたSQLite用のアノテーションのリスト。
     * @throws SQLiteAnnotationNotAttachedException パラメータのisEmptyがtrueかつリストが空の場合、実行時に発生する。
     */
    private inline fun <reified T> findTableClassAnnotations(tableClass: KClass<*>, isNotEmpty: Boolean = false, isFindSuperClass: Boolean = false): List<T> {
        // テーブルに付与されたアノテーションを取得
        val tableClassAnnotations = tableClass.annotations.filter { it is T }.map { it as T }

        // 親クラスに付与されたアノテーションの取得が指定されている場合、親クラスに付与されたアノテーションを取得
        var superTableClassAnnotationsList = listOf<List<T>>()
        if(isFindSuperClass) {
            superTableClassAnnotationsList = tableClass.superclasses.map { it.annotations.filter { it is T }.map { it as T } }
        }

        // 指定されたクラスと親クラスに付与されたアノテーションのリストを作成
        val resultClassAnnotations = tableClassAnnotations.toMutableList()
        superTableClassAnnotationsList.forEach { resultClassAnnotations.plusAssign(it) }

        // 空のリストを許可しない指定かつアノテーションのリストが空の場合、エラー
        if (
            isNotEmpty &&
            resultClassAnnotations.isEmpty()
        ) {
            throw SQLiteAnnotationNotAttachedException("指定されたクラスは${T::class.simpleName}アノテーションが付与されていません。")
        }
        return resultClassAnnotations.toList()
    }

    /**
     * 指定されたSQLite用のアノテーションが付与されたプロパティのリストを指定されたクラスから取得する。
     *
     * パラメータのisEmptyがfalseかつ指定されたアノテーションが付与されたプロパティが1つも存在しない場合、空のリストを返す。
     * パラメータのisEmptyがtrueかつ指定されたアノテーションが付与されたプロパティが1つも存在しない場合、エラーが発生する。
     *
     * @param T 取得したいSQLite用のアノテーションを表す。
     * @param tableClass SQLite用のアノテーションが付与されたクラス。
     * @param isNotEmpty 指定されたアノテーションが付与されたプロパティが1つも存在しない場合に空のリストを返すかどうかを指定する。
     * 空のリストを返さない指定にしているかつリストが空の場合、エラーが発生する。
     * @return 指定されたSQLite用のアノテーションが付与されたプロパティのリスト。
     * @throws SQLiteAnnotationNotAttachedException パラメータのisEmptyがtrueかつリストが空の場合、実行時に発生する。
     */
    private inline fun <reified T> findPropertiesAttachedTableColumnAnnotation(tableClass: KClass<*>, isNotEmpty: Boolean = false): List<KProperty1<*, *>> {
        // 指定されたアノテーションが付与されたプロパティを取得
        val propertiesAttachedTableColumnAnnotationTemp = tableClass.memberProperties.filter { it.annotations.any { it is T } }

        // KClass.memberPropertiesはアルファベット順でプロパティが返ってくる
        // KClass.java.declaredFieldsは順序が保証されていないが、定義した順でプロパティが返ってくる
        // クラスに定義した順で返すほうが自然なため、KClass.java.declaredFieldsを使用して定義順に並び替える
        val propertiesAttachedTableColumnAnnotation = mutableListOf<KProperty1<*, *>>()
        for (field in tableClass.java.declaredFields) {
            for (property in propertiesAttachedTableColumnAnnotationTemp) {
                if (property.name == field.name) {
                    propertiesAttachedTableColumnAnnotation.add(property)
                    break
                }
            }
        }
        for (superClass in tableClass.superclasses) {
            for (field in superClass.java.declaredFields) {
                for (property in propertiesAttachedTableColumnAnnotationTemp) {
                    if (property.name == field.name) {
                        propertiesAttachedTableColumnAnnotation.add(property)
                        break
                    }
                }
            }
        }

        // 空のリストを許可しない指定かつプロパティのリストが空の場合、エラー
        if (
            isNotEmpty &&
            propertiesAttachedTableColumnAnnotation.isEmpty()
        ) {
            throw SQLiteAnnotationNotAttachedException("指定されたクラスには${T::class.simpleName}アノテーションが付与さたプロパティが存在しません。")
        }
        return propertiesAttachedTableColumnAnnotation.toList()
    }

    /**
     * 指定されたSQLite用のアノテーションが付与されたプロパティを指定されたクラスとカラム名から取得する。
     *
     * 指定されたプロパティが存在しない場合、nullを返す。
     *
     * @param T 取得したいSQLite用のアノテーションを表す。
     * @param tableClass SQLite用のアノテーションが付与されたクラス。
     * @param columnName 取得したいカラム名。
     * @return 指定されたSQLite用のアノテーションが付与され、指定したカラムと一致するプロパティ。
     * 指定されたプロパティが存在しない場合、nullを返す。
     */
    private inline fun <reified T> findPropertyAttachedTableColumnAnnotation(tableClass: KClass<*>, columnName: String): KProperty1<*, *>? = findPropertiesAttachedTableColumnAnnotation<T>(tableClass).firstOrNull { getColumnName(it) == columnName }

    /**
     * 指定されたクラスからテーブル名を取得する。
     *
     * 指定されたクラスに付与されたTableアノテーションにテーブル名が指定されていない場合、指定されたクラスのクラス名を返す。
     *
     * @param tableClass テーブルクラス。
     * @return 指定されたクラスから取得したテーブル名。
     * @throws SQLiteAnnotationNotAttachedException 指定されたクラスにTableアノテーションが付与されていない場合、実行時に発生する。
     */
    fun getTableName(tableClass: KClass<*>): String {
        val table = findTableClassAnnotations<Table>(tableClass, true).first()
        return if (table.name == "") tableClass.simpleName ?: throw RuntimeException("テーブル名の取得に失敗しました。") else table.name
    }

    /**
     * 指定されたプロパティからカラム名を取得する。
     *
     * 指定されたプロパティに付与されたColumnアノテーションにカラム名が指定されていない場合、指定されたプロパティのプロパティ名を返す。
     *
     * @param columnAnnotationProperty Columnアノテーションが付与されたプロパティ。
     * @return 指定されたプロパティから取得したカラム名。
     * @throws SQLiteAnnotationNotAttachedException 指定されたプロパティにColumnアノテーションが付与されていない場合、実行時に発生する。
     */
    private fun getColumnName(columnAnnotationProperty: KProperty1<*, *>): String {
        val column = columnAnnotationProperty.annotations.find { it is Column } as? Column ?: throw SQLiteAnnotationNotAttachedException("指定されたプロパティに${Column::class.simpleName}アノテーションが付与されていません。")
        return if (column.name == "") columnAnnotationProperty.name else column.name
    }

    /**
     * テーブル名、カラム名がSQLiteの命名規則に従っているかどうかを確認する。
     *
     * @param name 確認したいテーブル名、もしくはカラム名。
     * @return true:指定された名前がSQLiteの命名規則に従っている場合。false:それ以外。
     */
    private fun checkName(name: String): Boolean = Pattern.matches("^[_a-zA-Z]\\w*?$", name)

    /**
     * 指定されたカラムが指定されたテーブルクラスに存在するかどうかを確認する。
     *
     * @param tableClass テーブルクラス。
     * @param columnName 存在するかどうかを確認したいカラム名。
     * @return true:指定されたカラムが存在する場合。false:それ以外。
     */
    private fun checkExistColumn(tableClass: KClass<*>, columnName: String) = findPropertyAttachedTableColumnAnnotation<Column>(tableClass, columnName) != null

    /**
     * 措定されたカラム全てが指定されたテーブル定義に存在するかどうかを確認する。
     *
     * @param columnNames
     * @param columnDefines
     * @return 指定されたカラムが存在しない場合、そのカラム名を返す。
     * 指定されたカラム全てが存在する場合、nullを返す。
     */
    private fun checkExistColumns(columnNames: List<String>, columnDefines: List<SQLiteColumnDefine>): String? = columnNames.firstOrNull { columnName -> !columnDefines.any { columnName == it.columnName } }

    /**
     * Columnアノテーションが付与されたプロパティの型が指定された型かどうかを確認する。
     *
     * @param tableClass テーブルクラス。
     * @param columnName プロパティの型を調べたいカラム名。
     * @param columnPropertyType 指定されたカラムに関連するプロパティの型と比べるための型。
     * @return true:指定されたカラムに関連するプロパティの型が指定された型と一致する場合。 false:それ以外。
     */
    private fun checkColumnPropertyType(tableClass: KClass<*>, columnName: String, columnPropertyType: KClass<*>): Boolean {
        var isMatchColumnType = false
        val columnAnnotationProperty = findPropertyAttachedTableColumnAnnotation<Column>(tableClass, columnName)
        if (
            columnAnnotationProperty != null &&
            columnAnnotationProperty.returnType.jvmErasure == columnPropertyType
        ) {
            isMatchColumnType = true
        }
        return isMatchColumnType
    }

    /**
     * 更新した日時を指定したカラムに自動で書き込むトリガーのクエリを返す。
     *
     * プライマリキーに設定されたカラムを更新するデータの条件とする。
     * そのため、PrimaryKeyアノテーションが付与されていないクラスが指定された場合、エラーが発生する。
     *
     * @param tableClass このトリガーを作成したいテーブルクラス。
     * @param columnName 日時を自動更新したいカラム名。
     * @return 更新した日時を指定したカラムに自動で書き込むトリガーのクエリ。
     * @throws SQLiteColumnTypeMismatchException 指定されたカラムに関連するプロパティがCalendar型でない場合、実行時に発生する。
     * @throws SQLiteAnnotationNotAttachedException Tableアノテーションが付与されていないクラスが指定された場合、実行時に発生する。
     * PrimaryKeyアノテーションが付与されていないクラスが指定された場合、実行時に発生する。
     */
    private fun getUpdatedTimeTrigger(tableClass: KClass<*>, columnName: String): String {
        // トリガーを設定するテーブル名を取得
        val tableName = getTableName(tableClass)

        // 指定されたカラムが存在しない、または指定されたカラムに関連するプロパティの型がCalendar型でない場合、エラー
        if (!checkExistColumn(tableClass, columnName)) throw SQLiteColumnNotFoundException("指定されたカラムが見つかりませんでした。:$tableName.$columnName")
        if (!checkColumnPropertyType(tableClass, columnName, Calendar::class)) throw SQLiteColumnTypeMismatchException("指定されたカラムに関連するプロパティの型がCalendar型ではありません。:$tableName.$columnName")

        // 設定されたプライマリキーから更新するデータの条件文を作成
        val primaryKeyAnnotation = findTableClassAnnotations<PrimaryKey>(tableClass, true)
        var whereQuery = ""
        primaryKeyAnnotation.first().columnNames.forEachIndexed { i, primaryKeyColumnName ->
            if (i > 0) whereQuery += " AND "
            whereQuery += "$primaryKeyColumnName = OLD.$primaryKeyColumnName"
        }

        // トリガーのクエリを作成
        return """
        |CREATE TRIGGER ${tableName}_${columnName}_updated_time_trigger AFTER UPDATE ON $tableName
        |BEGIN
        |    UPDATE $tableName SET $columnName = DATETIME('now', 'localtime') WHERE $whereQuery;
        |END;
        """.trimMargin()
    }
}

/**
 * SQLiteデータベースとやり取りするためのクラス。
 *
 * このクラスはSQLiteデータベースとやり取りするために必要なテーブル定義の情報として、SQLiteTableOperatorによって作成される。
 *
 * @param tableClass Tableアノテーションが付与されたクラス。
 * @property columnDefines カラム定義のリスト。
 * @property primaryKeyColumnNames プライマリキーに設定されたカラム名のリスト。
 * @property uniqueColumnNames ユニークに設定されたカラム名のリスト。
 * @property indexColumnNamesList インデックスに設定されたカラム名のリスト。
 * @property specifiedQueries トリガーなどの特殊な処理のリスト。
 * @constructor 問題があった場合エラーが発生する。
 * @throws SQLiteSameColumnExistException カラム定義のリストに同じ名前のカラムがあった場合、実行時に発生する。
 * @throws SQLiteAutoIncrementWrongSettingException プライマリキーでなくInt型でないカラムにオートインクリメントが設定されている場合、実行時に発生する。
 * @throws SQLiteSameIndexException 同一のインデックスが存在した場合、実行時に発生する。
 */
@PublishedApi internal class SQLiteTableDefine(
    tableClass: KClass<*>,
    val columnDefines: List<SQLiteColumnDefine>,
    val primaryKeyColumnNames: List<String>?,
    val uniqueColumnNames: List<String>?,
    val indexColumnNamesList: List<List<String>>?,
    val specifiedQueries: List<String>?
) {
    /**
     * 定義するテーブル名。
     */
    val tableName: String = SQLiteAnnotationOperator.getTableName(tableClass)

    init {
        // 同じ名前のカラム名があった場合、エラー
        columnDefines.forEach { targetColumnDefine ->
            columnDefines.forEach { comparedColumnDefine ->
                if (
                    targetColumnDefine != comparedColumnDefine &&
                    targetColumnDefine.columnName == comparedColumnDefine.columnName
                ) {
                    throw SQLiteSameColumnExistException("同じ名前のカラムが存在します。:$tableName.${targetColumnDefine.columnName}")
                }
            }
        }

        // オートインクリメントが設定されているカラムにプライマリキーが設定されていないまたはINTEGER型でない場合、エラー
        columnDefines.forEach {
            if (it.isAutoIncrement) {
                if (
                    primaryKeyColumnNames == null ||
                    !primaryKeyColumnNames.contains(it.columnName) ||
                    it.columnType != SQLiteType.INTEGER
                ) {
                    throw SQLiteAutoIncrementWrongSettingException("プライマリキーでなくInt型でないカラムにオートインクリメントが設定されています。:$tableName.${it.columnName}")
                }
            }
        }

        // 同一のインデックスが存在した場合、エラー
        indexColumnNamesList?.forEach { targetIndexColumnNames ->
            indexColumnNamesList.forEach { comparedIndexColumnNames ->
                if (
                targetIndexColumnNames != comparedIndexColumnNames &&
                        targetIndexColumnNames.size == comparedIndexColumnNames.size
                        ) {
                    var isSameColumn = true
                    for (targetColumnName in targetIndexColumnNames) {
                        for (comparedColumnName in comparedIndexColumnNames) {
                            if (targetColumnName != (comparedColumnName)) {
                                isSameColumn = false
                                break
                            }
                        }
                        if (!isSameColumn) break
                    }
                    if (isSameColumn) throw SQLiteSameIndexException("同一のインデックスが指定されています。:$tableName $targetIndexColumnNames")
                }
            }
        }
    }
}

/**
 * SQLiteのテーブル名、カラム名の命名規則に違反している場合に発生する。
 *
 * 命名規則は以下とする。
 * ・名前はアルファベット、アンダースコア( _ )で始まること
 * ・それ以降はアルファベット、数字、アンダースコアが続くこと
 *
 * @param message エラーの内容。
 */
open class SQLiteNamingRulesViolationException(message: String) : RuntimeException(message)

/**
 * テーブルクラスやテーブルクラス内のプロパティにアノテーションが指定されていない場合に発生する。
 *
 * @param message エラーの内容。
 */
open class SQLiteAnnotationNotAttachedException(message: String) : RuntimeException(message)

/**
 * テーブルに同じ名前のカラム名があった場合に発生する。
 *
 * @param message エラーの内容。
 */
open class SQLiteSameColumnExistException(message: String) : RuntimeException(message)

/**
 * テーブルに定義したカラムと一致するプロパティの型が異なる場合に発生する。
 *
 * @param message エラーの内容。
 */
open class SQLiteColumnTypeMismatchException(message: String) : RuntimeException(message)

/**
 * テーブルに設定したプライマリキーにカラムが指定されていない場合、カラム名が空文字で指定されている場合に発生する。
 *
 * @param message エラーの内容。
 */
open class SQLitePrimaryKeyNotSpecifiedException(message: String) : RuntimeException(message)

/**
 * オートインクリメントに設定したカラムがプライマリキーでないかつInt型でない場合に発生する。
 *
 * @param message エラーの内容。
 */
open class SQLiteAutoIncrementWrongSettingException(message: String) : RuntimeException(message)

/**
 * テーブルに設定したユニークにカラムが指定されていない場合、カラム名が空文字で指定されている場合に発生する。
 *
 * @param message エラーの内容。
 */
open class SQLiteUniqueNotSpecifiedException(message: String) : RuntimeException(message)

/**
 * テーブルに設定したインデックスにカラムが指定されていない場合、カラム名が空文字で指定されている場合に発生する。
 *
 * @param message エラーの内容。
 */
open class SQLiteIndexNotSpecifiedException(message: String) : RuntimeException(message)

/**
 * テーブルに設定したインデックスが他のインデックスのカラム全てと一致した場合に発生する。
 *
 * @param message エラーの内容。
 */
open class SQLiteSameIndexException(message: String) : RuntimeException(message)

/**
 * テーブルに設定した特殊な処理を表すクエリが指定されていない場合に発生する。
 *
 * @param message エラーの内容。
 */
open class SQLiteQueryNotSpecifiedException(message: String) : RuntimeException(message)
