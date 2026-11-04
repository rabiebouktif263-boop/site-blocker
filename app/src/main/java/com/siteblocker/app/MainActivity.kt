package com.siteblocker.app

import android.app.Activity
import android.content.Intent
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

    private lateinit var input: EditText
    private lateinit var listBox: LinearLayout
    private lateinit var toggle: Switch
    private var updating = false
    private val reqVpn = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(16)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Site Blocker"
            textSize = 26f
        })

        toggle = Switch(this).apply {
            text = "Blocking enabled"
            textSize = 18f
            setPadding(0, pad, 0, pad)
        }
        toggle.setOnCheckedChangeListener { _, checked ->
            if (!updating) {
                if (checked) startBlocking() else stopBlocking()
            }
        }
        root.addView(toggle)

        input = EditText(this).apply {
            hint = "Paste a link, e.g. https://example.com"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(input)

        root.addView(Button(this).apply {
            text = "Block this site"
            setOnClickListener { addCurrent() }
        })

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(
            ScrollView(this).apply { addView(listBox) },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        root.addView(TextView(this).apply {
            text = "Tip: in Chrome, turn off Settings > Privacy > Use secure DNS, " +
                "otherwise it can bypass the blocker."
            textSize = 12f
        })

        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        setToggle(BlockList.isEnabled(this))
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
        if (domains.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "No blocked sites yet."
                setPadding(0, dp(12), 0, 0)
            })
            return
        }
        for (d in domains) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(
                TextView(this).apply {
                    text = d
                    textSize = 17f
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            row.addView(Button(this).apply {
                text = "Remove"
                setOnClickListener {
                    BlockList.remove(this@MainActivity, d)
                    render()
                }
            })
            listBox.addView(row)
        }
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
    }

    private fun stopBlocking() {
        BlockList.setEnabled(this, false)
        startService(
            Intent(this, BlockerVpnService::class.java).setAction(BlockerVpnService.ACTION_STOP)
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqVpn) {
            if (resultCode == RESULT_OK) launchService() else setToggle(false)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
