package com.coordcast.castcore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns whatever the user types or pastes into a latitude and a longitude.
 *
 * <p>Three ideas hold the whole thing together:</p>
 *
 * <ol>
 *   <li><b>A number run is parsed as a whole.</b> Digits joined by {@code . - /} are one
 *       unit, never two numbers. That is what makes {@code 31.13.49.4} mean
 *       31°13'49.4" instead of "31.13 and 49.4" — the second reading is what used to
 *       push half of the input into the other field.</li>
 *   <li><b>A newline is a real boundary.</b> Degrees, minutes and seconds must sit on
 *       one line; a line break is never silently treated as a separator inside a
 *       single angle.</li>
 *   <li><b>Latitude and longitude are decided from the whole text</b>, not value by
 *       value: hemisphere letters and labels first, then which of the two possible
 *       assignments puts the pair somewhere plausible (China first), and only then the
 *       default "first value is the latitude".</li>
 * </ol>
 *
 * <pre>
 *   31.2304, 121.4737                        decimal, comma
 *   31.2304 121.4737                         decimal, space
 *   31.2304\n121.4737                        decimal, one per line
 *   纬度 31.2304 经度 121.4737                 Chinese labels
 *   我的位置：lat 31.2304 lng 121.4737         arbitrary prefix
 *   N31.2304 E121.4737                       leading hemisphere
 *   31.2304N,121.4737E                       trailing hemisphere
 *   N31°13'49.4" E121°28'25.3"               degrees, minutes, seconds
 *   31.13.49.4  121.28.25.3                  DMS written with dots
 *   31-13-49.4N 121-28-25.3E                 DMS written with dashes
 *   北纬31度13分49.4秒 东经121度28分25.3秒       Chinese units
 *   31 13 49.4 N  121 28 25.3 E              bare DMS, space separated
 *   31°13.823'N 121°28.42'E                  degrees and decimal minutes
 *   311349.4N 1212825.3E                     compact DDMMSS.ss
 * </pre>
 *
 * <p>Runs on the desktop JVM (no Android types), so it is covered by unit tests.</p>
 */
public final class CoordText {

    /** Why {@link Scan} did not produce a complete, valid pair. */
    public enum Issue {
        /** Nothing that looks like a coordinate. */
        EMPTY,
        /** Only one of the two values was found. */
        PARTIAL,
        /** Minutes or seconds outside 0–60. */
        MINUTES,
        /** A number outside the valid latitude/longitude range. */
        RANGE
    }

    /** Everything the UI needs to fill two fields and explain itself. */
    public static final class Scan {
        public Double latitude;
        public Double longitude;
        /** The text that produced each value, for the "已识别" line. */
        public String latRaw = "";
        public String lngRaw = "";
        /** True when a hemisphere letter or a label decided which is which. */
        public boolean latFromClue;
        public boolean lngFromClue;
        public Issue issue = Issue.EMPTY;

        public boolean hasLat() {
            return latitude != null;
        }

        public boolean hasLng() {
            return longitude != null;
        }

        public boolean complete() {
            return latitude != null && longitude != null;
        }
    }

    // ------------------------------------------------------------- tokenizer

    private static final int T_NUM = 0;
    private static final int T_DEG = 1;
    private static final int T_MIN = 2;
    private static final int T_SEC = 3;
    private static final int T_HEMI = 4;
    private static final int T_LAT = 5;
    private static final int T_LNG = 6;
    private static final int T_GAP = 7;
    private static final int T_OTHER = 8;

    /** Characters that may join the parts of one angle inside a single number run. */
    private static final String RUN_SEPARATORS = "[.\\-/:]";

    /** {@code DDMMSS.ss} / {@code DDDMMSS.ss} glued together, followed by a hemisphere. */
    private static final Pattern COMPACT_DMS =
            Pattern.compile("(\\d{5,7})(?:\\.(\\d+))?\\s*([NSEWnsew])");

    private static final class Tok {
        int type;
        double value;
        final String text;
        final int start;
        final int end;
        int line;
        char hemi;
        char prefixHemi;

        Tok(int type, String text, int start, int end, int line) {
            this.type = type;
            this.text = text;
            this.start = start;
            this.end = end;
            this.line = line;
        }
    }

