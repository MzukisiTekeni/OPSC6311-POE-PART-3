package com.budgetbuddy.app.db

import androidx.lifecycle.LiveData
import androidx.room.*

// ─────────────────────────────────────────────────────────────────────────────
// USER DAO
// ─────────────────────────────────────────────────────────────────────────────
@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun getUserById(id: Int): LiveData<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserByIdNow(id: Int): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("UPDATE users SET totalXp = totalXp + :xp WHERE id = :userId")
    suspend fun addXp(userId: Int, xp: Int)

    @Query("UPDATE users SET dayStreak = :streak, lastLoggedDate = :date WHERE id = :userId")
    suspend fun updateStreak(userId: Int, streak: Int, date: String)

    @Query("UPDATE users SET totalSaved = totalSaved + :amount WHERE id = :userId")
    suspend fun addToTotalSaved(userId: Int, amount: Double)

    @Query("UPDATE users SET level = :level WHERE id = :userId")
    suspend fun updateLevel(userId: Int, level: Int)

    @Query("DELETE FROM users")
    suspend fun deleteAll()
}

// ─────────────────────────────────────────────────────────────────────────────
// EXPENSE CATEGORY DAO
// ─────────────────────────────────────────────────────────────────────────────
@Dao
interface ExpenseCategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: ExpenseCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<ExpenseCategoryEntity>)

    @Update
    suspend fun update(category: ExpenseCategoryEntity)

    @Query("SELECT * FROM expense_categories WHERE userId = :userId AND isActive = 1 ORDER BY name ASC")
    fun getAllActive(userId: Int): LiveData<List<ExpenseCategoryEntity>>

    @Query("SELECT * FROM expense_categories WHERE userId = :userId AND isActive = 1 ORDER BY name ASC")
    suspend fun getAllActiveNow(userId: Int): List<ExpenseCategoryEntity>

    @Query("SELECT * FROM expense_categories WHERE userId = :userId ORDER BY name ASC")
    fun getAll(userId: Int): LiveData<List<ExpenseCategoryEntity>>

    @Query("UPDATE expense_categories SET isActive = :active WHERE id = :id AND userId = :userId")
    suspend fun setActive(id: Int, userId: Int, active: Boolean)

    @Query("SELECT COUNT(*) FROM expense_categories WHERE userId = :userId AND isActive = 1")
    suspend fun countActive(userId: Int): Int

    @Query("DELETE FROM expense_categories WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Int)
}

