package com.superscale

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : Activity() {

    private lateinit var preview: UpscaleView
    private lateinit var btnMode: Button
    private lateinit var btnUpscale: Button
    private lateinit var seekScale: SeekBar
    private lateinit var seekSharp: SeekBar
    private lateinit var tvStats: TextView
    private lateinit var tvScaleLabel: TextView
    private lateinit var tvSharpLabel: TextView
    private lateinit var imgResult: ImageView
    private lateinit var tvBusy: TextView

    private val pickImage = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preview = findViewById(R.id.preview)
        btnMode = findViewById(R.id.btnMode)
        btnUpscale = findViewById(R.id.btnUpscale)
        seekScale = findViewById(R.id.seekScale)
        seekSharp = findViewById(R.id.seekSharp)
        tvStats = findViewById(R.id.tvStats)
        tvScaleLabel = findViewById(R.id.tvScaleLabel)
        tvSharpLabel = findViewById(R.id.tvSharpLabel)
        imgResult = findViewById(R.id.imgResult)
        tvBusy = findViewById(R.id.tvBusy)

        preview.listener = object : UpscaleView.Listener {
            override fun onStats(fps: Float, internalW: Int, internalH: Int) {
                runOnUiThread {
                    tvStats.text = String.format(
                        "%.0f fps  |  internal %dx%d  |  %s",
                        fps, internalW, internalH,
                        if (preview.mode == UpscaleView.Mode.SMART) "SMART" else "BILINEAR"
                    )
                }
            }

            override fun onTapToggleMode() {
                toggleMode()
            }
        }

        btnMode.setOnClickListener { toggleMode() }

        // render scale: 25% .. 100%
        seekScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = 0.25f + progress / 100f
                preview.renderScale = scale
                tvScaleLabel.text = String.format(
                    "%s: %d%%", getString(R.string.label_resolution), (scale * 100).toInt()
                )
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekSharp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val s = progress / 100f
                preview.sharpness = s
                tvSharpLabel.text = String.format(
                    "%s: %.2f", getString(R.string.label_sharpness), s
                )
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnUpscale.setOnClickListener { launchPicker() }
        imgResult.setOnClickListener { imgResult.visibility = View.GONE }

        // initial values
        preview.renderScale = 0.50f
        seekScale.progress = 25
        preview.sharpness = 0.35f
        seekSharp.progress = 35
        tvScaleLabel.text = "রেন্ডার রেজোলিউশন: 50%"
        tvSharpLabel.text = "শার্পনেস: 0.35"
    }

    private fun toggleMode() {
        preview.mode =
            if (preview.mode == UpscaleView.Mode.SMART) UpscaleView.Mode.BILINEAR
            else UpscaleView.Mode.SMART
        btnMode.setText(
            if (preview.mode == UpscaleView.Mode.SMART) R.string.mode_smart
            else R.string.mode_bilinear
        )
    }

    private fun launchPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            startActivityForResult(intent, pickImage)
        } catch (e: Exception) {
            Toast.makeText(this, "কোনো গ্যালারি অ্যাপ পাওয়া যায়নি", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickImage || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return

        val src = decodeBounded(uri, 2048)
        if (src == null) {
            Toast.makeText(this, "ছবি পড়া যায়নি", Toast.LENGTH_LONG).show()
            return
        }

        tvBusy.visibility = View.VISIBLE
        val sharp = preview.sharpness

        Thread {
            try {
                val out = ImageUpscaler.upscale(src, 2.0f, sharp)
                val savedUri = saveToGallery(out)
                runOnUiThread {
                    tvBusy.visibility = View.GONE
                    imgResult.setImageBitmap(out)
                    imgResult.visibility = View.VISIBLE
                    AlertDialog.Builder(this)
                        .setTitle("আপস্কেল সম্পন্ন")
                        .setMessage(
                            "ইনপুট: ${src.width}x${src.height}\n" +
                            "আউটপুট: ${out.width}x${out.height}\n\n" +
                            "সেভ হয়েছে Pictures/SuperScale ফোল্ডারে।\n" +
                            "ছবিতে ট্যাপ করে বন্ধ করুন।"
                        )
                        .setPositiveButton("ঠিক আছে", null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvBusy.visibility = View.GONE
                    Toast.makeText(
                        this, "ব্যর্থ: ${e.message}", Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun decodeBounded(uri: Uri, maxSide: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            var w = bounds.outWidth
            var h = bounds.outHeight
            while (maxOf(w, h) / (sample * 2) >= maxSide) sample *= 2

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bmp = openStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null

            // hard cap so 2x output stays inside GL max texture size
            val side = maxOf(bmp.width, bmp.height)
            if (side > maxSide) {
                val k = maxSide.toFloat() / side
                bmp = Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * k).toInt().coerceAtLeast(1),
                    (bmp.height * k).toInt().coerceAtLeast(1),
                    true
                )
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }

    private fun openStream(uri: Uri): InputStream? = contentResolver.openInputStream(uri)

    private fun saveToGallery(bmp: Bitmap): Uri? {
        val name = "superscale_${System.currentTimeMillis()}.png"
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SuperScale")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return null
            contentResolver.openOutputStream(uri)?.use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            uri
        } else {
            // pre-Q: app-specific external dir, no runtime permission needed
            val dir = File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SuperScale"
            )
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, name)
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Uri.fromFile(f)
        }
    }
}
