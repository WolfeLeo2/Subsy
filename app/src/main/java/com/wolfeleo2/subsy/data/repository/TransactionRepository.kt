package com.wolfeleo2.subsy.data.repository

import com.wolfeleo2.subsy.data.local.dao.CustomCategoryDao
import com.wolfeleo2.subsy.data.local.dao.TransactionDao
import com.wolfeleo2.subsy.data.mapper.toDomain
import com.wolfeleo2.subsy.data.mapper.toEntity
import com.wolfeleo2.subsy.data.model.CustomCategory
import com.wolfeleo2.subsy.data.model.SpendCategory
import com.wolfeleo2.subsy.data.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TransactionRepository {
    /** Newest first. */
    fun getAll(): Flow<List<Transaction>>

    suspend fun getById(id: String): Transaction?

    suspend fun insert(transaction: Transaction)

    suspend fun update(transaction: Transaction)

    suspend fun delete(id: String)
}

class DefaultTransactionRepository(
    private val dao: TransactionDao,
) : TransactionRepository {
    override fun getAll(): Flow<List<Transaction>> = dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Transaction? = dao.getById(id)?.toDomain()

    override suspend fun insert(transaction: Transaction) = dao.insert(transaction.toEntity())

    override suspend fun update(transaction: Transaction) = dao.update(transaction.toEntity())

    override suspend fun delete(id: String) = dao.deleteById(id)
}

interface CategoryRepository {
    /** User-made categories, by name. */
    fun getCustom(): Flow<List<CustomCategory>>

    suspend fun add(category: CustomCategory)

    suspend fun update(category: CustomCategory)

    /** Deletes [category]; its transactions move to Other (or Other income). */
    suspend fun delete(category: CustomCategory)
}

class DefaultCategoryRepository(
    private val dao: CustomCategoryDao,
) : CategoryRepository {
    override fun getCustom(): Flow<List<CustomCategory>> = dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun add(category: CustomCategory) = dao.insert(category.toEntity())

    override suspend fun update(category: CustomCategory) = dao.update(category.id, category.name, category.colorHex)

    override suspend fun delete(category: CustomCategory) =
        dao.deleteAndReassign(category.id, category.key, SpendCategory.fallbackFor(category.kind).name)
}
