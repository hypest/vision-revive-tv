package org.hypest.visionrevivetv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String DTV_INPUT_ID = "com.technicolor.android.dtvinput/.DtvInputService";
    private static final String PREFS_NAME = "ota_tv_prefs";
    private static final String KEY_PREFS_VERSION = "prefs_version";
    private static final int CURRENT_PREFS_VERSION = 4;
    private static final String KEY_LAST_INDEX = "last_channel_index";
    private static final String KEY_CHANNEL_IDS = "channel_ids_csv";
    private static final String KEY_NAME_PREFIX = "ch_name_";
    private static final String KEY_DVB_PREFIX = "ch_dvb_";

    // Default scanned channel IDs from the most recent scan (650..696, 47 channels)
    private static final int DEFAULT_START_ID = 650;
    private static final int DEFAULT_END_ID = 696;

    private static final Map<String, String> sDiscoveredTripletToName = new LinkedHashMap<String, String>();

    private static final String[] GREEK_CHANNEL_PRESETS = new String[]{
        "ERT 1 HD", "ERT 2 HD", "ERT 3 HD", "ERT NEWS HD", "VOULI TV",
        "MEGA HD", "ANT1 HD", "ALPHA HD", "STAR HD", "SKAI HD", "OPEN BEYOND HD", "MAK TV HD",
        "ERT 1", "ERT 2", "ERT 3", "ERT NEWS",
        "MEGA", "ANT1", "ALPHA", "STAR", "SKAI", "OPEN BEYOND", "MAK TV",
        "4E", "ΒΕΡΓΙΝΑ", "DION TV", "EURO TV", "TV 100",
        "ACTION 24", "ATTICA TV", "ONE CHANNEL", "BLUE SKY", "ALERT TV",
        "KONTRA", "HIGH TV", "MAD TV", "SMILE TV", "NICKELODEON", "RISE TV",
        "EURONEWS", "RIK SAT", "TV5 MONDE", "BBC WORLD"
    };

    public static boolean isDefaultName(String name) {
        if (name == null) return true;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return true;
        return trimmed.matches("(?i)^(?:CH|OTA Channel)\\s*\\d+$");
    }

    public static class ChannelEntry {
        public final long id;
        public int displayNumber;
        public String name;
        public String dvbTriplet;
        public String currentProgramTitle;
        public String currentProgramGenre;

        public ChannelEntry(long id, int displayNumber, String name, String dvbTriplet) {
            this.id = id;
            this.displayNumber = displayNumber;
            this.name = name;
            this.dvbTriplet = dvbTriplet;
        }

        public Uri getUri() {
            return Uri.parse("content://android.media.tv/channel/" + id);
        }
    }

    private TvView mTvView;
    private LinearLayout mSidePanel;
    private ListView mChannelListView;
    private ArrayAdapter<String> mChannelAdapter;
    private LinearLayout mOsdBanner;
    private TextView mOsdTitle;
    private TextView mOsdProgram;
    private TextView mOsdSubtitle;
    private TextView mOsdStatus;
    private TextView mNumericEntryOverlay;

    private final List<ChannelEntry> mChannels = new ArrayList<ChannelEntry>();
    private final List<String> mChannelLabels = new ArrayList<String>();
    private int mCurrentIndex = 0;

    private String mCurrentVideoStatus = "Initializing tuner...";
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;
    private final List<TvTrackInfo> mAudioTracks = new ArrayList<TvTrackInfo>();
    private final List<TvTrackInfo> mSubtitleTracks = new ArrayList<TvTrackInfo>();
    private String mSelectedAudioTrackId = null;
    private String mSelectedSubtitleTrackId = null;

    private final StringBuilder mDigitBuffer = new StringBuilder();
    private long mLastBackPressMs = 0;
    private long mLastTuneTimeMs = 0;

    // Auto-probe state for discovering active channel IDs in TvProvider via DtvInputService
    private static final int PROBE_STAGE_IDLE = 0;
    private static final int PROBE_STAGE_COARSE = 1;
    private static final int PROBE_STAGE_FIND_START = 2;
    private static final int PROBE_STAGE_FIND_END = 3;

    private int mProbeStage = PROBE_STAGE_IDLE;
    private final List<Long> mCoarseCandidates = new ArrayList<Long>();
    private int mCoarseIndex = 0;
    private long mCurrentProbeId = -1;
    private long mProbeHitId = -1;
    private long mBinLow = 0;
    private long mBinHigh = 0;
    private long mFoundStartId = -1;
    private long mFoundEndId = -1;
    private boolean mAutoProbedThisSession = false;
    private boolean mWaitingForScannerReturn = false;
    private long mLastTuneTuningCallbackMs = 0;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final Runnable mProbeHitTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (mProbeStage != PROBE_STAGE_IDLE && mCurrentProbeId > 0) {
                checkProbeCandidateQuickLogcat(mCurrentProbeId);
            }
        }
    };

    private void checkProbeCandidateQuickLogcat(final long candidateId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean found = false;
                try {
                    Process proc = Runtime.getRuntime().exec(new String[]{
                        "logcat", "-d", "-t", "40", "-s", "DtvInputSession:D", "DTVFS:V"
                    });
                    BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    String line;
                    boolean tunedCandidate = false;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("onTune(content://android.media.tv/channel/" + candidateId)) {
                            tunedCandidate = true;
                        } else if (tunedCandidate && line.contains("DTVFS::Open")) {
                            found = true;
                            break;
                        }
                    }
                    reader.close();
                } catch (Exception ignored) {
                }
                final boolean exists = found;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onProbeCandidateResult(candidateId, exists);
                    }
                });
            }
        }).start();
    }

    private final Runnable mHideOsdRunnable = new Runnable() {
        @Override
        public void run() {
            if (mOsdBanner != null && (mSidePanel == null || mSidePanel.getVisibility() != View.VISIBLE)) {
                mOsdBanner.setVisibility(View.GONE);
            }
        }
    };

    private final Runnable mCommitDigitsRunnable = new Runnable() {
        @Override
        public void run() {
            commitNumericEntry();
        }
    };

    private void ensureLogcatUnthrottled() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Runtime.getRuntime().exec(new String[]{"logcat", "-P", ""});
                } catch (Exception ignored) {}
            }
        }).start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ensureLogcatUnthrottled();

        loadChannelsFromPrefs();
        buildUi();
        if (handleControlIntent(getIntent())) {
            return;
        }
        discoverChannelsFromLogcatAsync(false);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int savedIdx = prefs.getInt(KEY_LAST_INDEX, 0);
        if (savedIdx < 0 || savedIdx >= mChannels.size()) {
            savedIdx = 0;
        }
        tuneToIndex(savedIdx);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleControlIntent(intent);
    }

    /**
     * adb control hooks:
     *   am start -n org.hypest.visionrevivetv/.MainActivity --el start_id 584 --el end_id 630
     *   am start -n org.hypest.visionrevivetv/.MainActivity --ez probe true
     */
    private boolean handleControlIntent(Intent intent) {
        if (intent == null) return false;
        if (intent.getBooleanExtra("screenshot", false)) {
            intent.removeExtra("screenshot");
            File out = new File(getExternalFilesDir(null), "snap.png");
            captureScreenToFile(out);
            return true;
        }
        int targetIdx = intent.getIntExtra("index", -1);
        if (targetIdx >= 0 && targetIdx < mChannels.size()) {
            intent.removeExtra("index");
            tuneToIndex(targetIdx);
            return true;
        }
        long targetId = intent.getLongExtra("channel_id", -1);
        if (targetId > 0) {
            intent.removeExtra("channel_id");
            for (int i = 0; i < mChannels.size(); i++) {
                if (mChannels.get(i).id == targetId) {
                    tuneToIndex(i);
                    return true;
                }
            }
        }
        long s = intent.getLongExtra("start_id", -1);
        long e = intent.getLongExtra("end_id", -1);
        if (s > 0 && e >= s && (e - s) <= 250) {
            intent.removeExtra("start_id");
            intent.removeExtra("end_id");
            List<Long> newIds = new ArrayList<Long>();
            for (long id = s; id <= e; id++) {
                newIds.add(id);
            }
            saveChannelIdsToPrefs(newIds);
            rebuildChannelEntries(newIds);
            mAutoProbedThisSession = true;
            tuneToIndex(0);
            return true;
        }
        if (intent.getBooleanExtra("probe", false)) {
            intent.removeExtra("probe");
            mAutoProbedThisSession = true;
            startAutoProbeChannels(true);
            return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureLogcatUnthrottled();
        if (mWaitingForScannerReturn) {
            mWaitingForScannerReturn = false;
            // 1. Opportunistically discover channels and names directly from logcat right away before buffers scroll
            discoverChannelsFromLogcatAsync(false);
            // 2. Also run auto-probe to find the active ID boundary in TvProvider
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startAutoProbeChannels(true);
                }
            }, 600);
            return;
        }
        discoverChannelsFromLogcatAsync(false);
        if (mProbeStage == PROBE_STAGE_IDLE && !mChannels.isEmpty()) {
            tuneToIndex(mCurrentIndex);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoProbe();
        mHandler.removeCallbacks(mCheckTunedChannelLogRunnable);
        mHandler.removeCallbacks(mCheckTunedChannelLogRetryRunnable);
        mHandler.removeCallbacks(mPeriodicEitCheckRunnable);
        if (mTvView != null) {
            mTvView.reset();
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // 1. Fullscreen TvView
        mTvView = new TvView(this);
        mTvView.setFocusable(false);
        mTvView.setCallback(new TvView.TvInputCallback() {
            @Override
            public void onVideoAvailable(String inputId) {
                disableSecureSurface(mTvView);
                if (mProbeStage != PROBE_STAGE_IDLE && mCurrentProbeId > 0) {
                    mHandler.removeCallbacks(mProbeHitTimeoutRunnable);
                    onProbeCandidateResult(mCurrentProbeId, true);
                    return;
                }
                mCurrentVideoStatus = "Live DVB-T/T2 Signal Locked";
                updateOsdText();
                long osdTimeout = 5000;
                if (mCurrentIndex >= 0 && mCurrentIndex < mChannels.size()) {
                    ChannelEntry ch = mChannels.get(mCurrentIndex);
                    if (ch.currentProgramTitle != null && !ch.currentProgramTitle.isEmpty()) {
                        osdTimeout = 7500;
                    }
                }
                scheduleOsdHide(osdTimeout);
            }

            @Override
            public void onVideoUnavailable(String inputId, int reason) {
                if (mProbeStage != PROBE_STAGE_IDLE && mCurrentProbeId > 0) {
                    if (reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING) {
                        mHandler.removeCallbacks(mProbeHitTimeoutRunnable);
                        mHandler.postDelayed(mProbeHitTimeoutRunnable, 400);
                    } else if (reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN) {
                        // Immediate failure (/channel/<id> not found in TvProvider)
                        mHandler.removeCallbacks(mProbeHitTimeoutRunnable);
                        onProbeCandidateResult(mCurrentProbeId, false);
                    }
                    return;
                }

                if (reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING) {
                    mLastTuneTuningCallbackMs = System.currentTimeMillis();
                } else if (reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN) {
                    long delta = System.currentTimeMillis() - mLastTuneTuningCallbackMs;
                    if (delta >= 0 && delta < 160 && !mAutoProbedThisSession) {
                        mAutoProbedThisSession = true;
                        startAutoProbeChannels(false);
                        return;
                    }
                }

                mCurrentVideoStatus = "Video unavailable: " + formatUnavailableReason(reason);
                updateOsdText();
            }

            @Override
            public void onVideoSizeChanged(String inputId, int width, int height) {
                mVideoWidth = width;
                mVideoHeight = height;
                updateOsdText();
            }

            @Override
            public void onTracksChanged(String inputId, List<TvTrackInfo> tracks) {
                if (mProbeStage != PROBE_STAGE_IDLE && mCurrentProbeId > 0 && tracks != null && !tracks.isEmpty()) {
                    mHandler.removeCallbacks(mProbeHitTimeoutRunnable);
                    onProbeCandidateResult(mCurrentProbeId, true);
                    return;
                }
                mAudioTracks.clear();
                mSubtitleTracks.clear();
                if (tracks != null) {
                    for (TvTrackInfo t : tracks) {
                        if (t.getType() == TvTrackInfo.TYPE_AUDIO) {
                            mAudioTracks.add(t);
                        } else if (t.getType() == TvTrackInfo.TYPE_SUBTITLE) {
                            mSubtitleTracks.add(t);
                        }
                    }
                }
                mSelectedAudioTrackId = mTvView.getSelectedTrack(TvTrackInfo.TYPE_AUDIO);
                mSelectedSubtitleTrackId = mTvView.getSelectedTrack(TvTrackInfo.TYPE_SUBTITLE);
                updateOsdText();
            }

            @Override
            public void onTrackSelected(String inputId, int type, String trackId) {
                if (type == TvTrackInfo.TYPE_AUDIO) {
                    mSelectedAudioTrackId = trackId;
                } else if (type == TvTrackInfo.TYPE_SUBTITLE) {
                    mSelectedSubtitleTrackId = trackId;
                }
                updateOsdText();
            }

            @Override
            public void onContentAllowed(String inputId) {
                updateOsdText();
            }
        });
        root.addView(mTvView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 2. Bottom OSD Info Banner
        mOsdBanner = new LinearLayout(this);
        mOsdBanner.setOrientation(LinearLayout.VERTICAL);
        mOsdBanner.setPadding(dp(28), dp(18), dp(28), dp(18));
        GradientDrawable osdBg = new GradientDrawable();
        osdBg.setColor(Color.argb(215, 18, 24, 36));
        osdBg.setCornerRadius(dp(12));
        osdBg.setStroke(dp(1), Color.argb(120, 100, 181, 246));
        mOsdBanner.setBackground(osdBg);

        mOsdTitle = new TextView(this);
        mOsdTitle.setTextColor(Color.WHITE);
        mOsdTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        mOsdTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mOsdBanner.addView(mOsdTitle);

        mOsdProgram = new TextView(this);
        mOsdProgram.setTextColor(Color.rgb(255, 224, 130)); // Warm Amber / Gold
        mOsdProgram.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        mOsdProgram.setTypeface(Typeface.DEFAULT_BOLD);
        mOsdProgram.setPadding(0, dp(2), 0, dp(1));
        mOsdProgram.setVisibility(View.GONE);
        mOsdBanner.addView(mOsdProgram);

        mOsdSubtitle = new TextView(this);
        mOsdSubtitle.setTextColor(Color.rgb(144, 202, 249));
        mOsdSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        mOsdSubtitle.setPadding(0, dp(2), 0, dp(2));
        mOsdBanner.addView(mOsdSubtitle);

        mOsdStatus = new TextView(this);
        mOsdStatus.setTextColor(Color.rgb(200, 210, 220));
        mOsdStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mOsdBanner.addView(mOsdStatus);

        FrameLayout.LayoutParams osdParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        osdParams.setMargins(dp(40), dp(20), dp(40), dp(28));
        root.addView(mOsdBanner, osdParams);

        // 3. Top-Right Numeric Direct Entry Overlay
        mNumericEntryOverlay = new TextView(this);
        mNumericEntryOverlay.setTextColor(Color.WHITE);
        mNumericEntryOverlay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        mNumericEntryOverlay.setTypeface(Typeface.DEFAULT_BOLD);
        mNumericEntryOverlay.setPadding(dp(20), dp(10), dp(20), dp(10));
        GradientDrawable numBg = new GradientDrawable();
        numBg.setColor(Color.argb(220, 21, 101, 192));
        numBg.setCornerRadius(dp(10));
        mNumericEntryOverlay.setBackground(numBg);
        mNumericEntryOverlay.setVisibility(View.GONE);
        FrameLayout.LayoutParams numParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        numParams.setMargins(dp(32), dp(32), dp(32), dp(32));
        root.addView(mNumericEntryOverlay, numParams);

        // 4. Left Side Panel (Channel List & Controls)
        mSidePanel = new LinearLayout(this);
        mSidePanel.setOrientation(LinearLayout.VERTICAL);
        mSidePanel.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.argb(235, 14, 19, 28));
        mSidePanel.setBackground(panelBg);
        mSidePanel.setVisibility(View.GONE);

        TextView panelHeader = new TextView(this);
        panelHeader.setText("OTA DVB-T/T2 Channels");
        panelHeader.setTextColor(Color.WHITE);
        panelHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        panelHeader.setTypeface(Typeface.DEFAULT_BOLD);
        panelHeader.setPadding(0, 0, 0, dp(8));
        mSidePanel.addView(panelHeader);

        // Control buttons row 1
        LinearLayout btnRow1 = new LinearLayout(this);
        btnRow1.setOrientation(LinearLayout.HORIZONTAL);
        btnRow1.setPadding(0, 0, 0, dp(6));

        Button btnAudio = createActionButton("Audio Track");
        btnAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleAudioTrack();
            }
        });
        btnRow1.addView(btnAudio, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button btnSub = createActionButton("Subtitles");
        btnSub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleSubtitleTrack();
            }
        });
        btnRow1.addView(btnSub, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        mSidePanel.addView(btnRow1);

        // Control buttons row 2
        LinearLayout btnRow2 = new LinearLayout(this);
        btnRow2.setOrientation(LinearLayout.HORIZONTAL);
        btnRow2.setPadding(0, 0, 0, dp(10));

        Button btnRename = createActionButton("Name Ch");
        btnRename.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRenameChannelDialog();
            }
        });
        btnRow2.addView(btnRename, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button btnScan = createActionButton("OTA Scan");
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showScannerOptionsDialog();
            }
        });
        btnRow2.addView(btnScan, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        mSidePanel.addView(btnRow2);

        // Channel ListView
        mChannelListView = new ListView(this);
        mChannelListView.setDividerHeight(dp(2));
        mChannelListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        mChannelAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_activated_1,
                mChannelLabels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView tv = (TextView) v.findViewById(android.R.id.text1);
                if (tv != null) {
                    tv.setTextColor(position == mCurrentIndex ? Color.rgb(100, 221, 23) : Color.WHITE);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    tv.setTypeface(position == mCurrentIndex ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                }
                return v;
            }
        };
        mChannelListView.setAdapter(mChannelAdapter);
        mChannelListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                tuneToIndex(position);
                hideSidePanel();
            }
        });
        mSidePanel.addView(mChannelListView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        FrameLayout.LayoutParams sideParams = new FrameLayout.LayoutParams(
                dp(390),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START);
        root.addView(mSidePanel, sideParams);

        setContentView(root);
        refreshChannelLabels();
    }

    private Button createActionButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setAllCaps(false);
        return b;
    }

    private void loadChannelsFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getInt(KEY_PREFS_VERSION, 1) < CURRENT_PREFS_VERSION) {
            SharedPreferences.Editor editor = prefs.edit();
            Map<String, ?> all = prefs.getAll();
            for (String key : all.keySet()) {
                if (key.startsWith(KEY_NAME_PREFIX) || key.startsWith(KEY_DVB_PREFIX)) {
                    editor.remove(key);
                }
            }
            editor.putInt(KEY_PREFS_VERSION, CURRENT_PREFS_VERSION);
            editor.apply();
        }

        String csv = prefs.getString(KEY_CHANNEL_IDS, null);
        List<Long> ids = new ArrayList<Long>();
        if (csv != null && !csv.trim().isEmpty()) {
            String[] parts = csv.split(",");
            for (String p : parts) {
                try {
                    ids.add(Long.parseLong(p.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (ids.isEmpty()) {
            for (long id = DEFAULT_START_ID; id <= DEFAULT_END_ID; id++) {
                ids.add(id);
            }
        }
        rebuildChannelEntries(ids);
    }

    private final Runnable mCheckTunedChannelLogRunnable = new Runnable() {
        @Override
        public void run() {
            checkCurrentChannelLogsAsync();
        }
    };

    private final Runnable mCheckTunedChannelLogRetryRunnable = new Runnable() {
        @Override
        public void run() {
            checkCurrentChannelLogsAsync();
        }
    };

    private final Runnable mPeriodicEitCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkCurrentChannelLogsAsync();
            mHandler.postDelayed(mPeriodicEitCheckRunnable, 60000);
        }
    };

    private void rebuildChannelEntries(List<Long> ids) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mChannels.clear();
        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            String savedDvb = prefs.getString(KEY_DVB_PREFIX + id, null);
            String savedName = prefs.getString(KEY_NAME_PREFIX + id, null);

            String finalName;
            if (savedName != null && !savedName.trim().isEmpty() && !isDefaultName(savedName)) {
                finalName = savedName.trim();
            } else if (savedDvb != null && GreekDvbCatalog.lookupChannelName(savedDvb) != null) {
                finalName = GreekDvbCatalog.lookupChannelName(savedDvb);
            } else if (savedDvb != null && sDiscoveredTripletToName.containsKey(savedDvb)) {
                finalName = sDiscoveredTripletToName.get(savedDvb);
            } else {
                finalName = "";
            }

            mChannels.add(new ChannelEntry(id, i + 1, finalName, savedDvb != null ? savedDvb : ""));
        }
        sortAndRefreshChannels(false);
    }

    private void sortAndRefreshChannels(boolean preserveCurrentSelection) {
        long currentId = (preserveCurrentSelection && mCurrentIndex >= 0 && mCurrentIndex < mChannels.size())
                ? mChannels.get(mCurrentIndex).id : -1;

        Collections.sort(mChannels, new Comparator<ChannelEntry>() {
            @Override
            public int compare(ChannelEntry a, ChannelEntry b) {
                int rankA = GreekDvbCatalog.getChannelRank(a.dvbTriplet, a.name);
                int rankB = GreekDvbCatalog.getChannelRank(b.dvbTriplet, b.name);
                if (rankA != rankB) {
                    return Integer.compare(rankA, rankB);
                }
                return Long.compare(a.id, b.id);
            }
        });

        List<Long> sortedIds = new ArrayList<Long>();
        int num = 1;
        for (int i = 0; i < mChannels.size(); i++) {
            ChannelEntry ch = mChannels.get(i);
            ch.displayNumber = num;
            if (ch.name == null || ch.name.isEmpty() || isDefaultName(ch.name)) {
                String catName = GreekDvbCatalog.lookupChannelName(ch.dvbTriplet);
                if (catName != null) {
                    ch.name = catName;
                } else {
                    ch.name = String.format(Locale.US, "CH %02d", num);
                }
            }
            sortedIds.add(ch.id);
            if (ch.id == currentId) {
                mCurrentIndex = i;
            }
            num++;
        }
        saveChannelIdsToPrefs(sortedIds);
        refreshChannelLabels();
    }

    private void saveChannelIdsToPrefs(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(ids.get(i));
        }
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CHANNEL_IDS, sb.toString())
                .apply();
    }

    private void refreshChannelLabels() {
        mChannelLabels.clear();
        for (ChannelEntry ch : mChannels) {
            String numStr = String.format(Locale.US, "%02d", ch.displayNumber);
            String titlePart = isDefaultName(ch.name) ? ("CH " + numStr) : ch.name;
            if (ch.currentProgramTitle != null && !ch.currentProgramTitle.isEmpty()) {
                mChannelLabels.add(numStr + "   " + titlePart + "  •  " + ch.currentProgramTitle);
            } else {
                mChannelLabels.add(numStr + "   " + titlePart);
            }
        }
        if (mChannelAdapter != null) {
            mChannelAdapter.notifyDataSetChanged();
        }
    }

    private void tuneToIndex(int index) {
        stopAutoProbe();
        if (mChannels.isEmpty()) return;
        if (index < 0) index = mChannels.size() - 1;
        if (index >= mChannels.size()) index = 0;

        mCurrentIndex = index;
        mLastTuneTimeMs = System.currentTimeMillis();
        ChannelEntry ch = mChannels.get(mCurrentIndex);

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LAST_INDEX, mCurrentIndex)
                .apply();

        mVideoWidth = 0;
        mVideoHeight = 0;
        mAudioTracks.clear();
        mSubtitleTracks.clear();

        String numStr = String.format(Locale.US, "%02d", ch.displayNumber);
        if (isDefaultName(ch.name)) {
            mCurrentVideoStatus = "Tuning CH " + numStr + " (" + ch.getUri().toString() + ") ...";
        } else {
            mCurrentVideoStatus = "Tuning " + ch.name + " (" + ch.getUri().toString() + ") ...";
        }

        if (mTvView != null) {
            mTvView.setStreamVolume(1.0f);
            mTvView.setCaptionEnabled(true);
            mTvView.tune(DTV_INPUT_ID, ch.getUri());
            disableSecureSurface(mTvView);
        }

        mHandler.removeCallbacks(mCheckTunedChannelLogRunnable);
        mHandler.removeCallbacks(mCheckTunedChannelLogRetryRunnable);
        mHandler.removeCallbacks(mPeriodicEitCheckRunnable);
        mHandler.postDelayed(mCheckTunedChannelLogRunnable, 1500);
        mHandler.postDelayed(mCheckTunedChannelLogRetryRunnable, 3500);
        mHandler.postDelayed(mPeriodicEitCheckRunnable, 60000);

        if (mChannelAdapter != null) {
            mChannelAdapter.notifyDataSetChanged();
        }
        showOsdBanner();
    }

    private void checkCurrentChannelLogsAsync() {
        if (mChannels.isEmpty() || mCurrentIndex < 0 || mCurrentIndex >= mChannels.size()) return;
        final ChannelEntry currentCh = mChannels.get(mCurrentIndex);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Process proc = Runtime.getRuntime().exec(new String[]{
                        "logcat", "-d", "-t", "100", "-s", "DtvInputSession:D", "DTVFS:V", "TvPlayer:I", "DtvSetup:I", "dtv.EitHandler:V"
                    });
                    BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    Pattern dvbPattern = Pattern.compile("DTVFS::Open:\\d+\\s+path=/live/dvb:([0-9a-zA-Z._-]+)/media\\.ts");
                    Pattern zapPattern = Pattern.compile("zap on dvb://([0-9a-zA-Z._-]+)");
                    Pattern dumpPattern = Pattern.compile("(dvb://[0-9a-zA-Z._-]+)\\s*:\\s*(.+?)\\s+lcn=(\\d+)");
                    Pattern chPattern = Pattern.compile("Service\\s+(dvb://[0-9a-zA-Z._-]+)\\s+created\\s+->content://android\\.media\\.tv/channel/" + currentCh.id);

                    String foundDvb = null;
                    String foundName = null;
                    String foundProgramTitle = null;
                    String foundProgramGenre = null;
                    final Map<String, String> localTripletToName = new LinkedHashMap<String, String>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Matcher mc = chPattern.matcher(line);
                        if (mc.find()) {
                            foundDvb = mc.group(1);
                        }
                        Matcher md = dvbPattern.matcher(line);
                        if (md.find()) {
                            foundDvb = "dvb://" + md.group(1);
                        }
                        Matcher mz = zapPattern.matcher(line);
                        if (mz.find()) {
                            foundDvb = "dvb://" + mz.group(1);
                        }
                        Matcher mDump = dumpPattern.matcher(line);
                        if (mDump.find()) {
                            String d = mDump.group(1);
                            String n = mDump.group(2).trim();
                            localTripletToName.put(d, n);
                            sDiscoveredTripletToName.put(d, n);
                        }
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
                                        foundProgramTitle = evName.trim();
                                    }
                                    if (genre != null && !genre.trim().isEmpty()) {
                                        foundProgramGenre = genre.trim();
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    reader.close();

                    if (foundDvb != null && foundName == null) {
                        String catName = GreekDvbCatalog.lookupChannelName(foundDvb);
                        if (catName != null) {
                            foundName = catName;
                        } else if (localTripletToName.containsKey(foundDvb)) {
                            foundName = localTripletToName.get(foundDvb);
                        } else if (sDiscoveredTripletToName.containsKey(foundDvb)) {
                            foundName = sDiscoveredTripletToName.get(foundDvb);
                        }
                    }

                    android.util.Log.i("OtaTvChannels", "Tuned ch " + currentCh.id + " -> dvb=" + foundDvb + " name=" + foundName + " prog=" + foundProgramTitle);

                    final String finalDvb = foundDvb;
                    final String finalName = foundName;
                    final String finalProgramTitle = foundProgramTitle;
                    final String finalProgramGenre = foundProgramGenre;

                    if (finalDvb != null || finalName != null || finalProgramTitle != null) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                boolean changed = false;
                                if (finalDvb != null && (currentCh.dvbTriplet == null || currentCh.dvbTriplet.isEmpty() || !finalDvb.equals(currentCh.dvbTriplet))) {
                                    currentCh.dvbTriplet = finalDvb;
                                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                            .edit()
                                            .putString(KEY_DVB_PREFIX + currentCh.id, finalDvb)
                                            .apply();
                                    changed = true;
                                }
                                if (finalName != null && !finalName.isEmpty() && isDefaultName(currentCh.name)) {
                                    currentCh.name = finalName;
                                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                            .edit()
                                            .putString(KEY_NAME_PREFIX + currentCh.id, finalName)
                                            .apply();
                                    changed = true;
                                }
                                boolean newEpgArrived = false;
                                if (finalProgramTitle != null && !finalProgramTitle.equals(currentCh.currentProgramTitle)) {
                                    currentCh.currentProgramTitle = finalProgramTitle;
                                    currentCh.currentProgramGenre = finalProgramGenre;
                                    changed = true;
                                    newEpgArrived = true;
                                }
                                if (changed) {
                                    refreshChannelLabels();
                                    if (mCurrentIndex >= 0 && mCurrentIndex < mChannels.size() && mChannels.get(mCurrentIndex).id == currentCh.id) {
                                        updateOsdText();
                                        if (newEpgArrived) {
                                            boolean isOsdVisible = mOsdBanner != null && mOsdBanner.getVisibility() == View.VISIBLE;
                                            boolean isRecentTune = (System.currentTimeMillis() - mLastTuneTimeMs) < 9000;
                                            if (isOsdVisible || isRecentTune) {
                                                if (mOsdBanner != null) {
                                                    mOsdBanner.setVisibility(View.VISIBLE);
                                                }
                                                scheduleOsdHide(7500);
                                            }
                                        }
                                    }
                                }
                            }
                        });
                    }
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    public void captureScreenToFile(final File file) {
        try {
            final View decor = getWindow().getDecorView();
            int w = decor.getWidth();
            int h = decor.getHeight();
            if (w <= 0 || h <= 0) {
                w = 1920;
                h = 1080;
            }
            final Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    PixelCopy.request(getWindow(), bmp, new PixelCopy.OnPixelCopyFinishedListener() {
                        @Override
                        public void onPixelCopyFinished(int copyResult) {
                            if (copyResult == PixelCopy.SUCCESS) {
                                saveBitmapToFile(bmp, file);
                            } else {
                                drawDecorAndSave(decor, bmp, file);
                            }
                        }
                    }, mHandler);
                    return;
                } catch (Exception ignored) {
                }
            }
            drawDecorAndSave(decor, bmp, file);
        } catch (Exception e) {
            android.util.Log.e("VisionReviveTv", "Screenshot failed", e);
        }
    }

    private void drawDecorAndSave(View decor, Bitmap bmp, File file) {
        Canvas canvas = new Canvas(bmp);
        decor.draw(canvas);
        saveBitmapToFile(bmp, file);
    }

    private void saveBitmapToFile(Bitmap bmp, File file) {
        try {
            FileOutputStream fos = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.flush();
            fos.close();
            bmp.recycle();
            android.util.Log.i("VisionReviveTv", "Screenshot saved to " + file.getAbsolutePath());
        } catch (Exception e) {
            android.util.Log.e("VisionReviveTv", "Failed saving bitmap", e);
        }
    }

    private void disableSecureSurface(ViewGroup vg) {
        if (vg == null) return;
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (child instanceof SurfaceView) {
                try {
                    ((SurfaceView) child).setSecure(false);
                } catch (Exception ignored) {}
            } else if (child instanceof ViewGroup) {
                disableSecureSurface((ViewGroup) child);
            }
        }
    }

    private void showOsdBanner() {
        updateOsdText();
        if (mOsdBanner != null) {
            mOsdBanner.setVisibility(View.VISIBLE);
        }
        long osdTimeout = 5500;
        if (mCurrentIndex >= 0 && mCurrentIndex < mChannels.size()) {
            ChannelEntry ch = mChannels.get(mCurrentIndex);
            if (ch.currentProgramTitle != null && !ch.currentProgramTitle.isEmpty()) {
                osdTimeout = 7500;
            }
        }
        scheduleOsdHide(osdTimeout);
    }

    private void scheduleOsdHide(long delayMs) {
        mHandler.removeCallbacks(mHideOsdRunnable);
        mHandler.postDelayed(mHideOsdRunnable, delayMs);
    }

    private void updateOsdText() {
        if (mOsdTitle == null || mChannels.isEmpty()) return;
        ChannelEntry ch = mChannels.get(mCurrentIndex);
        String numStr = String.format(Locale.US, "%02d", ch.displayNumber);
        if (isDefaultName(ch.name)) {
            mOsdTitle.setText("CH " + numStr);
        } else {
            mOsdTitle.setText("CH " + numStr + "  —  " + ch.name);
        }

        if (mOsdProgram != null) {
            if (ch.currentProgramTitle != null && !ch.currentProgramTitle.isEmpty()) {
                StringBuilder prog = new StringBuilder();
                prog.append("▶  ").append(ch.currentProgramTitle);
                if (ch.currentProgramGenre != null && !ch.currentProgramGenre.isEmpty()) {
                    prog.append("   •   ").append(ch.currentProgramGenre);
                }
                mOsdProgram.setText(prog.toString());
                mOsdProgram.setVisibility(View.VISIBLE);
            } else {
                mOsdProgram.setVisibility(View.GONE);
            }
        }

        StringBuilder sub = new StringBuilder();
        if (mVideoWidth > 0 && mVideoHeight > 0) {
            sub.append(mVideoWidth).append("x").append(mVideoHeight);
        }
        if (!mAudioTracks.isEmpty()) {
            if (sub.length() > 0) sub.append("   |   ");
            sub.append("Audio: ").append(describeSelectedTrack(mAudioTracks, mSelectedAudioTrackId));
        }
        if (!mSubtitleTracks.isEmpty()) {
            if (sub.length() > 0) sub.append("   |   ");
            sub.append("Sub: ").append(describeSelectedTrack(mSubtitleTracks, mSelectedSubtitleTrackId));
        }
        if (ch.dvbTriplet != null && !ch.dvbTriplet.isEmpty()) {
            if (sub.length() > 0) sub.append("   |   ");
            sub.append(ch.dvbTriplet);
        } else {
            if (sub.length() > 0) sub.append("   |   ");
            sub.append("ID ").append(ch.id);
        }
        mOsdSubtitle.setText(sub.toString());
        mOsdStatus.setText(mCurrentVideoStatus
                + "    •    [OK/LEFT] Channel List   [UP/DOWN] Zap   [0-9] Direct Ch");
    }

    private String describeSelectedTrack(List<TvTrackInfo> tracks, String selectedId) {
        if (selectedId == null) return "Off (" + tracks.size() + ")";
        for (int i = 0; i < tracks.size(); i++) {
            TvTrackInfo t = tracks.get(i);
            if (selectedId.equals(t.getId())) {
                String lang = t.getLanguage();
                if (lang == null || lang.isEmpty()) lang = "Track " + (i + 1);
                return lang + " (" + (i + 1) + "/" + tracks.size() + ")";
            }
        }
        return selectedId;
    }

    private void cycleAudioTrack() {
        if (mAudioTracks.isEmpty()) {
            Toast.makeText(this, "No alternate audio tracks on this channel", Toast.LENGTH_SHORT).show();
            return;
        }
        int nextIdx = 0;
        for (int i = 0; i < mAudioTracks.size(); i++) {
            if (mAudioTracks.get(i).getId().equals(mSelectedAudioTrackId)) {
                nextIdx = (i + 1) % mAudioTracks.size();
                break;
            }
        }
        TvTrackInfo next = mAudioTracks.get(nextIdx);
        mTvView.selectTrack(TvTrackInfo.TYPE_AUDIO, next.getId());
        mSelectedAudioTrackId = next.getId();
        updateOsdText();
        Toast.makeText(this, "Audio: " + describeSelectedTrack(mAudioTracks, mSelectedAudioTrackId), Toast.LENGTH_SHORT).show();
    }

    private void cycleSubtitleTrack() {
        if (mSubtitleTracks.isEmpty()) {
            Toast.makeText(this, "No DVB subtitles on this channel", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mSelectedSubtitleTrackId == null) {
            TvTrackInfo first = mSubtitleTracks.get(0);
            mTvView.setCaptionEnabled(true);
            mTvView.selectTrack(TvTrackInfo.TYPE_SUBTITLE, first.getId());
            mSelectedSubtitleTrackId = first.getId();
        } else {
            int curIdx = -1;
            for (int i = 0; i < mSubtitleTracks.size(); i++) {
                if (mSubtitleTracks.get(i).getId().equals(mSelectedSubtitleTrackId)) {
                    curIdx = i;
                    break;
                }
            }
            if (curIdx + 1 < mSubtitleTracks.size()) {
                TvTrackInfo next = mSubtitleTracks.get(curIdx + 1);
                mTvView.selectTrack(TvTrackInfo.TYPE_SUBTITLE, next.getId());
                mSelectedSubtitleTrackId = next.getId();
            } else {
                mTvView.selectTrack(TvTrackInfo.TYPE_SUBTITLE, null);
                mSelectedSubtitleTrackId = null;
            }
        }
        updateOsdText();
        Toast.makeText(this, "Subtitles: " + describeSelectedTrack(mSubtitleTracks, mSelectedSubtitleTrackId), Toast.LENGTH_SHORT).show();
    }

    private void showRenameChannelDialog() {
        if (mChannels.isEmpty()) return;
        final ChannelEntry ch = mChannels.get(mCurrentIndex);

        final List<String> options = new ArrayList<String>();
        options.add("Custom Name (Type with keyboard)...");
        Collections.addAll(options, GREEK_CHANNEL_PRESETS);

        new AlertDialog.Builder(this)
                .setTitle("Name Channel #" + ch.displayNumber + " (ID " + ch.id + ")")
                .setItems(options.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showCustomNameInputDialog(ch);
                        } else {
                            applyChannelName(ch, options.get(which));
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCustomNameInputDialog(final ChannelEntry ch) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(ch.name);
        input.selectAll();

        new AlertDialog.Builder(this)
                .setTitle("Custom Name for Channel #" + ch.displayNumber)
                .setView(input)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String val = input.getText().toString().trim();
                        if (!val.isEmpty()) {
                            applyChannelName(ch, val);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyChannelName(ChannelEntry ch, String newName) {
        ch.name = newName;
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_NAME_PREFIX + ch.id, newName)
                .apply();
        sortAndRefreshChannels(true);
        updateOsdText();
    }

    private void showScannerOptionsDialog() {
        String[] actions = new String[]{
            "1. Auto-Detect Active Channels in TvProvider (Fast Probe)",
            "2. Launch Technicolor OTA Antenna Scanner UI",
            "3. Set Custom Channel ID Range"
        };
        new AlertDialog.Builder(this)
                .setTitle("OTA Channel Discovery & Setup")
                .setItems(actions, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            startAutoProbeChannels(true);
                        } else if (which == 1) {
                            launchTechnicolorOtaScanner();
                        } else if (which == 2) {
                            showCustomRangeDialog();
                        }
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void launchTechnicolorOtaScanner() {
        stopAutoProbe();
        mAutoProbedThisSession = false;
        mWaitingForScannerReturn = true;
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                TvInputInfo info = tim.getTvInputInfo(DTV_INPUT_ID);
                if (info != null) {
                    Intent setupIntent = info.createSetupIntent();
                    if (setupIntent != null) {
                        startActivity(setupIntent);
                        return;
                    }
                }
            }
            Intent fallback = new Intent();
            fallback.setComponent(new ComponentName(
                    "com.technicolor.android.dtvinput",
                    "com.technicolor.android.dtvinput.scan.TvScanActivity"));
            startActivity(fallback);
        } catch (Exception e) {
            Toast.makeText(this, "Could not launch scanner: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopAutoProbe() {
        mProbeStage = PROBE_STAGE_IDLE;
        mCurrentProbeId = -1;
        mHandler.removeCallbacks(mProbeHitTimeoutRunnable);
    }

    private void startAutoProbeChannels(boolean manualTrigger) {
        stopAutoProbe();
        mCoarseCandidates.clear();
        // Priority 1: Check 584..1500 in steps of 10 (new scans get higher auto-increment IDs)
        for (long id = 584; id <= 1500; id += 10) {
            mCoarseCandidates.add(id);
        }
        // Priority 2: Check 545 down to 5 in steps of 10, plus 1
        for (long id = 545; id >= 5; id -= 10) {
            mCoarseCandidates.add(id);
        }
        mCoarseCandidates.add(1L);

        mCoarseIndex = 0;
        mProbeHitId = -1;
        mFoundStartId = -1;
        mFoundEndId = -1;
        mProbeStage = PROBE_STAGE_COARSE;

        if (manualTrigger) {
            Toast.makeText(this, "Probing TvProvider for active OTA channels...", Toast.LENGTH_SHORT).show();
        }
        showOsdBanner();
        mHandler.removeCallbacks(mHideOsdRunnable);
        probeSingleCandidate(mCoarseCandidates.get(0));
    }

    private void probeSingleCandidate(final long candidateId) {
        if (mProbeStage == PROBE_STAGE_IDLE) return;
        mCurrentProbeId = candidateId;
        if (mProbeStage == PROBE_STAGE_COARSE) {
            mCurrentVideoStatus = "Auto-detecting TvProvider channels... checking ID " + candidateId;
        } else if (mProbeStage == PROBE_STAGE_FIND_START) {
            mCurrentVideoStatus = "Locating first channel around ID " + mProbeHitId + " (testing " + candidateId + ")...";
        } else if (mProbeStage == PROBE_STAGE_FIND_END) {
            mCurrentVideoStatus = "Locating last channel from ID " + mFoundStartId + " (testing " + candidateId + ")...";
        }
        updateOsdText();

        mHandler.removeCallbacks(mProbeHitTimeoutRunnable);
        // Fallback timeout in case REASON_TUNING callback is coalesced
        mHandler.postDelayed(mProbeHitTimeoutRunnable, 450);

        if (mTvView != null) {
            mTvView.tune(DTV_INPUT_ID, Uri.parse("content://android.media.tv/channel/" + candidateId));
        }
    }

    private void onProbeCandidateResult(final long candidateId, final boolean exists) {
        if (mProbeStage == PROBE_STAGE_IDLE || candidateId != mCurrentProbeId) return;
        mHandler.removeCallbacks(mProbeHitTimeoutRunnable);

        if (mProbeStage == PROBE_STAGE_COARSE) {
            if (exists) {
                mProbeHitId = candidateId;
                mProbeStage = PROBE_STAGE_FIND_START;
                mBinLow = Math.max(1L, mProbeHitId - 64L);
                mBinHigh = mProbeHitId;
                advanceBinarySearch();
            } else {
                mCoarseIndex++;
                if (mCoarseIndex < mCoarseCandidates.size()) {
                    final long nextId = mCoarseCandidates.get(mCoarseIndex);
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            probeSingleCandidate(nextId);
                        }
                    }, 60);
                } else {
                    stopAutoProbe();
                    mCurrentVideoStatus = "No channels in TvProvider — open OTA Scan to scan antenna";
                    updateOsdText();
                    Toast.makeText(this,
                            "No scanned channels currently in TvProvider. Launching OTA Scan setup option...",
                            Toast.LENGTH_LONG).show();
                }
            }
        } else if (mProbeStage == PROBE_STAGE_FIND_START) {
            if (exists) {
                mBinHigh = candidateId;
            } else {
                mBinLow = candidateId + 1;
            }
            advanceBinarySearch();
        } else if (mProbeStage == PROBE_STAGE_FIND_END) {
            if (exists) {
                mBinLow = candidateId;
            } else {
                mBinHigh = candidateId - 1;
            }
            advanceBinarySearch();
        }
    }

    private void advanceBinarySearch() {
        if (mProbeStage == PROBE_STAGE_FIND_START) {
            if (mBinLow < mBinHigh) {
                final long mid = (mBinLow + mBinHigh) / 2;
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        probeSingleCandidate(mid);
                    }
                }, 60);
            } else {
                mFoundStartId = mBinLow;
                mProbeStage = PROBE_STAGE_FIND_END;
                mBinLow = mProbeHitId;
                mBinHigh = mProbeHitId + 64L;
                advanceBinarySearch();
            }
        } else if (mProbeStage == PROBE_STAGE_FIND_END) {
            if (mBinLow < mBinHigh) {
                final long mid = (mBinLow + mBinHigh + 1) / 2;
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        probeSingleCandidate(mid);
                    }
                }, 60);
            } else {
                mFoundEndId = mBinLow;
                stopAutoProbe();
                if (mFoundStartId > 0 && mFoundEndId >= mFoundStartId) {
                    List<Long> discoveredIds = new ArrayList<Long>();
                    int count = (int) (mFoundEndId - mFoundStartId + 1);
                    for (int i = 0; i < count; i++) {
                        discoveredIds.add(mFoundStartId + i);
                    }
                    saveChannelIdsToPrefs(discoveredIds);
                    rebuildChannelEntries(discoveredIds);
                    Toast.makeText(this,
                            "Found " + discoveredIds.size() + " active OTA channels (IDs "
                                    + mFoundStartId + ".." + mFoundEndId + ")!",
                            Toast.LENGTH_LONG).show();
                    tuneToIndex(0);
                }
            }
        }
    }

    private void showCustomRangeDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), dp(12));

        long firstId = mChannels.isEmpty() ? DEFAULT_START_ID : mChannels.get(0).id;
        long lastId = mChannels.isEmpty() ? DEFAULT_END_ID : mChannels.get(mChannels.size() - 1).id;

        final EditText startEdit = new EditText(this);
        startEdit.setHint("Start Channel ID (e.g. 554)");
        startEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        startEdit.setText(String.valueOf(firstId));
        layout.addView(startEdit);

        final EditText endEdit = new EditText(this);
        endEdit.setHint("End Channel ID (e.g. 583)");
        endEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        endEdit.setText(String.valueOf(lastId));
        layout.addView(endEdit);

        new AlertDialog.Builder(this)
                .setTitle("Set TvProvider Channel ID Range")
                .setView(layout)
                .setPositiveButton("Apply", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            long s = Long.parseLong(startEdit.getText().toString().trim());
                            long e = Long.parseLong(endEdit.getText().toString().trim());
                            if (s > 0 && e >= s && (e - s) <= 250) {
                                List<Long> newIds = new ArrayList<Long>();
                                for (long id = s; id <= e; id++) {
                                    newIds.add(id);
                                }
                                saveChannelIdsToPrefs(newIds);
                                rebuildChannelEntries(newIds);
                                tuneToIndex(0);
                                Toast.makeText(MainActivity.this,
                                        "Loaded " + newIds.size() + " channels (IDs " + s + ".." + e + ")",
                                        Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception ex) {
                            Toast.makeText(MainActivity.this, "Invalid range", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Reads logcat (requires android.permission.READ_LOGS granted via ADB) to detect
     * DtvChannel scan lines of the form:
     *   DtvChannel: Service dvb://2156.104.2 created ->content://android.media.tv/channel/554
     * When a new batch of scanned channels is found in logcat, automatically updates the
     * channel list and DVB triplets!
     */
    private void discoverChannelsFromLogcatAsync(final boolean showFeedback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Map<Long, String> discoveredDvbs = new LinkedHashMap<Long, String>();
                final Map<Long, String> discoveredNames = new LinkedHashMap<Long, String>();
                final Map<Long, Integer> discoveredNumbers = new LinkedHashMap<Long, Integer>();

                // 1. Direct TvProvider ContentResolver Query
                try {
                    Uri[] urisToTry = new Uri[]{
                        TvContract.buildChannelsUriForInput(DTV_INPUT_ID),
                        TvContract.Channels.CONTENT_URI
                    };
                    for (Uri uri : urisToTry) {
                        Cursor c = getContentResolver().query(
                                uri,
                                new String[]{
                                    TvContract.Channels._ID,
                                    TvContract.Channels.COLUMN_DISPLAY_NUMBER,
                                    TvContract.Channels.COLUMN_DISPLAY_NAME,
                                    TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA
                                },
                                null, null, TvContract.Channels._ID + " ASC");
                        if (c != null) {
                            int count = c.getCount();
                            android.util.Log.i("OtaTvChannels", "TvProvider " + uri + " count=" + count);
                            if (count > 0) {
                                int idIdx = c.getColumnIndex(TvContract.Channels._ID);
                                int numIdx = c.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NUMBER);
                                int nameIdx = c.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NAME);
                                int dataIdx = c.getColumnIndex(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA);
                                while (c.moveToNext()) {
                                    long id = c.getLong(idIdx);
                                    String name = nameIdx >= 0 ? c.getString(nameIdx) : null;
                                    String numStr = numIdx >= 0 ? c.getString(numIdx) : null;
                                    String data = dataIdx >= 0 ? c.getString(dataIdx) : null;
                                    android.util.Log.i("OtaTvChannels", "Found ch ID=" + id + " name=" + name + " num=" + numStr);
                                    if (name != null && !name.trim().isEmpty()) {
                                        discoveredNames.put(id, name.trim());
                                    }
                                    if (numStr != null) {
                                        try {
                                            discoveredNumbers.put(id, Integer.parseInt(numStr.trim()));
                                        } catch (Exception ignored) {}
                                    }
                                    if (data != null && data.contains("\"uri\":\"dvb://")) {
                                        int u1 = data.indexOf("\"uri\":\"");
                                        int u2 = data.indexOf("\"", u1 + 7);
                                        if (u1 >= 0 && u2 > u1) {
                                            discoveredDvbs.put(id, data.substring(u1 + 7, u2));
                                        }
                                    }
                                }
                            }
                            c.close();
                            if (!discoveredNames.isEmpty()) {
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("OtaTvChannels", "Error querying TvContract.Channels", e);
                }

                // 2. Parse system logcat for DtvChannel, DtvInputSession, DtvSetup, DTVFS and TvPlayer logs
                try {
                    Process proc = Runtime.getRuntime().exec(new String[]{
                        "logcat", "-d", "-s", "DtvInputSession:D", "DtvChannel:D", "DtvChannelManager:D", "DtvSetup:D", "DtvSetup:I", "DTVFS:V", "TvPlayer:I"
                    });
                    BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    Pattern chPattern = Pattern.compile(
                            "Service\\s+(dvb://[0-9a-zA-Z._-]+)\\s+created\\s+->content://android\\.media\\.tv/channel/(\\d+)");
                    Pattern dumpPattern = Pattern.compile(
                            "(dvb://[0-9a-zA-Z._-]+)\\s*:\\s*(.+?)\\s+lcn=(\\d+)");
                    Pattern dtvfsPattern = Pattern.compile(
                            "DTVFS::Open:\\d+\\s+path=/live/dvb:([0-9a-zA-Z._-]+)/media\\.ts");
                    Pattern tunePattern = Pattern.compile(
                            "(?:onTune\\(content://android\\.media\\.tv/channel/(\\d+)|TvPlayer:\\s+start,\\s+uri:'content://android\\.media\\.tv/channel/(\\d+)')");

                    final Map<String, String> tripletToName = new LinkedHashMap<String, String>();
                    final Map<String, Integer> tripletToNum = new LinkedHashMap<String, Integer>();
                    Long lastSeenPlayerId = null;

                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("deletePreviouslyScannedChannels")) {
                            discoveredDvbs.clear();
                        }
                        Matcher mt = tunePattern.matcher(line);
                        if (mt.find()) {
                            try {
                                String idStr = mt.group(1) != null ? mt.group(1) : mt.group(2);
                                lastSeenPlayerId = Long.parseLong(idStr);
                            } catch (Exception ignored) {}
                        }
                        Matcher mdf = dtvfsPattern.matcher(line);
                        if (mdf.find() && lastSeenPlayerId != null) {
                            discoveredDvbs.put(lastSeenPlayerId, "dvb://" + mdf.group(1));
                        }
                        Matcher mc = chPattern.matcher(line);
                        if (mc.find()) {
                            String dvb = mc.group(1);
                            long id = Long.parseLong(mc.group(2));
                            discoveredDvbs.put(id, dvb);
                        }
                        Matcher md = dumpPattern.matcher(line);
                        if (md.find()) {
                            String dvb = md.group(1);
                            String name = md.group(2).trim();
                            int lcn = Integer.parseInt(md.group(3));
                            tripletToName.put(dvb, name);
                            tripletToNum.put(dvb, lcn);
                        }
                    }
                    reader.close();

                    for (Map.Entry<Long, String> entry : discoveredDvbs.entrySet()) {
                        long id = entry.getKey();
                        String dvb = entry.getValue();
                        if (tripletToName.containsKey(dvb)) {
                            discoveredNames.put(id, tripletToName.get(dvb));
                            sDiscoveredTripletToName.put(dvb, tripletToName.get(dvb));
                        } else if (sDiscoveredTripletToName.containsKey(dvb)) {
                            discoveredNames.put(id, sDiscoveredTripletToName.get(dvb));
                        }
                        if (tripletToNum.containsKey(dvb)) {
                            int num = tripletToNum.get(dvb);
                            discoveredNumbers.put(id, num);
                        }
                    }
                } catch (Exception ignored) {
                }

                if (!discoveredDvbs.isEmpty() || !discoveredNames.isEmpty()) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            for (Map.Entry<Long, String> entry : discoveredDvbs.entrySet()) {
                                editor.putString(KEY_DVB_PREFIX + entry.getKey(), entry.getValue());
                            }
                            for (Map.Entry<Long, String> entry : discoveredNames.entrySet()) {
                                long chId = entry.getKey();
                                String currentSaved = prefs.getString(KEY_NAME_PREFIX + chId, null);
                                if (isDefaultName(currentSaved)) {
                                    editor.putString(KEY_NAME_PREFIX + chId, entry.getValue());
                                }
                            }
                            editor.apply();

                            List<Long> newIds = new ArrayList<Long>();
                            for (ChannelEntry ce : mChannels) {
                                newIds.add(ce.id);
                            }
                            for (Long dId : discoveredDvbs.keySet()) {
                                if (!newIds.contains(dId)) {
                                    newIds.add(dId);
                                }
                            }
                            Collections.sort(newIds);

                            if (!newIds.isEmpty()) {
                                saveChannelIdsToPrefs(newIds);
                                rebuildChannelEntries(newIds);
                                updateOsdText();
                                if (showFeedback) {
                                    Toast.makeText(MainActivity.this,
                                            "Synced " + newIds.size() + " OTA channels with names!",
                                            Toast.LENGTH_LONG).show();
                                }
                            }
                        }
                    });
                } else if (showFeedback) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this,
                                    "Using saved channel range (" + mChannels.size() + " channels)",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void showSidePanel() {
        if (mSidePanel == null) return;
        mSidePanel.setVisibility(View.VISIBLE);
        showOsdBanner();
        mHandler.removeCallbacks(mHideOsdRunnable);
        if (mChannelListView != null) {
            mChannelListView.setSelection(mCurrentIndex);
            mChannelListView.requestFocus();
        }
    }

    private void hideSidePanel() {
        if (mSidePanel == null) return;
        mSidePanel.setVisibility(View.GONE);
        scheduleOsdHide(4000);
    }

    private boolean isSidePanelVisible() {
        return mSidePanel != null && mSidePanel.getVisibility() == View.VISIBLE;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Handle number keys 0-9 for direct channel tuning
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            int digit = keyCode - KeyEvent.KEYCODE_0;
            appendDigit(digit);
            return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            int digit = keyCode - KeyEvent.KEYCODE_NUMPAD_0;
            appendDigit(digit);
            return true;
        }

        // Channel Up / Down dedicated buttons always zap channels
        if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            tuneToIndex(mCurrentIndex + 1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
            tuneToIndex(mCurrentIndex - 1);
            return true;
        }

        // Info / Guide buttons
        if (keyCode == KeyEvent.KEYCODE_INFO || keyCode == KeyEvent.KEYCODE_GUIDE) {
            showOsdBanner();
            return true;
        }

        // Menu button toggles side panel
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (isSidePanelVisible()) {
                hideSidePanel();
            } else {
                showSidePanel();
            }
            return true;
        }

        // When side panel is closed, D-pad controls TV directly
        if (!isSidePanelVisible()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                tuneToIndex(mCurrentIndex + 1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                tuneToIndex(mCurrentIndex - 1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (mDigitBuffer.length() > 0) {
                    commitNumericEntry();
                } else {
                    showSidePanel();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                showOsdBanner();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                long now = System.currentTimeMillis();
                if (now - mLastBackPressMs < 2000) {
                    finish();
                } else {
                    mLastBackPressMs = now;
                    showOsdBanner();
                    Toast.makeText(this, "Press BACK again to exit OTA Live TV", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        } else {
            // Side panel is open
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                hideSidePanel();
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private void appendDigit(int digit) {
        if (mDigitBuffer.length() >= 3) {
            mDigitBuffer.setLength(0);
        }
        mDigitBuffer.append(digit);
        if (mNumericEntryOverlay != null) {
            mNumericEntryOverlay.setText("CH " + mDigitBuffer.toString());
            mNumericEntryOverlay.setVisibility(View.VISIBLE);
        }
        mHandler.removeCallbacks(mCommitDigitsRunnable);
        mHandler.postDelayed(mCommitDigitsRunnable, 1400);
    }

    private void commitNumericEntry() {
        mHandler.removeCallbacks(mCommitDigitsRunnable);
        if (mNumericEntryOverlay != null) {
            mNumericEntryOverlay.setVisibility(View.GONE);
        }
        if (mDigitBuffer.length() == 0) return;
        try {
            int num = Integer.parseInt(mDigitBuffer.toString());
            mDigitBuffer.setLength(0);
            if (num >= 1 && num <= mChannels.size()) {
                tuneToIndex(num - 1);
            } else {
                Toast.makeText(this, "Channel " + num + " out of range (1-" + mChannels.size() + ")", Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException ignored) {
            mDigitBuffer.setLength(0);
        }
    }

    private String formatUnavailableReason(int reason) {
        switch (reason) {
            case TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING:
                return "Tuning DVB-T/T2 transponder...";
            case TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL:
                return "Weak antenna signal";
            case TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING:
                return "Buffering stream...";
            case TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY:
                return "Audio-only service (Radio)";
            default:
                return "Reason code " + reason;
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics());
    }
}
