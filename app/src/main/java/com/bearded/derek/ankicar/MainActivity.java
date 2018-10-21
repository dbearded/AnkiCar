package com.bearded.derek.ankicar;

import android.content.pm.PackageManager;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.bearded.derek.ankicar.model.anki.Card;
import com.bearded.derek.ankicar.data.ReviewAdapter;
import com.bearded.derek.ankicar.model.AnkiDatabase;
import com.bearded.derek.ankicar.view.ReviewGestureListener;
import com.ichi2.anki.FlashCardsContract;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

import kotlin.Pair;

public class MainActivity extends AppCompatActivity implements ReviewGestureListener.ReviewGestureCallback,
    ReviewAdapter.Callback {

    private static final int REQUEST_PERMISSION_FLASHCARD = 2000;

    private boolean isTtsInitComplete;
    private boolean isCardAdapterInit;
    private boolean isQuestion; // !isQuestion == isAnswer always
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

        reviewAdapter = new ReviewAdapter(MainActivity.this, getContentResolver(), AnkiDatabase.Companion
            .getInstance(getApplicationContext()));

        textToSpeech = new TextToSpeech(MainActivity.this, new TextToSpeech.OnInitListener() {
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
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public boolean onFling(@NotNull Pair<String, Double> direction) {
        if (isTtsInitComplete) {
            textToSpeech.speak("Flinging " + direction.getFirst(), TextToSpeech.QUEUE_ADD, null,
                "fling:"+direction.getSecond().toString());
        }

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
        if (isTtsInitComplete) {
            textToSpeech.speak("Long press", TextToSpeech.QUEUE_ADD, null,
                "long press");
        }

        if (!isQuestion && isCardAdapterInit) {
            reviewAdapter.answer(1);
        }
    }

    @Override
    public boolean onDoubleTap() {
        if (isTtsInitComplete) {
            textToSpeech.speak("Double tapping", TextToSpeech.QUEUE_ADD, null,
                "double tap");
        }

        if (isCardAdapterInit) {
            ttsCard();
        }

        return true;
    }

    private void onUp() {
        if (isQuestion) {
            reviewAdapter.skip();
            textToSpeech.speak("Skipping", TextToSpeech.QUEUE_ADD, null,
                "Skipping " + currentCard.getNoteId());
        } else {
            reviewAdapter.answer(4);
            textToSpeech.speak("Too easy", TextToSpeech.QUEUE_ADD, null,
                "Too easy " + currentCard.getNoteId());
        }
    }

    private void onDown() {
        if (isQuestion) {
            reviewAdapter.flag();
            textToSpeech.speak("Flagging", TextToSpeech.QUEUE_ADD, null,
                "Flagging " + currentCard.getNoteId());
        } else {
            reviewAdapter.answer(2);
            textToSpeech.speak("A little hard", TextToSpeech.QUEUE_ADD, null,
                "A little hard " + currentCard.getNoteId());
        }
    }

    private void onLeft() {
        if (isQuestion) {
            revealAnswer();
        } else {
            reviewAdapter.answer(3);
            textToSpeech.speak("Good", TextToSpeech.QUEUE_ADD, null,
                "Good " + currentCard.getNoteId());
        }
    }

    private void onRight() {
        if (isQuestion) {
            reviewAdapter.previous();
            textToSpeech.speak("Going backwards", TextToSpeech.QUEUE_ADD, null,
                "Going backwards " + currentCard.getNoteId());
        } else {
            hideAnswer();
        }

    }

    private void ttsCard() {
        if (isQuestion) {
            textToSpeech.speak(currentCard.getQuestion(), TextToSpeech.QUEUE_ADD, null,
                String.valueOf(currentCard.getNoteId()) + "question");
        } else {
            textToSpeech.speak(currentCard.getAnswer(), TextToSpeech.QUEUE_ADD, null,
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
        textToSpeech.speak("Great job, your review is complete", TextToSpeech.QUEUE_ADD, null,
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
