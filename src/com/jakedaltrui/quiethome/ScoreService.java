package com.jakedaltrui.quiethome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class ScoreService {
    interface Listener {
        void onScores(String tickerText, boolean fresh);
    }

    private static final String TAG = "QuietHomeScores";
    private static final String PREFS = "score_ticker";
    private static final String CACHE_TEXT = "text";
    private static final String CACHE_LIVE = "favorite_live";
    private static final String CACHE_NEXT = "favorite_next";
    private static final String CACHE_NEXT_TIME = "favorite_next_time";
    private static final String CACHE_ALERT = "score_alert";
    private static final String CACHE_ALERT_ID = "score_alert_id";
    private static final String CACHE_SCORE_STATE = "favorite_score_state";
    private static final String MLB_URL =
            "https://statsapi.mlb.com/api/v1/schedule?sportId=1&hydrate=linescore,team&startDate=%s&endDate=%s";
    private static final String NFL_URL =
            "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard?limit=100";
    private static final String CFB_URL =
            "https://site.api.espn.com/apis/site/v2/sports/football/college-football/scoreboard?limit=200&groups=80";
    private static final String CBASE_URL =
            "https://site.api.espn.com/apis/site/v2/sports/baseball/college-baseball/scoreboard?limit=200";

    private final Context context;
    private final Handler mainHandler;
    private volatile boolean running;

    ScoreService(Context context, Handler mainHandler) {
        this.context = context.getApplicationContext();
        this.mainHandler = mainHandler;
    }

    String cachedText() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(CACHE_TEXT, "");
    }

    String favoriteLive() {
        return preferences().getString(CACHE_LIVE, "");
    }

    String favoriteNext() {
        return preferences().getString(CACHE_NEXT, "");
    }

    long favoriteNextTime() {
        return preferences().getLong(CACHE_NEXT_TIME, 0L);
    }

    String scoreAlert() {
        return preferences().getString(CACHE_ALERT, "");
    }

    String scoreAlertId() {
        return preferences().getString(CACHE_ALERT_ID, "");
    }

    private SharedPreferences preferences() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    void refresh(final Listener listener) {
        if (running) return;
        running = true;
        new Thread(new Runnable() {
            @Override public void run() {
                final ArrayList<Score> scores = new ArrayList<Score>();
                int successfulFeeds = 0;
                try {
                    parseMlb(readJson(mlbUrl()), scores);
                    successfulFeeds++;
                } catch (Exception e) {
                    Log.w(TAG, "MLB feed unavailable", e);
                }
                try {
                    parseEspn(readJson(NFL_URL), "NFL", scores);
                    successfulFeeds++;
                } catch (Exception e) {
                    Log.w(TAG, "NFL feed unavailable", e);
                }
                try {
                    parseEspn(readJson(CFB_URL), "NCAA FB", scores);
                    successfulFeeds++;
                } catch (Exception e) {
                    Log.w(TAG, "College football feed unavailable", e);
                }
                try {
                    parseEspn(readJson(CBASE_URL), "NCAA BSB", scores);
                    successfulFeeds++;
                } catch (Exception e) {
                    Log.w(TAG, "College baseball feed unavailable", e);
                }

                Collections.sort(scores, new Comparator<Score>() {
                    @Override public int compare(Score a, Score b) {
                        if (a.priority != b.priority) return b.priority - a.priority;
                        return Long.compare(a.startTime, b.startTime);
                    }
                });

                final boolean fresh = successfulFeeds > 0;
                final String text = fresh ? buildTicker(scores) : cachedText();
                if (fresh) {
                    updateFavoriteMetadata(scores);
                    preferences().edit().putString(CACHE_TEXT, text).apply();
                    Log.i(TAG, text);
                }
                running = false;
                mainHandler.post(new Runnable() {
                    @Override public void run() {
                        listener.onScores(text, fresh);
                    }
                });
            }
        }, "quiet-home-scores").start();
    }

    private String mlbUrl() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String start = format.format(calendar.getTime());
        return String.format(Locale.US, MLB_URL, start, start);
    }

    private JSONObject readJson(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(7000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "QuietHome/1.0 (personal TV scoreboard)");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new Exception("HTTP " + status);
            BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            try {
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 2 * 1024 * 1024) {
                        throw new java.io.IOException("Feed exceeds 2 MiB limit");
                    }
                    output.write(buffer, 0, count);
                }
            } finally {
                input.close();
            }
            return new JSONObject(output.toString("UTF-8"));
        } finally {
            connection.disconnect();
        }
    }

    private void parseMlb(JSONObject root, List<Score> scores) {
        JSONArray dates = root.optJSONArray("dates");
        if (dates == null) return;
        for (int d = 0; d < dates.length(); d++) {
            JSONArray games = dates.optJSONObject(d).optJSONArray("games");
            if (games == null) continue;
            for (int i = 0; i < games.length(); i++) {
                JSONObject game = games.optJSONObject(i);
                JSONObject teams = game.optJSONObject("teams");
                if (teams == null) continue;
                JSONObject away = teams.optJSONObject("away");
                JSONObject home = teams.optJSONObject("home");
                if (away == null || home == null) continue;
                JSONObject awayTeam = away.optJSONObject("team");
                JSONObject homeTeam = home.optJSONObject("team");
                int awayId = awayTeam == null ? -1 : awayTeam.optInt("id", -1);
                int homeId = homeTeam == null ? -1 : homeTeam.optInt("id", -1);
                boolean favorite = isFavoriteMlb(awayId) || isFavoriteMlb(homeId);
                JSONObject status = game.optJSONObject("status");
                String state = status == null ? "" : status.optString("abstractGameState", "");
                boolean live = "Live".equalsIgnoreCase(state);

                String awayAbbr = mlbAbbreviation(awayId,
                        awayTeam == null ? "AWAY" : awayTeam.optString("name", "AWAY"));
                String homeAbbr = mlbAbbreviation(homeId,
                        homeTeam == null ? "HOME" : homeTeam.optString("name", "HOME"));
                String detail;
                if ("Preview".equalsIgnoreCase(state)) {
                    detail = formatStart(game.optString("gameDate", ""));
                } else {
                    String phase = status == null ? state : status.optString("detailedState", state);
                    JSONObject linescore = game.optJSONObject("linescore");
                    if (live && linescore != null) {
                        String inning = linescore.optString("currentInningOrdinal", "");
                        String half = linescore.optString("inningHalf", "");
                        phase = (half.length() > 0 ? half.substring(0, 1).toUpperCase(Locale.US) : "")
                                + inning;
                    }
                    detail = phase.toUpperCase(Locale.US);
                }
                String text = awayAbbr + " " + away.optInt("score", 0)
                        + " - " + homeAbbr + " " + home.optInt("score", 0) + "  " + detail;
                if ("Preview".equalsIgnoreCase(state)) {
                    text = awayAbbr + " @ " + homeAbbr + "  " + detail;
                }
                scores.add(new Score("MLB", text,
                        favorite ? (live ? 130 : 110) : (live ? 80 : 40),
                        parseTime(game.optString("gameDate", "")), favorite, live,
                        "MLB-" + game.optLong("gamePk", 0L),
                        away.optInt("score", 0) + home.optInt("score", 0)));
            }
        }
    }

    private void parseEspn(JSONObject root, String league, List<Score> scores) {
        JSONArray events = root.optJSONArray("events");
        if (events == null) return;
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            JSONArray competitions = event == null ? null : event.optJSONArray("competitions");
            JSONObject competition = competitions == null ? null : competitions.optJSONObject(0);
            JSONArray competitors = competition == null ? null : competition.optJSONArray("competitors");
            if (competitors == null || competitors.length() < 2) continue;
            JSONObject away = null;
            JSONObject home = null;
            for (int c = 0; c < competitors.length(); c++) {
                JSONObject competitor = competitors.optJSONObject(c);
                if (competitor == null) continue;
                if ("away".equals(competitor.optString("homeAway"))) away = competitor;
                if ("home".equals(competitor.optString("homeAway"))) home = competitor;
            }
            if (away == null || home == null) continue;
            JSONObject awayTeam = away.optJSONObject("team");
            JSONObject homeTeam = home.optJSONObject("team");
            boolean favorite = isFavoriteEspn(league, awayTeam) || isFavoriteEspn(league, homeTeam);
            JSONObject status = competition.optJSONObject("status");
            if (status == null) status = event.optJSONObject("status");
            JSONObject type = status == null ? null : status.optJSONObject("type");
            String state = type == null ? "pre" : type.optString("state", "pre");
            boolean live = "in".equals(state);
            long startTime = parseTime(event.optString("date", ""));
            // ESPN returns the last championship date during baseball's offseason.
            if ("NCAA BSB".equals(league)) {
                int month = Calendar.getInstance().get(Calendar.MONTH);
                boolean inSeason = month >= Calendar.FEBRUARY && month <= Calendar.JUNE;
                if (!inSeason || (startTime > 0
                        && startTime < System.currentTimeMillis() - 48L * 60L * 60L * 1000L)) continue;
            }

            String awayAbbr = displayTeam(league, awayTeam);
            String homeAbbr = displayTeam(league, homeTeam);
            String detail;
            if ("pre".equals(state)) {
                detail = formatStart(event.optString("date", ""));
                String network = network(competition);
                if (network.length() > 0) detail += " / " + network;
            } else {
                detail = type == null ? state : type.optString("shortDetail", state);
                detail = detail.toUpperCase(Locale.US);
            }
            String text;
            if ("pre".equals(state)) {
                text = awayAbbr + " @ " + homeAbbr + "  " + detail;
            } else {
                text = awayAbbr + " " + away.optString("score", "0")
                        + " - " + homeAbbr + " " + home.optString("score", "0")
                        + "  " + detail;
            }
            int priority = favorite ? (live ? 140 : 110) : (live ? 80 : 40);
            scores.add(new Score(league, text, priority, startTime, favorite, live,
                    league + "-" + event.optString("id", String.valueOf(startTime)),
                    parseScore(away.optString("score", "0"))
                            + parseScore(home.optString("score", "0"))));
        }
    }

    private int parseScore(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean isFavoriteMlb(int id) {
        return id == 147 || id == 115; // Yankees, Rockies
    }

    private String mlbAbbreviation(int id, String fallback) {
        switch (id) {
            case 108: return "LAA";
            case 109: return "ARI";
            case 110: return "BAL";
            case 111: return "BOS";
            case 112: return "CHC";
            case 113: return "CIN";
            case 114: return "CLE";
            case 115: return "COL";
            case 116: return "DET";
            case 117: return "HOU";
            case 118: return "KC";
            case 119: return "LAD";
            case 120: return "WSH";
            case 121: return "NYM";
            case 133: return "ATH";
            case 134: return "PIT";
            case 135: return "SD";
            case 136: return "SEA";
            case 137: return "SF";
            case 138: return "STL";
            case 139: return "TB";
            case 140: return "TEX";
            case 141: return "TOR";
            case 142: return "MIN";
            case 143: return "PHI";
            case 144: return "ATL";
            case 145: return "CWS";
            case 146: return "MIA";
            case 147: return "NYY";
            case 158: return "MIL";
            default: break;
        }
        String[] words = fallback.toUpperCase(Locale.US).split(" ");
        String last = words.length == 0 ? fallback : words[words.length - 1];
        return last.length() <= 4 ? last : last.substring(0, 3);
    }

    private boolean isFavoriteEspn(String league, JSONObject team) {
        if (team == null) return false;
        String id = team.optString("id", "");
        String abbreviation = team.optString("abbreviation", "").toUpperCase(Locale.US);
        String display = team.optString("displayName", "").toLowerCase(Locale.US);
        if ("NFL".equals(league)) return "NYG".equals(abbreviation) || "DEN".equals(abbreviation);
        if ("NCAA FB".equals(league)) return "99".equals(id) || "LSU".equals(abbreviation);
        return "UNO".equals(abbreviation) || "LSUNO".equals(abbreviation)
                || display.contains("new orleans privateers")
                || display.contains("lsu new orleans");
    }

    private String displayTeam(String league, JSONObject team) {
        if (team == null) return "TBD";
        if ("NCAA BSB".equals(league) && isFavoriteEspn(league, team)) return "LSU NOLA";
        String abbreviation = team.optString("abbreviation", "").toUpperCase(Locale.US);
        if (abbreviation.length() > 0) return abbreviation;
        String name = team.optString("shortDisplayName", team.optString("name", "TBD"));
        return name.length() <= 8 ? name.toUpperCase(Locale.US)
                : name.substring(0, 8).toUpperCase(Locale.US);
    }

    private boolean isBigGame(JSONObject competition, JSONObject away, JSONObject home) {
        int awayRank = rank(away);
        int homeRank = rank(home);
        if ((awayRank > 0 && awayRank <= 25) || (homeRank > 0 && homeRank <= 25)) return true;
        JSONArray notes = competition.optJSONArray("notes");
        if (notes != null) {
            for (int i = 0; i < notes.length(); i++) {
                String headline = notes.optJSONObject(i).optString("headline", "").toLowerCase(Locale.US);
                if (headline.contains("playoff") || headline.contains("championship")
                        || headline.contains("bowl")) return true;
            }
        }
        String network = network(competition).toUpperCase(Locale.US);
        return network.contains("ESPN") || network.contains("ABC") || network.contains("NBC")
                || network.contains("CBS") || network.contains("FOX") || network.contains("NFL NET");
    }

    private int rank(JSONObject competitor) {
        JSONObject rank = competitor.optJSONObject("curatedRank");
        return rank == null ? 99 : rank.optInt("current", 99);
    }

    private String network(JSONObject competition) {
        String direct = competition.optString("broadcast", "");
        if (direct.length() > 0) return direct;
        JSONArray broadcasts = competition.optJSONArray("broadcasts");
        if (broadcasts == null || broadcasts.length() == 0) return "";
        JSONArray names = broadcasts.optJSONObject(0).optJSONArray("names");
        return names == null || names.length() == 0 ? "" : names.optString(0, "");
    }

    private String formatStart(String iso) {
        long time = parseTime(iso);
        if (time <= 0) return "UPCOMING";
        return new SimpleDateFormat("EEE h:mm a", Locale.US).format(new Date(time)).toUpperCase(Locale.US);
    }

    private long parseTime(String iso) {
        if (iso == null || iso.length() == 0) return 0L;
        try {
            int fraction = iso.indexOf('.');
            if (fraction > 0) {
                int utc = iso.indexOf('Z', fraction);
                if (utc > fraction) iso = iso.substring(0, fraction) + "Z";
            }
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return format.parse(iso).getTime();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String buildTicker(List<Score> scores) {
        if (scores.isEmpty()) return "NO GAMES ON THE BOARD";
        StringBuilder text = new StringBuilder();
        String[] leagues = new String[] { "NFL", "NCAA FB", "NCAA BSB", "MLB" };
        for (String league : leagues) {
            for (Score score : scores) {
                if (!league.equals(score.league)) continue;
                if (text.length() > 0) text.append("     |     ");
                text.append(score.league).append("::").append(score.text);
            }
        }
        return text.toString();
    }

    private void updateFavoriteMetadata(List<Score> scores) {
        String live = "";
        String next = "";
        long nextTime = 0L;
        long now = System.currentTimeMillis();
        JSONObject oldState;
        try {
            oldState = new JSONObject(preferences().getString(CACHE_SCORE_STATE, "{}"));
        } catch (Exception ignored) {
            oldState = new JSONObject();
        }
        JSONObject newState = new JSONObject();
        String alert = "";
        String alertId = preferences().getString(CACHE_ALERT_ID, "");

        for (Score score : scores) {
            if (!score.favorite) continue;
            if (score.live && live.length() == 0) live = score.league + "  " + score.text;
            if (!score.live && score.startTime > now
                    && (nextTime == 0L || score.startTime < nextTime)) {
                nextTime = score.startTime;
                next = score.league + "  " + score.text;
            }
            if (score.live && score.eventId.length() > 0) {
                try {
                    newState.put(score.eventId, score.totalScore);
                } catch (Exception ignored) {
                }
                if (oldState.has(score.eventId)
                        && score.totalScore > oldState.optInt(score.eventId, score.totalScore)) {
                    alert = "SCORE UPDATE  " + score.text;
                    alertId = score.eventId + "-" + score.totalScore;
                }
            }
        }

        preferences().edit()
                .putString(CACHE_LIVE, live)
                .putString(CACHE_NEXT, next)
                .putLong(CACHE_NEXT_TIME, nextTime)
                .putString(CACHE_ALERT, alert)
                .putString(CACHE_ALERT_ID, alertId)
                .putString(CACHE_SCORE_STATE, newState.toString())
                .apply();
    }

    private static final class Score {
        final String league;
        final String text;
        final int priority;
        final long startTime;
        final boolean favorite;
        final boolean live;
        final String eventId;
        final int totalScore;

        Score(String league, String text, int priority, long startTime,
              boolean favorite, boolean live, String eventId, int totalScore) {
            this.league = league;
            this.text = text;
            this.priority = priority;
            this.startTime = startTime;
            this.favorite = favorite;
            this.live = live;
            this.eventId = eventId;
            this.totalScore = totalScore;
        }
    }
}
