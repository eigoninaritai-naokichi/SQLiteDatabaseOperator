package com.eigoninaritai.sqlitedatabaseoperatorsample

import com.eigoninaritai.sqlitedatabaseoperator.Column
import com.eigoninaritai.sqlitedatabaseoperator.ColumnConstant
import com.eigoninaritai.sqlitedatabaseoperator.SQLiteTrigger
import java.util.*

/**
 * SQLiteで使用するテーブルのマッピングクラスの基礎クラス。
 *
 * @property createdAt 作成日時。
 * データが作成された日時。
 * 初期値に現在時刻が代入される。
 * @property updatedAt 更新日時。
 * データが更新された日時。
 * トリガーによって更新される。
 */
abstract class SQLiteTableBase(
    @Column(name = "created_at", defaultValue = ColumnConstant.CURRENT_TIME_AS_DEFAULT, shouldUseInInsert = false, shouldUseInUpdate = false)
    val createdAt: Calendar = Calendar.getInstance(),
    @Column(name = "updated_at", defaultValue = ColumnConstant.CURRENT_TIME_AS_DEFAULT, shouldUseInInsert = false, shouldUseInUpdate = false, triggers = [SQLiteTrigger.UPDATED_TIME_TRIGGER])
    val updatedAt: Calendar = Calendar.getInstance()
)