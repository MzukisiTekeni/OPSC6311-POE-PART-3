package com.budgetbuddy.app

import android.os.Bundle
import android.view.*
import android.widget.*
// AppCompatActivity replaced by BaseThemedActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.budgetbuddy.app.db.BudgetRepository
import com.budgetbuddy.app.db.NotificationEntity
import com.budgetbuddy.app.db.SessionManager
import kotlinx.coroutines.launch

class NotifAdapter(
    private val items: MutableList<NotificationEntity>,
    private val onDismiss: (NotificationEntity) -> Unit,
    private val onRead: (NotificationEntity) -> Unit
) : RecyclerView.Adapter<NotifAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val dot: View        = v.findViewById(R.id.v_unread_dot)
        val icon: TextView   = v.findViewById(R.id.tv_notif_icon)
        val title: TextView  = v.findViewById(R.id.tv_notif_title)
        val body: TextView   = v.findViewById(R.id.tv_notif_body)
        val time: TextView   = v.findViewById(R.id.tv_time)
        val tag: TextView    = v.findViewById(R.id.tv_tag)
        val close: ImageView = v.findViewById(R.id.iv_close)
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_notification_card, p, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val n = items[pos]
        h.dot.visibility = if (!n.isRead) View.VISIBLE else View.INVISIBLE
        h.icon.text      = n.icon
        h.title.text     = n.title
        h.body.text      = n.body
        h.time.text      = n.time

        val (bgRes, colorRes, label) = when (n.tag) {
            "ALERT"   -> Triple(R.drawable.bg_tag_alert,   R.color.tag_alert_text,   "Alert")
            "NUDGE"   -> Triple(R.drawable.bg_tag_nudge,   R.color.tag_nudge_text,   "Nudge")
            "INSIGHT" -> Triple(R.drawable.bg_tag_insight, R.color.tag_insight_text, "Insight")
            else      -> Triple(R.drawable.bg_tag_nudge,   R.color.tag_nudge_text,   n.tag)
        }
        h.tag.setBackgroundResource(bgRes)
        h.tag.setTextColor(h.itemView.context.getColor(colorRes))
        h.tag.text = label

        // Tap card body → mark as read
        h.itemView.setOnClickListener { if (!n.isRead) onRead(n) }
        // Tap X → delete
        h.close.setOnClickListener { onDismiss(n) }
    }

    fun update(newItems: List<NotificationEntity>) {
        items.clear(); items.addAll(newItems); notifyDataSetChanged()
    }
}

class NotificationsActivity : BaseThemedActivity() {

    override fun themedBackgroundViewIds() = emptyList<Int>()
    override fun themedTextViewIds()       = listOf(R.id.btn_clear_all)


    private lateinit var repo: BudgetRepository
    private lateinit var adapter: NotifAdapter
    private var userId     = -1
    private var allNotifs  = listOf<NotificationEntity>()
    private var currentTag : String? = null

    override fun onResume() {
        super.onResume()
        // Repaint selected tab whenever we return (theme may have changed)
        if (::adapter.isInitialized) {
            val selId = notifTabMap.entries.firstOrNull { it.value == currentTag }?.key ?: R.id.tab_all
            paintTabs(selId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        applyCurrentTheme()
        repo   = BudgetRepository(this)
        userId = SessionManager.getUserId(this)

        findViewById<ImageView>(R.id.iv_back).setOnClickListener { finish() }

        adapter = NotifAdapter(
            mutableListOf(),
            onDismiss = { notif ->
                lifecycleScope.launch { repo.dismissNotification(userId, notif.id) }
            },
            onRead = { notif ->
                lifecycleScope.launch { repo.notificationDao.markRead(notif.id, userId) }
            }
        )

        findViewById<RecyclerView>(R.id.rv_notifications).apply {
            layoutManager = LinearLayoutManager(this@NotificationsActivity)
            adapter        = this@NotificationsActivity.adapter
        }

        // Mark all unread as read the moment the screen opens
        lifecycleScope.launch { repo.notificationDao.markAllRead(userId) }

        findViewById<TextView>(R.id.btn_clear_all).setOnClickListener {
            lifecycleScope.launch { repo.clearAllNotifications(userId) }
        }

        // Single LiveData drives both the list and the badge
        repo.getAllNotifications(userId).observe(this) { notifs ->
            allNotifs = notifs
            filterAndDisplay()
            updateBadge(notifs.count { !it.isRead })
        }

        setupTabs()
    }

    /**
     * Updates the red badge TextView (tv_notif_badge) that sits on top of the
     * bell icon in whatever layout references this screen.
     * The badge is hidden when count == 0.
     */
    private fun updateBadge(unread: Int) {
        val badge = findViewById<TextView?>(R.id.tv_notif_badge) ?: return
        if (unread > 0) {
            badge.visibility = View.VISIBLE
            badge.text       = if (unread > 99) "99+" else unread.toString()
        } else {
            badge.visibility = View.GONE
        }
    }

    private val notifTabMap: Map<Int, String?> = mapOf(
        R.id.tab_all      to null,
        R.id.tab_alerts   to "ALERT",
        R.id.tab_nudges   to "NUDGE",
        R.id.tab_insights to "INSIGHT"
    )

    private fun paintTabs(selectedId: Int) {
        val primary = ThemeManager.getPalette(this).primary
        notifTabMap.keys.forEach { id ->
            val tv = findViewById<TextView>(id) ?: return@forEach
            if (id == selectedId) {
                ThemeManager.tintBackground(tv, primary)
                tv.setTextColor(getColor(R.color.text_on_primary))
            } else {
                tv.setBackgroundResource(R.drawable.bg_chip_unselected)
                tv.setTextColor(getColor(R.color.text_secondary))
            }
        }
    }

    private fun setupTabs() {
        // Paint initial state (All selected)
        paintTabs(R.id.tab_all)

        notifTabMap.forEach { (id, tag) ->
            findViewById<TextView>(id).setOnClickListener {
                currentTag = tag
                paintTabs(id)
                filterAndDisplay()
            }
        }
    }

    private fun filterAndDisplay() {
        val filtered = if (currentTag == null) allNotifs
                       else allNotifs.filter { it.tag == currentTag }
        adapter.update(filtered)
    }
}
