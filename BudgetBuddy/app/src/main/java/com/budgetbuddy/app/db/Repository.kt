package com.budgetbuddy.app.db

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * BudgetRepository
 * ─────────────────
 * This is the single source of truth for all data operations in the app.
 * Every method takes a userId so that no two users ever see each other's data.
 * The userId always comes from SessionManager - I never hardcode it.
 *
 * I kept all DB access in here so the Activities don't have to know
 * anything about Room, DAOs, or coroutines beyond calling these methods.
 */
class BudgetRepository(context: Context) {

    private val TAG = "BudgetRepository"

    private val db          = AppDatabase.getInstance(context)
    val userDao             = db.userDao()
    val categoryDao         = db.expenseCategoryDao()
    val expenseDao          = db.expenseDao()
    val budgetDao           = db.budgetDao()
    val savingsGoalDao      = db.savingsGoalDao()
    val notificationDao     = db.notificationDao()
    val badgeDao            = db.badgeDao()

    // Formatters I use throughout the app - centralised here so nothing drifts out of sync
    fun currentMonth(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
    fun today(): String        = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    // ── User ──────────────────────────────────────────────────────────────────

    /**
     * Creates a new user account. Password is stored as a hash (not plaintext).
     * Initials are derived from the first 2 chars of the username for the avatar.
     */
    suspend fun registerUser(username: String, email: String, password: String): Long {
        Log.d(TAG, "registerUser: Creating account for username='$username', email='$email'")

        // hashCode() isn't cryptographically safe but it's fine for a student project
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
        val newId = userDao.insertUser(user)
        Log.i(TAG, "registerUser: New user inserted with id=$newId, initials='$initials'")
        return newId
    }

    /**
     * Looks up the user by username and checks the password hash matches.
     * Returns null if either the username doesn't exist or the password is wrong.
     */
    suspend fun loginUser(username: String, password: String): UserEntity? {
        Log.d(TAG, "loginUser: Attempting login for username='$username'")

        val user = userDao.getUserByUsername(username)
        if (user == null) {
            Log.w(TAG, "loginUser: No user found with username='$username'")
            return null
        }

        val hash = password.hashCode().toString()
        return if (user.passwordHash == hash) {
            Log.i(TAG, "loginUser: Password matched for userId=${user.id}")
            user
        } else {
            Log.w(TAG, "loginUser: Password hash mismatch for username='$username'")
            null
        }
    }

    fun getLoggedInUser(userId: Int): LiveData<UserEntity?> {
        Log.d(TAG, "getLoggedInUser: Subscribing to live user data for userId=$userId")
        return userDao.getUserById(userId)
    }

    /**
     * Adds XP to the user and checks if they've crossed a level-up threshold.
     * Every 2000 XP = 1 level. If they level up, the level field is updated too.
     */
    suspend fun addXp(userId: Int, xp: Int) {
        Log.d(TAG, "addXp: Adding $xp XP to userId=$userId")
        userDao.addXp(userId, xp)

        val user = userDao.getUserByIdNow(userId) ?: run {
            Log.w(TAG, "addXp: User not found after XP update for userId=$userId")
            return
        }

        val newLevel = (user.totalXp / 2000) + 1
        if (newLevel > user.level) {
            Log.i(TAG, "addXp: Level up! userId=$userId is now level $newLevel (totalXp=${user.totalXp})")
            userDao.updateLevel(userId, newLevel)
        }
    }

    /**
     * Updates the user's daily streak. If they logged something yesterday, the streak continues.
     * If they missed a day, the streak resets to 1. Same day = no change.
     */
    suspend fun updateStreak(userId: Int) {
        val user     = userDao.getUserByIdNow(userId) ?: return
        val todayStr = today()

        // Already logged today - don't double-count it
        if (user.lastLoggedDate == todayStr) {
            Log.d(TAG, "updateStreak: Already logged today for userId=$userId - no change")
            return
        }

        val yesterday = LocalDate.now().minusDays(1)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        val newStreak = if (user.lastLoggedDate == yesterday) {
            Log.d(TAG, "updateStreak: Consecutive day detected - extending streak to ${user.dayStreak + 1}")
            user.dayStreak + 1
        } else {
            Log.d(TAG, "updateStreak: Streak broken for userId=$userId - resetting to 1")
            1
        }

        userDao.updateStreak(userId, newStreak, todayStr)
        Log.i(TAG, "updateStreak: userId=$userId streak is now $newStreak")

        // Check if this new streak qualifies for any badges
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
        Log.d(TAG, "saveSelectedCategories: Saving ${selected.size} categories for userId=$userId")
        selected.forEach { (emoji, name) ->
            categoryDao.insert(ExpenseCategoryEntity(userId = userId, name = name, emoji = emoji))
        }
        Log.d(TAG, "saveSelectedCategories: Done inserting categories")
    }

    suspend fun addCustomCategory(userId: Int, emoji: String, name: String): Long {
        Log.d(TAG, "addCustomCategory: Adding '$emoji $name' for userId=$userId")
        val id = categoryDao.insert(ExpenseCategoryEntity(userId = userId, name = name, emoji = emoji))
        Log.i(TAG, "addCustomCategory: Inserted with id=$id")
        return id
    }

    // ── Expenses ──────────────────────────────────────────────────────────────

    fun getExpensesByMonth(userId: Int, month: String): LiveData<List<ExpenseEntity>> =
        expenseDao.getByMonth(userId, month)

    fun getTotalSpentByMonth(userId: Int, month: String): LiveData<Double> =
        expenseDao.getTotalSpentByMonth(userId, month)

    fun getSpendingByCategory(userId: Int, month: String): LiveData<List<CategorySpending>> =
        expenseDao.getSpendingByCategoryForMonth(userId, month)

    /**
     * Saves an expense record and awards XP + updates the streak.
     * The month is extracted from the date string (yyyy-MM-dd → yyyy-MM)
     * so monthly queries always work correctly.
     */
    suspend fun saveExpense(
        userId: Int,
        amount: Double,
        description: String,
        category: ExpenseCategoryEntity,
        date: String,
        receiptPath: String = ""
    ): Long {
        // Pull the month out of the date so we can query by month later
        val month = date.substring(0, 7)
        Log.d(TAG, "saveExpense: userId=$userId, amount=R$amount, category='${category.name}', date=$date")

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
        Log.i(TAG, "saveExpense: Expense inserted with id=$id for month=$month")

        // Award XP for logging an expense - encourages daily use
        addXp(userId, 10)
        updateStreak(userId)
        return id
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        Log.d(TAG, "deleteExpense: Deleting expense id=${expense.id}, amount=R${expense.amount}")
        expenseDao.delete(expense)
    }

    suspend fun getWeeklyTotals(userId: Int): List<DailyTotal> {
        // Go back 7 days including today for the weekly chart
        val sevenDaysAgo = LocalDate.now().minusDays(6)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        Log.d(TAG, "getWeeklyTotals: Fetching daily totals from $sevenDaysAgo for userId=$userId")
        return expenseDao.getDailyTotals(userId, sevenDaysAgo)
    }

    suspend fun getDaysTracked(userId: Int, month: String): Int =
        expenseDao.getDaysTrackedInMonth(userId, month)

    // ── Budgets ───────────────────────────────────────────────────────────────

    fun getBudgetsByMonth(userId: Int, month: String): LiveData<List<BudgetEntity>> =
        budgetDao.getByMonth(userId, month)

    fun getTotalBudget(userId: Int, month: String): LiveData<Double> =
        budgetDao.getTotalBudgetForMonth(userId, month)

    /**
     * Saves (or replaces) the budget for a specific category.
     * I delete the old entry first because upsert wasn't available in the
     * version of Room I'm using, so this achieves the same result.
     */
    suspend fun saveBudget(
        userId: Int,
        category: ExpenseCategoryEntity,
        amount: Double,
        month: String
    ): Long {
        Log.d(TAG, "saveBudget: Setting R$amount budget for '${category.name}' in $month (userId=$userId)")

        // Remove any existing budget for this category+month before inserting the new one
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

        Log.i(TAG, "saveBudget: Budget inserted with id=$id")

        // Award the "First Budget" badge the first time a user sets any budget
        awardBadgeIfNew(userId, BadgeKeys.FIRST_BUDGET)
        return id
    }

    // ── Overall budget balance (SharedPreferences, keyed by userId) ───────────
    // I'm using SharedPreferences here instead of Room because it's a single scalar
    // that doesn't need relational querying - simpler and faster

    private fun budgetPrefs(context: Context) =
        context.getSharedPreferences("budgetbuddy_budget", Context.MODE_PRIVATE)

    fun saveOverallBudget(context: Context, userId: Int, amount: Double) {
        Log.d(TAG, "saveOverallBudget: Saving overall budget R$amount for userId=$userId")
        budgetPrefs(context).edit()
            .putFloat("overall_$userId", amount.toFloat())
            .apply()
    }

    fun loadOverallBudget(context: Context, userId: Int): Double {
        val amount = budgetPrefs(context).getFloat("overall_$userId", 0f).toDouble()
        Log.d(TAG, "loadOverallBudget: Loaded R$amount for userId=$userId")
        return amount
    }

    fun loadBudgetBalance(context: Context, userId: Int): Double {
        val balance = budgetPrefs(context).getFloat("balance_$userId", 0f).toDouble()
        Log.d(TAG, "loadBudgetBalance: Current balance R$balance for userId=$userId")
        return balance
    }

    fun adjustOverallBudgetBalance(context: Context, userId: Int, delta: Double) {
        val prefs   = budgetPrefs(context)
        val current = prefs.getFloat("balance_$userId", 0f).toDouble()
        val updated = current + delta
        Log.d(TAG, "adjustOverallBudgetBalance: userId=$userId balance R$current + R$delta = R$updated")
        prefs.edit().putFloat("balance_$userId", updated.toFloat()).apply()
    }

    fun saveMinGoal(context: Context, userId: Int, amount: Double) {
        Log.d(TAG, "saveMinGoal: Saving min monthly savings goal R$amount for userId=$userId")
        budgetPrefs(context).edit()
            .putFloat("min_goal_$userId", amount.toFloat())
            .apply()
    }

    fun loadMinGoal(context: Context, userId: Int): Double =
        budgetPrefs(context).getFloat("min_goal_$userId", 0f).toDouble()

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
    ): Long {
        Log.d(TAG, "createSavingsGoal: '$name' target=R$targetAmount by $targetDate for userId=$userId")
        val id = savingsGoalDao.insert(
            SavingsGoalEntity(
                userId             = userId,
                name               = name,
                targetAmount       = targetAmount,
                targetDate         = targetDate,
                frequency          = frequency,
                contributionAmount = contributionAmount
            )
        )
        Log.i(TAG, "createSavingsGoal: Goal created with id=$id")
        return id
    }

