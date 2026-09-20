package systems.sieber.remotespotlight;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;

import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.Result;

import java.util.Timer;
import java.util.TimerTask;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class ControlActivity extends AppCompatActivity implements ZXingScannerView.ResultHandler {

    ControlActivity me;
    TcpClient mTcpClient;
    FeatureCheck fc;
    SharedPreferences mSettings;

    ClipboardManager mClipboard;
    String mLastClipboardText;
    boolean mSyncClipboard;

    boolean sendValues = false;
    boolean ignoreKeyboardTextChanges = false;
    boolean keyboardWasVisible = false;

    String mAddress;
    int mPort;
    String mAuthCode;

    private final int REQUEST_HELP = 1;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);
        me = this;
        mSettings = getSharedPreferences(ConnectActivity.PREFS_NAME, 0);

        // init toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        updateBottomNavigationSelection(R.id.buttonMouseMode);

        // do feature check
        fc = new FeatureCheck(this);
        fc.init();

        // show volume button hint
        if(!mSettings.getBoolean("volume-hint-shown", false)) {
            Snackbar.make(findViewById(R.id.controlMainView), getResources().getString(R.string.volume_button_hint), Snackbar.LENGTH_LONG).show();
            SharedPreferences.Editor edit = mSettings.edit();
            edit.putBoolean("volume-hint-shown", true);
            edit.apply();
        }

        EditText keyboardText = findViewById(R.id.editTextControlKeyboardText);
        keyboardText.addTextChangedListener(new TextWatcher() {
            private String removedText = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                removedText = s.subSequence(start, start + count).toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(ignoreKeyboardTextChanges) return;
                if(before == 0 && count == 0) return;
                if(fc == null || !fc.unlockedKeyboard) {
                    dialogInApp(getResources().getString(R.string.feature_locked_keyboard), getResources().getString(R.string.feature_locked_text));
                    return;
                }

                int removedCodePoints = removedText.codePointCount(0, removedText.length());
                for(int i = 0; i < removedCodePoints; i++) sendBackspace();
                sendImmediateText(s.subSequence(start, start + count).toString());
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.controlMainView), (view, insets) -> {
            boolean keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            findViewById(R.id.bottomNavigation).setVisibility(
                    keyboardVisible ? View.GONE : View.VISIBLE);
            if(keyboardWasVisible && !keyboardVisible) dismissKeyboardOverlay();
            keyboardWasVisible = keyboardVisible;
            return insets;
        });

        (findViewById(R.id.buttonSpotlight)).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch( event.getAction() ) {
                    case MotionEvent.ACTION_DOWN:
                        if(mTcpClient != null) mTcpClient.sendMessage("START");
                        sendValues = true;
                        break;
                    case MotionEvent.ACTION_UP:
                        if(mTcpClient != null) mTcpClient.sendMessage("STOP");
                        sendValues = false;
                        break;
                }
                return false;
            }
        });
        (findViewById(R.id.buttonMouseLeft)).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch( event.getAction() ) {
                    case MotionEvent.ACTION_DOWN:
                        if(mTcpClient != null) mTcpClient.sendMessage("MDOWN");
                        break;
                    case MotionEvent.ACTION_UP:
                        if(mTcpClient != null) mTcpClient.sendMessage("MUP");
                        break;
                }
                return false;
            }
        });
        (findViewById(R.id.buttonMouseRight)).setOnTouchListener(new View.OnTouchListener() {
            @SuppressWarnings("SwitchStatementWithTooFewBranches")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch( event.getAction() ) {
                    case MotionEvent.ACTION_DOWN:
                        if(mTcpClient != null) mTcpClient.sendMessage("MRIGHT");
                        break;
                }
                return false;
            }
        });
        (findViewById(R.id.buttonTouchpad)).setOnTouchListener(new View.OnTouchListener() {
            @SuppressWarnings("FieldCanBeLocal")
            private final int maxPixelMovementForMouseClick = 5;
            private int _xDelta;
            private int _yDelta;
            private int _xDown;
            private int _yDown;
            long startTime;
            boolean sendMouse = false;
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                final int X = (int) event.getRawX();
                final int Y = (int) event.getRawY();
                switch(event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        startTime = System.currentTimeMillis();
                        _xDelta = X;
                        _yDelta = Y;
                        _xDown = X;
                        _yDown = Y;
                        sendMouse = true;
                        break;
                    case MotionEvent.ACTION_UP:
                        sendMouse = false;
                        long difference = System.currentTimeMillis() - startTime;
                        if(difference < 400
                                && Math.abs(Math.abs(_xDown) - Math.abs(X)) < maxPixelMovementForMouseClick
                                && Math.abs(Math.abs(_yDown) - Math.abs(Y)) < maxPixelMovementForMouseClick) {
                            if(mTcpClient != null) mTcpClient.sendMessage("MLEFT");
                        }
                        break;
                    case MotionEvent.ACTION_POINTER_DOWN:
                    case MotionEvent.ACTION_POINTER_UP:
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if(sendMouse && mTcpClient != null && X - _xDelta != 0 && Y - _yDelta != 0)
                            mTcpClient.sendMessage("M"+"|"+Integer.toString(X - _xDelta)+"|"+Integer.toString(Y - _yDelta));
                        _xDelta = X;
                        _yDelta = Y;
                        break;
                }
                return true;
            }
        });

        //dv = (DemoView) findViewById(R.id.view_demo);

        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if(rotationVectorSensor == null) {
            Log.e("sensors", "Sensor not available.");
        }
        SensorEventListener sensorListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                //dv.update(-sensorEvent.values[2] * 10, -sensorEvent.values[0] * 10);
                if(mTcpClient != null && sendValues) {
                    if(Math.abs(sensorEvent.values[2]) > 0.01f && Math.abs(sensorEvent.values[0]) > 0.01f)
                        mTcpClient.sendMessage("S"+"|"+Float.toString(-sensorEvent.values[2])+"|"+Float.toString(-sensorEvent.values[0]));
                }
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {
            }
        };
        sensorManager.registerListener(
                sensorListener,
                rotationVectorSensor,
                SensorManager.SENSOR_DELAY_GAME
        );

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // setup clipboard listener
        mClipboard = (ClipboardManager) this.getSystemService(CLIPBOARD_SERVICE);
        mSyncClipboard = mSettings.getBoolean("sync-clipboard", false);

        // establish connection to server
        Intent intent = getIntent();
        mAddress = intent.getStringExtra("address");
        mPort = intent.getIntExtra("port",4444);
        mAuthCode = intent.getStringExtra("authCode");
        connect();

        // send periodic ping packets
        TimerTask taskCheckEvent = new TimerTask() {
            @Override
            public void run() {
                // server will disconnect if no message received within 5 seconds
                if(mTcpClient != null) mTcpClient.sendMessage("PING");
            }
        };
        new Timer(false).schedule(taskCheckEvent, 0, 2000);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_control, menu);
        menu.findItem(R.id.action_sync_clipboard).setChecked(mSyncClipboard);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_sync_clipboard:
                item.setChecked(!item.isChecked());
                mSyncClipboard = item.isChecked();
                SharedPreferences.Editor edit = mSettings.edit();
                edit.putBoolean("sync-clipboard", mSyncClipboard);
                edit.apply();
                break;
            case android.R.id.home:
                finish();
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    public void showMouseControls(View view) {
        EditText keyboardText = findViewById(R.id.editTextControlKeyboardText);
        hideKeyboard(keyboardText);
        dismissKeyboardOverlay();
        findViewById(R.id.linearLayoutControlDefaults).setVisibility(View.VISIBLE);
        findViewById(R.id.constraintLayoutControlScanner).setVisibility(View.GONE);
        if(mScannerView != null) mScannerView.stopCamera();
        updateBottomNavigationSelection(R.id.buttonMouseMode);
    }

    public void showKeyboardOverlay(View view) {
        if(fc == null || !fc.unlockedKeyboard) {
            dialogInApp(getResources().getString(R.string.feature_locked_keyboard), getResources().getString(R.string.feature_locked_text));
            return;
        }

        findViewById(R.id.editTextControlKeyboardText).setVisibility(View.VISIBLE);
        updateBottomNavigationSelection(R.id.buttonKeyboardMode);
        showKeyboard();
    }

    public void showScannerControls(View view) {
        EditText keyboardText = findViewById(R.id.editTextControlKeyboardText);
        hideKeyboard(keyboardText);
        dismissKeyboardOverlay();
        setupCamera();
        findViewById(R.id.linearLayoutControlDefaults).setVisibility(View.GONE);
        findViewById(R.id.constraintLayoutControlScanner).setVisibility(View.VISIBLE);
        updateBottomNavigationSelection(R.id.buttonScannerMode);
    }

    private void connect() {
        new ConnectTask(mAddress, mPort).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void askReconnect() {
        AlertDialog.Builder ad = new AlertDialog.Builder(this);
        ad.setTitle(getString(R.string.connfailed_title));
        ad.setIcon(getDrawable(R.drawable.ic_warning_orange_24dp));
        ad.setPositiveButton(getString(R.string.reconnect), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                connect();
            }
        });
        ad.setNeutralButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        ad.setCancelable(false);
        ad.show();
    }

    private void dialogInApp(String title, String text) {
        AlertDialog.Builder ad = new AlertDialog.Builder(this);
        ad.setTitle(title);
        ad.setMessage(text);
        ad.setIcon(getDrawable(R.drawable.ic_warning_orange_24dp));
        ad.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        ad.setNeutralButton(getString(R.string.more), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startActivityForResult(new Intent(me, HelpActivity.class), REQUEST_HELP);
            }
        });
        ad.show();
    }

    private void showKeyboard() {
        View inputView = findViewById(R.id.editTextControlKeyboardText);
        inputView.requestFocus();

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if(imm != null) {
            inputView.post(() -> imm.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT));
        }
    }
    private void hideKeyboard(EditText et) {
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(imm != null) {
            imm.hideSoftInputFromWindow(et.getWindowToken(), 0);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK
                && findViewById(R.id.editTextControlKeyboardText).getVisibility() == View.VISIBLE) {
            EditText keyboardText = findViewById(R.id.editTextControlKeyboardText);
            hideKeyboard(keyboardText);
            dismissKeyboardOverlay();
            return true;
        } else if(keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
        } else if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if(mTcpClient != null) mTcpClient.sendMessage("VOLUMEDOWN");
            return true;
        } else if(keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if(mTcpClient != null) mTcpClient.sendMessage("VOLUMEUP");
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void finish() {
        if(mTcpClient != null) mTcpClient.stopClient();
        super.finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(findViewById(R.id.constraintLayoutControlScanner).getVisibility() == View.VISIBLE
                && mScannerView != null) {
            mScannerView.startCamera();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus && mSyncClipboard) {
            CharSequence clipboardText = mClipboard.getText();
            if(clipboardText != null && !clipboardText.toString().equals(mLastClipboardText)) {
                mLastClipboardText = clipboardText.toString();
                if(mTcpClient != null) mTcpClient.sendMessage("CLIPBOARD|"+mLastClipboardText);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        EditText keyboardText = findViewById(R.id.editTextControlKeyboardText);
        hideKeyboard(keyboardText);
        dismissKeyboardOverlay();
        if(mScannerView != null) mScannerView.stopCamera();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_HELP) {
            Log.i("FEATURECHECK", "init new feature check");
            fc = new FeatureCheck(this);
            fc.init();
        }
    }

    @Override
    public void handleResult(Result rawResult) {
        vibrate();
        if(fc != null && fc.unlockedScanner) {
            String code = rawResult.getText();
            if(mTcpClient != null) {
                mTcpClient.sendMessage("TEXT|"+code);
                if(((CheckBox) findViewById(R.id.checkBoxControlScannerReturn)).isChecked())
                    mTcpClient.sendMessage("RETURN");
            }
            dialogScanned(code);
        } else {
            dialogInApp(getResources().getString(R.string.feature_locked_scanner), getResources().getString(R.string.feature_locked_text));
        }
    }

    private void dialogScanned(String text) {
        new AlertDialog.Builder(this)
                .setMessage( text )
                .setPositiveButton(getResources().getString(R.string.next_scan), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        mScannerView.resumeCameraPreview(me);
                    }})
                //.setNegativeButton(getResources().getString(R.string.abort), null)
                .show();
    }

    private void vibrate() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if(v != null) {
                v.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } else {
            //deprecated in API 26
            if(v != null) {
                v.vibrate(100);
            }
        }
    }

    private void sendImmediateText(String text) {
        int segmentStart = 0;
        for(int i = 0; i < text.length(); i++) {
            if(text.charAt(i) == '\n') {
                sendMessage(text.substring(segmentStart, i));
                sendReturn();
                segmentStart = i + 1;
            }
        }
        sendMessage(text.substring(segmentStart));
    }

    private void clearKeyboardText() {
        EditText keyboardText = findViewById(R.id.editTextControlKeyboardText);
        if(keyboardText.length() == 0) return;

        ignoreKeyboardTextChanges = true;
        keyboardText.setText("");
        ignoreKeyboardTextChanges = false;
    }

    private void dismissKeyboardOverlay() {
        EditText keyboardText = findViewById(R.id.editTextControlKeyboardText);
        clearKeyboardText();
        keyboardText.clearFocus();
        keyboardText.setVisibility(View.GONE);
        keyboardWasVisible = false;

        int selectedButton = findViewById(R.id.constraintLayoutControlScanner).getVisibility() == View.VISIBLE
                ? R.id.buttonScannerMode
                : R.id.buttonMouseMode;
        updateBottomNavigationSelection(selectedButton);
    }

    private void updateBottomNavigationSelection(int selectedButton) {
        findViewById(R.id.buttonMouseMode).setSelected(selectedButton == R.id.buttonMouseMode);
        findViewById(R.id.buttonKeyboardMode).setSelected(selectedButton == R.id.buttonKeyboardMode);
        findViewById(R.id.buttonScannerMode).setSelected(selectedButton == R.id.buttonScannerMode);
    }

    public void sendMessage(String text) {
        if(!text.equals("")) {
            if(mTcpClient != null) mTcpClient.sendMessage("TEXT|"+text);
        }
    }
    public void sendReturn() {
        if(mTcpClient != null) mTcpClient.sendMessage("RETURN");
    }
    public void sendBackspace() {
        if(mTcpClient != null) mTcpClient.sendMessage("BACKSPACE");
    }
    public void sendPrev(View v) {
        if(mTcpClient != null) mTcpClient.sendMessage("PREV");
    }
    public void sendNext(View v) {
        if(mTcpClient != null) mTcpClient.sendMessage("NEXT");
    }

    private final static int CAMERA_PERMISSION = 1;
    private ZXingScannerView mScannerView;
    private void setupCamera() {
        // init scanner
        mScannerView = findViewById(R.id.scannerView);
        mScannerView.setFlash(false);
        mScannerView.setAutoFocus(true);
        mScannerView.setAspectTolerance(0.5f);
        mScannerView.setResultHandler(this);
        mScannerView.startCamera();

        // check camera permission
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        }
    }

    public enum messageType {
        normalExit,
        authFailed,
        connectionFailed,
        connectionClosed
    }
    private void finishWithMessage(messageType m) {
        Intent returnIntent = getIntent();
        returnIntent.putExtra("result",m);
        setResult(Activity.RESULT_OK,returnIntent);
        finish();
    }

    public class ConnectTask extends AsyncTask<String, String, TcpClient> {

        private final String address;
        private final int port;

        ConnectTask(String _address, int _port) {
            address = _address;
            port = _port;
        }

        @Override
        protected TcpClient doInBackground(String... message) {
            try {
                mTcpClient = new TcpClient(address, port,
                        new TcpClient.OnMessageReceived() {
                            @Override
                            // here the messageReceived method is implemented
                            public void messageReceived(String message) {
                                if(message.equals("HELLO!")) {
                                    Log.d("Sending authcode", "--> "+mAuthCode);
                                    mTcpClient.sendMessage(mAuthCode);
                                }
                                // this method calls the onProgressUpdate
                                publishProgress(message);
                            }
                        },
                        new TcpClient.OnConnectionClosed() {
                            @Override
                            public void connectionClosed(boolean authFailed) {
                                if(authFailed) finishWithMessage(messageType.authFailed);
                                else finishWithMessage(messageType.connectionClosed);
                            }
                        },
                        new TcpClient.OnConnectionFailed() {
                            @Override
                            public void connectionFailed() {
                                finishWithMessage(messageType.connectionFailed);
                            }
                        });
                mTcpClient.run();
            } catch(ConnectionAbortException e) {
                publishProgress("RECONNECT");
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            if(values[0].equals("RECONNECT")) {
                askReconnect();
            } else if(values[0].startsWith("CLIPBOARD|")
            && mSyncClipboard) {
                String t = values[0].substring(10);
                mLastClipboardText = t;
                mClipboard.setText(t);
            }
        }

    }
}