    /** A recognised angle, before it is decided whether it is a latitude or a longitude. */
    private static final class Cand {
        double value;
        int start;
        int end;
        int headIndex;
        char hemi;
        boolean badMinutes;
        /** True when the text carried a decimal point — coordinates usually do. */
        boolean decimal;
        int kind; // 0 unknown, 1 latitude, 2 longitude
    }

    private CoordText() {
    }

    // ------------------------------------------------------------------ entry

    /** Scans free text; never throws, always returns a result. */
    public static Scan scan(String text) {
        Scan scan = new Scan();
        if (text == null || text.trim().isEmpty()) {
            return scan;
        }
        String normalised = expandCompactDms(normalize(text));
        List<Tok> tokens = tokenize(normalised);
        attachPrefixHemispheres(tokens);
        List<Cand> cands = candidates(tokens, normalised);
        assignLabels(tokens, cands);

        boolean badMinutes = false;
        boolean sawImplausible = false;
        List<Cand> usable = new ArrayList<>();
        List<Cand> latClues = new ArrayList<>();
        List<Cand> lngClues = new ArrayList<>();
        for (Cand c : cands) {
            badMinutes |= c.badMinutes;
            if (Math.abs(c.value) > 180) {
                // Not a coordinate at all: a house number, a year, a phone number.
                sawImplausible = true;
                continue;
            }
            usable.add(c);
            if (c.kind == 1) {
                latClues.add(c);
            } else if (c.kind == 2) {
                lngClues.add(c);
            }
        }

        Cand lat = latClues.isEmpty() ? null : latClues.get(0);
        Cand lng = lngClues.isEmpty() ? null : lngClues.get(0);

        if (lat == null && lng == null) {
            // Nothing is labelled, so the text is read as a whole: every ordered pair is
            // scored and the reading that best explains all of it wins. Deciding number
            // by number is what used to promote a stray "3号" to a latitude and leave the
            // real coordinate sitting in the other slot.
            int best = Integer.MIN_VALUE;
            for (int i = 0; i < usable.size(); i++) {
                Cand a = usable.get(i);
                if (Math.abs(a.value) > 90) {
                    continue; // cannot be a latitude
                }
                for (int j = 0; j < usable.size(); j++) {
                    if (i == j) {
                        continue;
                    }
                    Cand b = usable.get(j);
                    int score = latEvidence(a) + lngEvidence(b);
                    if (Math.abs(i - j) == 1) {
                        score += ADJACENT_BONUS; // the two halves of one coordinate
                    }
                    if (score > best) {
                        best = score;
                        lat = a;
                        lng = b;
                    }
                }
            }
            if (lat == null && lng == null && usable.size() == 1) {
                Cand only = usable.get(0);
                if (Math.abs(only.value) > 90) {
                    lng = only;
                } else {
                    lat = only;
                }
            }
        } else if (lat == null) {
            lat = complete(usable, lng, true);
        } else if (lng == null) {
            lng = complete(usable, lat, false);
        }

        if (lat != null) {
            scan.latitude = lat.value;
            scan.latRaw = slice(normalised, lat);
            scan.latFromClue = lat.kind == 1;
        }
        if (lng != null) {
            scan.longitude = lng.value;
            scan.lngRaw = slice(normalised, lng);
            scan.lngFromClue = lng.kind == 2;
        }

        if (badMinutes) {
            scan.issue = Issue.MINUTES;
        } else if (lat != null && Math.abs(lat.value) > 90) {
            scan.issue = Issue.RANGE;
        } else if (scan.complete()) {
            scan.issue = null;
        } else if (scan.hasLat() || scan.hasLng()) {
            scan.issue = Issue.PARTIAL;
        } else if (sawImplausible || !usable.isEmpty()) {
            // Numbers were found but no reading of them makes a valid pair.
            scan.issue = Issue.RANGE;
        } else {
            scan.issue = Issue.EMPTY;
        }
        return scan;
    }

    private static String slice(String text, Cand c) {
        int from = Math.max(0, Math.min(c.start, text.length()));
        int to = Math.max(from, Math.min(c.end, text.length()));
        return text.substring(from, to).trim();
    }

    // -------------------------------------------------------------- covering

