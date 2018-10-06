package com.bearded.derek.ankicar;

import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.ichi2.anki.FlashCardsContract;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION_FLASHCARD = 2000;

    private boolean TTS_INIT_COMPLETE;
    private boolean CARDS_COMPLETE;

    List<ReviewInfo> reviewInfo = new ArrayList<>();
    TextToSpeech textToSpeech;
    Card card;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handlePermssions();

        QueryAnkiSchedule queryAnkiSchedule = new QueryAnkiSchedule(new QueryAnkiSchedule.OnCompletionListener() {
            @Override
            public void onComplete(@NotNull List<ReviewInfo> reviewInfo) {
                MainActivity.this.reviewInfo = reviewInfo;

            }
        });

        List<Long> notedIds = new ArrayList<>();
        notedIds.add(1507431823033L);
//        notedIds.add(1512853233522L);
//        notedIds.add(1514954618389L);

        final QueryAnkiSpecificSimpleCards specificCards = new QueryAnkiSpecificSimpleCards(notedIds,
            new QueryAnkiSpecificSimpleCards.OnCompletionListener() {
                @Override
                public void onComplete(@NotNull List<AnkiCard> reviewInfo) {
                    for (AnkiCard card :
                        reviewInfo) {
                        String noteId = String.valueOf(card.getNoteId());
                        Log.v("NoteId:", noteId);
                        String quesSimp = card.getQuestionSimple();
                        Log.v("Question Simple:", quesSimp);
                        String ansSimp  = card.getAnswerSimple();
                        Log.v("Answer Simple:", ansSimp);
                        String ansPure = card.getAnswerPure();
                        Log.v("Answer Pure:", ansPure);
//                        Log.v("Question", card.getQuestion());
//                        Log.v("Answer", card.getAnswer());
                    }

                    QueryAnkiModels models = new QueryAnkiModels(reviewInfo,
                        new QueryAnkiModels.OnCompletionListener() {
                            @Override
                            public void onComplete(@NotNull List<AnkiCard> reviewInfo) {
                                Long mid = reviewInfo.get(0).getModelId();
                                Log.v("Models", mid.toString());
                                Card card = Card.Companion.convert(reviewInfo.get(0), ClozeStatementCleanser.INSTANCE);
                                Long noteId = card.getNoteId();
                                Long cardOrd = card.getCardOrd();
                                String question = card.getQuestion();
                                String answer = card.getAnswer();
                                MainActivity.this.card = card;
                                CARDS_COMPLETE = true;
                                textToSpeech = new TextToSpeech(MainActivity.this, new TextToSpeech.OnInitListener() {
                                    @Override
                                    public void onInit(int status) {
                                        if (status == TextToSpeech.SUCCESS) {
                                            textToSpeech.setLanguage(Locale.US);
                                            TTS_INIT_COMPLETE = true;
                                            speakCards();
                                        }
                                    }
                                });

                            }
                        });
                    models.execute(getContentResolver());
                }
            });

//        specificCards.execute(getContentResolver());
        if (!shouldRequestPermission(FlashCardsContract.READ_WRITE_PERMISSION)) {
            specificCards.execute(getContentResolver());
        }
    }

    private void speakCards() {
        textToSpeech.speak(card.getQuestion(), TextToSpeech.QUEUE_FLUSH, null, "question");
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
        textToSpeech.stop();
        textToSpeech.shutdown();
        super.onDestroy();
    }

    static class QueryAnkiScheduleA extends AsyncTask<ContentResolver, Void, List<ReviewInfo>> {

        private static final String EMPTY_MEDIA = "[]";

        private WeakReference<CompletionListener> weakReferenceListener;

        public QueryAnkiScheduleA(CompletionListener listener) {
            weakReferenceListener = new WeakReference<>(listener);
        }

        interface CompletionListener {
            void onComplete(List<ReviewInfo> reviewInfo);
        }

        @Override
        protected List<ReviewInfo> doInBackground(ContentResolver... contentResolvers) {
            ContentResolver cr = contentResolvers[0];
            if (cr == null) {
                return null;
            }

            List<ReviewInfo> reviewInfo = new ArrayList<>();
            Uri scheduled_cards_uri = FlashCardsContract.ReviewInfo.CONTENT_URI;

            String deckSelect = "limit=?";
            String deckArgs[] = new String[]{"100"};

            try (Cursor cur = cr.query(scheduled_cards_uri,
                null,  // projection
                deckSelect,  // if null, default values will be used
                deckArgs,  // if null, the deckSelector must not contain any placeholders ("?")
                null   // sortOrder is ignored for this URI
            )) {
                while (cur.moveToNext()) {
                    if (TextUtils.equals(cur.getString(4), EMPTY_MEDIA)) {
                        reviewInfo.add(new ReviewInfo(cur.getLong(0),
                            cur.getLong(1),
                            cur.getLong(2),
                            cur.getString(3)));
                    }
                }
            }

            return reviewInfo;
        }

        @Override
        protected void onPostExecute(List<ReviewInfo> reviewInfo) {
            CompletionListener listener = weakReferenceListener.get();
            if (listener != null) {
                listener.onComplete(reviewInfo);
            }
        }
    }
}
