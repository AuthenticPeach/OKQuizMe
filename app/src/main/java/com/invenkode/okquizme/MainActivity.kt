package com.invenkode.okquizme

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import java.util.Locale
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import androidx.appcompat.app.AlertDialog



class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: LinearLayout
    private lateinit var sharedPrefs: SharedPreferences

    private lateinit var tvKnownCount: TextView
    private lateinit var tvUnknownCount: TextView
    private lateinit var tvCardTitle: TextView
    private lateinit var btnDarkMode: Button
    private lateinit var btnUpload: Button
    private lateinit var btnFlip: Button
    private lateinit var btnNext: Button
    private lateinit var btnKnown: Button
    private lateinit var btnStudyAgainShuffle: Button
    private lateinit var btnStudyRemaining: Button
    private lateinit var btnReverseQuiz: Button
    private var isReversedMode = false
    private lateinit var progressBar: ProgressBar
    private lateinit var spinnerDecks: Spinner
    private lateinit var tvFlashcardScroll: TextView
    private lateinit var tvFlashcardAuto: TextView
    private lateinit var btnToggleTextMode: Button
    private var useScrollMode = true
    private lateinit var btnEndDeck: Button
    private var flashcards = listOf<Flashcard>()
    private var currentIndex = 0
    private var showingFront = true
    private val knownCards = mutableSetOf<Int>()
    private var hasAdvanced = false
    private var isDeckComplete = false
    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind root and prefs
        rootLayout = findViewById(R.id.rootLayout)
        sharedPrefs = getSharedPreferences("settings", MODE_PRIVATE)

        // Bind views
        tvKnownCount          = findViewById(R.id.tvKnownCount)
        tvUnknownCount        = findViewById(R.id.tvUnknownCount)
        tvCardTitle           = findViewById(R.id.tvCardTitle)
        btnDarkMode           = findViewById(R.id.btnDarkMode)
        btnUpload             = findViewById(R.id.btnUpload)
        btnFlip               = findViewById(R.id.btnFlip)
        btnNext               = findViewById(R.id.btnNext)
        btnKnown              = findViewById(R.id.btnKnown)
        btnStudyAgainShuffle  = findViewById(R.id.btnStudyAgainShuffle)
        btnStudyRemaining     = findViewById(R.id.btnStudyRemaining)
        btnReverseQuiz        = findViewById(R.id.btnReverseQuiz)
        progressBar           = findViewById(R.id.progressBar)
        spinnerDecks          = findViewById(R.id.spinnerDecks)
        tvFlashcardScroll = findViewById(R.id.tvFlashcardScroll)
        tvFlashcardAuto  = findViewById(R.id.tvFlashcardAuto)
        btnToggleTextMode = findViewById(R.id.btnToggleTextMode)
        btnEndDeck = findViewById(R.id.btnEndDeck)
        setupEndDeckButton()

        btnToggleTextMode.setOnClickListener {
            useScrollMode = !useScrollMode
            updateFlashcard() // Re-render current card
        }


        // Text-to-speech init
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
            }
        }

        // Load saved deck if exists
        getSharedPreferences("flashcards", MODE_PRIVATE)
            .getString("cards_json", null)
            ?.let { json ->
                flashcards = Gson().fromJson(json, Array<Flashcard>::class.java).toList()
            }

        // Apply theme colors & initial UI
        applyThemeColors()
        updateFlashcard()

        // Click listeners
        btnDarkMode.setOnClickListener { toggleDarkMode() }

        btnUpload.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "text/plain"
            }
            startActivityForResult(intent, REQUEST_CODE_PICK_FILE)
        }

        tvFlashcardScroll.setOnClickListener {
            if (flashcards.isNotEmpty() && !isEndOfDeck()) {
                showingFront = !showingFront
                updateFlashcard(true)
            }
        }

        tvFlashcardAuto.setOnClickListener {
            if (flashcards.isNotEmpty() && !isEndOfDeck()) {
                showingFront = !showingFront
                updateFlashcard(true)
            }
        }


        btnFlip.setOnClickListener {
            if (flashcards.isNotEmpty() && !isEndOfDeck()) {
                showingFront = !showingFront
                updateFlashcard(true) // use animation
            }
        }


        btnNext.setOnClickListener {
            goToNextCard(true)
        }

        btnKnown.setOnClickListener {
            knownCards.add(currentIndex)
            goToNextCard(true)
        }


        btnStudyAgainShuffle.setOnClickListener {
            flashcards = flashcards.shuffled()
            knownCards.clear()
            currentIndex = 0
            showingFront = true
            isDeckComplete = false
            hasAdvanced = false
            updateFlashcard()
        }

        btnStudyRemaining.setOnClickListener {
            flashcards = flashcards
                .filterIndexed { i, _ -> i !in knownCards }
                .shuffled()
            knownCards.clear()
            currentIndex = 0
            showingFront = true
            isDeckComplete = false
            hasAdvanced = false
            updateFlashcard()
        }


        btnReverseQuiz.setOnClickListener {
            isReversedMode = true
            flashcards = flashcards.shuffled()
            knownCards.clear()
            currentIndex = 0
            showingFront = true
            isDeckComplete = false
            hasAdvanced = false
            updateFlashcard()
        }


        val deckName = sharedPrefs.getString("current_deck", null)
        deckName?.let {
            sharedPrefs.getString("cards_json_$it", null)?.let { json ->
                flashcards = Gson().fromJson(json, Array<Flashcard>::class.java).toList()
            }
        }

        spinnerDecks.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val deckName = parent?.getItemAtPosition(position) as? String ?: return
                val deckJson = sharedPrefs.getString("cards_json_$deckName", null) ?: return

                flashcards = Gson().fromJson(deckJson, Array<Flashcard>::class.java).toList().shuffled()
                sharedPrefs.edit().putString("current_deck", deckName).apply()
                knownCards.clear()
                currentIndex = 0
                showingFront = true
                isReversedMode = false
                updateFlashcard()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        data?.data?.let { uri ->
            val deckName = uri.lastPathSegment?.substringAfterLast("/") ?: "deck"
            sharedPrefs.edit().putString("current_deck", deckName).apply()
            sharedPrefs.edit().putString("cards_json_$deckName", Gson().toJson(flashcards)).apply()

            if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == Activity.RESULT_OK) {
                data?.data?.let { uri ->
                    val text = contentResolver.openInputStream(uri)
                        ?.bufferedReader().use { it?.readText() ?: "" }
                    flashcards = parseFlashcards(text).shuffled()
                    getSharedPreferences("flashcards", MODE_PRIVATE)
                        .edit()
                        .putString("cards_json", Gson().toJson(flashcards))
                        .apply()
                    knownCards.clear()
                    currentIndex = 0
                    showingFront = true
                    updateFlashcard()
                }
            }
        }
    }

    private fun parseFlashcards(text: String): List<Flashcard> {
        val regex = Regex("\"(.*?)\"\\s*[-=]\\s*\"(.*?)\"")
        return text.lines().mapNotNull { line ->
            regex.find(line)?.destructured?.let { (front, back) ->
                Flashcard(front, back)
            }
        }
    }

    private fun getActiveFlashcardTextView(): TextView {
        return if (useScrollMode) tvFlashcardScroll else tvFlashcardAuto
    }

    private fun goToNextCard(animate: Boolean = false) {
        if (flashcards.isNotEmpty() && currentIndex < flashcards.size - 1) {
            currentIndex++
            showingFront = true
            updateFlashcard(animate)
        } else {
            // Show deck complete screen
            isDeckComplete = true
            val unknownLeft = flashcards.size - knownCards.size
            tvCardTitle.text = "Deck Complete"
            getActiveFlashcardTextView().text = "🔔 Deck Complete!\nUnknown left: $unknownLeft"
            updateCounts()
            showEndButtons()
        }
    }


    private fun animateFlashcardFlip(newText: String) {
        val flashcardView = getActiveFlashcardTextView()
        val scale = resources.displayMetrics.density
        flashcardView.cameraDistance = 8000 * scale

        val animatorOut = ObjectAnimator.ofFloat(flashcardView, "rotationY", 0f, 90f)
        val animatorIn  = ObjectAnimator.ofFloat(flashcardView, "rotationY", -90f, 0f)
        animatorOut.duration = 150
        animatorIn.duration  = 150

        animatorOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                flashcardView.text = newText
                animatorIn.start()
            }
        })
        animatorOut.start()
    }


    private fun updateFlashcard(shouldAnimateFlip: Boolean = false) {
        // 1) If we're in the "deck complete" state, do nothing.
        if (isDeckComplete) return

        // 2) Always update the Known/Unknown counts
        updateCounts()

        // 3) If there's no deck at all, show the "no cards loaded" UI
        if (flashcards.isEmpty()) {
            tvCardTitle.text = ""
            if (useScrollMode) {
                tvFlashcardScroll.text = getString(R.string.no_cards_loaded)
                tvFlashcardScroll.visibility = View.VISIBLE
                tvFlashcardAuto.visibility = View.GONE
            } else {
                tvFlashcardAuto.text = getString(R.string.no_cards_loaded)
                tvFlashcardAuto.visibility = View.VISIBLE
                tvFlashcardScroll.visibility = View.GONE
            }
            showNormalButtons()
            progressBar.progress = 0
            return
        }

        // 4) Otherwise, render the current card
        val card  = flashcards[currentIndex]
        val front = if (isReversedMode) card.back else card.front
        val back  = if (isReversedMode) card.front else card.back
        val newText = if (showingFront) front else back

        tvCardTitle.text = "Card ${currentIndex + 1} / ${flashcards.size}"
        showNormalButtons()

        // 5) Flip animation or straight‐draw
        if (shouldAnimateFlip) {
            animateFlashcardFlip(newText)
        } else {
            if (useScrollMode) {
                tvFlashcardScroll.text = newText
                tvFlashcardScroll.visibility = View.VISIBLE
                tvFlashcardAuto.visibility = View.GONE
            } else {
                tvFlashcardAuto.text = newText
                tvFlashcardAuto.visibility = View.VISIBLE
                tvFlashcardScroll.visibility = View.GONE
            }
        }

        // 6) Speak and update progress
        tts.speak(newText, TextToSpeech.QUEUE_FLUSH, null, null)
        val percent = ((currentIndex + 1).toFloat() / flashcards.size * 100).toInt()
        progressBar.progress = percent
    }



    private fun updateCounts() {
        val known   = knownCards.size
        val unknown = flashcards.size - known
        tvKnownCount.text   = "Known: $known"
        tvUnknownCount.text = "Unknown: $unknown"
    }

    private fun isEndOfDeck(): Boolean =
        currentIndex == flashcards.lastIndex

    private fun showNormalButtons() {
        btnFlip.visibility               = View.VISIBLE
        btnNext.visibility               = View.VISIBLE
        btnKnown.visibility              = View.VISIBLE
        btnStudyAgainShuffle.visibility  = View.GONE
        btnStudyRemaining.visibility     = View.GONE
        btnReverseQuiz.visibility       = View.GONE
        spinnerDecks.visibility         = View.GONE

    }

    private fun showEndButtons() {
        btnFlip.visibility               = View.GONE
        btnNext.visibility               = View.GONE
        btnKnown.visibility              = View.GONE
        btnStudyAgainShuffle.visibility  = View.VISIBLE
        btnStudyRemaining.visibility     = View.VISIBLE

        btnReverseQuiz.visibility       = View.VISIBLE
        val decks = loadDeckList()
        if (decks.isNotEmpty()) {
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, decks)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerDecks.adapter = adapter
            spinnerDecks.visibility = View.VISIBLE
        }

    }
    private fun loadDeckList(): List<String> {
        val allPrefs = sharedPrefs.all
        return allPrefs.keys
            .filter { it.startsWith("cards_json_") }
            .map { it.removePrefix("cards_json_") }
    }

    private fun resetDeck() {
        flashcards = listOf()
        knownCards.clear()
        currentIndex = 0
        showingFront = true
        isDeckComplete = false
        tvCardTitle.text = ""
        updateFlashcard()
    }


    private fun setupEndDeckButton() {
        btnEndDeck.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("End Deck Early?")
                .setMessage("Are you sure you want to end this deck? Your progress will be reset.")
                .setPositiveButton("Yes") { _, _ ->
                    showEndOfDeckScreen()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun applyThemeColors() {
        val isDark   = sharedPrefs.getBoolean("dark_mode", false)
        val bgColor  = if (isDark) Color.BLACK else Color.WHITE
        val fgColor  = if (isDark) Color.WHITE else Color.BLACK
        val btnTint  = if (isDark) Color.DKGRAY else Color.LTGRAY

        rootLayout.setBackgroundColor(bgColor)

        // Set text color for both flashcard text views
        tvFlashcardScroll.setTextColor(fgColor)
        tvFlashcardAuto.setTextColor(fgColor)

        tvCardTitle.setTextColor(fgColor)

        listOf(
            btnDarkMode,
            btnUpload,
            btnFlip,
            btnNext,
            btnKnown,
            btnStudyAgainShuffle,
            btnStudyRemaining,
            btnReverseQuiz,
            btnToggleTextMode
        ).forEach { btn ->
            btn.setTextColor(fgColor)
            btn.backgroundTintList = ColorStateList.valueOf(btnTint)
        }
    }

    private fun toggleDarkMode() {
        val isDark = sharedPrefs.getBoolean("dark_mode", false)
        sharedPrefs.edit()
            .putBoolean("dark_mode", !isDark)
            .apply()
        applyThemeColors()
    }

    private fun showEndOfDeckScreen() {
        isDeckComplete = true
        val unknownLeft = flashcards.size - knownCards.size
        tvCardTitle.text = getString(R.string.deck_complete)
        getActiveFlashcardTextView().text =
            "🔔 Deck Complete!\nUnknown left: $unknownLeft"
        updateCounts()
        showEndButtons()
    }


    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CODE_PICK_FILE = 1
    }
}

