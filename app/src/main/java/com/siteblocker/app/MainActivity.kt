package com.siteblocker.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val bgColor = Color.parseColor("#0F172A")
    private val cardColor = Color.parseColor("#1E293B")
    private val fieldColor = Color.parseColor("#0B1220")
    private val textMain = Color.parseColor("#F8FAFC")
    private val textSub = Color.parseColor("#94A3B8")
    private val accent = Color.parseColor("#6366F1")
    private val green = Color.parseColor("#22C55E")
    private val red = Color.parseColor("#F87171")

    private lateinit var input: EditText
    private lateinit var listBox: LinearLayout
    private lateinit var toggle: Switch
    private lateinit var statusTitle: TextView
    private lateinit var statusSub: TextView
    private lateinit var sectionLabel: TextView
    private var updating = false
    private val reqVpn = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(dp(20), dp(24), dp(20), dp(16))
        }

        root.addView(text("Site Blocker", 30f, textMain, bold = true))
        root.addView(text("Block distracting websites in every browser", 14f, textSub), lp(top = 2))

        // Status card with the main switch
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(cardColor, 20f)
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        val statusText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusTitle = text("Protection is off", 18f, textMain, bold = true)
        statusSub = text("", 13f, textSub)
        statusText.addView(statusTitle)
        statusText.addView(statusSub, lp(top = 2))
        statusCard.addView(
            statusText,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        toggle = Switch(this)
        toggle.setOnCheckedChangeListener { _, checked ->
            if (!updating) {
                if (checked) startBlocking() else stopBlocking()
            }
        }
        statusCard.addView(toggle)
        root.addView(statusCard, lp(top = 20))

        // Add-site card
        val addCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 20f)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        input = EditText(this).apply {
            hint = "Paste a link, e.g. https://example.com"
            setHintTextColor(textSub)
            setTextColor(textMain)
            textSize = 15f
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            background = rounded(fieldColor, 14f)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        addCard.addView(input)
        addCard.addView(Button(this).apply {
            text = "Block this site"
            transformationMethod = null
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            stateListAnimator = null
            background = rounded(accent, 14f)
            setOnClickListener { addCurrent() }
        }, lp(top = 12, h = dp(52)))
        root.addView(addCard, lp(top = 16))

        sectionLabel = text("Blocked sites", 13f, textSub, bold = true)
        root.addView(sectionLabel, lp(top = 22))

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(
            ScrollView(this).apply { addView(listBox) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                .apply { topMargin = dp(8) }
        )

        root.addView(
            text(
                "Tip: in Chrome turn off Settings > Privacy and security > Use secure DNS, " +
                    "otherwise it can bypass the blocker.",
                11f, textSub
            ), lp(top = 8)
        )

        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        setToggle(BlockList.isEnabled(this))
        updateStatus()
    }

    private fun addCurrent() {
        val domain = BlockList.extractDomain(input.text.toString())
        if (domain == null) {
            Toast.makeText(this, "That doesn't look like a valid link", Toast.LENGTH_SHORT).show()
            return
        }
        BlockList.add(this, domain)
        input.text.clear()
        render()
        Toast.makeText(this, "Blocked $domain", Toast.LENGTH_SHORT).show()
    }

    private fun render() {
        listBox.removeAllViews()
        val domains = BlockList.getAll(this).sorted()
        sectionLabel.text = "BLOCKED SITES  (${domains.size})"
        updateStatus()
        if (domains.isEmpty()) {
            listBox.addView(text("Nothing blocked yet. Paste a link above.", 14f, textSub), lp(top = 8))
            return
        }
        for (d in domains) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(cardColor, 16f)
                setPadding(dp(16), dp(10), dp(8), dp(10))
            }
            row.addView(
                text(d, 16f, textMain),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            row.addView(Button(this).apply {
                text = "Remove"
                transformationMethod = null
                textSize = 13f
                setTextColor(red)
                stateListAnimator = null
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { askPasswordToRemove(d) }
            })
            listBox.addView(row, lp(top = 8))
        }
    }

    private fun askPasswordToRemove(domain: String) {
        val pw = EditText(this).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        AlertDialog.Builder(this)
            .setTitle("Remove $domain?")
            .setMessage("Enter the password to unblock this site.")
            .setView(pw)
            .setPositiveButton("Remove") { _, _ ->
                if (BlockList.checkPassword(pw.text.toString())) {
                    BlockList.remove(this, domain)
                    render()
                    Toast.makeText(this, "Removed $domain", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Wrong password", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateStatus() {
        val on = BlockList.isEnabled(this)
        val n = BlockList.getAll(this).size
        statusTitle.text = if (on) "Protection is on" else "Protection is off"
        statusTitle.setTextColor(if (on) green else textMain)
        statusSub.text = if (n == 1) "1 site blocked" else "$n sites blocked"
    }

    private fun setToggle(on: Boolean) {
        updating = true
        toggle.isChecked = on
        updating = false
    }

    private fun startBlocking() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, reqVpn)
        } else {
            launchService()
        }
    }

    private fun launchService() {
        BlockList.setEnabled(this, true)
        startService(
            Intent(this, BlockerVpnService::class.java).setAction(BlockerVpnService.ACTION_START)
        )
        updateStatus()
    }

    private fun stopBlocking() {
        BlockList.setEnabled(this, false)
        startService(
            Intent(this, BlockerVpnService::class.java).setAction(BlockerVpnService.ACTION_STOP)
        )
        updateStatus()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqVpn) {
            if (resultCode == RESULT_OK) launchService() else setToggle(false)
        }
    }

    // ---------- small UI helpers ----------

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun lp(top: Int = 0, h: Int = LinearLayout.LayoutParams.WRAP_CONTENT) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h)
            .apply { topMargin = dp(top) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