    /** Roughly mainland China; used only to break ties between two readings. */
    private static final double CN_LAT_MIN = 3.86;
    private static final double CN_LAT_MAX = 53.55;
    private static final double CN_LNG_MIN = 73.66;
    private static final double CN_LNG_MAX = 135.05;

    /** Two adjacent values are far more likely to be one coordinate than two distant ones. */
    private static final int ADJACENT_BONUS = 15;

    private static boolean inChina(double lat, double lng) {
        return lat >= CN_LAT_MIN && lat <= CN_LAT_MAX
                && lng >= CN_LNG_MIN && lng <= CN_LNG_MAX;
    }

    /** How much a value looks like the latitude of the pair. */
    private static int latEvidence(Cand c) {
        int score = 0;
        if (c.kind == 1) {
            score += 100; // a N/S hemisphere or a 纬度 label said so
        }
        if (Math.abs(c.value) <= 90) {
            score += 10;
        }
        if (c.value >= CN_LAT_MIN && c.value <= CN_LAT_MAX) {
            score += 6;
        }
        if (c.decimal) {
            score += 4;
        }
        return score;
    }

    /** How much a value looks like the longitude of the pair. */
    private static int lngEvidence(Cand c) {
        int score = 0;
        if (c.kind == 2) {
            score += 100; // an E/W hemisphere or a 经度 label said so
        }
        if (Math.abs(c.value) > 90) {
            score += 40; // only a longitude can be out here
        }
        if (c.value >= CN_LNG_MIN && c.value <= CN_LNG_MAX) {
            score += 6;
        }
        if (c.decimal) {
            score += 4;
        }
        return score;
    }

    /**
     * Completes a half-read pair, but only with a value that carries evidence of its own.
     * Without that guard a stray number — a house number, or the degrees left behind by a
     * paste whose lines do not belong together — would be pressed into service and turn a
     * partial reading into a confident wrong one.
     */
    private static Cand complete(List<Cand> cands, Cand known, boolean wantLatitude) {
        for (Cand c : cands) {
            if (c == known) {
                continue;
            }
            if (wantLatitude) {
                if (Math.abs(c.value) <= 90) {
                    return c;
                }
            } else if (Math.abs(c.value) > 90 || inChina(known.value, c.value)) {
                return c;
            }
        }
        return null;
    }

    // ----------------------------------------------------------- normalisation

    /** Folds every spelling of the same idea onto one canonical character. */
    public static String normalize(String s) {
        String t = s;
        // Chinese direction words first, so 度 inside them is not touched.
        t = t.replace("北纬", "N").replace("南纬", "S")
                .replace("东经", "E").replace("西经", "W");
        t = t.replace("纬度", "LAT").replace("经度", "LNG");
        // Full-width punctuation and signs.
        t = t.replace('，', ',').replace('。', '.').replace('：', ':').replace('；', ';')
                .replace('（', '(').replace('）', ')').replace('　', ' ')
                .replace('－', '-').replace('＋', '+').replace('＝', '=')
                .replace('．', '.').replace('＇', '\'').replace('＂', '"')
                .replace('／', '/').replace('、', ',');
        // Prime / quote / degree look-alikes.
        t = t.replace('′', '\'').replace('’', '\'').replace('‘', '\'').replace('`', '\'')
                .replace('″', '"').replace('“', '"').replace('”', '"')
                .replace('º', '°').replace('˚', '°').replace('〇', '0');
        // Unit words.
        t = t.replace("度", "°").replace("分", "'").replace("秒", "\"");
        // Latin labels, longest first.
        t = replaceIgnoreCase(t, "latitude", "LAT");
        t = replaceIgnoreCase(t, "longitude", "LNG");
        t = replaceIgnoreCase(t, "lng", "LNG");
        t = replaceIgnoreCase(t, "lon", "LNG");
        t = replaceIgnoreCase(t, "lat", "LAT");
        return t;
    }

    private static String replaceIgnoreCase(String s, String find, String replacement) {
        return Pattern.compile(Pattern.quote(find), Pattern.CASE_INSENSITIVE)
                .matcher(s).replaceAll(Matcher.quoteReplacement(replacement));
    }

