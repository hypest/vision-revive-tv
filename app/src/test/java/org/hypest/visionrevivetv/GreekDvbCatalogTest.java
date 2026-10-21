package org.hypest.visionrevivetv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GreekDvbCatalogTest {

    @Test
    public void testNationalBroadcastersLookup() {
        assertEquals("ERT 1 HD", GreekDvbCatalog.lookupChannelName("dvb://212c.2.28"));
        assertEquals("ERT 2", GreekDvbCatalog.lookupChannelName("dvb://212c.2.14"));
        assertEquals("ERT 3 HD", GreekDvbCatalog.lookupChannelName("dvb://212c.2.23"));
        assertEquals("ERT NEWS HD", GreekDvbCatalog.lookupChannelName("dvb://212c.2.29"));
        assertEquals("VOULI TV", GreekDvbCatalog.lookupChannelName("dvb://212c.2.9"));

        assertEquals("ALPHA HD", GreekDvbCatalog.lookupChannelName("dvb://212c.65.44c"));
        assertEquals("SKAI HD", GreekDvbCatalog.lookupChannelName("dvb://212c.65.4b0"));
        assertEquals("ANT1 HD", GreekDvbCatalog.lookupChannelName("dvb://212c.65.514"));
        assertEquals("OPEN BEYOND HD", GreekDvbCatalog.lookupChannelName("dvb://212c.65.578"));

        assertEquals("STAR HD", GreekDvbCatalog.lookupChannelName("dvb://212c.67.5dc"));
        assertEquals("MEGA HD", GreekDvbCatalog.lookupChannelName("dvb://212c.67.640"));
        assertEquals("ANT1 HD", GreekDvbCatalog.lookupChannelName("dvb://212c.67.6a4"));
        assertEquals("MAK TV HD", GreekDvbCatalog.lookupChannelName("dvb://212c.67.708"));
    }

    @Test
    public void testErtMux2Tsid1ChannelsAreNotRadio() {
        // Regress: TSID 1 (dvb://212c.1.*) must be television channels, NOT radio stations
        assertEquals("ERT 2 HD", GreekDvbCatalog.lookupChannelName("dvb://212c.1.1"));
        assertEquals("ERT 3", GreekDvbCatalog.lookupChannelName("dvb://212c.1.2"));
        assertEquals("ERT 1", GreekDvbCatalog.lookupChannelName("dvb://212c.1.3"));
        assertEquals("RIK Sat", GreekDvbCatalog.lookupChannelName("dvb://212c.1.4"));
        assertEquals("TV5 MONDE", GreekDvbCatalog.lookupChannelName("dvb://212c.1.5"));
        assertEquals("ERT World", GreekDvbCatalog.lookupChannelName("dvb://212c.1.6"));
        assertEquals("ERT NEWS", GreekDvbCatalog.lookupChannelName("dvb://212c.1.7"));

        // Must not be ranked as radio (9000)
        assertTrue(GreekDvbCatalog.getChannelRank("dvb://212c.1.1", "ERT 2 HD") < 9000);
        assertTrue(GreekDvbCatalog.getChannelRank("dvb://212c.1.2", "ERT 3") < 9000);
        assertTrue(GreekDvbCatalog.getChannelRank("dvb://212c.1.3", "ERT 1") < 9000);
        assertTrue(GreekDvbCatalog.getChannelRank("dvb://212c.1.4", "RIK Sat") < 9000);
        assertTrue(GreekDvbCatalog.getChannelRank("dvb://212c.1.5", "TV5 MONDE") < 9000);
        assertTrue(GreekDvbCatalog.getChannelRank("dvb://212c.1.6", "ERT World") < 9000);
        assertTrue(GreekDvbCatalog.getChannelRank("dvb://212c.1.7", "ERT NEWS") < 9000);

        // ERT 2 HD should have priority rank 20
        assertEquals(20, GreekDvbCatalog.getChannelRank("dvb://212c.1.1", "ERT 2 HD"));
    }

    @Test
    public void testTv100RegionalVsErtRadio() {
        // TV 100 on TSID 197 must be regional TV
        assertEquals("TV 100", GreekDvbCatalog.lookupChannelName("dvb://212c.197.10cc"));
        assertEquals(510, GreekDvbCatalog.getChannelRank("dvb://212c.197.10cc", "TV 100"));

        // ERT regional radio feeds on TSID 2 must be classified as radio
        assertEquals("ERT 95.8 FM (Radio)", GreekDvbCatalog.lookupChannelName("dvb://212c.2.21"));
        assertEquals("ERT 102 FM (Radio)", GreekDvbCatalog.lookupChannelName("dvb://212c.2.20"));
        assertEquals(9000, GreekDvbCatalog.getChannelRank("dvb://212c.2.21", "ERT 95.8 FM (Radio)"));
        assertEquals(9000, GreekDvbCatalog.getChannelRank("dvb://212c.2.20", "ERT 102 FM (Radio)"));
    }

    @Test
    public void testHierarchyTiers() {
        int rankErt1Hd = GreekDvbCatalog.getChannelRank("dvb://212c.2.28", "ERT 1 HD");
        int rankAlphaHd = GreekDvbCatalog.getChannelRank("dvb://212c.65.44c", "ALPHA HD");
        int rankTv100 = GreekDvbCatalog.getChannelRank("dvb://212c.197.10cc", "TV 100");
        int rankVergina = GreekDvbCatalog.getChannelRank("dvb://212c.195.1090", "ΒΕΡΓΙΝΑ");
        int rankRikSat = GreekDvbCatalog.getChannelRank("dvb://212c.1.4", "RIK Sat");
        int rankMegaSd = GreekDvbCatalog.getChannelRank("dvb://212c.69.e10", "MEGA");
        int rankRadio = GreekDvbCatalog.getChannelRank("dvb://212c.2.b", "Proto 91.6 (Radio)");

        // 1. National HD < Regional TV
        assertTrue(rankErt1Hd < rankTv100);
        assertTrue(rankAlphaHd < rankVergina);

        // 2. Regional TV < International TV
        assertTrue(rankTv100 < rankRikSat);

        // 3. International TV < SD Duplicates
        assertTrue(rankRikSat < rankMegaSd);

        // 4. SD Duplicates < Radio
        assertTrue(rankMegaSd < rankRadio);
        assertEquals(9000, rankRadio);
    }

    @Test
    public void testDefaultMappingForColdStart() {
        // IDs 791 to 837 must have pre-seeded mappings
        for (long id = 791; id <= 837; id++) {
            assertNotNull("Default DVB mapping must exist for channel ID " + id,
                    GreekDvbCatalog.getDefaultDvbForChannel(id));
        }

        // Channel 807 must map to ERT 1 HD
        assertEquals("dvb://212c.2.28", GreekDvbCatalog.getDefaultDvbForChannel(807));

        // Channel 820 must map to TV 100
        assertEquals("dvb://212c.197.10cc", GreekDvbCatalog.getDefaultDvbForChannel(820));

        // Channel 837 must map to ERT 2 HD
        assertEquals("dvb://212c.1.1", GreekDvbCatalog.getDefaultDvbForChannel(837));
    }
}
