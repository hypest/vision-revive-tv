package org.hypest.visionrevivetv;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Built-in catalog of standard Greek DVB terrestrial triplets (ONID 0x212c)
 * calibrated empirically against on-screen video feeds and SI/EIT broadcasts.
 */
public final class GreekDvbCatalog {

    private static final Map<String, String> CATALOG = new HashMap<String, String>();
    private static final Map<Long, String> DEFAULT_ID_TO_DVB = new HashMap<Long, String>();

    static {
        // Default scanned ID -> DVB Triplet mapping for Wind Vision box
        DEFAULT_ID_TO_DVB.put(791L, "dvb://212c.197.111c"); // 4E
        DEFAULT_ID_TO_DVB.put(792L, "dvb://212c.195.1090"); // ΒΕΡΓΙΝΑ
        DEFAULT_ID_TO_DVB.put(793L, "dvb://212c.69.ce4");   // ANT1 (SD)
        DEFAULT_ID_TO_DVB.put(794L, "dvb://212c.65.44c");   // ALPHA HD
        DEFAULT_ID_TO_DVB.put(795L, "dvb://212c.67.6a4");   // ANT1 HD
        DEFAULT_ID_TO_DVB.put(796L, "dvb://212c.195.10c2"); // GNOMI TV
        DEFAULT_ID_TO_DVB.put(797L, "dvb://212c.2.e");      // Trito 90.9
        DEFAULT_ID_TO_DVB.put(798L, "dvb://212c.2.d");      // Proto 91.6
        DEFAULT_ID_TO_DVB.put(799L, "dvb://212c.2.c");      // Deftero 103.7
        DEFAULT_ID_TO_DVB.put(800L, "dvb://212c.2.1f");     // ERA Sport
        DEFAULT_ID_TO_DVB.put(801L, "dvb://212c.2.1f");     // ERA Sport
        DEFAULT_ID_TO_DVB.put(802L, "dvb://212c.2.a");      // ERT 1 (SD)
        DEFAULT_ID_TO_DVB.put(803L, "dvb://212c.195.10ae"); // EURO TV
        DEFAULT_ID_TO_DVB.put(804L, "dvb://212c.65.514");   // ANT1 HD
        DEFAULT_ID_TO_DVB.put(805L, "dvb://212c.65.578");   // OPEN BEYOND HD
        DEFAULT_ID_TO_DVB.put(806L, "dvb://212c.2.29");     // ERT NEWS HD
        DEFAULT_ID_TO_DVB.put(807L, "dvb://212c.2.28");     // ERT 1 HD
        DEFAULT_ID_TO_DVB.put(808L, "dvb://212c.197.1112"); // SMILE TV
        DEFAULT_ID_TO_DVB.put(809L, "");                    // PONTOS TV
        DEFAULT_ID_TO_DVB.put(810L, "dvb://212c.69.c1c");   // ALPHA (SD)
        DEFAULT_ID_TO_DVB.put(811L, "dvb://212c.2.23");     // ERT 3 HD
        DEFAULT_ID_TO_DVB.put(812L, "dvb://212c.2.22");     // Deftero 103.7
        DEFAULT_ID_TO_DVB.put(813L, "dvb://212c.2.21");     // TV 100
        DEFAULT_ID_TO_DVB.put(814L, "dvb://212c.2.20");     // EURO TV
        DEFAULT_ID_TO_DVB.put(815L, "dvb://212c.65.4b0");   // SKAI HD
        DEFAULT_ID_TO_DVB.put(816L, "dvb://212c.69.dac");   // STAR (SD)
        DEFAULT_ID_TO_DVB.put(817L, "dvb://212c.2.9");      // VOULI TV
        DEFAULT_ID_TO_DVB.put(818L, "dvb://212c.197.1108"); // EUROPE ONE
        DEFAULT_ID_TO_DVB.put(819L, "dvb://212c.67.5dc");   // STAR HD
        DEFAULT_ID_TO_DVB.put(820L, "dvb://212c.197.10cc"); // TV 100
        DEFAULT_ID_TO_DVB.put(821L, "dvb://212c.69.c80");   // SKAI (SD)
        DEFAULT_ID_TO_DVB.put(822L, "dvb://212c.2.17");     // Voice of Greece
        DEFAULT_ID_TO_DVB.put(823L, "dvb://212c.2.16");     // ERA Sport
        DEFAULT_ID_TO_DVB.put(824L, "dvb://212c.69.e74");   // MAK TV
        DEFAULT_ID_TO_DVB.put(825L, "dvb://212c.69.e10");   // MEGA (SD)
        DEFAULT_ID_TO_DVB.put(826L, "dvb://212c.2.15");     // Kosmos 93.6
        DEFAULT_ID_TO_DVB.put(827L, "dvb://212c.2.14");     // ERT 2
        DEFAULT_ID_TO_DVB.put(828L, "dvb://212c.67.640");   // MEGA HD
        DEFAULT_ID_TO_DVB.put(829L, "dvb://212c.1.7");      // Radio
        DEFAULT_ID_TO_DVB.put(830L, "dvb://212c.69.d48");   // OPEN BEYOND (SD)
        DEFAULT_ID_TO_DVB.put(831L, "dvb://212c.195.10a4"); // DION TV
        DEFAULT_ID_TO_DVB.put(832L, "dvb://212c.1.6");      // ERT NEWS (SD) / Radio
        DEFAULT_ID_TO_DVB.put(833L, "dvb://212c.1.5");      // Radio
        DEFAULT_ID_TO_DVB.put(834L, "dvb://212c.1.4");      // Radio
        DEFAULT_ID_TO_DVB.put(835L, "dvb://212c.1.3");      // Radio
        DEFAULT_ID_TO_DVB.put(836L, "dvb://212c.1.2");      // Radio
        DEFAULT_ID_TO_DVB.put(837L, "dvb://212c.1.1");      // Radio
        // --- ERT National & Public Multiplex (TSID 2 in Central Macedonia) ---
        CATALOG.put("dvb://212c.2.28", "ERT 1 HD");
        CATALOG.put("dvb://212c.2.14", "ERT 2");
        CATALOG.put("dvb://212c.2.23", "ERT 3 HD");
        CATALOG.put("dvb://212c.2.29", "ERT NEWS HD");
        CATALOG.put("dvb://212c.2.9", "VOULI TV");
        CATALOG.put("dvb://212c.2.a", "ERT 1");

        // --- Digea National Commercial HD Multiplexes (TSID 65 & 67) ---
        CATALOG.put("dvb://212c.65.44c", "ALPHA HD");
        CATALOG.put("dvb://212c.65.4b0", "SKAI HD");
        CATALOG.put("dvb://212c.65.514", "ANT1 HD");
        CATALOG.put("dvb://212c.65.578", "OPEN BEYOND HD");
        CATALOG.put("dvb://212c.67.5dc", "STAR HD");
        CATALOG.put("dvb://212c.67.640", "MEGA HD");
        CATALOG.put("dvb://212c.67.6a4", "ANT1 HD");
        CATALOG.put("dvb://212c.67.708", "MAK TV HD");

        // --- Digea National Commercial SD Multiplex (TSID 69) ---
        CATALOG.put("dvb://212c.69.c1c", "ALPHA");
        CATALOG.put("dvb://212c.69.ce4", "ANT1");
        CATALOG.put("dvb://212c.69.e10", "MEGA");
        CATALOG.put("dvb://212c.69.d48", "OPEN BEYOND");
        CATALOG.put("dvb://212c.69.c80", "SKAI");
        CATALOG.put("dvb://212c.69.dac", "STAR");
        CATALOG.put("dvb://212c.69.e74", "MAK TV");

        // --- Central Macedonia Regional Multiplex 1 (TSID 195 - Thessaloniki) ---
        CATALOG.put("dvb://212c.195.1090", "ΒΕΡΓΙΝΑ");
        CATALOG.put("dvb://212c.195.10a4", "DION TV");
        CATALOG.put("dvb://212c.195.10ae", "EURO TV");
        CATALOG.put("dvb://212c.195.10c2", "GNOMI TV");

        // --- Central Macedonia Regional Multiplex 2 (TSID 197) ---
        CATALOG.put("dvb://212c.197.10cc", "TV 100");
        CATALOG.put("dvb://212c.197.1108", "EUROPE ONE");
        CATALOG.put("dvb://212c.197.1112", "SMILE TV");
        CATALOG.put("dvb://212c.197.111c", "4E");

        // --- Regional Channels (Alternate / Legacy triplets) ---
        CATALOG.put("dvb://212c.2.21", "TV 100");
        CATALOG.put("dvb://212c.2.20", "EURO TV");

        // --- ERA / ERT Radio Stations (TSID 2 audio-only) ---
        CATALOG.put("dvb://212c.2.b", "Proto 91.6 (Radio)");
        CATALOG.put("dvb://212c.2.c", "Deftero 103.7 (Radio)");
        CATALOG.put("dvb://212c.2.d", "Proto 91.6 (Radio)");
        CATALOG.put("dvb://212c.2.e", "Trito 90.9 (Radio)");
        CATALOG.put("dvb://212c.2.15", "Kosmos 93.6 (Radio)");
        CATALOG.put("dvb://212c.2.16", "ERA Sport 101.8 (Radio)");
        CATALOG.put("dvb://212c.2.17", "Voice of Greece (Radio)");
        CATALOG.put("dvb://212c.2.1f", "ERA Sport 101.8 (Radio)");
        CATALOG.put("dvb://212c.2.22", "Deftero 103.7 (Radio)");

        // --- ERT Radio (TSID 1) ---
        CATALOG.put("dvb://212c.1.1", "Proto 91.6 (Radio)");
        CATALOG.put("dvb://212c.1.2", "Deftero 103.7 (Radio)");
        CATALOG.put("dvb://212c.1.3", "Trito 90.9 (Radio)");
        CATALOG.put("dvb://212c.1.4", "ERA Sport 101.8 (Radio)");
        CATALOG.put("dvb://212c.1.5", "Kosmos 93.6 (Radio)");
        CATALOG.put("dvb://212c.1.6", "Voice of Greece (Radio)");
        CATALOG.put("dvb://212c.1.7", "ERT Cosmos (Radio)");
    }

