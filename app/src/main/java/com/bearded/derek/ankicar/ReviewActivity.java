package com.bearded.derek.ankicar;

import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.bearded.derek.ankicar.data.ReviewAdapter;
import com.bearded.derek.ankicar.model.AnkiDatabase;
import com.bearded.derek.ankicar.model.Review;
import com.bearded.derek.ankicar.model.anki.Card;
import com.bearded.derek.ankicar.utils.Logger;
import com.bearded.derek.ankicar.view.ReviewGestureListener;
import com.ichi2.anki.FlashCardsContract;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import kotlin.Pair;

public class ReviewActivity extends BaseActivity implements ReviewGestureListener.ReviewGestureCallback,
    ReviewAdapter.Callback {

    private static final int REQUEST_PERMISSION_FLASHCARD = 2000;

    private boolean isTtsInitComplete;

    private boolean isCardAdapterInit;

    private boolean isQuestion; // !isQuestion == isAnswer always

    private Date startTime;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd-yy-HH_mm_ss");

    @Override
    protected void onPause() {
        AsyncTask<AnkiDatabase, Void, Void> task = new AsyncTask<AnkiDatabase, Void, Void>() {
            @Override
            protected Void doInBackground(AnkiDatabase... ankiDatabases) {
                AnkiDatabase db = ankiDatabases[0];
                db.reviewDao().insert(new Review(startTime, new Date(System.currentTimeMillis())));
                return null;
            }
        };
        task.execute(AnkiDatabase.Companion.getInstance(this));
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        startTime = new Date(System.currentTimeMillis());
        Logger.Companion.setFilename("AnkiCar" + dateFormat.format(startTime));
    }

    private TextView questionTextView, answerTextView;

    private ReviewAdapter reviewAdapter;

    private GestureDetectorCompat gestureDetector;

    private ReviewGestureListener gestureListener;

    private TextToSpeech textToSpeech;

    private Card currentCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handlePermssions();

        questionTextView = findViewById(R.id.question);
        answerTextView = findViewById(R.id.answer);
        answerTextView.setVisibility(View.GONE);

        gestureListener = new ReviewGestureListener(this);
        gestureDetector = new GestureDetectorCompat(this, gestureListener);

        reviewAdapter = new ReviewAdapter(ReviewActivity.this, getContentResolver(), AnkiDatabase.Companion
            .getInstance(getApplicationContext()));

        textToSpeech = new TextToSpeech(ReviewActivity.this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech.setLanguage(Locale.US);
                    isTtsInitComplete = true;
                    if (!shouldRequestPermission(FlashCardsContract.READ_WRITE_PERMISSION)) {
                        reviewAdapter.init(null);
                    }
                }
            }
        });

        isQuestion = true; // Always start with a question
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (this.gestureDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void handlePermssions() {
        if (shouldRequestPermission(FlashCardsContract.READ_WRITE_PERMISSION)) {
            ActivityCompat.requestPermissions(this, new String[]{FlashCardsContract.READ_WRITE_PERMISSION},
                REQUEST_PERMISSION_FLASHCARD);
        }
    }

    private boolean shouldRequestPermission(String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }

        return ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onDestroy() {
        reviewAdapter.flush();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public boolean onFling(@NotNull Pair<String, Double> direction) {
        // Commenting because too verbose
//        if (isTtsInitComplete) {
//            textToSpeech.speak("Flinging " + direction.getFirst(), TextToSpeech.QUEUE_FLUSH, null,
//                "fling:"+direction.getSecond().toString());
//        }

        if (isCardAdapterInit) {
            switch (direction.getFirst()) {
                case "up":
                    onUp();
                    break;
                case "down":
                    onDown();
                    break;
                case "left":
                    onLeft();
                    break;
                case "right":
                    onRight();
                    break;
                default:
                    return false;
            }
        }

        return true;
    }

    @Override
    public void onLongPress() {
        // Too verbose
//        if (isTtsInitComplete) {
//            textToSpeech.speak("Long press", TextToSpeech.QUEUE_FLUSH, null,
//                "long press");
//        }

        if (!isQuestion && isCardAdapterInit) {
            reviewAdapter.answer(1);
            textToSpeech.speak("Again", TextToSpeech.QUEUE_FLUSH, null,
                "Again");
        }
    }

    @Override
    public boolean onDoubleTap() {
        // Commenting because too verbose
//        if (isTtsInitComplete) {
//            textToSpeech.speak("Double tapping", TextToSpeech.QUEUE_FLUSH, null,
//                "double tap");
//        }

        if (isCardAdapterInit) {
            ttsCard();
        }

        return true;
    }

    private void onUp() {
        if (isQuestion) {
            reviewAdapter.skip();
            textToSpeech.speak("Skip", TextToSpeech.QUEUE_FLUSH, null,
                "Skip " + currentCard.getNoteId());
        } else {
            reviewAdapter.answer(4);
            textToSpeech.speak("Easy", TextToSpeech.QUEUE_FLUSH, null,
                "Easy " + currentCard.getNoteId());
        }
    }

    private void onDown() {
        if (isQuestion) {
            reviewAdapter.flag();
            textToSpeech.speak("Flag", TextToSpeech.QUEUE_FLUSH, null,
                "Flag " + currentCard.getNoteId());
        } else {
            reviewAdapter.answer(2);
            textToSpeech.speak("Hard", TextToSpeech.QUEUE_FLUSH, null,
                "Hard " + currentCard.getNoteId());
        }
    }

    private void onLeft() {
        if (isQuestion) {
            revealAnswer();
        } else {
            reviewAdapter.answer(3);
            textToSpeech.speak("Good", TextToSpeech.QUEUE_FLUSH, null,
                "Good " + currentCard.getNoteId());
        }
    }

    private void onRight() {
        if (isQuestion) {
            reviewAdapter.previous();
            textToSpeech.speak("Undo", TextToSpeech.QUEUE_FLUSH, null,
                "Undo " + currentCard.getNoteId());
        } else {
            hideAnswer();
        }

    }

    private void ttsCard() {
        if (isQuestion) {
            textToSpeech.speak(currentCard.getQuestion(), TextToSpeech.QUEUE_ADD, null,
                String.valueOf(currentCard.getNoteId()) + "question");
        } else {
            textToSpeech.speak(currentCard.getAnswer(), TextToSpeech.QUEUE_FLUSH, null,
                String.valueOf(currentCard.getNoteId()) + "answer");
        }
    }

    private void revealAnswer() {
        isQuestion = false;
        answerTextView.setText(currentCard.getAnswer());
        answerTextView.setVisibility(View.VISIBLE);
        ttsCard();
    }

    private void hideAnswer() {
        isQuestion = true;
        answerTextView.setVisibility(View.GONE);
        ttsCard();
    }

    @Override
    public void reviewComplete() {
        textToSpeech.speak("Great job, your review is complete", TextToSpeech.QUEUE_FLUSH, null,
            "Review Complete");
    }

    @Override
    public void nextCard(@NotNull Card card) {
        currentCard = card;
        isCardAdapterInit = true;
        isQuestion = true;
        questionTextView.setText(card.getQuestion());
        answerTextView.setVisibility(View.GONE);
        ttsCard();
    }
}
