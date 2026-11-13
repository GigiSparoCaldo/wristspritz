package com.akemi.wristspritz;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.List;

public class LibraryView {
    public interface Callbacks {
        void onSelectBook(BookData.BookMeta book);
        void onDeleteBook(BookData.BookMeta book);
        void onImportClicked();
        void onSelectChapter(int wordIndex);
        void onCloseOverlay();
    }

    private final Activity act;
    private final float density;

    public LibraryView(Activity act) {
        this.act = act;
        this.density = act.getResources().getDisplayMetrics().density;
    }

    private int dp(int v) { return Math.round(v * density); }

    private GradientDrawable roundBox(String fill, String stroke, int radDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(fill));
        gd.setCornerRadius(dp(radDp));
        if (stroke != null) gd.setStroke(dp(1), Color.parseColor(stroke));
        return gd;
    }

    public void renderLibrary(LinearLayout box, List<BookData.BookMeta> books, String activeId, final Callbacks cb) {
        box.removeAllViews();

        Button backBtn = pill("←  Torna alla lettura", "#111620", "#E2E8F0", "#1F2937");
        backBtn.setOnClickListener(v -> cb.onCloseOverlay());
        box.addView(backBtn);

        TextView hdr = new TextView(act);
        hdr.setText("LIBRERIA");
        hdr.setTextColor(Color.parseColor("#64748B"));
        hdr.setTextSize(10.5f);
        hdr.setLetterSpacing(0.14f);
        hdr.setTypeface(null, Typeface.BOLD);
        hdr.setGravity(Gravity.CENTER);
        hdr.setPadding(0, dp(8), 0, dp(6));
        box.addView(hdr);

        Button impBtn = pill("+  Importa EPUB / TXT", "#161D2B", "#F8FAFC", "#334155");
        impBtn.setOnClickListener(v -> cb.onImportClicked());
        box.addView(impBtn);

        for (final BookData.BookMeta b : books) {
            boolean cur = b.id.equals(activeId);
            LinearLayout card = new LinearLayout(act);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(roundBox(cur ? "#121824" : "#0B0E14", cur ? "#EF4444" : "#1C2331", 14));
            card.setPadding(dp(10), dp(10), dp(10), dp(10));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.setMargins(0, dp(5), 0, dp(5));
            card.setLayoutParams(clp);

            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            if (b.coverPath != null && !b.coverPath.isEmpty() && new File(b.coverPath).exists()) {
                ImageView iv = new ImageView(act);
                Bitmap bmp = BitmapFactory.decodeFile(b.coverPath);
                iv.setImageBitmap(bmp);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(40), dp(58));
                ilp.rightMargin = dp(10);
                iv.setLayoutParams(ilp);
                row.addView(iv);
            } else {
                TextView cov = new TextView(act);
                cov.setText(b.title.length() >= 2 ? b.title.substring(0, 2).toUpperCase() : "BK");
                cov.setTextColor(Color.parseColor("#E2E8F0"));
                cov.setTextSize(12.5f);
                cov.setTypeface(Typeface.SERIF, Typeface.BOLD);
                cov.setGravity(Gravity.CENTER);
                cov.setBackground(roundBox("#141923", "#2A3447", 6));
                LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(40), dp(58));
                glp.rightMargin = dp(10);
                cov.setLayoutParams(glp);
                row.addView(cov);
            }

            LinearLayout info = new LinearLayout(act);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvT = new TextView(act);
            tvT.setText(b.title);
            tvT.setTextColor(Color.parseColor("#F8FAFC"));
            tvT.setTextSize(12.5f);
            tvT.setTypeface(null, Typeface.BOLD);
            tvT.setMaxLines(2);
            info.addView(tvT);

            TextView tvA = new TextView(act);
            tvA.setText(b.author);
            tvA.setTextColor(Color.parseColor("#64748B"));
            tvA.setTextSize(10f);
            tvA.setMaxLines(1);
            info.addView(tvA);

            int pct = b.percent();
            TextView tvSt = new TextView(act);
            tvSt.setText("Pag. " + b.currentPage() + "/" + b.totalPages() + "  ·  " + pct + "%");
            tvSt.setTextColor(Color.parseColor(cur ? "#EF4444" : "#94A3B8"));
            tvSt.setTextSize(10.5f);
            tvSt.setTypeface(null, Typeface.BOLD);
            tvSt.setPadding(0, dp(3), 0, dp(4));
            info.addView(tvSt);

            LinearLayout barBg = new LinearLayout(act);
            barBg.setBackground(roundBox("#18202E", null, 3));
            barBg.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));
            LinearLayout barFill = new LinearLayout(act);
            barFill.setBackground(roundBox(cur ? "#EF4444" : "#94A3B8", null, 3));
            barFill.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Math.max(0.02f, pct / 100f)));
            LinearLayout barEmp = new LinearLayout(act);
            barEmp.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Math.max(0f, 1f - (pct / 100f))));
            barBg.addView(barFill);
            barBg.addView(barEmp);
            info.addView(barBg);

            row.addView(info);
            card.addView(row);

            LinearLayout actRow = new LinearLayout(act);
            actRow.setOrientation(LinearLayout.HORIZONTAL);
            actRow.setPadding(0, dp(7), 0, 0);

            Button openBtn = pill(cur ? "Continua" : "Apri", cur ? "#EF4444" : "#18202E", "#FFFFFF", cur ? null : "#2A3447");
            openBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            openBtn.setOnClickListener(v -> cb.onSelectBook(b));
            actRow.addView(openBtn);

            Button delBtn = pill("Rimuovi", "#0E1219", "#64748B", "#1E2532");
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.leftMargin = dp(6);
            delBtn.setLayoutParams(dlp);
            delBtn.setOnClickListener(v -> cb.onDeleteBook(b));
            actRow.addView(delBtn);

            card.addView(actRow);
            box.addView(card);
        }
    }

    public void renderChapters(LinearLayout box, BookData.BookMeta meta, List<BookData.Chapter> chapters,
                               int currentWord, int wpm, List<BookData.Bookmark> bookmarks, final Callbacks cb) {
        box.removeAllViews();

        Button backBtn = pill("←  Torna alla lettura", "#111620", "#E2E8F0", "#1F2937");
        backBtn.setOnClickListener(v -> cb.onCloseOverlay());
        box.addView(backBtn);

        TextView title = new TextView(act);
        title.setText(meta.title);
        title.setTextColor(Color.parseColor("#F8FAFC"));
        title.setTextSize(13f);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(6), 0, dp(2));
        box.addView(title);

        TextView sub = new TextView(act);
        sub.setText(meta.percent() + "% completato  ·  Pag. " + meta.currentPage() + " di " + meta.totalPages());
        sub.setTextColor(Color.parseColor("#64748B"));
        sub.setTextSize(10.5f);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, dp(6));
        box.addView(sub);

        if (!bookmarks.isEmpty()) {
            TextView bmHdr = new TextView(act);
            bmHdr.setText("SEGNALIBRI");
            bmHdr.setTextColor(Color.parseColor("#64748B"));
            bmHdr.setTextSize(10f);
            bmHdr.setLetterSpacing(0.14f);
            bmHdr.setTypeface(null, Typeface.BOLD);
            bmHdr.setGravity(Gravity.CENTER);
            bmHdr.setPadding(0, dp(4), 0, dp(3));
            box.addView(bmHdr);

            for (final BookData.Bookmark bm : bookmarks) {
                Button bmBtn = pill("« " + bm.label + " »", "#12161F", "#E2E8F0", "#2A3447");
                bmBtn.setOnClickListener(v -> cb.onSelectChapter(bm.wordIdx));
                box.addView(bmBtn);
            }
        }

        TextView chHdr = new TextView(act);
        chHdr.setText("INDICE CAPITOLI");
        chHdr.setTextColor(Color.parseColor("#64748B"));
        chHdr.setTextSize(10f);
        chHdr.setLetterSpacing(0.14f);
        chHdr.setTypeface(null, Typeface.BOLD);
        chHdr.setGravity(Gravity.CENTER);
        chHdr.setPadding(0, dp(8), 0, dp(4));
        box.addView(chHdr);

        for (int i = 0; i < chapters.size(); i++) {
            final BookData.Chapter ch = chapters.get(i);
            int chLen = Math.max(1, ch.endIdx - ch.startIdx);
            int chPct = currentWord > ch.endIdx ? 100 : currentWord <= ch.startIdx ? 0
                    : Math.min(100, Math.round(((currentWord - ch.startIdx) * 100f) / chLen));
            int remPct = 100 - chPct;
            boolean active = currentWord >= ch.startIdx && currentWord <= ch.endIdx;
            int mins = Math.max(1, Math.round((chLen * (remPct / 100f)) / Math.max(100, wpm)));

            LinearLayout card = new LinearLayout(act);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(roundBox(active ? "#121824" : "#0B0E14", active ? "#EF4444" : "#1C2331", 12));
            card.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(3), 0, dp(3));
            card.setLayoutParams(lp);

            String num = String.format("%02d. ", i + 1);
            TextView tvC = new TextView(act);
            tvC.setText(num + ch.title);
            tvC.setTextColor(Color.parseColor("#F8FAFC"));
            tvC.setTextSize(12f);
            tvC.setTypeface(null, Typeface.BOLD);
            card.addView(tvC);

            TextView tvM = new TextView(act);
            tvM.setText(chPct == 100 ? "Completato" : ("Fine capitolo: " + remPct + "%  ·  " + mins + " min"));
            tvM.setTextColor(Color.parseColor(chPct == 100 ? "#64748B" : active ? "#EF4444" : "#94A3B8"));
            tvM.setTextSize(10.5f);
            card.addView(tvM);

            card.setOnClickListener(v -> cb.onSelectChapter(ch.startIdx));
            box.addView(card);
        }
    }

    private Button pill(String text, String bg, String fg, String stroke) {
        Button b = new Button(act);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(11.5f);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(Color.parseColor(fg));
        b.setBackground(roundBox(bg, stroke, 999));
        b.setPadding(dp(12), dp(7), dp(12), dp(7));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(3), 0, dp(3));
        b.setLayoutParams(lp);
        return b;
    }
}
