package com.phrasepocket.app.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.phrasepocket.app.R
import com.phrasepocket.app.data.AppDatabase
import com.phrasepocket.app.data.Entry
import com.phrasepocket.app.ui.MainActivity
import com.phrasepocket.app.widget.ReviewWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Edge-docked floating ball. Tap to expand an input panel for quickly saving
 * a word or a note from any app. Requires SYSTEM_ALERT_WINDOW permission.
 */
class FloatingService : Service() {

    companion object {
        const val ACTION_STOP = "com.phrasepocket.app.action.FLOATING_STOP"
        private const val CHANNEL_ID = "floating_ball"
        private const val NOTIF_ID = 42
    }

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var ballView: View? = null
    private var panelView: View? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db by lazy { AppDatabase.get(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification())
        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (ballView == null && panelView == null) showBall()
        return START_STICKY
    }

    override fun onDestroy() {
        removeView(ballView); ballView = null
        removeView(panelView); panelView = null
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.floating_notification_title),
                NotificationManager.IMPORTANCE_LOW)
        )
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, FloatingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.floating_notification_title))
            .setContentText(getString(R.string.floating_notification_text))
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.floating_stop), stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun overlayParams(focusable: Boolean): WindowManager.LayoutParams {
        val flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = resources.displayMetrics.heightPixels / 3
        }
    }

    private fun removeView(view: View?) {
        view?.let { runCatching { windowManager.removeView(it) } }
    }

    // ---------------- Collapsed ball ----------------

    private fun showBall() {
        removeView(panelView); panelView = null
        val view = LayoutInflater.from(this).inflate(R.layout.floating_ball, null)
        val params = overlayParams(focusable = false)
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        view.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f; var downY = 0f
            var startX = 0; var startY = 0
            var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX; downY = event.rawY
                        startX = params.x; startY = params.y
                        moved = false
                        v.alpha = 1f
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) moved = true
                        params.x = startX + dx
                        params.y = startY + dy
                        windowManager.updateViewLayout(v, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.alpha = 0.65f
                        if (!moved) {
                            showPanel()
                        } else {
                            // Snap to the nearest horizontal edge.
                            val screenWidth = resources.displayMetrics.widthPixels
                            params.x = if (params.x + v.width / 2 < screenWidth / 2) 0
                            else screenWidth - v.width
                            windowManager.updateViewLayout(v, params)
                        }
                        return true
                    }
                }
                return false
            }
        })

        view.alpha = 0.65f
        windowManager.addView(view, params)
        ballView = view
    }

    // ---------------- Expanded input panel ----------------

    private fun showPanel() {
        removeView(ballView); ballView = null
        val view = LayoutInflater.from(this).inflate(R.layout.floating_input, null)
        val params = overlayParams(focusable = true).apply {
            x = (resources.displayMetrics.widthPixels - dpToPx(300)) / 2
        }

        val input = view.findViewById<EditText>(R.id.floating_input_text)
        val radioWord = view.findViewById<RadioButton>(R.id.floating_radio_word)
        val radioNote = view.findViewById<RadioButton>(R.id.floating_radio_note)

        view.findViewById<View>(R.id.floating_save).setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            val entry = Entry(
                type = if (radioNote.isChecked) Entry.TYPE_NOTE else Entry.TYPE_WORD,
                text = text
            )
            scope.launch {
                db.entryDao().insert(entry)
                ReviewWidgetProvider.refreshAll(this@FloatingService)
                launch(Dispatchers.Main) {
                    Toast.makeText(this@FloatingService, R.string.saved, Toast.LENGTH_SHORT).show()
                    showBall()
                }
            }
        }

        view.findViewById<View>(R.id.floating_collapse).setOnClickListener { showBall() }

        windowManager.addView(view, params)
        panelView = view
        input.requestFocus()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
