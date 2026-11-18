package com.offlinechecker

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.SimpleAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    private var days = 7
    private var showSystem = false
    private var sortByName = false
    private var allRows: List<AppUsage> = emptyList()

    private lateinit var spinner: Spinner
    private lateinit var sort: ImageButton
    private lateinit var grant: Button
    private lateinit var hint: TextView
    private lateinit var list: ListView

    private data class AppUsage(
        val label: String,
        val icon: Drawable,
        val rx: Long,
        val tx: Long,
        val hasInternet: Boolean
    ) {
        val total get() = rx + tx
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        actionBar?.subtitle = "v${BuildConfig.VERSION_NAME}"
        spinner = findViewById(R.id.spinner)
        sort = findViewById(R.id.sort)
        grant = findViewById(R.id.grant)
        hint = findViewById(R.id.hint)
        list = findViewById(R.id.list)

        sort.setOnClickListener { showSortMenu() }
        updateSort()

        spinner.adapter = ArrayAdapter.createFromResource(
            this, R.array.ranges, android.R.layout.simple_spinner_item
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinner.setSelection(1)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val picked = if (position == 0) 1 else 7
                if (picked == days) return
                days = picked
                if (hasUsageAccess()) load()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        grant.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    override fun onResume() {
        super.onResume()
        val access = hasUsageAccess()
        spinner.isEnabled = access
        grant.visibility = if (access) View.GONE else View.VISIBLE
        hint.visibility = grant.visibility
        if (access) load()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != R.id.system_apps) return super.onOptionsItemSelected(item)
        showSystem = !showSystem
        item.title = getString(if (showSystem) R.string.hide_system_apps else R.string.show_system_apps)
        if (hasUsageAccess()) load()
        return true
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(AppOpsManager::class.java)
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun load() {
        val end = System.currentTimeMillis()
        val start = end - days * 86_400_000L
        val system = showSystem
        io.execute {
            val rows = try {
                collect(start, end, system)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (rows == null) {
                    Toast.makeText(this, R.string.usage_access_missing, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                allRows = rows
                render()
            }
        }
    }

    private fun updateSort() {
        sort.contentDescription = getString(
            if (sortByName) R.string.sort_name_hint else R.string.sort_usage_hint
        )
    }

    private fun showSortMenu() {
        val popup = PopupMenu(this, sort)
        popup.menuInflater.inflate(R.menu.sort, popup.menu)
        // Exclusive menu groups ignore false: setting any item checks it.
        // Check only the active item, otherwise the last call wins.
        popup.menu.findItem(
            if (sortByName) R.id.sort_name else R.id.sort_usage
        ).isChecked = true
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.sort_usage -> sortByName = false
                R.id.sort_name -> sortByName = true
                else -> return@setOnMenuItemClickListener false
            }
            updateSort()
            render()
            true
        }
        popup.show()
    }

    private fun render() {
        val data = sortRows().map {
            mapOf(
                "icon" to it.icon,
                "app" to it.label,
                "usage" to if (it.hasInternet) {
                    "↓ ${readable(it.rx)}   ↑ ${readable(it.tx)}"
                } else {
                    getString(R.string.no_internet_permission)
                }
            )
        }
        list.adapter = SimpleAdapter(
            this,
            data,
            R.layout.row,
            arrayOf("icon", "app", "usage"),
            intArrayOf(R.id.icon, R.id.app, R.id.usage)
        ).apply {
            viewBinder = SimpleAdapter.ViewBinder { view, value, _ ->
                if (view is ImageView && value is Drawable) {
                    view.setImageDrawable(value)
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun sortRows(): List<AppUsage> {
        if (sortByName) return allRows.sortedBy { it.label.lowercase() }
        val (online, offline) = allRows.partition { it.hasInternet }
        return online.sortedWith(
            compareByDescending<AppUsage> { it.total }.thenBy { it.label }
        ) + offline.sortedBy { it.label }
    }

    private fun collect(start: Long, end: Long, system: Boolean): List<AppUsage> {
        val manager = getSystemService(NetworkStatsManager::class.java)
        val traffic = HashMap<Int, LongArray>()
        val bucket = NetworkStats.Bucket()
        var read = false
        for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            try {
                val stats = manager.querySummary(type, null, start, end)
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val bytes = traffic.getOrPut(bucket.uid) { LongArray(2) }
                    bytes[0] += bucket.rxBytes
                    bytes[1] += bucket.txBytes
                }
                read = true
            } catch (e: Exception) {
                // A network type may be unavailable (e.g. no SIM); keep the other.
            }
        }
        if (!read) throw SecurityException("No network stats available")
        val pm = packageManager
        val apps = HashMap<Int, AppUsage>()
        for (pkg in pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
            val info = pkg.applicationInfo ?: continue
            if (!system && (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
            val hasInternet =
                pkg.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
            apps[info.uid] = AppUsage(
                info.loadLabel(pm).toString(), info.loadIcon(pm), 0, 0, hasInternet
            )
        }
        return apps.map { (uid, app) ->
            val bytes = traffic[uid] ?: LongArray(2)
            app.copy(rx = bytes[0], tx = bytes[1])
        }
    }

    private fun readable(bytes: Long) = Formatter.formatShortFileSize(this, bytes)
}
