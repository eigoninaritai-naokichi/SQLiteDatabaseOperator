package com.eigoninaritai.sqlitedatabaseoperatorsample

import com.eigoninaritai.sqlitedatabaseoperator.Column
import com.eigoninaritai.sqlitedatabaseoperator.ForeignKey
import com.eigoninaritai.sqlitedatabaseoperator.PrimaryKey
import com.eigoninaritai.sqlitedatabaseoperator.Table
import com.eigoninaritai.sqlitedatabaseoperator.Unique

/**
 * SQLiteDatabaseOperatorのサンプルテーブル。
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
    @Column(name = "string_with_default", defaultValue = "Default Value")
    val stringWithDefault: String
) : SQLiteTableBase()

/**
 *
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
