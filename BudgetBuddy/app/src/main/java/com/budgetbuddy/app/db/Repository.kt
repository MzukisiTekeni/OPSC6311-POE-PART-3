package com.budgetbuddy.app.db

import android.content.Context
import androidx.lifecycle.LiveData
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * BudgetRepository
 * ─────────────────
 * Every method that reads or writes data requires a userId so that no two
 * users ever see each other's data. The userId always comes from SessionManager.
 */
class BudgetRepository(context: Context) {

    private val db          = AppDatabase.getInstance(context)
    val userDao             = db.userDao()
    val categoryDao         = db.expenseCategoryDao()
    val expenseDao          = db.expenseDao()
    val budgetDao           = db.budgetDao()
    val savingsGoalDao      = db.savingsGoalDao()
    val notificationDao     = db.notificationDao()
    val badgeDao            = db.badgeDao()

    fun currentMonth(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
    fun today(): String        = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    // ── User ──────────────────────────────────────────────────────────────────

    suspend fun registerUser(username: String, email: String, password: String): Long {
        val hash     = password.hashCode().toString()
        val initials = username.take(2).uppercase()
        val month    = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        val user = UserEntity(
            username       = username,
            email          = email,
            passwordHash   = hash,
            avatarInitials = initials,
            memberSince    = month
        )
        return userDao.insertUser(user)
    }

    suspend fun loginUser(username: String, password: String): UserEntity? {
        val user = userDao.getUserByUsername(username) ?: return null
        val hash = password.hashCode().toString()
        return if (user.passwordHash == hash) user else null
    }

    fun getLoggedInUser(userId: Int): LiveData<UserEntity?> = userDao.getUserById(userId)

    suspend fun addXp(userId: Int, xp: Int) {
        userDao.addXp(userId, xp)
        val user = userDao.getUserByIdNow(userId) ?: return
        val newLevel = (user.totalXp / 2000) + 1
        if (newLevel > user.level) userDao.updateLevel(userId, newLevel)
    }

    suspend fun updateStreak(userId: Int) {
        val user      = userDao.getUserByIdNow(userId) ?: return
        val todayStr  = today()
        if (user.lastLoggedDate == todayStr) return
        val yesterday = LocalDate.now().minusDays(1)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val newStreak = if (user.lastLoggedDate == yesterday) user.dayStreak + 1 else 1
        userDao.updateStreak(userId, newStreak, todayStr)
        // Check streak badges after updating
        checkStreakBadges(userId, newStreak)
    }

    // ── Categories ────────────────────────────────────────────────────────────

    fun getAllCategories(userId: Int): LiveData<List<ExpenseCategoryEntity>> =
        categoryDao.getAll(userId)

    fun getActiveCategories(userId: Int): LiveData<List<ExpenseCategoryEntity>> =
        categoryDao.getAllActive(userId)

    suspend fun getActiveCategoriesNow(userId: Int): List<ExpenseCategoryEntity> =
        categoryDao.getAllActiveNow(userId)

    suspend fun saveSelectedCategories(userId: Int, selected: List<Pair<String, String>>) {
        selected.forEach { (emoji, name) ->
            categoryDao.insert(ExpenseCategoryEntity(userId = userId, name = name, emoji = emoji))
        }
    }

    suspend fun addCustomCategory(userId: Int, emoji: String, name: String): Long =
        categoryDao.insert(ExpenseCategoryEntity(userId = userId, name = name, emoji = emoji))

    // ── Expenses ──────────────────────────────────────────────────────────────

    fun getExpensesByMonth(userId: Int, month: String): LiveData<List<ExpenseEntity>> =
        expenseDao.getByMonth(userId, month)

    fun getTotalSpentByMonth(userId: Int, month: String): LiveData<Double> =
        expenseDao.getTotalSpentByMonth(userId, month)

    fun getSpendingByCategory(userId: Int, month: String): LiveData<List<CategorySpending>> =
        expenseDao.getSpendingByCategoryForMonth(userId, month)

    suspend fun saveExpense(
        userId: Int,
        amount: Double,
        description: String,
        category: ExpenseCategoryEntity,
        date: String,
        receiptPath: String = ""
    ): Long {
        val month = date.substring(0, 7)
        val id = expenseDao.insert(
            ExpenseEntity(
                userId        = userId,
                amount        = amount,
                description   = description,
                categoryId    = category.id,
                categoryName  = category.name,
                categoryEmoji = category.emoji,
                date          = date,
                month         = month,
                receiptPath   = receiptPath
            )
        )
        // Award XP for logging an expense
        addXp(userId, 10)
        // Update streak
        updateStreak(userId)
        return id
    }

    suspend fun deleteExpense(expense: ExpenseEntity) = expenseDao.delete(expense)

    suspend fun getWeeklyTotals(userId: Int): List<DailyTotal> {
        val sevenDaysAgo = LocalDate.now().minusDays(6)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        return expenseDao.getDailyTotals(userId, sevenDaysAgo)
    }

    suspend fun getDaysTracked(userId: Int, month: String): Int =
        expenseDao.getDaysTrackedInMonth(userId, month)

    // ── Budgets ───────────────────────────────────────────────────────────────

    fun getBudgetsByMonth(userId: Int, month: String): LiveData<List<BudgetEntity>> =
        budgetDao.getByMonth(userId, month)

    fun getTotalBudget(userId: Int, month: String): LiveData<Double> =
        budgetDao.getTotalBudgetForMonth(userId, month)

    suspend fun saveBudget(
        userId: Int,
        category: ExpenseCategoryEntity,
        amount: Double,
        month: String
    ): Long {
        budgetDao.deleteBudgetForCategory(userId, month, category.id)
        val id = budgetDao.insert(
            BudgetEntity(
                userId        = userId,
                categoryId    = category.id,
                categoryName  = category.name,
                categoryEmoji = category.emoji,
                amount        = amount,
                month         = month
            )
        )
        // Award "First budget" badge when user sets their first budget
        awardBadgeIfNew(userId, BadgeKeys.FIRST_BUDGET)
        return id
    }

    // ── Overall budget balance (SharedPreferences, keyed by userId) ───────────

    private fun budgetPrefs(context: Context) =
        context.getSharedPreferences("budgetbuddy_budget", Context.MODE_PRIVATE)

    fun saveOverallBudget(context: Context, userId: Int, amount: Double) {
        budgetPrefs(context).edit()
            .putFloat("overall_$userId", amount.toFloat())
            .putFloat("balance_$userId", amount.toFloat())
            .apply()
    }

    fun loadOverallBudget(context: Context, userId: Int): Double =
        budgetPrefs(context).getFloat("overall_$userId", 0f).toDouble()

    fun loadBudgetBalance(context: Context, userId: Int): Double =
        budgetPrefs(context).getFloat(
            "balance_$userId",
            budgetPrefs(context).getFloat("overall_$userId", 0f)
        ).toDouble()

    fun adjustOverallBudgetBalance(context: Context, userId: Int, delta: Double) {
        val prefs   = budgetPrefs(context)
        val current = prefs.getFloat("balance_$userId",
            prefs.getFloat("overall_$userId", 0f))
        prefs.edit().putFloat("balance_$userId", (current + delta).toFloat()).apply()
    }

    // ── Savings Goals ─────────────────────────────────────────────────────────

    fun getActiveGoals(userId: Int): LiveData<List<SavingsGoalEntity>> =
        savingsGoalDao.getActive(userId)

    fun getCompletedGoals(userId: Int): LiveData<List<SavingsGoalEntity>> =
        savingsGoalDao.getCompleted(userId)

    fun getAllGoals(userId: Int): LiveData<List<SavingsGoalEntity>> =
        savingsGoalDao.getAll(userId)

    fun getTotalSaved(userId: Int): LiveData<Double> =
        savingsGoalDao.getTotalSavedAcrossAll(userId)

    fun countActiveGoals(userId: Int): LiveData<Int> =
        savingsGoalDao.countActive(userId)

    fun countCompletedGoals(userId: Int): LiveData<Int> =
        savingsGoalDao.countCompleted(userId)

    suspend fun createSavingsGoal(
        userId: Int,
        name: String,
        targetAmount: Double,
        targetDate: String,
        frequency: String,
        contributionAmount: Double
    ): Long = savingsGoalDao.insert(
        SavingsGoalEntity(
            userId             = userId,
            name               = name,
            targetAmount       = targetAmount,
            targetDate         = targetDate,
            frequency          = frequency,
            contributionAmount = contributionAmount
        )
    )

    suspend fun addGoalContribution(goalId: Int, userId: Int, amount: Double) {
        savingsGoalDao.addContribution(goalId, userId, amount)
        // Award "First saver" badge when user makes their first contribution
        awardBadgeIfNew(userId, BadgeKeys.FIRST_SAVER)
        // Check if consistent saver (simplified: award after any 4+ contributions)
        checkConsistentSaverBadge(userId)
    }

    suspend fun completeGoal(goal: SavingsGoalEntity, userId: Int) {
        savingsGoalDao.markCompleted(goal.id, userId, today())
        userDao.addXp(userId, 200)
        userDao.addToTotalSaved(userId, goal.savedAmount)
        // Check goal-related badges
        val completed = savingsGoalDao.countCompletedNow(userId)
        val active    = savingsGoalDao.countActiveNow(userId)
        if (active == 0 && completed > 0) awardBadgeIfNew(userId, BadgeKeys.ALL_GOALS)
        if (completed >= 3)               awardBadgeIfNew(userId, BadgeKeys.GOAL_CRUSHER)
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    fun getAllNotifications(userId: Int): LiveData<List<NotificationEntity>> =
        notificationDao.getAll(userId)

    fun countUnread(userId: Int): LiveData<Int> =
        notificationDao.countUnread(userId)

    suspend fun addNotification(
        userId: Int,
        icon: String,
        title: String,
        body: String,
        time: String,
        tag: String,
        groupLabel: String = "Today"
    ) = notificationDao.insert(
        NotificationEntity(
            userId     = userId,
            icon       = icon,
            title      = title,
            body       = body,
            time       = time,
            tag        = tag,
            groupLabel = groupLabel
        )
    )

    suspend fun dismissNotification(userId: Int, id: Int) =
        notificationDao.deleteById(id, userId)

    suspend fun clearAllNotifications(userId: Int) =
        notificationDao.deleteAll(userId)

    // ── Badges ────────────────────────────────────────────────────────────────

    fun getEarnedBadges(userId: Int): LiveData<List<BadgeEntity>> =
        badgeDao.getAll(userId)

    suspend fun getEarnedBadgeKeysNow(userId: Int): Set<String> =
        badgeDao.getAllNow(userId).map { it.badgeKey }.toSet()

    /**
     * Inserts a badge only if the user doesn't already have it.
     * Returns true if newly awarded.
     */
    suspend fun awardBadgeIfNew(userId: Int, badgeKey: String): Boolean {
        if (badgeDao.hasBadge(userId, badgeKey) > 0) return false
        badgeDao.insert(BadgeEntity(userId = userId, badgeKey = badgeKey))
        return true
    }

    private suspend fun checkStreakBadges(userId: Int, streak: Int) {
        if (streak >= 7)  awardBadgeIfNew(userId, BadgeKeys.STREAK_7)
        if (streak >= 30) awardBadgeIfNew(userId, BadgeKeys.STREAK_30)
    }

    /**
     * Award BUDGET_MASTER when the user has stayed within budget for a full month.
     * Call this from MonthlyBudgetActivity or BudgetHealthActivity after computing
     * whether the user was within budget this month.
     */
    suspend fun checkBudgetMasterBadge(userId: Int, withinBudget: Boolean) {
        if (withinBudget) awardBadgeIfNew(userId, BadgeKeys.BUDGET_MASTER)
    }

    /**
     * Award NO_SPEND_DAY when a full calendar day passes with zero expenses logged.
     * Call this from wherever you explicitly want to reward no-spend days.
     */
    suspend fun checkNoSpendDayBadge(userId: Int) {
        awardBadgeIfNew(userId, BadgeKeys.NO_SPEND_DAY)
    }

    private suspend fun checkConsistentSaverBadge(userId: Int) {
        // Award after making contributions to 4 or more different goals total
        val activeGoals = savingsGoalDao.countActiveNow(userId)
        val completedGoals = savingsGoalDao.countCompletedNow(userId)
        if (activeGoals + completedGoals >= 4) {
            awardBadgeIfNew(userId, BadgeKeys.CONSISTENT_SAVER)
        }
    }

    suspend fun clearBadges(userId: Int) = badgeDao.deleteAllForUser(userId)
}
