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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION_FLASHCARD = 2000;

    private boolean TTS_INIT_COMPLETE;
    private boolean CARDS_COMPLETE;

    List<ReviewInfo> reviewInfo = new ArrayList<>();
    TextToSpeech textToSpeech;
    List<Card> cards = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handlePermssions();

        QueryAnkiSchedule queryAnkiSchedule = new QueryAnkiSchedule(new QueryAnkiSchedule.OnCompletionListener() {
            @Override
            public void onComplete(@NotNull List<ReviewInfo> reviewInfo) {
                MainActivity.this.reviewInfo = reviewInfo;

                List<Long> notedIds = new ArrayList<>();
                for (ReviewInfo r :
                    reviewInfo) {
                    notedIds.add(r.getNoteId());
                }

                final QueryAnkiSpecificSimpleCards specificCards = new QueryAnkiSpecificSimpleCards(notedIds,
                    new QueryAnkiSpecificSimpleCards.OnCompletionListener() {
                        @Override
                        public void onComplete(@NotNull List<AnkiCard> reviewInfo) {
                            for (AnkiCard card :
                                reviewInfo) {
                                String noteId = String.valueOf(card.getNoteId());
                                Log.v("NoteId:", noteId);
                                String quesSimp = card.getQuestionSimple();
//                        Log.v("Question Simple:", quesSimp);
                                String ansSimp  = card.getAnswerSimple();
//                        Log.v("Answer Simple:", ansSimp);
                                String ansPure = card.getAnswerPure();
//                        Log.v("Answer Pure:", ansPure);
//                        Log.v("Question", card.getQuestion());
//                        Log.v("Answer", card.getAnswer());
                            }

                            QueryAnkiModels models = new QueryAnkiModels(reviewInfo,
                                new QueryAnkiModels.OnCompletionListener() {
                                    @Override
                                    public void onComplete(@NotNull List<AnkiCard> reviewInfo) {
                                        Long mid = reviewInfo.get(0).getModelId();
                                        Log.v("Models", mid.toString());
                                        for (AnkiCard ankiCard :
                                            reviewInfo) {
                                            cards.add(Card.Companion.build(ankiCard, DataKt.getCleanser(ankiCard)));
                                        }
                                        MainActivity.this.cards = cards;
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
                specificCards.execute(getContentResolver());

            }
        });

//        List<Long> notedIds = new ArrayList<>();
//        notedIds.add(1507431823033L);
//        notedIds.add(1507431823033L);
//        notedIds.add(1512853233522L);
//        notedIds.add(1514954618389L);



//        specificCards.execute(getContentResolver());
        if (!shouldRequestPermission(FlashCardsContract.READ_WRITE_PERMISSION)) {
            queryAnkiSchedule.execute(getContentResolver());
        }

//        textToSpeech = new TextToSpeech(MainActivity.this, new TextToSpeech.OnInitListener() {
//            @Override
//            public void onInit(int status) {
//                if (status == TextToSpeech.SUCCESS) {
//                    textToSpeech.setLanguage(Locale.US);
//                    TTS_INIT_COMPLETE = true;
//                    speakCards();
//                }
//            }
//        });

    }

    private void speakCards() {
//        textToSpeech.speak(card.getQuestion(), TextToSpeech.QUEUE_FLUSH, null, "question");
//        for (int i = 0; i < cards.size(); i++) {
//            textToSpeech.speak(cards.get(i).getQuestion(), TextToSpeech.QUEUE_ADD, null, "question" + i);
//        }
        textToSpeech.speak(cards.get(cards.size()-1).getQuestion(), TextToSpeech.QUEUE_ADD, null, "question");
        String str = "Problem: Two objects step forward at different rates. Will they ever be at the same place on " +
            "the same step?<div>Approach: ?</div>";
//        str = str.replaceFirst("<div>(.*)</div>","");
        Pattern p = Pattern.compile("<div>(.*?)</div>");
        Matcher m = p.matcher(str);
        if (m.find()) {
            String s = m.group(1);
            str = m.replaceAll(" " + m.group(1));
        }

        String s = Jsoup.parse("Problem: Two objects step forward at different rates. Will they ever be at the same place on the same step?<div>Approach: ?</div>\n" +
            "    <p>\n" +
            "    Approach: [...]").text();
        s = s.replace("Approach: [...]", "");

        String t = "<style>/* general card style */\n" +
            "\n" +
            "    html {\n" +
            "      /* scrollbar always visible in order to prevent shift when revealing answer*/\n" +
            "      overflow-y: scroll;\n" +
            "    }\n" +
            "\n" +
            "    .card {\n" +
            "      font-family: \"Helvetica LT Std\", Helvetica, Arial, Sans;\n" +
            "      font-size: 150%;\n" +
            "      text-align: center;\n" +
            "      color: black;\n" +
            "      background-color: white;\n" +
            "    }\n" +
            "\n" +
            "    /* general layout */\n" +
            "\n" +
            "    .text {\n" +
            "      /* center left-aligned text on card */\n" +
            "      display: inline-block;\n" +
            "      align: center;\n" +
            "      text-align: left;\n" +
            "      margin: auto;\n" +
            "      max-width: 40em;\n" +
            "    }\n" +
            "\n" +
            "    .hidden {\n" +
            "      /* guarantees a consistent width across front and back */\n" +
            "      font-weight: bold;\n" +
            "      display: block;\n" +
            "      line-height:0;\n" +
            "      height: 0;\n" +
            "      overflow: hidden;\n" +
            "      visibility: hidden;\n" +
            "    }\n" +
            "\n" +
            "    .title {\n" +
            "      font-weight: bold;\n" +
            "      font-size: 1.1em;\n" +
            "      margin-bottom: 1em;\n" +
            "      text-align: center;\n" +
            "    }\n" +
            "\n" +
            "    /* clozes */\n" +
            "\n" +
            "    .cloze {\n" +
            "      /* regular cloze deletion */\n" +
            "      font-weight: bold;\n" +
            "      color: #0048FF;\n" +
            "    }\n" +
            "\n" +
            "    /* original text reveal hint */\n" +
            "\n" +
            "    .fullhint a {\n" +
            "      color: #0048FF;\n" +
            "    }\n" +
            "\n" +
            "    .card21 .fullhint{\n" +
            "      /* no need to display hint on last card */\n" +
            "      display:none;\n" +
            "    }\n" +
            "\n" +
            "    /* additional fields */\n" +
            "\n" +
            "    .extra{\n" +
            "      margin-top: 0.5em;\n" +
            "      margin: auto;\n" +
            "      max-width: 40em;\n" +
            "    }\n" +
            "\n" +
            "    .extra-entry{\n" +
            "      margin-top: 0.8em;\n" +
            "      font-size: 0.9em;\n" +
            "      text-align:left;\n" +
            "    }\n" +
            "\n" +
            "    .extra-descr{\n" +
            "      margin-bottom: 0.2em;\n" +
            "      font-weight: bold;\n" +
            "      font-size: 1em;\n" +
            "    }</style><div class=\"front\">\n" +
            "      <div class=\"title\">Hello</div>\n" +
            "      <div class=\"text\">\n" +
            "\n" +
            "        <div>One</div><div><span class=cloze>[...]</span></div><div>...</div><div>...</div><div>...</div>\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "\n" +
            "        <div class=\"hidden\">\n" +
            "           <div>One<div>Two</div><div>Three</div><div>Four</div><div>Five</div></div>\n" +
            "        </div>\n" +
            "      </div>\n" +
            "    </div>";
        Document doc = Jsoup.parse(t);
        Elements items = doc.getElementsByClass("front").first().selectFirst("[class='text']").children()
            .select("div:not([class='hidden'], [class='hidden'] *)");
        List<String> list = items.eachText();
        String v = items.text();

        String st = "AdapterView's interface <span class=cloze>[...]</span> has the following parameters for method " +
            "onItemClick(AdapterView&lt;?&gt;, View, int position, long id)&nbsp;";
        String stv = Jsoup.parse(st).text();
        String stp = stv.replace("[...]", " blank ");
//        textToSpeech.speak(stp, TextToSpeech.QUEUE_FLUSH, null, "question");
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
