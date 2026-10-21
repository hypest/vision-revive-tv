package org.hypest.visionrevivetv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EitLogParserTest {

    @Test
    public void testParseShortEventDescWithGreekChars() {
        String logLine = "10-21 22:12:34.343  2541  2541 V dtv.EitHandler:     Desc:{\"short_event_desc\":{\"iso_language\":\"gre\",\"event_name\":\"54ο Παγκόσμιο Πρωτάθλημα Ενόργανης Γυμναστικής - Ρότερνταμ 2026 (Z)\",\"text\":\"Αθλητικά (Γυμναστική) Τελικοί | Ομαδικό Γυναικών\"},\"extended_event_desc\":{\"iso_language\":\"gre\",\"text\":\"Έτος παραγωγής: 2026\"},\"parental_rating_desc\":{\"country_code\":\"902\",\"rating\":0}}";

        EitLogParser.Result result = EitLogParser.parseText(logLine, 807);

        assertEquals("54ο Παγκόσμιο Πρωτάθλημα Ενόργανης Γυμναστικής - Ρότερνταμ 2026 (Z)", result.programTitle);
        assertEquals("Αθλητικά (Γυμναστική) Τελικοί | Ομαδικό Γυναικών", result.programGenre);
        assertTrue(result.hasData());
    }

    @Test
    public void testParseDowntonAbbeyEpg() {
        String logLine = "10-21 22:20:58.724  5262  5332 V dtv.EitHandler:     Desc:{\"short_event_desc\":{\"iso_language\":\"gre\",\"event_name\":\"Ο Πύργος του Ντάουντον - B Κύκλος[K8]\",\"text\":\"Ψυχαγωγία (Ξένη σειρά) Επεισόδιο 9ο και 10ο (τελευταίο Β' Κύκλου)\"}}";

        EitLogParser.Result result = EitLogParser.parseText(logLine, 807);

        assertEquals("Ο Πύργος του Ντάουντον - B Κύκλος[K8]", result.programTitle);
        assertEquals("Ψυχαγωγία (Ξένη σειρά) Επεισόδιο 9ο και 10ο (τελευταίο Β' Κύκλου)", result.programGenre);
    }

    @Test
    public void testParseDvbTripletFromDtvfsOpen() {
        String logLine = "10-21 21:30:00.123  3161  3200 I DTVFS   : DTVFS::Open:42 path=/live/dvb:212c.65.44c/media.ts";

        EitLogParser.Result result = EitLogParser.parseText(logLine, 794);

        assertEquals("dvb://212c.65.44c", result.dvbTriplet);
    }

    @Test
    public void testParseDvbTripletFromZap() {
        String logLine = "10-21 21:30:00.123  2541  2541 D DtvInputSession: zap on dvb://212c.2.28";

        EitLogParser.Result result = EitLogParser.parseText(logLine, 807);

        assertEquals("dvb://212c.2.28", result.dvbTriplet);
    }

    @Test
    public void testParseDvbTripletFromServiceCreated() {
        String logLine = "10-21 21:30:00.123  2541  2541 I DtvSetup: Service dvb://212c.197.10cc created ->content://android.media.tv/channel/820";

        EitLogParser.Result result = EitLogParser.parseText(logLine, 820);

        assertEquals("dvb://212c.197.10cc", result.dvbTriplet);
    }

    @Test
    public void testIgnoresTvPlayerSpam() {
        String multiLine = "10-21 22:19:27.593  2541  2637 I TvPlayer: RendererScheduler.onMediaFrame: bad timestamp, set position using read pointer\n"
                + "10-21 22:19:27.593  2541  2637 W TvPlayer: PositionHandler.setPresentationTimestampUs: new position seems not correct\n"
                + "10-21 22:19:28.000  2541  2541 V dtv.EitHandler:     Desc:{\"short_event_desc\":{\"iso_language\":\"gre\",\"event_name\":\"Ειδήσεις\",\"text\":\"Ενημέρωση\"}}\n";

        EitLogParser.Result result = EitLogParser.parseText(multiLine, 807);

        assertEquals("Ειδήσεις", result.programTitle);
        assertEquals("Ενημέρωση", result.programGenre);
    }

    @Test
    public void testMalformedJsonFailsGracefully() {
        String malformed = "10-21 22:19:28.000  2541  2541 V dtv.EitHandler:     Desc:{not a valid json string}";

        EitLogParser.Result result = EitLogParser.parseText(malformed, 807);

        assertNull(result.programTitle);
        assertNull(result.programGenre);
        assertFalse(result.hasData());
    }

    @Test
    public void testEmptyAndNullLogs() {
        EitLogParser.Result r1 = EitLogParser.parseText(null, 807);
        assertNotNull(r1);
        assertFalse(r1.hasData());

        EitLogParser.Result r2 = EitLogParser.parseText("", 807);
        assertNotNull(r2);
        assertFalse(r2.hasData());
    }
}
