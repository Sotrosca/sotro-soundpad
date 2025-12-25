package com.example.sotrosoundpad

import android.content.ClipData
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.DragEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var container: GridLayout
    private var mediaPlayer: MediaPlayer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentRecordingFile: File? = null
    private var isEditMode = false
    private var isDeleteMode = false
    private var editMenuItem: MenuItem? = null
    private var deleteMenuItem: MenuItem? = null
    
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var visualizerRunnable: Runnable? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showRecordDialog()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

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
        supportActionBar?.title = "Sotro Soundpad"

        container = findViewById(R.id.sound_buttons_container)

        findViewById<Button>(R.id.btn_import).setOnClickListener {
            pickSounds.launch(arrayOf("audio/*"))
        }

        findViewById<Button>(R.id.btn_record).setOnClickListener {
            checkPermissionAndRecord()
        }

        refreshButtons()
    }

    private fun checkPermissionAndRecord() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                showRecordDialog()
            }
            else -> {
                requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun showRecordDialog() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Record Audio")

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)
        layout.gravity = android.view.Gravity.CENTER_HORIZONTAL

        val input = EditText(this)
        input.hint = "Recording Name"
        // input.setText("Recording_${System.currentTimeMillis() / 1000}") // Removed default name
        layout.addView(input)

        // Chronometer for timer
        val timer = android.widget.Chronometer(this)
        timer.textSize = 30f
        timer.gravity = android.view.Gravity.CENTER
        timer.visibility = View.GONE
        layout.addView(timer)

        // Visualizer
        val visualizer = View(this)
        val size = (60 * resources.displayMetrics.density).toInt()
        val params = LinearLayout.LayoutParams(size, size)
        params.gravity = android.view.Gravity.CENTER
        params.topMargin = 30
        visualizer.layoutParams = params
        visualizer.setBackgroundResource(R.drawable.recording_visualizer)
        visualizer.visibility = View.INVISIBLE // Hidden initially
        layout.addView(visualizer)

        val statusText = TextView(this)
        statusText.text = "Ready to record"
        statusText.setPadding(0, 20, 0, 20)
        statusText.gravity = android.view.Gravity.CENTER
        layout.addView(statusText)

        val recordBtn = Button(this)
        recordBtn.text = "Start Recording"
        recordBtn.setBackgroundColor(Color.RED)
        recordBtn.setTextColor(Color.WHITE)
        layout.addView(recordBtn)

        builder.setView(layout)
        builder.setNegativeButton("Cancel") { dialog, _ ->
            if (isRecording) {
                stopRecording()
                currentRecordingFile?.delete()
            }
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()

        recordBtn.setOnClickListener {
            if (!isRecording) {
                val name = input.text.toString().trim().uppercase()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // Sanitize name
                val sanitized = name.replace(",", "").replace("|", "")
                val file = File(getExternalFilesDir("sounds"), "$sanitized.mp3")
                
                if (startRecording(file)) {
                    isRecording = true
                    currentRecordingFile = file
                    
                    // UI Updates for Recording
                    recordBtn.text = "Stop Recording"
                    statusText.text = "🔴 RECORDING"
                    statusText.setTextColor(Color.RED)
                    statusText.setTypeface(null, android.graphics.Typeface.BOLD)
                    
                    input.isEnabled = false
                    
                    // Start Timer
                    timer.base = android.os.SystemClock.elapsedRealtime()
                    timer.visibility = View.VISIBLE
                    timer.start()
                    
                    // Start Visualizer Animation
                    visualizer.visibility = View.VISIBLE
                    visualizerRunnable = object : Runnable {
                        override fun run() {
                            if (isRecording && mediaRecorder != null) {
                                val maxAmplitude = mediaRecorder?.maxAmplitude ?: 0
                                // Normalize 0-32767 to 1.0-2.0 scale
                                val scale = 1.0f + (maxAmplitude / 20000f).coerceAtMost(1.0f)
                                visualizer.animate().scaleX(scale).scaleY(scale).setDuration(50).start()
                                handler.postDelayed(this, 50)
                            }
                        }
                    }
                    handler.post(visualizerRunnable!!)
                }
            } else {
                stopRecording()
                timer.stop()
                
                // Stop Visualizer
                if (visualizerRunnable != null) handler.removeCallbacks(visualizerRunnable!!)
                visualizer.visibility = View.INVISIBLE
                visualizer.scaleX = 1.0f
                visualizer.scaleY = 1.0f
                
                isRecording = false
                dialog.dismiss()
                refreshButtons()
                Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show()
            }
        }
        
        dialog.setOnDismissListener {
            if (isRecording) {
                stopRecording()
                currentRecordingFile?.delete()
                isRecording = false
            }
            // Ensure visualizer stops
            if (visualizerRunnable != null) handler.removeCallbacks(visualizerRunnable!!)
        }
    }

    private fun startRecording(file: File): Boolean {
        return try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error starting recorder", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        editMenuItem = menu?.findItem(R.id.action_edit)
        deleteMenuItem = menu?.findItem(R.id.action_delete)
        updateActionBarIcons()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit -> {
                isEditMode = !isEditMode
                if (isEditMode) isDeleteMode = false // Mutually exclusive
                updateActionBarIcons()
                refreshButtons()
                true
            }
            R.id.action_delete -> {
                isDeleteMode = !isDeleteMode
                if (isDeleteMode) isEditMode = false // Mutually exclusive
                updateActionBarIcons()
                refreshButtons()
                true
            }
            R.id.action_reset -> {
                showResetConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateActionBarIcons() {
        editMenuItem?.setIcon(if (isEditMode) R.drawable.ic_done else R.drawable.ic_edit)
        deleteMenuItem?.setIcon(if (isDeleteMode) R.drawable.ic_done else R.drawable.ic_delete)
        
        // Optional: Hide/Disable the other icon when one mode is active to avoid confusion
        editMenuItem?.isVisible = !isDeleteMode
        deleteMenuItem?.isVisible = !isEditMode
    }

    private fun showResetConfirmation() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Reset App")
            .setMessage("This will delete ALL sounds and data. Are you sure?")
            .setPositiveButton("Delete All") { _, _ ->
                resetApp()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetApp() {
        // Clear SharedPreferences
        val prefs = getSharedPreferences("soundpad_prefs", MODE_PRIVATE)
        prefs.edit().clear().apply()

        // Clear Internal Storage Files
        val dir = getExternalFilesDir("sounds")
        dir?.listFiles()?.forEach { it.delete() }

        // Refresh UI
        refreshButtons()
        android.widget.Toast.makeText(this, "App reset successfully", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun updateEditIcon() {
        editMenuItem?.setIcon(if (isEditMode) R.drawable.ic_done else R.drawable.ic_edit)
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
        val totalSpacing = ((32 + 48) * density).toInt()
        val buttonWidth = (screenWidth - totalSpacing) / 3
        val marginPx = (8 * density).toInt()

        // Ensure row count is at least 1 to avoid layout issues
        container.rowCount = if (files.isNotEmpty()) (files.size + 2) / 3 else 1

        files.forEachIndexed { index, file ->
            val btn = androidx.appcompat.widget.AppCompatButton(this)
            
            val row = index / 3
            val col = index % 3
            val params = GridLayout.LayoutParams(
                GridLayout.spec(row, GridLayout.FILL),
                GridLayout.spec(col, GridLayout.FILL)
            )
            
            params.width = buttonWidth
            params.height = buttonWidth // Square for uniform size
            params.setMargins(marginPx, marginPx, marginPx, marginPx)
            btn.layoutParams = params

            btn.text = file.nameWithoutExtension
            btn.setTextColor(Color.WHITE)
            
            // Define Drag and Drop listeners
            val onLongClickDrag = View.OnLongClickListener { view ->
                val data = ClipData.newPlainText("file_path", file.absolutePath)
                val shadowBuilder = View.DragShadowBuilder(view)
                view.startDragAndDrop(data, shadowBuilder, file, 0)
                true
            }

            val onDrag = View.OnDragListener { v, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        v.alpha = 0.5f
                        true
                    }
                    DragEvent.ACTION_DRAG_EXITED -> {
                        v.alpha = 1.0f
                        true
                    }
                    DragEvent.ACTION_DROP -> {
                        v.alpha = 1.0f
                        val sourceFile = event.localState as? File
                        if (sourceFile != null && sourceFile.absolutePath != file.absolutePath) {
                            swapFiles(sourceFile, file)
                        }
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        v.alpha = 1.0f
                        true
                    }
                    else -> true
                }
            }

            if (isEditMode) {
                btn.setBackgroundResource(R.drawable.button_background_rename) // Orange for rename/reorder
                btn.setOnClickListener { showRenameDialog(file) }
                btn.setOnLongClickListener(onLongClickDrag)
                btn.setOnDragListener(onDrag)
            } else if (isDeleteMode) {
                btn.setBackgroundResource(R.drawable.button_background_edit) // Red for delete mode
                btn.setOnClickListener { showDeleteDialog(file) }
                btn.setOnLongClickListener(null)
                btn.setOnDragListener(null)
            } else {
                btn.setBackgroundResource(R.drawable.button_background) // Normal purple
                btn.setOnClickListener { playFile(file) }
                btn.setOnLongClickListener(onLongClickDrag)
                btn.setOnDragListener(onDrag)
            }

            btn.setPadding(16, 16, 16, 16)
            btn.gravity = android.view.Gravity.CENTER
            btn.maxLines = 3
            btn.ellipsize = android.text.TextUtils.TruncateAt.END

            // Controlled text auto-sizing
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                btn,
                10, 14, 1, TypedValue.COMPLEX_UNIT_SP
            )

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

    private fun showRenameDialog(file: File) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Rename Sound")

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val input = EditText(this)
        input.setText(file.nameWithoutExtension)
        layout.addView(input)

        builder.setView(layout)
        builder.setPositiveButton("Rename") { _, _ ->
            val newName = input.text.toString().trim().uppercase()
            if (newName.isNotEmpty() && newName != file.nameWithoutExtension) {
                renameSound(file, newName)
            } else if (newName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun renameSound(file: File, newName: String) {
        val sanitized = newName.replace(",", "").replace("|", "")
        val newFile = File(file.parent, "$sanitized.mp3")
        
        if (newFile.exists()) {
            Toast.makeText(this, "Name already exists", Toast.LENGTH_SHORT).show()
            return
        }

        if (file.renameTo(newFile)) {
            // Update order list
            val currentOrder = loadOrder().toMutableList()
            val index = currentOrder.indexOf(file.name)
            if (index != -1) {
                currentOrder[index] = newFile.name
                saveOrder(currentOrder)
            }
            refreshButtons()
            Toast.makeText(this, "Renamed successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Error renaming", Toast.LENGTH_SHORT).show()
        }
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