    private GreekDvbCatalog() {}

    /**
     * Looks up the standard station name for a given DVB triplet string.
     * Accepts formats like "dvb://212c.195.1090" or "212c.195.1090".
     */
    public static String lookupChannelName(String dvbTriplet) {
        if (dvbTriplet == null) return null;
        String key = dvbTriplet.trim().toLowerCase(Locale.US);
        if (!key.startsWith("dvb://")) {
            key = "dvb://" + key;
        }
        return CATALOG.get(key);
    }

    /**
     * Returns the empirically calibrated default DVB triplet for a given channel ID
     * from the standard Wind Vision DTV scan, if known.
     */
    public static String getDefaultDvbForChannel(long channelId) {
        return DEFAULT_ID_TO_DVB.get(channelId);
    }

    /**
     * Returns an ordering rank where national HD channels come first in standard
     * Greek channel lineup order, followed by regional TV channels, SD duplicates,
     * unidentified channels, and finally radio channels.
     */
    public static int getChannelRank(String dvbTriplet, String name) {
        String effectiveName = (name != null) ? name.trim() : "";
        if (effectiveName.isEmpty() || MainActivity.isDefaultName(effectiveName)) {
            String catalogName = lookupChannelName(dvbTriplet);
            if (catalogName != null) {
                effectiveName = catalogName;
            }
        }

        if (effectiveName.isEmpty() || MainActivity.isDefaultName(effectiveName)) {
            return 5000;
        }

        String n = effectiveName.toUpperCase(Locale.US);

        // Radio channels go last
        if (n.contains("(RADIO)") || (dvbTriplet != null && (dvbTriplet.startsWith("dvb://212c.1.") || dvbTriplet.matches("dvb://212c\\.2\\.(b|c|d|e|15|16|17|1f|20|22)")))) {
            return 9000;
        }

        // --- National Greek Broadcasters (Standard LCN Lineup) ---
        // National HD: 10..120
        // Regional TV: 500..800
        // National SD Duplicates: 1000..1200
        if (n.startsWith("ERT 1") || n.startsWith("ERT1")) {
            return n.contains("HD") ? 10 : 1010;
        }
        if (n.startsWith("ERT 2") || n.startsWith("ERT2")) {
            return 20; // ERT 2 is standard LCN #2
        }
        if (n.startsWith("ERT 3") || n.startsWith("ERT3")) {
            return n.contains("HD") ? 30 : 1030;
        }
        if (n.contains("ERT NEWS") || n.contains("ERTNEWS")) {
            return 35;
        }
        if (n.startsWith("VOULI")) {
            return 38;
        }
        if (n.startsWith("ALPHA")) {
            return n.contains("HD") ? 40 : 1040;
        }
        if (n.startsWith("ANT1") || n.startsWith("ANTENNA")) {
            return n.contains("HD") ? 50 : 1050;
        }
        if (n.startsWith("MEGA")) {
            return n.contains("HD") ? 60 : 1060;
        }
        if (n.startsWith("OPEN")) {
            return n.contains("HD") ? 70 : 1070;
        }
        if (n.startsWith("SKAI")) {
            return n.contains("HD") ? 80 : 1080;
        }
        if (n.startsWith("STAR")) {
            return n.contains("HD") ? 90 : 1090;
        }
        if (n.startsWith("MAK") || n.contains("MAKEDONIA")) {
            return n.contains("HD") ? 100 : 500;
        }

        // Regional TV Channels (Thessaloniki / Central Macedonia)
        if (n.startsWith("TV 100") || n.startsWith("TV100")) return 510;
        if (n.contains("ΒΕΡΓΙΝΑ") || n.contains("VERGINA")) return 520;
        if (n.equals("4E")) return 530;
        if (n.contains("EURO TV") || n.contains("EUROTV")) return 540;
        if (n.contains("DION")) return 550;
        if (n.contains("GNOMI")) return 560;
        if (n.contains("SMILE")) return 570;
        if (n.contains("EUROPE ONE") || n.contains("EUROPE 1")) return 580;
        if (n.contains("PONTOS")) return 590;

        return 800;
    }
}