// ─────────────────────────────────────────────────────────────────────────────
// EXPENSE DAO
// ─────────────────────────────────────────────────────────────────────────────
@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE userId = :userId ORDER BY date DESC")
    fun getAll(userId: Int): LiveData<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE userId = :userId AND month = :month ORDER BY date DESC")
    fun getByMonth(userId: Int, month: String): LiveData<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE userId = :userId AND month = :month ORDER BY date DESC")
    suspend fun getByMonthNow(userId: Int, month: String): List<ExpenseEntity>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE userId = :userId AND month = :month")
    fun getTotalSpentByMonth(userId: Int, month: String): LiveData<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE userId = :userId AND month = :month")
    suspend fun getTotalSpentByMonthNow(userId: Int, month: String): Double

    @Query("""
        SELECT categoryId, categoryName, categoryEmoji, COALESCE(SUM(amount),0) as total
        FROM expenses
        WHERE userId = :userId AND month = :month
        GROUP BY categoryId
        ORDER BY total DESC
    """)
    fun getSpendingByCategoryForMonth(userId: Int, month: String): LiveData<List<CategorySpending>>

    @Query("""
        SELECT categoryId, categoryName, categoryEmoji, COALESCE(SUM(amount),0) as total
        FROM expenses
        WHERE userId = :userId AND month = :month
        GROUP BY categoryId
        ORDER BY total DESC
    """)
    suspend fun getSpendingByCategoryForMonthNow(userId: Int, month: String): List<CategorySpending>

    @Query("""
        SELECT date, COALESCE(SUM(amount),0) as total
        FROM expenses
        WHERE userId = :userId AND date >= :fromDate
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getDailyTotals(userId: Int, fromDate: String): List<DailyTotal>

    @Query("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE userId = :userId AND date >= :fromDate")
    suspend fun getTotalSince(userId: Int, fromDate: String): Double

    @Query("SELECT COUNT(DISTINCT date) FROM expenses WHERE userId = :userId AND month = :month")
    suspend fun getDaysTrackedInMonth(userId: Int, month: String): Int

    @Query("DELETE FROM expenses WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Int, userId: Int)

    @Query("DELETE FROM expenses WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Int)
}

// Helper data classes
data class CategorySpending(
    val categoryId: Int,
    val categoryName: String,
    val categoryEmoji: String,
    val total: Double
)

data class DailyTotal(
    val date: String,
    val total: Double
)

// ─────────────────────────────────────────────────────────────────────────────
// BUDGET DAO
// ─────────────────────────────────────────────────────────────────────────────
@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE userId = :userId AND month = :month ORDER BY categoryName ASC")
    fun getByMonth(userId: Int, month: String): LiveData<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE userId = :userId AND month = :month ORDER BY categoryName ASC")
    suspend fun getByMonthNow(userId: Int, month: String): List<BudgetEntity>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM budgets WHERE userId = :userId AND month = :month")
    fun getTotalBudgetForMonth(userId: Int, month: String): LiveData<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM budgets WHERE userId = :userId AND month = :month")
    suspend fun getTotalBudgetForMonthNow(userId: Int, month: String): Double

    @Query("SELECT * FROM budgets WHERE userId = :userId AND month = :month AND categoryId = :categoryId LIMIT 1")
    suspend fun getBudgetForCategory(userId: Int, month: String, categoryId: Int): BudgetEntity?

    @Query("DELETE FROM budgets WHERE userId = :userId AND month = :month AND categoryId = :categoryId")
    suspend fun deleteBudgetForCategory(userId: Int, month: String, categoryId: Int)

    @Query("DELETE FROM budgets WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Int)
}

// ─────────────────────────────────────────────────────────────────────────────
// SAVINGS GOAL DAO
// ─────────────────────────────────────────────────────────────────────────────
@Dao
interface SavingsGoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: SavingsGoalEntity): Long

    @Update
    suspend fun update(goal: SavingsGoalEntity)

    @Delete
    suspend fun delete(goal: SavingsGoalEntity)

    @Query("SELECT * FROM savings_goals WHERE userId = :userId AND isCompleted = 0 ORDER BY targetDate ASC")
    fun getActive(userId: Int): LiveData<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE userId = :userId AND isCompleted = 1 ORDER BY completedDate DESC")
    fun getCompleted(userId: Int): LiveData<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE userId = :userId ORDER BY isCompleted ASC, targetDate ASC")
    fun getAll(userId: Int): LiveData<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE id = :id AND userId = :userId")
    suspend fun getById(id: Int, userId: Int): SavingsGoalEntity?

    @Query("SELECT COALESCE(SUM(savedAmount), 0) FROM savings_goals WHERE userId = :userId AND isCompleted = 0")
    fun getTotalActiveSaved(userId: Int): LiveData<Double>

    @Query("SELECT COALESCE(SUM(savedAmount), 0) FROM savings_goals WHERE userId = :userId")
    fun getTotalSavedAcrossAll(userId: Int): LiveData<Double>

    @Query("SELECT COUNT(*) FROM savings_goals WHERE userId = :userId AND isCompleted = 0")
    fun countActive(userId: Int): LiveData<Int>

    @Query("SELECT COUNT(*) FROM savings_goals WHERE userId = :userId AND isCompleted = 1")
    fun countCompleted(userId: Int): LiveData<Int>

    @Query("SELECT COUNT(*) FROM savings_goals WHERE userId = :userId AND isCompleted = 1")
    suspend fun countCompletedNow(userId: Int): Int

    @Query("SELECT COUNT(*) FROM savings_goals WHERE userId = :userId AND isCompleted = 0")
    suspend fun countActiveNow(userId: Int): Int

    @Query("UPDATE savings_goals SET savedAmount = savedAmount + :amount WHERE id = :goalId AND userId = :userId")
    suspend fun addContribution(goalId: Int, userId: Int, amount: Double)

    @Query("UPDATE savings_goals SET isCompleted = 1, completedDate = :date, xpEarned = 200 WHERE id = :goalId AND userId = :userId")
    suspend fun markCompleted(goalId: Int, userId: Int, date: String)

    @Query("DELETE FROM savings_goals WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Int)
}

// ─────────────────────────────────────────────────────────────────────────────
// NOTIFICATION DAO
// ─────────────────────────────────────────────────────────────────────────────
@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity): Long

    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAll(userId: Int): LiveData<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE userId = :userId AND tag = :tag ORDER BY createdAt DESC")
    fun getByTag(userId: Int, tag: String): LiveData<List<NotificationEntity>>

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id AND userId = :userId")
    suspend fun markRead(id: Int, userId: Int)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    suspend fun markAllRead(userId: Int)

    @Query("DELETE FROM notifications WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: Int, userId: Int)

    @Query("DELETE FROM notifications WHERE userId = :userId")
    suspend fun deleteAll(userId: Int)

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND isRead = 0")
    fun countUnread(userId: Int): LiveData<Int>
}

// ─────────────────────────────────────────────────────────────────────────────
// BADGE DAO
// ─────────────────────────────────────────────────────────────────────────────
@Dao
interface BadgeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(badge: BadgeEntity): Long

    @Query("SELECT * FROM badges WHERE userId = :userId ORDER BY earnedAt ASC")
    fun getAll(userId: Int): LiveData<List<BadgeEntity>>

    @Query("SELECT * FROM badges WHERE userId = :userId ORDER BY earnedAt ASC")
    suspend fun getAllNow(userId: Int): List<BadgeEntity>

    @Query("SELECT COUNT(*) FROM badges WHERE userId = :userId AND badgeKey = :key")
    suspend fun hasBadge(userId: Int, key: String): Int

    @Query("DELETE FROM badges WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Int)
}
