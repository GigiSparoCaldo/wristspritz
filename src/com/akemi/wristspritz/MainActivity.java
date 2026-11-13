package com.akemi.wristspritz;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_IMPORT = 88;
    private SpritzView spritzView;
    private LibraryView libView;
    private ScrollView drawerScroll;
    private LinearLayout drawerBox;
    private Button chapterTopBtn;
    private TextView chapterSubTv;
    private Button wpmBtn;

    private final ExecutorService ioPool = Executors.newSingleThreadExecutor();
    private final Handler playHandler = new Handler(Looper.getMainLooper());
    private Vibrator vibrator;

    private EpubParser.ParsedBook currentBook;
    private int wordIdx = 0;
    private int wpm = 300;
    private float fontSp = 28f;
    private int crownStep = 1;
    private boolean isPlaying = false;
    private int lastChapterIdx = -1;
    private float rotaryAccum = 0f;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPlaying || currentBook == null || currentBook.words.length == 0) return;
            if (wordIdx < currentBook.words.length - 1) {
                wordIdx++;
                refreshWordDisplay(true);
                playHandler.postDelayed(this, computeDelayMs(currentBook.words[wordIdx]));
            } else {
                setPlaying(false);
                Toast.makeText(MainActivity.this, "Libro completato", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        SharedPreferences p = getSharedPreferences("spritz_prefs", MODE_PRIVATE);
        wpm = p.getInt("wpm", 300);
        fontSp = p.getFloat("font_sp", 28f);
        crownStep = p.getInt("crown_step", 1);

        libView = new LibraryView(this);
        buildMainUi();
        bootLibrary();
    }

    private void buildMainUi() {
        float d = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        spritzView = new SpritzView(this, fontSp, new SpritzView.Listener() {
            @Override public void onTapCenter() { if (currentBook != null) setPlaying(!isPlaying); }
            @Override public void onLongPressCenter() { showContextPeek(); }
        });
        root.addView(spritzView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout topHud = new LinearLayout(this);
        topHud.setOrientation(LinearLayout.VERTICAL);
        topHud.setGravity(Gravity.CENTER_HORIZONTAL);
        topHud.setPadding(Math.round(44 * d), Math.round(18 * d), Math.round(44 * d), 0);

        chapterTopBtn = new Button(this);
        chapterTopBtn.setText("Cap. 1/1  ·  Fine 100%  ▾");
        chapterTopBtn.setAllCaps(false);
        chapterTopBtn.setTextSize(10.5f);
        chapterTopBtn.setTypeface(null, Typeface.BOLD);
        chapterTopBtn.setTextColor(Color.parseColor("#E2E8F0"));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Color.parseColor("#0E1117"));
        pill.setCornerRadius(999f);
        pill.setStroke(Math.round(d), Color.parseColor("#1F2937"));
        chapterTopBtn.setBackground(pill);
        chapterTopBtn.setPadding(Math.round(11 * d), Math.round(4 * d), Math.round(11 * d), Math.round(4 * d));
        chapterTopBtn.setMinHeight(0);
        chapterTopBtn.setMinimumHeight(0);
        chapterTopBtn.setOnClickListener(v -> openChapterSelector());
        topHud.addView(chapterTopBtn);

        chapterSubTv = new TextView(this);
        chapterSubTv.setText("Caricamento...");
        chapterSubTv.setTextColor(Color.parseColor("#64748B"));
        chapterSubTv.setTextSize(9.5f);
        chapterSubTv.setMaxLines(1);
        chapterSubTv.setEllipsize(TextUtils.TruncateAt.END);
        chapterSubTv.setGravity(Gravity.CENTER);
        chapterSubTv.setPadding(0, Math.round(3 * d), 0, 0);
        topHud.addView(chapterSubTv);
        root.addView(topHud, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        LinearLayout botHud = new LinearLayout(this);
        botHud.setOrientation(LinearLayout.HORIZONTAL);
        botHud.setGravity(Gravity.CENTER);
        botHud.setPadding(Math.round(32 * d), 0, Math.round(32 * d), Math.round(30 * d));
        botHud.addView(hudBtn("≡", 34, v -> openBookSelector()));
        botHud.addView(hudBtn("↺", 34, v -> rewindSentence()));
        wpmBtn = hudBtn(String.valueOf(wpm), 48, v -> cycleWpm());
        wpmBtn.setOnLongClickListener(v -> {
            crownStep = (crownStep == 1) ? 3 : 1;
            getSharedPreferences("spritz_prefs", MODE_PRIVATE).edit().putInt("crown_step", crownStep).apply();
            Toast.makeText(this, "Passo corona: " + crownStep + (crownStep == 1 ? " parola" : " parole"), Toast.LENGTH_SHORT).show();
            return true;
        });
        botHud.addView(wpmBtn);
        botHud.addView(hudBtn("Aa", 34, v -> cycleFontSize()));
        root.addView(botHud, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        drawerScroll = new ScrollView(this);
        drawerScroll.setBackgroundColor(Color.BLACK);
        drawerScroll.setVerticalScrollBarEnabled(false);
        drawerScroll.setVisibility(View.GONE);
        drawerBox = new LinearLayout(this);
        drawerBox.setOrientation(LinearLayout.VERTICAL);
        drawerBox.setPadding(Math.round(24 * d), Math.round(30 * d), Math.round(24 * d), Math.round(46 * d));
        drawerScroll.addView(drawerBox);
        root.addView(drawerScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        spritzView.setFocusable(true);
        spritzView.requestFocus();
    }

    private Button hudBtn(String label, int widthDp, View.OnClickListener l) {
        float d = getResources().getDisplayMetrics().density;
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(11f);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(Color.parseColor("#CBD5E1"));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0E1117"));
        bg.setCornerRadius(999f);
        bg.setStroke(Math.round(d), Color.parseColor("#1F2937"));
        b.setBackground(bg);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setMinHeight(0); b.setMinimumHeight(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(widthDp * d), Math.round(31 * d));
        lp.setMargins(Math.round(3 * d), 0, Math.round(3 * d), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            if (drawerScroll.getVisibility() == View.VISIBLE) {
                drawerScroll.scrollBy(0, Math.round(-ev.getAxisValue(MotionEvent.AXIS_SCROLL) * 60f));
                return true;
            }
            if (currentBook != null && currentBook.words.length > 0) {
                if (isPlaying) setPlaying(false);
                rotaryAccum += -ev.getAxisValue(MotionEvent.AXIS_SCROLL);
                if (Math.abs(rotaryAccum) >= 0.45f) {
                    int dir = rotaryAccum > 0 ? 1 : -1;
                    rotaryAccum = 0f;
                    wordIdx = Math.max(0, Math.min(currentBook.words.length - 1, wordIdx + (dir * crownStep)));
                    refreshWordDisplay(true);
                }
                return true;
            }
        }
        return super.dispatchGenericMotionEvent(ev);
    }

    private void setPlaying(boolean play) {
        isPlaying = play;
        playHandler.removeCallbacks(tickRunnable);
        if (isPlaying) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            playHandler.postDelayed(tickRunnable, computeDelayMs(currentBook.words[wordIdx]));
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            saveCurrentProgress();
        }
        refreshWordDisplay(false);
    }

    private long computeDelayMs(String word) {
        long base = Math.round(60000.0 / Math.max(100, wpm));
        if (word == null || word.isEmpty()) return base;
        char last = word.charAt(word.length() - 1);
        if (last == '.' || last == '!' || last == '?' || last == ';') return Math.round(base * 2.1);
        if (last == ',' || last == ':' || last == '—') return Math.round(base * 1.5);
        if (word.length() >= 10) return Math.round(base * 1.25);
        return base;
    }

    private void refreshWordDisplay(boolean hapticOnChapter) {
        if (currentBook == null || currentBook.words.length == 0) return;
        int chIdx = findChapterIndex(wordIdx);
        BookData.Chapter ch = currentBook.chapters.get(chIdx);
        int chLen = Math.max(1, ch.endIdx - ch.startIdx);
        int chDonePct = Math.min(100, Math.max(0, Math.round(((wordIdx - ch.startIdx) * 100f) / chLen)));
        int chRemPct = 100 - chDonePct;
        int remMins = Math.max(1, Math.round(Math.max(0, ch.endIdx - wordIdx) / (float) Math.max(100, wpm)));

        StringBuilder prevSb = new StringBuilder();
        for (int i = Math.max(0, wordIdx - 3); i < wordIdx; i++) {
            if (prevSb.length() > 0) prevSb.append(' ');
            prevSb.append(currentBook.words[i]);
        }
        StringBuilder nextSb = new StringBuilder();
        for (int i = wordIdx + 1; i <= Math.min(currentBook.words.length - 1, wordIdx + 3); i++) {
            if (nextSb.length() > 0) nextSb.append(' ');
            nextSb.append(currentBook.words[i]);
        }

        chapterTopBtn.setText("Cap. " + (chIdx + 1) + "/" + currentBook.chapters.size() + "  ·  Fine " + chRemPct + "%  ▾");
        chapterSubTv.setText(ch.title);
        spritzView.updateState(currentBook.words[wordIdx], prevSb.toString(), nextSb.toString(), wpm, isPlaying, remMins + " min");

        if (hapticOnChapter && lastChapterIdx != -1 && lastChapterIdx != chIdx && vibrator != null) {
            vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE));
        }
        lastChapterIdx = chIdx;
    }

    private int findChapterIndex(int idx) {
        for (int i = 0; i < currentBook.chapters.size(); i++) {
            BookData.Chapter c = currentBook.chapters.get(i);
            if (idx >= c.startIdx && idx <= c.endIdx) return i;
        }
        return 0;
    }

    private void rewindSentence() {
        if (currentBook == null) return;
        setPlaying(false);
        int i = Math.max(0, wordIdx - 2);
        while (i > 0) {
            String w = currentBook.words[i - 1];
            if (w.endsWith(".") || w.endsWith("!") || w.endsWith("?")) break;
            i--;
        }
        wordIdx = i;
        refreshWordDisplay(false);
    }

    private void showContextPeek() {
        if (currentBook == null) return;
        setPlaying(false);
        int from = Math.max(0, wordIdx - 22), to = Math.min(currentBook.words.length - 1, wordIdx + 22);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i <= to; i++) sb.append(i == wordIdx ? "«" + currentBook.words[i] + "» " : currentBook.words[i] + " ");
        new AlertDialog.Builder(this).setTitle("Contesto").setMessage(sb.toString().trim())
                .setPositiveButton("Segnalibro", (d, w) -> saveQuickBookmark()).setNegativeButton("Chiudi", null).show();
    }

    private void saveQuickBookmark() {
        if (currentBook == null) return;
        int to = Math.min(currentBook.words.length - 1, wordIdx + 4);
        StringBuilder sb = new StringBuilder();
        for (int i = wordIdx; i <= to; i++) sb.append(currentBook.words[i]).append(" ");
        BookData.addBookmark(this, currentBook.meta.id, wordIdx, sb.toString().trim());
        Toast.makeText(this, "Segnalibro salvato", Toast.LENGTH_SHORT).show();
    }

    private void cycleWpm() {
        wpm = wpm < 250 ? 300 : wpm < 350 ? 400 : wpm < 450 ? 500 : wpm < 550 ? 600 : 200;
        getSharedPreferences("spritz_prefs", MODE_PRIVATE).edit().putInt("wpm", wpm).apply();
        wpmBtn.setText(String.valueOf(wpm));
        refreshWordDisplay(false);
    }

    private void cycleFontSize() {
        fontSp = fontSp < 26f ? 28f : fontSp < 30f ? 33f : fontSp < 35f ? 38f : 24f;
        getSharedPreferences("spritz_prefs", MODE_PRIVATE).edit().putFloat("font_sp", fontSp).apply();
        spritzView.setFontSp(fontSp);
    }

    private void bootLibrary() {
        ioPool.execute(() -> {
            scanDownloadsFolder();
            List<BookData.BookMeta> list = BookData.loadLibrary(this);
            if (list.isEmpty()) {
                try {
                    File sample = new File(getFilesDir(), "Guida_WristSpritz.txt");
                    String demo = "Capitolo 1 Benvenuto su WristSpritz\n"
                            + "Gira la corona del tuo OnePlus Watch 3 per far scorrere le parole una alla volta. "
                            + "La lettera rossa al centro allinea lo sguardo senza muovere gli occhi. "
                            + "Tocca il centro dello schermo per avviare o fermare la lettura automatica.\n"
                            + "Capitolo 2 Libri EPUB e TXT\n"
                            + "Tocca il menu in basso a sinistra per aprire la libreria e importare file EPUB o TXT.";
                    try (FileOutputStream fos = new FileOutputStream(sample)) { fos.write(demo.getBytes(StandardCharsets.UTF_8)); }
                    EpubParser.ParsedBook pb = EpubParser.parseFile(this, sample, "demo_guide", 0);
                    list.add(pb.meta);
                    BookData.saveLibrary(this, list);
                } catch (Exception ignored) {}
            }
            String lastId = getSharedPreferences("spritz_prefs", MODE_PRIVATE).getString("last_book_id", "");
            BookData.BookMeta target = list.get(0);
            for (BookData.BookMeta b : list) if (b.id.equals(lastId)) { target = b; break; }
            openBookInternal(target);
        });
    }

    private void scanDownloadsFolder() {
        try {
            File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dl == null || !dl.exists() || dl.listFiles() == null) return;
            List<BookData.BookMeta> lib = BookData.loadLibrary(this);
            boolean changed = false;
            for (File f : dl.listFiles()) {
                String low = f.getName().toLowerCase();
                if (f.isFile() && (low.endsWith(".epub") || low.endsWith(".txt"))) {
                    boolean exists = false;
                    for (BookData.BookMeta b : lib) if (b.filePath.equals(f.getAbsolutePath())) { exists = true; break; }
                    if (!exists) { lib.add(EpubParser.parseFile(this, f, null, 0).meta); changed = true; }
                }
            }
            if (changed) BookData.saveLibrary(this, lib);
        } catch (Exception ignored) {}
    }

    private void openBookInternal(final BookData.BookMeta meta) {
        ioPool.execute(() -> {
            try {
                final EpubParser.ParsedBook pb = EpubParser.parseFile(this, new File(meta.filePath), meta.id, meta.currentWord);
                runOnUiThread(() -> {
                    currentBook = pb; wordIdx = pb.meta.currentWord; lastChapterIdx = -1;
                    drawerScroll.setVisibility(View.GONE);
                    refreshWordDisplay(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Errore apertura: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void openBookSelector() {
        setPlaying(false);
        scanDownloadsFolder();
        final List<BookData.BookMeta> list = BookData.loadLibrary(this);
        String activeId = currentBook != null ? currentBook.meta.id : "";
        libView.renderLibrary(drawerBox, list, activeId, new LibraryView.Callbacks() {
            @Override public void onSelectBook(BookData.BookMeta book) { openBookInternal(book); }
            @Override public void onDeleteBook(BookData.BookMeta book) { list.remove(book); BookData.saveLibrary(MainActivity.this, list); openBookSelector(); }
            @Override public void onImportClicked() {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
                startActivityForResult(i, REQ_IMPORT);
            }
            @Override public void onSelectChapter(int idx) {}
            @Override public void onCloseOverlay() { drawerScroll.setVisibility(View.GONE); }
        });
        drawerScroll.setVisibility(View.VISIBLE);
        drawerScroll.scrollTo(0, 0);
    }

    private void openChapterSelector() {
        if (currentBook == null) return;
        setPlaying(false);
        saveCurrentProgress();
        libView.renderChapters(drawerBox, currentBook.meta, currentBook.chapters, wordIdx, wpm,
                BookData.getBookmarks(this, currentBook.meta.id), new LibraryView.Callbacks() {
            @Override public void onSelectBook(BookData.BookMeta book) {}
            @Override public void onDeleteBook(BookData.BookMeta book) {}
            @Override public void onImportClicked() {}
            @Override public void onSelectChapter(int idx) {
                wordIdx = Math.max(0, Math.min(currentBook.words.length - 1, idx));
                drawerScroll.setVisibility(View.GONE);
                refreshWordDisplay(false);
            }
            @Override public void onCloseOverlay() { drawerScroll.setVisibility(View.GONE); }
        });
        drawerScroll.setVisibility(View.VISIBLE);
        drawerScroll.scrollTo(0, 0);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_IMPORT && res == RESULT_OK && data != null && data.getData() != null) {
            final Uri uri = data.getData();
            ioPool.execute(() -> {
                try {
                    String name = "libro.epub";
                    try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                        if (c != null && c.moveToFirst()) {
                            int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                            if (idx >= 0) name = c.getString(idx);
                        }
                    }
                    File dir = new File(getFilesDir(), "books");
                    if (!dir.exists()) dir.mkdirs();
                    File dest = new File(dir, name);
                    try (InputStream is = getContentResolver().openInputStream(uri); FileOutputStream fos = new FileOutputStream(dest)) {
                        byte[] buf = new byte[8192]; int n;
                        while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
                    }
                    EpubParser.ParsedBook pb = EpubParser.parseFile(this, dest, null, 0);
                    List<BookData.BookMeta> lib = BookData.loadLibrary(this);
                    lib.add(0, pb.meta); BookData.saveLibrary(this, lib);
                    openBookInternal(pb.meta);
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "Errore import: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
        }
    }

    private void saveCurrentProgress() {
        if (currentBook != null) { currentBook.meta.currentWord = wordIdx; BookData.updateProgress(this, currentBook.meta.id, wordIdx); }
    }

    @Override
    public void onBackPressed() {
        if (drawerScroll.getVisibility() == View.VISIBLE) { drawerScroll.setVisibility(View.GONE); return; }
        setPlaying(false); super.onBackPressed();
    }

    @Override
    protected void onPause() { setPlaying(false); super.onPause(); }
}
