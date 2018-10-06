package com.bearded.derek.ankicar;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;

import com.ichi2.anki.FlashCardsContract;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION_FLASHCARD = 2000;

    List<ReviewInfo> reviewInfo = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handlePermssions();

        QueryAnkiSchedule queryAnkiSchedule = new QueryAnkiSchedule(new QueryAnkiSchedule.OnCompletionListener() {
            @Override
            public void onComplete(@NotNull List<ReviewInfo> reviewInfo) {
                MainActivity.this.reviewInfo = reviewInfo;
                QueryAnkiSimpleCards simpleCards = new QueryAnkiSimpleCards(reviewInfo,
                    new QueryAnkiSimpleCards.OnCompletionListener() {
                        @Override
                        public void onComplete(@NotNull List<ReviewInfo> reviewInfo) {

                        }
                    });
                simpleCards.execute(getContentResolver());
            }
        });

        queryAnkiSchedule.execute(getContentResolver());
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
