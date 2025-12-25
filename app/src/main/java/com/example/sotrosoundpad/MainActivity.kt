package com.example.sotrosoundpad

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
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

        refreshButtons()
    }

    private fun refreshButtons() {
        container.removeAllViews()
        val dir = getExternalFilesDir("sounds")
        if (dir == null) return
        val files = dir.listFiles() ?: return
        files.sortBy { it.name }
        for (file in files) {
            val btn = Button(this)
            btn.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            btn.text = file.name
            btn.setOnClickListener { playFile(file) }
            container.addView(btn)
        }
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

    private fun copyUriToInternal(uri: Uri) {
        try {
            val name = queryName(uri)
            val target = File(getExternalFilesDir("sounds"), name)
            contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
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
