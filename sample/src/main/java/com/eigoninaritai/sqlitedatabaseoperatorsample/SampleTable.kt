package com.eigoninaritai.sqlitedatabaseoperatorsample

import com.eigoninaritai.sqlitedatabaseoperator.Column
import com.eigoninaritai.sqlitedatabaseoperator.ForeignKey
import com.eigoninaritai.sqlitedatabaseoperator.PrimaryKey
import com.eigoninaritai.sqlitedatabaseoperator.Table
import com.eigoninaritai.sqlitedatabaseoperator.Unique

/**
 * SQLiteDatabaseOperatorのサンプルテーブル。
 *
 * @param id プライマリキーになるカラム。
 * @param nullableInt Nullが可能なINTEGER型のカラム。
 * @param limitedLengthString 文字数制限されたカラム。
 * @param intWithDefault デフォルト値が設定されたINTEGER型のカラム。
 * @param stringWithDefault デフォルト値が設定されたTEXT型のカラム。
 */
@Table("sample_table")
@PrimaryKey("_id")
@Unique("limited_length_string")
data class SampleTable(
    @Column(name = "_id")
    val id: Long = 0,
    @Column(name = "nullable_int")
    val nullableInt: Int?,
    @Column(name = "limited_length_string", length = 256)
    val limitedLengthString: String,
    @Column(name = "int_with_default", defaultValue = "12345")
    val intWithDefault: Int,
    @Column(name = "string_with_default", defaultValue = "Default Value")
    val stringWithDefault: String
) : SQLiteTableBase()

/**
 * SQLiteDatabaseOperatorのサンプルテーブル2。
 *
 * @param id プライマリキーになるカラム。
 * @param primaryKeyString プライマリキーになるカラム。
 * @param foreignReferenceColumn sample_table.limited_length_stringを外部キーとして参照しているカラム。
 */
@Table("sample_table2")
@PrimaryKey("_id", "primary_key_string")
data class SampleTable2(
    @Column(name = "_id")
    val id: Long = 0,
    @Column(name = "primary_key_string")
    val primaryKeyString: String,
    @Column(name = "foreign_reference_column")
    @ForeignKey(SampleTable::class, "limited_length_string", isDeleteCascade = true)
    val foreignReferenceColumn: String
) : SQLiteTableBase()
