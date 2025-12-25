package com.example.sotrosoundpad

import android.content.ClipData
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var container: GridLayout
    private var mediaPlayer: MediaPlayer? = null

    private val pickSounds = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris != null && uris.isNotEmpty()) {
            for (uri in uris) {
                copyUriToInternal(uri)
            }
            refreshButtons()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        container = findViewById(R.id.sound_buttons_container)

        findViewById<Button>(R.id.btn_import).setOnClickListener {
            pickSounds.launch(arrayOf("audio/*"))
        }

        setupTrash()
        refreshButtons()
    }

    private fun setupTrash() {
        val trashView = findViewById<TextView>(R.id.tv_trash)
        trashView.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    v.setBackgroundResource(R.drawable.trash_background_active)
                    (v as TextView).text = "DROP TO DELETE"
                    v.setTextColor(Color.WHITE)
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    v.setBackgroundResource(R.drawable.trash_background_normal)
                    (v as TextView).text = "🗑️ Drag here to delete"
                    v.setTextColor(Color.parseColor("#757575"))
                    true
                }
                DragEvent.ACTION_DROP -> {
                    val file = event.localState as? File
                    if (file != null) {
                        showDeleteDialog(file)
                    }
                    v.setBackgroundResource(R.drawable.trash_background_normal)
                    (v as TextView).text = "🗑️ Drag here to delete"
                    v.setTextColor(Color.parseColor("#757575"))
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    v.setBackgroundResource(R.drawable.trash_background_normal)
                    (v as TextView).text = "🗑️ Drag here to delete"
                    v.setTextColor(Color.parseColor("#757575"))
                    true
                }
                else -> false
            }
        }
    }

    private fun copyUriToInternal(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val originalName = queryName(uri)
            // Sanitize filename to prevent CSV/persistence issues
            val sanitizedName = originalName.replace(",", "").replace("|", "")
            val destFile = File(getExternalFilesDir("sounds"), sanitizedName)
            
            inputStream.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun refreshButtons() {
        container.removeAllViews()
        val dir = getExternalFilesDir("sounds")
        if (dir == null) return
        val files = dir.listFiles()?.toMutableList() ?: return

        // Sort by saved preference
        val savedOrder = loadOrder()
        if (savedOrder.isNotEmpty()) {
            files.sortWith(Comparator { f1, f2 ->
                val idx1 = savedOrder.indexOf(f1.name)
                val idx2 = savedOrder.indexOf(f2.name)
                if (idx1 != -1 && idx2 != -1) {
                    idx1.compareTo(idx2)
                } else if (idx1 != -1) {
                    -1 // f1 is in the list, f2 is not (f1 comes first)
                } else if (idx2 != -1) {
                    1 // f2 is in the list, f1 is not (f2 comes first)
                } else {
                    f1.name.compareTo(f2.name) // Neither is in the list, alphabetical order
                }
            })
        } else {
            files.sortBy { it.name }
        }

        // Save current order (including new files)
        saveOrder(files.map { it.name })

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val density = displayMetrics.density

        // Calculate width for 3 columns respecting margins and padding
        // Parent padding: 16dp * 2 = 32dp
        // Button margins: 8dp * 2 * 3 = 48dp
        // Total non-button space occupied: 80dp
        // Give an extra 16dp to ensure they fit loosely
        val totalSpacing = ((32 + 48 + 16) * density).toInt()
        val buttonWidth = (screenWidth - totalSpacing) / 3
        val marginPx = (8 * density).toInt()

        // Ensure row count is at least 1 to avoid layout issues
        container.rowCount = if (files.isNotEmpty()) (files.size + 2) / 3 else 1

        files.forEachIndexed { index, file ->
            val btn = androidx.appcompat.widget.AppCompatButton(this)
            
            val row = index / 3
            val col = index % 3
            val params = GridLayout.LayoutParams(
                GridLayout.spec(row),
                GridLayout.spec(col)
            )
            
            params.width = buttonWidth
            params.height = buttonWidth // Square for uniform size
            params.setMargins(marginPx, marginPx, marginPx, marginPx)
            btn.layoutParams = params

            btn.text = file.nameWithoutExtension
            btn.setTextColor(Color.WHITE)
            btn.setBackgroundResource(R.drawable.button_background)
            btn.setPadding(16, 16, 16, 16)
            btn.gravity = android.view.Gravity.CENTER
            btn.maxLines = 3
            btn.ellipsize = android.text.TextUtils.TruncateAt.END

            // Controlled text auto-sizing
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                btn,
                10, 14, 1, TypedValue.COMPLEX_UNIT_SP
            )

            btn.setOnClickListener { playFile(file) }

            // Configure Drag and Drop (Start)
            btn.setOnLongClickListener { view ->
                val data = ClipData.newPlainText("file_path", file.absolutePath)
                val shadowBuilder = View.DragShadowBuilder(view)
                view.startDragAndDrop(data, shadowBuilder, file, 0)
                true
            }

            // Configure Drag and Drop (Target - Swap)
            btn.setOnDragListener { v, event ->
                when (event.action) {
                    DragEvent.ACTION_DROP -> {
                        val sourceFile = event.localState as? File
                        if (sourceFile != null && sourceFile.absolutePath != file.absolutePath) {
                            swapFiles(sourceFile, file)
                        }
                        true
                    }
                    else -> true
                }
            }

            container.addView(btn)
        }
    }

    private fun swapFiles(source: File, target: File) {
        val currentOrder = loadOrder().toMutableList()
        val idx1 = currentOrder.indexOf(source.name)
        val idx2 = currentOrder.indexOf(target.name)

        if (idx1 != -1 && idx2 != -1) {
            Collections.swap(currentOrder, idx1, idx2)
            saveOrder(currentOrder)
            refreshButtons()
        }
    }

    private fun saveOrder(order: List<String>) {
        val prefs = getSharedPreferences("soundpad_prefs", MODE_PRIVATE)
        prefs.edit().putString("order", order.joinToString("|")).apply()
    }

    private fun loadOrder(): List<String> {
        val prefs = getSharedPreferences("soundpad_prefs", MODE_PRIVATE)
        val orderString = prefs.getString("order", "") ?: ""
        return if (orderString.isNotEmpty()) orderString.split("|") else emptyList()
    }

    private fun showDeleteDialog(file: File) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete sound")
            .setMessage("Are you sure you want to delete '${file.name}'?")
            .setPositiveButton("Yes") { _, _ ->
                if (file.delete()) {
                    android.widget.Toast.makeText(this, "Sound deleted", android.widget.Toast.LENGTH_SHORT).show()
                    refreshButtons()
                } else {
                    android.widget.Toast.makeText(this, "Error deleting", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun playFile(file: File) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer()
        try {
            mediaPlayer?.setDataSource(file.absolutePath)
            mediaPlayer?.prepare()
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener { mp -> mp.release() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun queryName(uri: Uri): String {
        var name = ""
        val cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                name = it.getString(0)
            }
        }
        if (name.isBlank()) {
            name = UUID.randomUUID().toString() + ".dat"
        }
        return name
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}
