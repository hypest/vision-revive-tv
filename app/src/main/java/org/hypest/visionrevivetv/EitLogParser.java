package org.hypest.visionrevivetv;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for broadcast DVB EIT (Event Information Table) events and transport stream
 * metadata logged by the underlying Technicolor TV tuner service.
 */
public final class EitLogParser {

    private static final Pattern DVB_PATH_PATTERN =
            Pattern.compile("DTVFS::Open:\\d+\\s+path=/live/dvb:([0-9a-zA-Z._-]+)/media\\.ts");
    private static final Pattern ZAP_PATTERN =
            Pattern.compile("zap on dvb://([0-9a-zA-Z._-]+)");
    private static final Pattern DUMP_PATTERN =
            Pattern.compile("(dvb://[0-9a-zA-Z._-]+)\\s*:\\s*(.+?)\\s+lcn=(\\d+)");

    public static class Result {
        public String dvbTriplet;
        public String channelName;
        public String programTitle;
        public String programGenre;
        public final Map<String, String> discoveredTriplets = new LinkedHashMap<String, String>();

        public boolean hasData() {
            return dvbTriplet != null || channelName != null || programTitle != null;
        }
    }

    private EitLogParser() {}

    /**
     * Parses reader output from logcat (dtv.EitHandler, DTVFS, etc.) for a specific tuned channel ID.
     */
    public static Result parse(BufferedReader reader, long channelId) throws IOException {
        Result result = new Result();
        Pattern chPattern = (channelId > 0)
                ? Pattern.compile("Service\\s+(dvb://[0-9a-zA-Z._-]+)\\s+created\\s+->content://android\\.media\\.tv/channel/" + channelId)
                : null;

        String line;
        while ((line = reader.readLine()) != null) {
            parseLineInto(line, chPattern, result);
        }
        return result;
    }

    /**
     * Parses a raw text block from logcat for a specific tuned channel ID.
     */
    public static Result parseText(String logText, long channelId) {
        if (logText == null || logText.trim().isEmpty()) {
            return new Result();
        }
        try {
            return parse(new BufferedReader(new StringReader(logText)), channelId);
        } catch (IOException e) {
            return new Result();
        }
    }

    /**
     * Processes a single log line and updates the Result instance.
     */
    public static void parseLineInto(String line, Pattern chPattern, Result result) {
        if (line == null || line.isEmpty()) return;

        if (chPattern != null) {
            Matcher mc = chPattern.matcher(line);
            if (mc.find()) {
                result.dvbTriplet = mc.group(1);
            }
        }

        Matcher md = DVB_PATH_PATTERN.matcher(line);
        if (md.find()) {
            result.dvbTriplet = "dvb://" + md.group(1);
        }

        Matcher mz = ZAP_PATTERN.matcher(line);
        if (mz.find()) {
            result.dvbTriplet = "dvb://" + mz.group(1);
        }

        Matcher mDump = DUMP_PATTERN.matcher(line);
        if (mDump.find()) {
            String d = mDump.group(1);
            String n = mDump.group(2).trim();
            result.discoveredTriplets.put(d, n);
        }

        // EIT JSON descriptor
        if (line.contains("dtv.EitHandler:") && line.contains("Desc:{")) {
            try {
                int idx = line.indexOf("Desc:{");
                String jsonStr = line.substring(idx + 5).trim();
                JSONObject json = new JSONObject(jsonStr);
                JSONObject shortDesc = json.optJSONObject("short_event_desc");
                if (shortDesc != null) {
                    String evName = shortDesc.optString("event_name", null);
                    String genre = shortDesc.optString("text", null);
                    if (evName != null && !evName.trim().isEmpty()) {
                        result.programTitle = evName.trim();
                    }
                    if (genre != null && !genre.trim().isEmpty()) {
                        result.programGenre = genre.trim();
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}
