package com.akemi.wristspritz;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class BookData {
    public static class Chapter {
        public String title;
        public int startIdx;
        public int endIdx;

        public Chapter(String title, int startIdx, int endIdx) {
            this.title = title;
            this.startIdx = startIdx;
            this.endIdx = endIdx;
        }
    }

    public static class Bookmark {
        public int wordIdx;
        public String label;

        public Bookmark(int wordIdx, String label) {
            this.wordIdx = wordIdx;
            this.label = label;
        }
    }

    public static class BookMeta {
        public String id;
        public String title;
        public String author;
        public String filePath;
        public String coverPath;
        public int totalWords;
        public int currentWord;
        public int chapterCount;

        public int totalPages() { return Math.max(1, (totalWords + 249) / 250); }
        public int currentPage() { return Math.min(totalPages(), Math.max(1, (currentWord + 249) / 250)); }
        public int percent() { return totalWords <= 0 ? 0 : Math.min(100, Math.round((currentWord * 100f) / totalWords)); }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("spritz_prefs", Context.MODE_PRIVATE);
    }

    public static List<BookMeta> loadLibrary(Context ctx) {
        List<BookMeta> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(ctx).getString("library_json", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                BookMeta b = new BookMeta();
                b.id = o.optString("id");
                b.title = o.optString("title", "Senza titolo");
                b.author = o.optString("author", "Autore");
                b.filePath = o.optString("filePath", "");
                b.coverPath = o.optString("coverPath", "");
                b.totalWords = o.optInt("totalWords", 0);
                b.currentWord = o.optInt("currentWord", 0);
                b.chapterCount = o.optInt("chapterCount", 1);
                list.add(b);
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static void saveLibrary(Context ctx, List<BookMeta> list) {
        try {
            JSONArray arr = new JSONArray();
            for (BookMeta b : list) {
                JSONObject o = new JSONObject();
                o.put("id", b.id);
                o.put("title", b.title);
                o.put("author", b.author);
                o.put("filePath", b.filePath);
                o.put("coverPath", b.coverPath);
                o.put("totalWords", b.totalWords);
                o.put("currentWord", b.currentWord);
                o.put("chapterCount", b.chapterCount);
                arr.put(o);
            }
            prefs(ctx).edit().putString("library_json", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static void updateProgress(Context ctx, String bookId, int wordIdx) {
        List<BookMeta> list = loadLibrary(ctx);
        for (BookMeta b : list) {
            if (b.id.equals(bookId)) { b.currentWord = wordIdx; break; }
        }
        saveLibrary(ctx, list);
        prefs(ctx).edit().putString("last_book_id", bookId).apply();
    }

    public static List<Bookmark> getBookmarks(Context ctx, String bookId) {
        List<Bookmark> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(ctx).getString("bm_" + bookId, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Bookmark(o.optInt("w", 0), o.optString("l", "")));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static void addBookmark(Context ctx, String bookId, int wordIdx, String label) {
        List<Bookmark> list = getBookmarks(ctx, bookId);
        list.add(0, new Bookmark(wordIdx, label));
        if (list.size() > 15) list = list.subList(0, 15);
        try {
            JSONArray arr = new JSONArray();
            for (Bookmark b : list) arr.put(new JSONObject().put("w", b.wordIdx).put("l", b.label));
            prefs(ctx).edit().putString("bm_" + bookId, arr.toString()).apply();
        } catch (Exception ignored) {}
    }
}
