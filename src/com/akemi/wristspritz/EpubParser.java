
    private static ParsedBook parseEpub(Context ctx, File file, String id, int savedWord) throws Exception {
        try (ZipFile zip = new ZipFile(file)) {
            String opfPath = extractAttr(readZipStr(zip, "META-INF/container.xml"), "full-path");
            if (opfPath.isEmpty()) throw new Exception("OPF non trovato");
            String opfDir = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";
            String opfXml = readZipStr(zip, opfPath);

            String title = file.getName().replaceFirst("\\.[^.]+$", ""), author = "Autore", coverId = "", ncxHref = "";
            Map<String, String> mHref = new HashMap<>(), mType = new HashMap<>();
            List<String> spineIds = new ArrayList<>();

            XmlPullParser xpp = XmlPullParserFactory.newInstance().newPullParser();
            xpp.setInput(new StringReader(opfXml));
            int ev = xpp.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String tag = xpp.getName() != null ? xpp.getName().toLowerCase() : "";
                    if (tag.endsWith("title")) { String t = safeText(xpp).trim(); if (!t.isEmpty()) title = t; }
                    else if (tag.endsWith("creator")) { String a = safeText(xpp).trim(); if (!a.isEmpty()) author = a; }
                    else if ("meta".equals(tag) && "cover".equalsIgnoreCase(xpp.getAttributeValue(null, "name"))) {
                        coverId = xpp.getAttributeValue(null, "content");
                    } else if ("item".equals(tag)) {
                        String iid = xpp.getAttributeValue(null, "id"), href = xpp.getAttributeValue(null, "href");
                        String mt = xpp.getAttributeValue(null, "media-type"), pr = xpp.getAttributeValue(null, "properties");
                        if (iid != null && href != null) {
                            mHref.put(iid, href);
                            mType.put(iid, mt != null ? mt : "");
                            if (pr != null && pr.contains("cover-image")) coverId = iid;
                            if ("application/x-dtbncx+xml".equals(mt)) ncxHref = href;
                        }
                    } else if ("itemref".equals(tag)) {
                        String idref = xpp.getAttributeValue(null, "idref");
                        if (idref != null) spineIds.add(idref);
                    }
                }
                ev = xpp.next();
            }

            String coverPath = extractCover(ctx, zip, opfDir, coverId, mHref, mType, id);
            Map<String, String> ncxTitles = parseNcxTitles(zip, opfDir, ncxHref);
            List<String> words = new ArrayList<>();
            List<BookData.Chapter> chapters = new ArrayList<>();

            for (String sid : spineIds) {
                String href = mHref.get(sid);
                if (href == null) continue;
                String html = readZipStr(zip, resolveZipPath(opfDir, href));
                if (html.isEmpty()) continue;
                String clean = html.replaceAll("(?is)<head.*?</head>|<script.*?</script>|<style.*?</style>", " ");
                String plain = Html.fromHtml(clean.replaceAll("(?is)<[^>]+>", " "), Html.FROM_HTML_MODE_LEGACY)
                        .toString().replaceAll("\\s+", " ").trim();
                if (plain.isEmpty()) continue;

                int start = words.size();
                for (String w : plain.split("\\s+")) if (!w.isEmpty()) words.add(w);
                if (words.size() > start) {
                    String chTitle = ncxTitles.get(href);
                    if (chTitle == null || chTitle.isEmpty()) {
                        Matcher hm = Pattern.compile("(?is)<h[1-3][^>]*>(.*?)</h[1-3]>").matcher(clean);
                        if (hm.find()) chTitle = Html.fromHtml(hm.group(1).replaceAll("(?is)<[^>]+>", " "), Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\s+", " ").trim();
                    }
                    if (chTitle == null || chTitle.isEmpty()) chTitle = "Capitolo " + (chapters.size() + 1);
                    chapters.add(new BookData.Chapter(chTitle, start, words.size() - 1));
                }
            }
            if (chapters.isEmpty()) chapters.add(new BookData.Chapter("Libro", 0, Math.max(0, words.size() - 1)));

            BookData.BookMeta meta = new BookData.BookMeta();
            meta.id = id; meta.title = title; meta.author = author;
            meta.filePath = file.getAbsolutePath(); meta.coverPath = coverPath;
            meta.totalWords = words.size();
            meta.currentWord = Math.min(savedWord, Math.max(0, words.size() - 1));
            meta.chapterCount = chapters.size();

            ParsedBook pb = new ParsedBook();
            pb.meta = meta; pb.words = words.toArray(new String[0]); pb.chapters = chapters;
            return pb;
        }
    }

    private static Map<String, String> parseNcxTitles(ZipFile zip, String opfDir, String ncxHref) {
        Map<String, String> map = new HashMap<>();
        if (ncxHref == null || ncxHref.isEmpty()) return map;
        try {
            XmlPullParser xpp = XmlPullParserFactory.newInstance().newPullParser();
            xpp.setInput(new StringReader(readZipStr(zip, resolveZipPath(opfDir, ncxHref))));
            String last = "";
            int ev = xpp.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String t = xpp.getName() != null ? xpp.getName().toLowerCase() : "";
                    if (t.endsWith("text")) last = safeText(xpp).trim();
                    else if (t.endsWith("content")) {
                        String src = xpp.getAttributeValue(null, "src");
                        if (src != null && !last.isEmpty()) {
                            String base = src.contains("#") ? src.substring(0, src.indexOf('#')) : src;
                            if (!map.containsKey(base)) map.put(base, last);
                        }
                    }
                }
                ev = xpp.next();
            }
        } catch (Exception ignored) {}
        return map;
    }

    private static String extractCover(Context ctx, ZipFile zip, String opfDir, String coverId,
                                       Map<String, String> hrefs, Map<String, String> types, String bookId) {
        try {
            String href = (coverId != null && !coverId.isEmpty()) ? hrefs.get(coverId) : null;
            if (href == null) {
                for (Map.Entry<String, String> e : hrefs.entrySet()) {
                    String tp = types.get(e.getKey());
                    if (tp != null && tp.startsWith("image/") && e.getValue().toLowerCase().contains("cover")) { href = e.getValue(); break; }
                }
            }
            if (href == null) return "";
            ZipEntry ze = zip.getEntry(resolveZipPath(opfDir, href));
            if (ze == null) return "";
            byte[] bytes = readAll(zip.getInputStream(ze));
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true; BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            int s = 1; while (o.outWidth / s > 180) s *= 2;
            o.inJustDecodeBounds = false; o.inSampleSize = s;
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            if (bmp == null) return "";
            File dir = new File(ctx.getFilesDir(), "covers");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, bookId + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(out)) { bmp.compress(Bitmap.CompressFormat.JPEG, 80, fos); }
            return out.getAbsolutePath();
        } catch (Exception ignored) { return ""; }
    }

    private static String resolveZipPath(String baseDir, String rel) {
        String full = baseDir + rel;
        while (full.contains("/./")) full = full.replace("/./", "/");
        return full.replaceFirst("^/", "");
    }

    private static String readZipStr(ZipFile zip, String path) {
        try {
            ZipEntry ze = zip.getEntry(path);
            return ze == null ? "" : new String(readAll(zip.getInputStream(ze)), StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }

    private static byte[] readAll(InputStream is) throws Exception {
        try (InputStream in = is; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static String extractAttr(String xml, String attr) {
        Matcher m = Pattern.compile(attr + "\\s*=\\s*[\"']([^\"']+)[\"']").matcher(xml);
        return m.find() ? m.group(1) : "";
    }

    private static String safeText(XmlPullParser xpp) {
        try { return xpp.nextText(); } catch (Exception e) { return ""; }
    }
}
