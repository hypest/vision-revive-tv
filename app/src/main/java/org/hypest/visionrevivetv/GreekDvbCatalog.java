package org.hypest.visionrevivetv;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Built-in catalog of standard Greek DVB terrestrial triplets (ONID 0x212c)
 * and channel ranking logic so national channels appear first in standard LCN order.
 */
public final class GreekDvbCatalog {

    private static final Map<String, String> CATALOG = new HashMap<String, String>();

    static {
        // --- ERT National Multiplexes ---
        CATALOG.put("dvb://212c.195.10ae", "ERT 1 HD");
        CATALOG.put("dvb://212c.195.10b8", "ERT 2");
        CATALOG.put("dvb://212c.195.10c2", "ERT 3 HD");
        CATALOG.put("dvb://212c.195.10a4", "ERT NEWS HD");
        CATALOG.put("dvb://212c.197.1108", "VOULI TV");
        CATALOG.put("dvb://212c.197.1112", "ERT 2");
        CATALOG.put("dvb://212c.197.111c", "ERT 2");
        CATALOG.put("dvb://212c.2.28", "ERT 1");

        // --- Digea National Commercial HD Muxes ---
        CATALOG.put("dvb://212c.65.44c", "ALPHA HD");
        CATALOG.put("dvb://212c.65.4b0", "ANT1 HD");
        CATALOG.put("dvb://212c.65.514", "ANT1 HD");
        CATALOG.put("dvb://212c.65.578", "OPEN BEYOND HD");
        CATALOG.put("dvb://212c.67.5dc", "SKAI HD");
        CATALOG.put("dvb://212c.67.640", "MEGA HD");
        CATALOG.put("dvb://212c.67.6a4", "STAR HD");
        CATALOG.put("dvb://212c.67.708", "STAR HD");
        CATALOG.put("dvb://212c.69.c80", "OPEN BEYOND");
        CATALOG.put("dvb://212c.69.ce4", "MAK TV");
        CATALOG.put("dvb://212c.69.d48", "ALPHA");
        CATALOG.put("dvb://212c.69.dac", "ANT1");

        // --- Digea Commercial SD (Mux 2 / Central Macedonia fallback) ---
        CATALOG.put("dvb://212c.2.c", "MEGA");
        CATALOG.put("dvb://212c.2.d", "ANT1");
        CATALOG.put("dvb://212c.2.e", "ALPHA");
        CATALOG.put("dvb://212c.2.14", "STAR");
        CATALOG.put("dvb://212c.2.15", "SKAI");
        CATALOG.put("dvb://212c.2.16", "OPEN BEYOND");
        CATALOG.put("dvb://212c.2.17", "MAK TV");

        // --- Central Macedonia Regional Channels (Thessaloniki) ---
        CATALOG.put("dvb://212c.2.a", "4E");
        CATALOG.put("dvb://212c.2.b", "ΒΕΡΓΙΝΑ");
        CATALOG.put("dvb://212c.195.1090", "ΒΕΡΓΙΝΑ");
        CATALOG.put("dvb://212c.2.1f", "EUROPE ONE");
        CATALOG.put("dvb://212c.2.20", "EURO TV");
        CATALOG.put("dvb://212c.2.21", "TV 100");
        CATALOG.put("dvb://212c.2.22", "DION TV");
        CATALOG.put("dvb://212c.2.29", "ATLAS TV");

        // --- Attica Regional Channels ---
        CATALOG.put("dvb://212c.69.c1c", "ACTION 24");
        CATALOG.put("dvb://212c.69.ce4_attica", "ONE CHANNEL");
        CATALOG.put("dvb://212c.69.d48_attica", "KONTRA CHANNEL");
        CATALOG.put("dvb://212c.69.dac_attica", "BLUE SKY");
        CATALOG.put("dvb://212c.69.e10", "ALERT TV");
        CATALOG.put("dvb://212c.69.e74", "SMILE TV");
        CATALOG.put("dvb://212c.2.9", "MAD TV");

        // --- ERT Radio ---
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
     * Returns an ordering rank where national channels come first in standard
     * Greek channel lineup order, followed by regional TV channels, unidentified
     * channels, and finally radio channels.
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
            // Unidentified / neutral channel
            return 5000;
        }

        String n = effectiveName.toUpperCase(Locale.US);

        // Radio channels go last
        if (n.contains("(RADIO)") || (dvbTriplet != null && dvbTriplet.startsWith("dvb://212c.1."))) {
            return 9000;
        }

        // --- National Greek Broadcasters (Standard LCN Lineup) ---
        if (n.startsWith("ERT 1") || n.startsWith("ERT1")) {
            return n.contains("HD") ? 10 : 11;
        }
        if (n.startsWith("ERT 2") || n.startsWith("ERT2")) {
            return n.contains("HD") ? 20 : 21;
        }
        if (n.startsWith("ERT 3") || n.startsWith("ERT3")) {
            return n.contains("HD") ? 30 : 31;
        }
        if (n.startsWith("ANT1") || n.startsWith("ANTENNA")) {
            return n.contains("HD") ? 40 : 41;
        }
        if (n.startsWith("ALPHA")) {
            return n.contains("HD") ? 50 : 51;
        }
        if (n.startsWith("MEGA")) {
            return n.contains("HD") ? 60 : 61;
        }
        if (n.startsWith("SKAI")) {
            return n.contains("HD") ? 70 : 71;
        }
        if (n.startsWith("STAR")) {
            return n.contains("HD") ? 80 : 81;
        }
        if (n.startsWith("OPEN")) {
            return n.contains("HD") ? 90 : 91;
        }
        if (n.startsWith("MAK") || n.contains("MAKEDONIA")) {
            return n.contains("HD") ? 100 : 101;
        }
        if (n.startsWith("VOULI")) {
            return 110;
        }
        if (n.contains("ERT NEWS") || n.contains("ERTNEWS")) {
            return 120;
        }

        // Regional and other identified TV channels
        return 1000;
    }
}