    /**
     * Rewrites glued {@code DDMMSS.ss} forms into explicit DMS. Only fires when the
     * digits cannot be a decimal degree value (i.e. they exceed 180), so a plain
     * {@code 121.4737E} is never touched.
     */
    public static String expandCompactDms(String s) {
        Matcher m = COMPACT_DMS.matcher(s);
        // StringBuffer, not StringBuilder: the StringBuilder overload of
        // appendReplacement only exists from Java 9, and this compiles for Java 8.
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String digits = m.group(1);
            String frac = m.group(2);
            char hemi = m.group(3).charAt(0);
            String whole = digits + (frac == null ? "" : "." + frac);
            String replacement = m.group();
            if (Double.parseDouble(whole) > 180) {
                int degLen = digits.length() - 4;
                int deg = Integer.parseInt(digits.substring(0, degLen));
                int min = Integer.parseInt(digits.substring(degLen, degLen + 2));
                int sec = Integer.parseInt(digits.substring(degLen + 2));
                if (min < 60 && sec < 60) {
                    replacement = deg + "°" + min + "'" + sec + (frac == null ? "" : "." + frac) + "\"" + hemi;
                }
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    // ------------------------------------------------------------- tokenizing

    /**
     * Hand-rolled scanner rather than one big regex, because a number run has to be
     * consumed as a unit: {@code .} always continues a run (it is either a decimal
     * point or a DMS separator), while {@code -} and {@code /} continue it only before
     * any dot has been seen. That is what keeps {@code 31-13-49.4} as one angle and
     * {@code 31.2304-121.4737} as two.
     */
    private static List<Tok> tokenize(String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0;
        int line = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);

            if (c == '\n') {
                out.add(new Tok(T_GAP, "\n", i, i + 1, line));
                line++;
                i++;
                continue;
            }

            if (isDigit(c)) {
                int start = i;
                StringBuilder run = new StringBuilder();
                boolean hasDot = false;
                while (i < n) {
                    char d = s.charAt(i);
                    if (isDigit(d)) {
                        run.append(d);
                        i++;
                        continue;
                    }
                    if (d == '.' && i + 1 < n && isDigit(s.charAt(i + 1))) {
                        hasDot = true;
                        run.append(d);
                        i++;
                        continue;
                    }
                    if ((d == '-' || d == '/' || d == ':') && !hasDot
                            && i + 1 < n && isDigit(s.charAt(i + 1))) {
                        run.append(d);
                        i++;
                        continue;
                    }
                    break;
                }
                String text = run.toString();
                Double value = interpretRun(text);
                if (value == null) {
                    // The run is not one angle, but it may be two decimal coordinates
                    // that the user joined with the same dot they write degrees with.
                    List<Tok> parts = decimalPair(text, start, line);
                    if (parts != null) {
                        out.addAll(parts);
                        continue;
                    }
                    // Otherwise keep it as inert text so nothing downstream can mistake
                    // part of it for a coordinate.
                    out.add(new Tok(T_OTHER, text, start, i, line));
                    continue;
                }
                Tok tok = new Tok(T_NUM, text, start, i, line);
                tok.value = value;
                out.add(tok);
                continue;
            }

            if (c == '°') {
                out.add(new Tok(T_DEG, "°", i, i + 1, line));
                i++;
                continue;
            }
            if (c == '\'') {
                out.add(new Tok(T_MIN, "'", i, i + 1, line));
                i++;
                continue;
            }
            if (c == '"') {
                out.add(new Tok(T_SEC, "\"", i, i + 1, line));
                i++;
                continue;
            }

            if (c == 'L' && s.startsWith("LAT", i)) {
                out.add(new Tok(T_LAT, "LAT", i, i + 3, line));
                i += 3;
                continue;
            }
            if (c == 'L' && s.startsWith("LNG", i)) {
                out.add(new Tok(T_LNG, "LNG", i, i + 3, line));
                i += 3;
                continue;
            }

            if (c == 'N' || c == 'S' || c == 'E' || c == 'W'
                    || c == 'n' || c == 's' || c == 'e' || c == 'w') {
                // "GPS", "East", "No. 5" — a letter that is part of a word is not a
                // hemisphere marker, only N/S/E/W standing on their own are.
                char before = i > 0 ? s.charAt(i - 1) : 0;
                char after = i + 1 < n ? s.charAt(i + 1) : 0;
                if (isAsciiLetter(before) || isAsciiLetter(after)) {
                    out.add(new Tok(T_OTHER, String.valueOf(c), i, i + 1, line));
                } else {
                    Tok tok = new Tok(T_HEMI, String.valueOf(c), i, i + 1, line);
                    tok.hemi = Character.toUpperCase(c);
                    out.add(tok);
                }
                i++;
                continue;
            }

            if (Character.isWhitespace(c)) {
                int start = i;
                while (i < n && Character.isWhitespace(s.charAt(i)) && s.charAt(i) != '\n') {
                    i++;
                }
                out.add(new Tok(T_GAP, s.substring(start, i), start, i, line));
                continue;
            }

            out.add(new Tok(T_OTHER, String.valueOf(c), i, i + 1, line));
            i++;
        }
        return out;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /**
     * Reads one number run as a single angle.
     *
     * <pre>
     *   31           → 31°            (integer degrees)
     *   31.2304      → 31.2304°       (decimal degrees)
     *   31.13.49     → 31°13'49"      (DMS, dot separated)
     *   31.13.49.4   → 31°13'49.4"    (DMS with a decimal second)
     *   31-13-49.4   → 31°13'49.4"    (DMS, dash separated)
     *   2024-10-07   → rejected       (out of range / bad minutes)
     * </pre>
     *
     * @return the angle in decimal degrees, or null when the run is not a valid angle
     */
    public static Double interpretRun(String run) {
        String[] parts = run.split(RUN_SEPARATORS, -1);
        if (parts.length == 1) {
            return parse(parts[0]);
        }
        if (parts.length == 2) {
            Double whole = parse(parts[0]);
            if (whole == null) {
                return null;
            }
            if (run.indexOf('.') >= 0) {
                // "31.2304" — a dot is the decimal point.
                return Double.parseDouble(parts[0] + "." + parts[1]);
            }
            // A dash, slash or colon between two numbers means minutes, not a fraction:
            // nobody writes 31.2304 as "31-2304".
            Double minutes = parse(parts[1]);
            if (minutes == null || whole > 180 || minutes >= 60) {
                return null;
            }
            return whole + minutes / 60.0;
        }
        if (parts.length == 3 || parts.length == 4) {
            Double deg = parse(parts[0]);
            Double min = parse(parts[1]);
            Double sec = parts.length == 4 ? parse(parts[2] + "." + parts[3]) : parse(parts[2]);
            if (deg == null || min == null || sec == null) {
                return null;
            }
            if (deg > 180 || min >= 60 || sec >= 60) {
                return null;
            }
            return deg + min / 60.0 + sec / 3600.0;
        }
        return null;
    }

    private static Double parse(String digits) {
        try {
            return Double.parseDouble(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** One decimal degree value, used when a rejected run is split back into coordinates. */
    private static final Pattern DECIMAL = Pattern.compile("\\d{1,3}(?:\\.\\d+)?");

    /**
     * Reads a rejected run as two decimal coordinates that were joined by the same
     * character the user writes degrees with: {@code 31.2304.121.4737}.
     *
     * <p>Both halves must carry a fractional part, which is what separates a coordinate
     * pair from a date ({@code 2024.10.07}) or a phone number — those are left alone.</p>
     *
     * @return replacement tokens, or null when the run is not a coordinate pair
     */
    private static List<Tok> decimalPair(String run, int offset, int line) {
        List<Tok> parts = new ArrayList<>();
        Matcher m = DECIMAL.matcher(run);
        while (m.find()) {
            String text = m.group();
            if (text.indexOf('.') < 0) {
                return null;
            }
            double value = Double.parseDouble(text);
            if (value > 180) {
                return null;
            }
            Tok tok = new Tok(T_NUM, text, offset + m.start(), offset + m.end(), line);
            tok.value = value;
            parts.add(tok);
        }
        return parts.size() == 2 ? parts : null;
    }

    /** Skips whitespace but never crosses a line break. */
    private static int skipSpaces(List<Tok> t, int i) {
        while (i < t.size() && t.get(i).type == T_GAP && t.get(i).text.indexOf('\n') < 0) {
            i++;
        }
        return i;
    }

    /**
     * Decides whether a hemisphere letter belongs to the number before it or the one
     * after it.
     *
     * <p>Immediate adjacency always wins: {@code N31.2} is a prefix, {@code 31.2N} a
     * suffix. When the letter stands alone between two numbers the whole string
     * decides: if it opens with a letter ({@code N 31.2 E 121.4}) every letter is
     * read as a prefix, and if it opens with a number ({@code 31.2 N 121.4 E}) every
     * letter is read as a suffix. That keeps the two spellings from being mixed up,
     * which a purely local rule cannot do.</p>
     */
    private static void attachPrefixHemispheres(List<Tok> t) {
        boolean prefixMode = false;
        for (Tok tok : t) {
            if (tok.type == T_HEMI) {
                prefixMode = true;
                break;
            }
            if (tok.type == T_NUM) {
                break;
            }
        }
        for (int i = 0; i < t.size(); i++) {
            Tok tok = t.get(i);
            if (tok.type != T_HEMI) {
                continue;
            }
            boolean touchesPrevious = i > 0 && t.get(i - 1).type == T_NUM;
            boolean touchesNext = i + 1 < t.size() && t.get(i + 1).type == T_NUM;
            if (touchesPrevious) {
                continue; // trailing, e.g. 31.2N
            }
            if (touchesNext || prefixMode) { // leading, e.g. N31.2 or N 31.2
                int target = skipSpaces(t, i + 1);
                if (target < t.size() && t.get(target).type == T_NUM
                        && t.get(target).line == tok.line) {
                    t.get(target).prefixHemi = tok.hemi;
                    tok.type = T_OTHER;
                }
            }
        }
    }

    // --------------------------------------------------------------- grammar

    private static List<Cand> candidates(List<Tok> t, String text) {
        List<Cand> out = new ArrayList<>();
        int n = t.size();
        int i = 0;
        while (i < n) {
            Tok head = t.get(i);
            if (head.type != T_NUM) {
                i++;
                continue;
            }
            int end = i + 1;
            double degrees = head.value;
            double minutes = 0;
            double seconds = 0;
            boolean badMinutes = false;
            /** True once the value has to be assembled from several tokens. */
            boolean composite = false;

            int afterNumber = skipSpaces(t, i + 1);
            boolean hasDegreeSign = afterNumber < n && t.get(afterNumber).type == T_DEG
                    && t.get(afterNumber).line == head.line;

            if (hasDegreeSign) {
                end = afterNumber + 1;
                int k = skipSpaces(t, end);
                if (k < n && t.get(k).type == T_NUM && t.get(k).line == head.line) {
                    int afterM = skipSpaces(t, k + 1);
                    boolean minSign = afterM < n && t.get(afterM).type == T_MIN;
                    boolean secSign = afterM < n && t.get(afterM).type == T_SEC;
                    boolean anotherDegree = afterM < n && t.get(afterM).type == T_DEG;
                    double mv = t.get(k).value;
                    if (secSign && !minSign) {
                        // 31°49.4" — degrees and seconds, the minutes were omitted.
                        seconds = mv;
                        end = afterM + 1;
                        composite = true;
                    } else if (minSign || (!anotherDegree && mv < 60)) {
                        if (mv >= 60) {
                            badMinutes = true;
                        }
                        minutes = mv;
                        end = minSign ? afterM + 1 : k + 1;
                        composite = true;
                        int s = skipSpaces(t, end);
                        if (s < n && t.get(s).type == T_NUM) {
                            int afterS = skipSpaces(t, s + 1);
                            boolean secSymbol = afterS < n && t.get(afterS).type == T_SEC;
                            boolean degreeAfter = afterS < n && t.get(afterS).type == T_DEG;
                            double sv = t.get(s).value;
                            if ((secSymbol || (!degreeAfter && sv < 60)) && sv < 60) {
                                seconds = sv;
                                end = secSymbol ? afterS + 1 : s + 1;
                            }
                        }
                    }
                }
            } else if (isPureInteger(head.text)) {
                // No symbols at all, all on one line: 31 13 49.4 [N]
                int a = skipSpaces(t, i + 1);
                if (a < n && t.get(a).type == T_NUM && t.get(a).line == head.line
                        && isPureInteger(t.get(a).text) && t.get(a).value < 60
                        && degrees <= 180) {
                    int b = skipSpaces(t, a + 1);
                    if (b < n && t.get(b).type == T_NUM && t.get(b).line == head.line
                            && t.get(b).value < 60) {
                        minutes = t.get(a).value;
                        seconds = t.get(b).value;
                        end = b + 1;
                        composite = true;
                    }
                }
                if (!composite) {
                    // Degrees and decimal minutes, e.g. "31 13.5 N": only trusted when
                    // a hemisphere proves the two numbers describe one angle.
                    int a2 = skipSpaces(t, i + 1);
                    if (a2 < n && t.get(a2).type == T_NUM && t.get(a2).line == head.line
                            && t.get(a2).value < 60 && degrees <= 180) {
                        int afterA = skipSpaces(t, a2 + 1);
                        if (afterA < n && t.get(afterA).type == T_HEMI
                                && t.get(afterA).line == head.line) {
                            minutes = t.get(a2).value;
                            end = a2 + 1;
                            composite = true;
                        }
                    }
                }
            }

            double value = composite ? degrees + minutes / 60.0 + seconds / 3600.0 : degrees;

            char hemi = head.prefixHemi;
            int h = skipSpaces(t, end);
            if (h < n && t.get(h).type == T_HEMI && t.get(h).line == head.line) {
                hemi = t.get(h).hemi;
                end = h + 1;
            }

            // A leading minus/plus sign.
            int before = i - 1;
            boolean negated = before >= 0 && t.get(before).type == T_OTHER
                    && ("-".equals(t.get(before).text) || "−".equals(t.get(before).text));
            if (negated) {
                value = -value;
            }
            if (hemi == 'S' || hemi == 'W') {
                value = -Math.abs(value);
            } else if (hemi == 'N' || hemi == 'E') {
                value = Math.abs(value);
            }

            Cand c = new Cand();
            c.value = value;
            c.start = t.get(negated ? before : i).start;
            c.end = t.get(Math.min(end, n) - 1).end;
            c.headIndex = i;
            c.hemi = hemi;
            c.badMinutes = badMinutes;
            c.decimal = head.text.indexOf('.') >= 0;
            if (hemi == 'N' || hemi == 'S') {
                c.kind = 1;
            } else if (hemi == 'E' || hemi == 'W') {
                c.kind = 2;
            }
            out.add(c);
            i = Math.max(end, i + 1);
        }
        return out;
    }

    private static boolean isPureInteger(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Binds every {@code LAT}/{@code LNG} label to the nearest number that no other
     * label has claimed yet, preferring the number on the label's left when the
     * distances tie.
     *
     * <p>Claiming (rather than each number independently looking around) is what makes
     * both {@code 31.2304 纬度 121.4737 经度} and {@code 纬度 31.2304 经度 121.4737}
     * come out right, since the second label can no longer steal the first number.</p>
     */
    private static void assignLabels(List<Tok> t, List<Cand> cands) {
        java.util.Set<Integer> claimed = new java.util.HashSet<>();
        for (int li = 0; li < t.size(); li++) {
            int labelType = t.get(li).type;
            if (labelType != T_LAT && labelType != T_LNG) {
                continue;
            }
            Cand best = null;
            int bestCost = Integer.MAX_VALUE;
            for (Cand c : cands) {
                if (claimed.contains(c.headIndex)) {
                    continue;
                }
                int cost = c.headIndex >= li
                        ? (c.headIndex - li) * 2 + 1 // to the right: +1 so left wins ties
                        : (li - c.headIndex) * 2;    // to the left
                if (cost < bestCost) {
                    bestCost = cost;
                    best = c;
                }
            }
            if (best != null && bestCost <= 12) { // within ~6 tokens
                claimed.add(best.headIndex);
                best.kind = labelType == T_LAT ? 1 : 2;
            }
        }
    }

    // ------------------------------------------------------------------ dms

    /** Formats decimal degrees as {@code 31°13'49.4"N} (used for the "已识别" hint). */
    public static String toDms(double degrees, boolean latitude) {
        char hemi = latitude ? (degrees < 0 ? 'S' : 'N') : (degrees < 0 ? 'W' : 'E');
        double abs = Math.abs(degrees);
        int d = (int) Math.floor(abs);
        double minutesFull = (abs - d) * 60.0;
        int m = (int) Math.floor(minutesFull);
        double s = (minutesFull - m) * 60.0;
        return String.format(Locale.US, "%d°%02d'%04.1f\"%c", d, m, s, hemi);
    }
}
