package com.agenticmessenger.mcp.android

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Where the user decides which apps may use Amp's MCP tools. Shows the package and the start of the
 * signing certificate so an impostor with the same package name but another key is recognisable.
 *
 * A debuggable build also accepts `--ez approve_all_pending true` so a device test can approve
 * without tapping; a release build ignores it.
 */
class ApprovalActivity : Activity() {
    private lateinit var approvals: PrefsCallerApprovals
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        approvals = PrefsCallerApprovals(this)
        handleAutoApproveIntent(intent)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(list) })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutoApproveIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        handleAutoApproveIntent(intent)
        render()
    }

    private fun handleAutoApproveIntent(targetIntent: Intent?) {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable && targetIntent?.getBooleanExtra("approve_all_pending", false) == true) {
            approvals.pending().forEach(approvals::approve)
        }
    }

    private fun render() {
        list.removeAllViews()
        list.addView(text("Apps asking to use Amp", bold = true))
        val pending = approvals.pending()
        if (pending.isEmpty()) list.addView(text("No requests."))
        pending.forEach { c ->
            list.addView(text("${c.packageName}\nkey ${c.signingCertSha256.take(16)}..."))
            list.addView(row(
                button("Allow") { approvals.approve(c); render() },
                button("Deny") { approvals.deny(c); render() },
            ))
        }
        list.addView(text("Decided", bold = true))
        approvals.decided().forEach { (c, d) ->
            list.addView(text("${c.packageName}  ${d.name.lowercase()}  key ${c.signingCertSha256.take(8)}..."))
            list.addView(button("Forget") { approvals.revoke(c); render() })
        }
    }

    private fun text(s: String, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = if (bold) 18f else 14f
        setPadding(0, 16, 0, 4)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach(::addView)
    }
}
