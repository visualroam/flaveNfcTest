package at.flave.nfc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.os.Handler;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import static android.provider.Settings.ACTION_NFCSHARING_SETTINGS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Created by peterlampesberger on 25.03.16.
 */
public abstract class FlaveNFCActivity extends Activity {
    private static final String TAG = "FlaveNFCActivity";
    public static long NFC_RATE_LIMIT_DELAY = MILLISECONDS.convert(4, SECONDS);
    public static Long lastNFCScanTimestamp;
    public static String lastNfcRFID = "";
    protected NfcAdapter mNfcAdapter;
    IntentFilter[] intentFiltersArray;

    PendingIntent pendingIntent;
    protected NfcAdapter.ReaderCallback nfcReaderCallback;

    public String[][] techListsArray = null;
    public boolean hideInterfaces = true;
    protected EditText rfidText;
    protected boolean rfidTextSelectable = false;
    protected boolean default_sound_enabled = true;
    public boolean disableRfidTextFieldCallbacks = false;
    private boolean shortPress = false;
    //protected SoundHandler soundHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();


        techListsArray = new String[][]{new String[]{
                NfcA.class.getName(),
                /*NfcB.class.getName(),
                NfcF.class.getName(),
                NfcV.class.getName(),
                Ndef.class.getName(),
                NdefFormatable.class.getName(),
                MifareClassic.class.getName(),
                MifareUltralight.class.getName(),*/

        }};

        IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        this.registerReceiver(mReceiver, filter);

        setupNfcReaderCallback();
        setupNfcAdapter();

    }

    protected void setupNfcReaderCallback() {
        if (this.nfcReaderCallback == null) {
            this.nfcReaderCallback = new NfcAdapter.ReaderCallback() {
                @Override
                public void onTagDiscovered(final Tag tag) {
                    final String rfid = getRfidFromBytes(tag.getId()).toUpperCase();
                    Log.wtf(TAG, this.getClass().getName() + " READ RFID" + rfid);
                    if (isNfcTimeoutActive(rfid)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onNfcTimeoutActive(rfid, System.currentTimeMillis() - lastNFCScanTimestamp);
                            }
                        });
                        return;
                    }

                    lastNFCScanTimestamp = System.currentTimeMillis();
                    lastNfcRFID = rfid;

                    // do something
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            handlePossibleAdminRfid(rfid);
                        }
                    });

                }
            };
        }
    }


    private String getTag() {
        return "FlaveNFCActivity:" + this.getClass().getName();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            if (action.equals(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)) {
                final int state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE,
                        NfcAdapter.STATE_OFF);
                switch (state) {
                    case NfcAdapter.STATE_OFF:
                        break;
                    case NfcAdapter.STATE_TURNING_OFF:
                        break;
                    case NfcAdapter.STATE_ON:
                        if (mNfcAdapter == null) {
                            setupNfcAdapter();
                        }
                        /*if(mNfcAdapter != null && !mNfcAdapter.isEnabled()) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mNfcAdapter.enableForegroundDispatch(context.getApplicationContext().getClass(), pendingIntent, intentFiltersArray, techListsArray);
                                }
                            });
                        }*/
                        break;
                    case NfcAdapter.STATE_TURNING_ON:
                        break;
                }
            }
        }
    };

    protected void setupNfcAdapter() {
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        if (mNfcAdapter == null) {
            Log.wtf(getTag(), "NO NFC ADAPTER RECEIVED (==null) FROM NfcAdapter.getDefaultAdapter(this)");
        } else {
            mNfcAdapter.enableReaderMode(this,
                    nfcReaderCallback,
                    NfcAdapter.FLAG_READER_NFC_A |
                            NfcAdapter.FLAG_READER_NFC_B |
                            NfcAdapter.FLAG_READER_NFC_F |
                            NfcAdapter.FLAG_READER_NFC_V |
                            NfcAdapter.FLAG_READER_NFC_BARCODE |
                            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    null);

            pendingIntent = PendingIntent.getActivity(
                    this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        }
        IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        try {
            ndef.addDataType("*/*");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            Log.wtf(getTag(), e);
            e.printStackTrace();
        }
        intentFiltersArray = new IntentFilter[]{ndef};
    }


    /**
     * Determines weather there was a scan within the delay defined trough "NFC_RATE_LIMIT_DELAY".
     *
     * @return True if currently is timeout otherwise false
     */
    protected boolean isNfcTimeoutActive(String rfid) {
        if (lastNFCScanTimestamp == null || lastNfcRFID == null || lastNfcRFID.isEmpty())
            return false;

        return lastNFCScanTimestamp > (System.currentTimeMillis() - NFC_RATE_LIMIT_DELAY) && lastNfcRFID.equals(rfid);
    }

    protected void onNfcTimeoutActive(String rfid, final long msSinceLastScan) {
        if (Build.MODEL.startsWith("FX200") && msSinceLastScan < 1000) {
            // ignore, since it triggers the nfc reading event twice so fast
            return;
        }


        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Too fast - please wait " + Math.round((NFC_RATE_LIMIT_DELAY - msSinceLastScan) / 1000l) + " seconds...", Toast.LENGTH_SHORT).show();
            }
        });
    }

    protected void setNfcTimeoutDelay(long ms) {
        NFC_RATE_LIMIT_DELAY = ms;
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (rfidText != null)
            rfidText.requestFocus();

        if (hideInterfaces) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }

        if (mNfcAdapter != null && mNfcAdapter.isEnabled()) {
            mNfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
            if (mNfcAdapter.isNdefPushEnabled() && (Build.MODEL.startsWith("FX100") || Build.MODEL.startsWith("FX200") || Build.MODEL.startsWith("FX300"))) {
                // Android Beam is disabled, show the settings UI
                // to enable Android Beam
                Toast.makeText(this, "Please disable Android Beam first!",
                        Toast.LENGTH_LONG).show();
                startActivity(new Intent(ACTION_NFCSHARING_SETTINGS));
            }
        }

    }

    @Override
    public void onDestroy() {

        super.onDestroy();
        // Remove the broadcast listener
        try {
            this.unregisterReceiver(mReceiver);
        } catch (IllegalArgumentException ignored) {
        }


    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);


        if (hideInterfaces) {
            View decorView = getWindow().getDecorView();
            if (hasFocus) {
                decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra(NfcAdapter.EXTRA_ID)) {
            handlePossibleAdminRfid(getRfidFromBytes(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mNfcAdapter != null && mNfcAdapter.isEnabled()) mNfcAdapter.disableForegroundDispatch(this);
    }


    public String getRfidFromBytes(byte[] tagId) {

        byte[] newTag = null;
        if (tagId != null) {
            newTag = new byte[tagId.length];
            System.arraycopy(tagId, 0, newTag, 0, tagId.length);
        }

        return ByteArrayToHexString(newTag);
    }

    private static String ByteArrayToHexString(byte[] inarray) {
        int i, j, in;
        String[] hex = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F"};
        String out = "";

        // Reversed
        //for (j = inarray.length - 1; j >= 0; --j) {

        // Default Order
        for (j = 0; j < inarray.length; j++) {
            in = (int) inarray[j] & 0xff;
            i = (in >> 4) & 0x0f;
            out += hex[i];
            i = in & 0x0f;
            out += hex[i];
        }
        return out;
    }

    private void handlePossibleAdminRfid(String rfid) {
        processNFCRfid(rfid);
    }

    protected void processNFCRfid(String rfid) {
    }
}
