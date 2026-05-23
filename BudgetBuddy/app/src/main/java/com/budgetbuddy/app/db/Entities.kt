package com.budgetbuddy.app.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ─────────────────────────────────────────────────────────────────────────────
// USER
// ─────────────────────────────────────────────────────────────────────────────
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val email: String,
    val passwordHash: String,
    val avatarInitials: String,
    val memberSince: String,
    val totalXp: Int = 0,
    val level: Int = 1,
    val dayStreak: Int = 0,
    val lastLoggedDate: String = "",
    val totalSaved: Double = 0.0,
    val currency: String = "ZAR",
    val notificationsEnabled: Boolean = true
)

// ─────────────────────────────────────────────────────────────────────────────
// EXPENSE CATEGORY
// ─────────────────────────────────────────────────────────────────────────────
@Entity(
    tableName = "expense_categories",
    foreignKeys = [ForeignKey(
        entity        = UserEntity::class,
        parentColumns = ["id"],
        childColumns  = ["userId"],
        onDelete      = ForeignKey.CASCADE
    )],
    indices = [Index("userId")]
)
data class ExpenseCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val name: String,
    val emoji: String,
    val isActive: Boolean = true
)

// ─────────────────────────────────────────────────────────────────────────────
// EXPENSE
// ─────────────────────────────────────────────────────────────────────────────
@Entity(
    tableName = "expenses",
    foreignKeys = [ForeignKey(
        entity        = UserEntity::class,
        parentColumns = ["id"],
        childColumns  = ["userId"],
        onDelete      = ForeignKey.CASCADE
    )],
    indices = [Index("userId")]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val amount: Double,
    val description: String,
    val categoryId: Int,
    val categoryName: String,
    val categoryEmoji: String,
    val date: String,
    val month: String,
    val receiptPath: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────────
// BUDGET
// ─────────────────────────────────────────────────────────────────────────────
@Entity(
    tableName = "budgets",
    foreignKeys = [ForeignKey(
        entity        = UserEntity::class,
        parentColumns = ["id"],
        childColumns  = ["userId"],
        onDelete      = ForeignKey.CASCADE
    )],
    indices = [Index("userId")]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val categoryId: Int,
    val categoryName: String,
    val categoryEmoji: String,
    val amount: Double,
    val month: String
)

// ─────────────────────────────────────────────────────────────────────────────
// SAVINGS GOAL
// ─────────────────────────────────────────────────────────────────────────────
@Entity(
    tableName = "savings_goals",
    foreignKeys = [ForeignKey(
        entity        = UserEntity::class,
        parentColumns = ["id"],
        childColumns  = ["userId"],
        onDelete      = ForeignKey.CASCADE
    )],
    indices = [Index("userId")]
)
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val name: String,
    val targetAmount: Double,
    val savedAmount: Double = 0.0,
    val targetDate: String,
    val frequency: String,
    val contributionAmount: Double = 0.0,
    val isCompleted: Boolean = false,
    val completedDate: String = "",
    val xpEarned: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────────
// NOTIFICATION
// ─────────────────────────────────────────────────────────────────────────────
@Entity(
    tableName = "notifications",
    foreignKeys = [ForeignKey(
        entity        = UserEntity::class,
        parentColumns = ["id"],
        childColumns  = ["userId"],
        onDelete      = ForeignKey.CASCADE
    )],
    indices = [Index("userId")]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val icon: String,
    val title: String,
    val body: String,
    val time: String,
    val tag: String,
    val groupLabel: String = "",
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────────
// BADGE
// Tracks which badges a user has earned.
// badgeKey matches the string constants in BadgeKeys (e.g. "first_budget").
// ─────────────────────────────────────────────────────────────────────────────
@Entity(
    tableName = "badges",
    foreignKeys = [ForeignKey(
        entity        = UserEntity::class,
        parentColumns = ["id"],
        childColumns  = ["userId"],
        onDelete      = ForeignKey.CASCADE
    )],
    indices = [Index("userId"), Index(value = ["userId", "badgeKey"], unique = true)]
)
data class BadgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val badgeKey: String,
    val earnedAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────────
// BADGE KEYS — single source of truth for badge identifiers
// ─────────────────────────────────────────────────────────────────────────────
object BadgeKeys {
    const val FIRST_BUDGET    = "first_budget"    // Set first category budget
    const val STREAK_7        = "streak_7"        // 7-day logging streak
    const val FIRST_SAVER     = "first_saver"     // Make first savings goal contribution
    const val STREAK_30       = "streak_30"       // 30-day logging streak
    const val ALL_GOALS       = "all_goals"       // Complete all active savings goals
    const val BUDGET_MASTER   = "budget_master"   // Stay within budget 3 months in a row
    const val NO_SPEND_DAY    = "no_spend_day"    // Log no expenses for a full day (explicitly)
    const val GOAL_CRUSHER    = "goal_crusher"    // Complete 3 savings goals total
    const val CONSISTENT_SAVER = "consistent_saver" // Make contributions for 4 weeks in a row
}