    suspend fun addGoalContribution(goalId: Int, userId: Int, amount: Double) {
        Log.d(TAG, "addGoalContribution: Adding R$amount to goalId=$goalId for userId=$userId")
        savingsGoalDao.addContribution(goalId, userId, amount)

        // Award the badge when the user makes their very first savings contribution
        awardBadgeIfNew(userId, BadgeKeys.FIRST_SAVER)
        checkConsistentSaverBadge(userId)
    }

    /**
     * Marks a goal as complete, awards 200 XP, and adds the saved amount
     * to the user's total saved stat. Also checks goal-related badges.
     */
    suspend fun completeGoal(goal: SavingsGoalEntity, userId: Int) {
        Log.i(TAG, "completeGoal: Marking goal '${goal.name}' (id=${goal.id}) as complete for userId=$userId")
        savingsGoalDao.markCompleted(goal.id, userId, today())

        // Big XP reward for finishing a goal
        userDao.addXp(userId, 200)
        userDao.addToTotalSaved(userId, goal.savedAmount)
        Log.d(TAG, "completeGoal: Awarded 200 XP, added R${goal.savedAmount} to totalSaved")

        // Check badge conditions
        val completed = savingsGoalDao.countCompletedNow(userId)
        val active    = savingsGoalDao.countActiveNow(userId)

        if (active == 0 && completed > 0) {
            Log.d(TAG, "completeGoal: All goals completed - checking ALL_GOALS badge")
            awardBadgeIfNew(userId, BadgeKeys.ALL_GOALS)
        }
        if (completed >= 3) {
            Log.d(TAG, "completeGoal: 3+ goals completed - checking GOAL_CRUSHER badge")
            awardBadgeIfNew(userId, BadgeKeys.GOAL_CRUSHER)
        }
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
    ) {
        Log.d(TAG, "addNotification: [$tag] '$title' for userId=$userId")
        notificationDao.insert(
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
    }

    suspend fun dismissNotification(userId: Int, id: Int) {
        Log.d(TAG, "dismissNotification: Deleting notificationId=$id for userId=$userId")
        notificationDao.deleteById(id, userId)
    }

    suspend fun clearAllNotifications(userId: Int) {
        Log.d(TAG, "clearAllNotifications: Wiping all notifications for userId=$userId")
        notificationDao.deleteAll(userId)
    }

    // ── Badges ────────────────────────────────────────────────────────────────

    fun getEarnedBadges(userId: Int): LiveData<List<BadgeEntity>> =
        badgeDao.getAll(userId)

    suspend fun getEarnedBadgeKeysNow(userId: Int): Set<String> =
        badgeDao.getAllNow(userId).map { it.badgeKey }.toSet()

    /**
     * Awards a badge only if the user doesn't already have it.
     * Returns true if this was a new award, false if they had it already.
     * This prevents duplicate badge entries in the DB.
     */
    suspend fun awardBadgeIfNew(userId: Int, badgeKey: String): Boolean {
        if (badgeDao.hasBadge(userId, badgeKey) > 0) {
            Log.d(TAG, "awardBadgeIfNew: userId=$userId already has badge '$badgeKey' - skipping")
            return false
        }
        Log.i(TAG, "awardBadgeIfNew: Awarding new badge '$badgeKey' to userId=$userId")
        badgeDao.insert(BadgeEntity(userId = userId, badgeKey = badgeKey))
        return true
    }

    private suspend fun checkStreakBadges(userId: Int, streak: Int) {
        Log.d(TAG, "checkStreakBadges: Checking streak badges for streak=$streak (userId=$userId)")
        if (streak >= 7)  awardBadgeIfNew(userId, BadgeKeys.STREAK_7)
        if (streak >= 30) awardBadgeIfNew(userId, BadgeKeys.STREAK_30)
    }

    /**
     * Awards BUDGET_MASTER when the user has stayed within budget for a full month.
     * This should be called from MonthlyBudgetActivity or BudgetHealthActivity.
     */
    suspend fun checkBudgetMasterBadge(userId: Int, withinBudget: Boolean) {
        Log.d(TAG, "checkBudgetMasterBadge: userId=$userId withinBudget=$withinBudget")
        if (withinBudget) awardBadgeIfNew(userId, BadgeKeys.BUDGET_MASTER)
    }

    /**
     * Awards NO_SPEND_DAY when a full day passes with zero expenses.
     * Call this from wherever you explicitly want to reward no-spend days.
     */
    suspend fun checkNoSpendDayBadge(userId: Int) {
        Log.d(TAG, "checkNoSpendDayBadge: Checking no-spend day for userId=$userId")
        awardBadgeIfNew(userId, BadgeKeys.NO_SPEND_DAY)
    }

    /**
     * Checks whether the user has been contributing to enough different goals
     * to qualify as a Consistent Saver. I set the threshold at 4 goals total.
     */
    private suspend fun checkConsistentSaverBadge(userId: Int) {
        val activeGoals    = savingsGoalDao.countActiveNow(userId)
        val completedGoals = savingsGoalDao.countCompletedNow(userId)
        val totalGoals     = activeGoals + completedGoals
        Log.d(TAG, "checkConsistentSaverBadge: userId=$userId has $totalGoals goals (active=$activeGoals, completed=$completedGoals)")

        if (totalGoals >= 4) {
            awardBadgeIfNew(userId, BadgeKeys.CONSISTENT_SAVER)
        }
    }

    suspend fun clearBadges(userId: Int) {
        Log.d(TAG, "clearBadges: Clearing all badges for userId=$userId")
        badgeDao.deleteAllForUser(userId)
    }
}
