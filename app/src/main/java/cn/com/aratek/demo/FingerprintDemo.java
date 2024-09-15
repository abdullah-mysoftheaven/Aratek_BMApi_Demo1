package cn.com.aratek.demo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import cn.com.aratek.fp.Bione;
import cn.com.aratek.fp.FingerprintImage;
import cn.com.aratek.fp.FingerprintScanner;
import cn.com.aratek.util.Result;

import static cn.com.aratek.fp.FingerprintImage.IMAGE_FORMAT_WSQ;
import static cn.com.aratek.fp.FingerprintImage.fromData;





@SuppressLint({"SdCardPath", "HandlerLeak"})
public class FingerprintDemo extends Activity implements View.OnClickListener
{

    private static final String TAG = FingerprintDemo.class.getSimpleName();
    private static final String FP_DB_PATH = "/sdcard/fp.db";

    private static final int MSG_SHOW_ERROR = 0;
    private static final int MSG_SHOW_INFO = 1;
    private static final int MSG_UPDATE_IMAGE = 2;
    private static final int MSG_UPDATE_BUTTON = 4;
    private static final int MSG_UPDATE_SN = 5;
    private static final int MSG_UPDATE_FW_VERSION = 6;
    private static final int MSG_SHOW_PROGRESS_DIALOG = 7;
    private static final int MSG_DISMISS_PROGRESS_DIALOG = 8;
    private static final int MSG_SHOW_CAPTURE_TIME = 9;
    private static final String[] PERMISSIONS = { Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE};
    private PermissionsManager mPermissionsManager;
    private TextView mInformation;
    private TextView mDetails;
    private TextView mSN;
    private TextView mFwVersion;
    private Button mBtnEnroll;
    private Button mBtnVerify;
    private Button mBtnIdentify;
    private Button mBtnClear;
    private Button mBtnShow;
    private EditText mFpId;
    private ImageView mFingerprintImage;
    private ProgressDialog mProgressDialog;
    private FingerprintScanner mScanner;
    private FingerprintImage mLastImage;
    private byte[][] mLastFeatures;
    private FingerprintTask mTask;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SHOW_ERROR: {
                    mInformation.setTextColor(getResources().getColor(R.color.error_text_color));
                    mDetails.setTextColor(getResources().getColor(R.color.error_details_text_color));
                    mInformation.setText(((Bundle) msg.obj).getString("information"));
                    mDetails.setText(((Bundle) msg.obj).getString("details"));
                    break;
                }
                case MSG_SHOW_INFO: {
                    mInformation.setTextColor(getResources().getColor(R.color.information_text_color));
                    mDetails.setTextColor(getResources().getColor(R.color.information_details_text_color));
                    mInformation.setText(((Bundle) msg.obj).getString("information"));
                    mDetails.setText(((Bundle) msg.obj).getString("details"));
                    break;
                }
                case MSG_UPDATE_IMAGE: {
                    mFingerprintImage.setImageBitmap((Bitmap) msg.obj);
                    break;
                }
                case MSG_UPDATE_BUTTON: {
                    Boolean enable = (Boolean) msg.obj;
                    mBtnEnroll.setEnabled(enable);
                    mBtnVerify.setEnabled(enable);
                    mBtnIdentify.setEnabled(enable);
                    mBtnClear.setEnabled(enable);
                    mBtnShow.setEnabled(enable);
                    break;
                }
                case MSG_UPDATE_SN: {
                    mSN.setText((String) msg.obj);
                    break;
                }
                case MSG_UPDATE_FW_VERSION: {
                    mFwVersion.setText((String) msg.obj);
                    break;
                }
                case MSG_SHOW_PROGRESS_DIALOG: {
                    String[] info = (String[]) msg.obj;
                    mProgressDialog.setTitle(info[0]);
                    mProgressDialog.setMessage(info[1]);
                    mProgressDialog.show();
                    break;
                }
                case MSG_DISMISS_PROGRESS_DIALOG: {
                    mProgressDialog.dismiss();
                    break;
                }
                case MSG_SHOW_CAPTURE_TIME: {
                    mFwVersion.setText(((Bundle) msg.obj).getString("time"));
                    break;
                }

            }
        }
    };



    /**
     * //     * 检查蓝牙权限
     */
    public void checkBlePermission() {

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

//            ActivityCompat.requestPermissions(this,
//                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},


//                    1);
            ActivityCompat.requestPermissions(this,
                    PERMISSIONS,
                    1);


        } else {
            Log.i("tag", "已申请权限");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fingerprint);
        checkBlePermission();
        mScanner = FingerprintScanner.getInstance(this);
       // mScanner.setDevicePath("/dev/ttyS1");

        mInformation = (TextView) findViewById(R.id.tv_info);
        mDetails = (TextView) findViewById(R.id.tv_details);
        mSN = (TextView) findViewById(R.id.tv_fps_sn);
        mFwVersion = (TextView) findViewById(R.id.tv_fps_fw);
        mFpId = (EditText) findViewById(R.id.et_id);
        mFingerprintImage = (ImageView) findViewById(R.id.fingerimage);

        mBtnEnroll = (Button) findViewById(R.id.bt_enroll);
        mBtnVerify = (Button) findViewById(R.id.bt_verify);
        mBtnIdentify = (Button) findViewById(R.id.bt_identify);
        mBtnClear = (Button) findViewById(R.id.bt_clear);
        mBtnShow = (Button) findViewById(R.id.bt_show);

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
        mProgressDialog.setIndeterminate(false);
        mProgressDialog.setCancelable(false);

        enableControl(false);


    }

    @Override
    protected void onResume() {
        super.onResume();

        openDevice();
    }

    @Override
    protected void onPause() {
        closeDevice();

        super.onPause();
    }



    @Override
    public void onClick(View v) {

        int id = v.getId();

        if (id == R.id.bt_enroll) {
            enroll();
        } else if (id == R.id.bt_verify) {
            verify();
        } else if (id == R.id.bt_identify) {
            identify();
        } else if (id == R.id.bt_clear) {
            clearFingerprintDatabase();
        } else if (id == R.id.bt_delete) {
            deleteFingerprintFromDatabase();
        } else if (id == R.id.bt_show) {
            showFingerprintImage();
        } else if (id == R.id.bt_get_capacity) {
            getCapacity();
        } else if (id == R.id.bt_get_enrolled_count) {
            getEnrolledCount();
        } else if (id == R.id.bt_get_enrolled_list) {
            getEnrolledList();
        } else if (id == R.id.bt_get_template_from_chip) {
            getTemplateFromChip();
        } else if (id == R.id.bt_download_template_to_chip) {
            downloadTemplateToChip();
        } else if (id == R.id.bt_capture_feature) {
            captureFeature();
        } else if (id == R.id.bt_extract_feature) {
            extractFeature();
        } else if (id == R.id.bt_capture_iso_feature) {
            captureISOFeature();
        } else if (id == R.id.bt_extract_iso_feature) {
            extractISOFeature();
        }
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Intent intent = new Intent();
            intent.setAction("android.intent.action.MAIN");
            intent.addCategory("android.intent.category.HOME");
            startActivity(intent);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void updateFingerprintImage(FingerprintImage fi) {
        byte[] fpBmp = null;
        Bitmap bitmap;
        if (fi == null || (fpBmp = fi.convert2Bmp()) == null || (bitmap = BitmapFactory.decodeByteArray(fpBmp, 0, fpBmp.length)) == null) {
            bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.nofinger);
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_IMAGE, bitmap));
    }

    private void enableControl(boolean enable) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_BUTTON, enable));
    }

    private void openDevice() {
        new Thread() {
            @Override
            public void run() {
                synchronized (FingerprintDemo.this) {
                    showProgressDialog(getString(R.string.loading), getString(R.string.preparing_device));
                    int error;
                   // mScanner.PowerOnOff(1); //上电
                    //Log.i("Sanny","S8 PowerOnOff 1.");
//                    try {
//                        Thread.sleep(2000);
//                    } catch (InterruptedException e) {
//                        Thread.currentThread().interrupt();
//                    }
//                    if ((error = mScanner.powerOn()) != FingerprintScanner.RESULT_OK) {
//                        showError(getString(R.string.fingerprint_device_power_on_failed), getFingerprintErrorString(error));
//                    }
                    if ((error = mScanner.open()) != FingerprintScanner.RESULT_OK) {
                        mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_SN, getString(R.string.fps_sn, "null")));
                        mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_FW_VERSION, getString(R.string.fps_fw, "null")));
                        showError(getString(R.string.fingerprint_device_open_failed), getFingerprintErrorString(error));
                    } else {
                        Result res = mScanner.getSN();
                        mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_SN, getString(R.string.fps_sn, (String) res.data)));
                        res = mScanner.getFirmwareVersion();
                        mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_FW_VERSION, getString(R.string.fps_fw, (String) res.data)));
                        showInformation(getString(R.string.fingerprint_device_open_success), null);
                        enableControl(true);


                    }



                    ///myerror
                    File file = new File(FP_DB_PATH);
                    if (!file.exists()) {
                        Log.e(TAG, "Database file not found at path: " + FP_DB_PATH);
                    }
                    int errorrr = Bione.initialize(FingerprintDemo.this, FP_DB_PATH);
                    if (error != Bione.RESULT_OK) {
                        Log.e(TAG, "Fingerprint algorithm initialization failed with error code: " + errorrr);
                        String errorMessage = getFingerprintErrorString(errorrr);
                        Log.e(TAG, "Error message: " + errorMessage);
                        showError(getString(R.string.algorithm_initialization_failed), errorMessage);
                    }



//                    if ((error = Bione.initialize(FingerprintDemo.this, FP_DB_PATH)) != Bione.RESULT_OK) {
//                        showError(getString(R.string.algorithm_initialization_failed), getFingerprintErrorString(error));
//                    }

//                    if ((error = Bione.initialize(FingerprintDemo.this, FP_DB_PATH)) != Bione.RESULT_OK) {
//                        showError(getString(R.string.algorithm_initialization_failed), getFingerprintErrorString(error));
//                    }

                    Log.i(TAG, "Fingerprint algorithm version: " + Bione.getVersion());
                    dismissProgressDialog();
                }
            }
        }.start();
    }

    private void closeDevice() {
        new Thread() {
            @Override
            public void run() {
                synchronized (FingerprintDemo.this) {
                   // mScanner.PowerOnOff(0);//下电
                    showProgressDialog(getString(R.string.loading), getString(R.string.closing_device));
                    enableControl(false);
                    int error;
                    if (mTask != null && mTask.getStatus() != AsyncTask.Status.FINISHED) {
                        mTask.cancel(false);
                        mTask.waitForDone();
                    }
                    if ((error = mScanner.close()) != FingerprintScanner.RESULT_OK) {
                        showError(getString(R.string.fingerprint_device_close_failed), getFingerprintErrorString(error));
                    } else {
                        showInformation(getString(R.string.fingerprint_device_close_success), null);
                    }
                    if ((error = mScanner.powerOff()) != FingerprintScanner.RESULT_OK) {
                        showError(getString(R.string.fingerprint_device_power_off_failed), getFingerprintErrorString(error));
                    }
                    if ((error = Bione.exit()) != Bione.RESULT_OK) {
                        showError(getString(R.string.algorithm_cleanup_failed), getFingerprintErrorString(error));
                    }
                    dismissProgressDialog();
                }
            }
        }.start();
    }

    private void enroll() {
//        Log.i(TAG, "enroll btn entry...");
        mTask = new FingerprintTask();
        mTask.execute("enroll");
    }

    private void verify() {
        mTask = new FingerprintTask();
        mTask.execute("verify");
    }

    private void identify() {
        mTask = new FingerprintTask();
        mTask.execute("identify");
    }

    private void clearFingerprintDatabase() {
        int error = Bione.clear();
        if (error == Bione.RESULT_OK) {
            showInformation(getString(R.string.clear_fingerprint_database_success), null);
        } else {
            showError(getString(R.string.clear_fingerprint_database_failed), getFingerprintErrorString(error));
        }
//        int error = mScanner.clearDatabaseOfChip();
//        if (error == FingerprintScanner.RESULT_OK) {
//            showInformation(getString(R.string.clear_fingerprint_database_success), null);
//        } else {
//            showError(getString(R.string.clear_fingerprint_database_failed), getFingerprintErrorString(error));
//        }

    }

    private void deleteFingerprintFromDatabase() {
        try {
            int id = Integer.parseInt(mFpId.getText().toString());
            int error = mScanner.deleteTemplateFromChip(id);
            if (error == FingerprintScanner.RESULT_OK) {
                showInformation(getString(R.string.delete_fingerprint_success), null);
            } else {
                showError(getString(R.string.delete_fingerprint_failed), getFingerprintErrorString(error));
            }
        } catch (NumberFormatException e) {
            showError(getString(R.string.delete_fingerprint_failed), getString(R.string.error_number_format));
        }
    }

    private void showFingerprintImage() {
        mTask = new FingerprintTask();
        mTask.execute("show");
    }

    private void getCapacity() {
        int error = mScanner.getCapacityOfChip();
        if (error >= 0) {
            showInformation(getString(R.string.get_capacity_success), getString(R.string.capacity, error));
        } else {
            showError(getString(R.string.get_capacity_failed), getFingerprintErrorString(error));
        }



    }

    private void getEnrolledCount() {
        int error = mScanner.getEnrolledCountOnChip();
        if (error >= 0) {
            showInformation(getString(R.string.get_enrolled_count_success), getString(R.string.enrolled_count, error));
        } else {
            showError(getString(R.string.get_enrolled_count_failed), getFingerprintErrorString(error));
        }
    }

    private void getEnrolledList() {
        Result res = mScanner.getEnrolledListFromChip();
        if (res.error == FingerprintScanner.RESULT_OK) {
            showInformation(getString(R.string.get_enrolled_list_success), null);
            StringBuffer sb = new StringBuffer();
            sb.append("Enrolled list:\n");
            boolean[] list = (boolean[]) res.data;
            for (int i = 0; i < list.length; i++) {
                sb.append(String.format("    id[%4d]: %s\n", i, list[i] ? "Enrolled" : "Empty"));
            }
            Log.d(TAG, sb.toString());
        } else {
            showError(getString(R.string.get_enrolled_list_failed), getFingerprintErrorString(res.error));
        }
    }

    private void getTemplateFromChip() {
        try {
            int id = Integer.parseInt(mFpId.getText().toString());
            byte[][] features = new byte[3][];
            for (int i = 0; i < 3; i++) {
                Result res = mScanner.getFeatureFromChip(id, i + 1);
                if (res.error != FingerprintScanner.RESULT_OK) {
                    showError(getString(R.string.get_feature_from_chip_failed), getFingerprintErrorString(res.error));
                    return;
                }
                features[i] = (byte[]) res.data;
            }
            mLastFeatures = features;
            showInformation(getString(R.string.get_feature_from_chip_success), null);
        } catch (NumberFormatException e) {
            showError(getString(R.string.get_feature_from_chip_failed), getString(R.string.error_number_format));
        }
    }

    private void downloadTemplateToChip() {
        try {
            int id = Integer.parseInt(mFpId.getText().toString());
            if (mLastFeatures == null || mLastFeatures[0] == null || mLastFeatures[1] == null || mLastFeatures[2] == null) {
                showError(getString(R.string.download_template_to_chip_failed), getString(R.string.get_template_first));
                return;
            }
            int error = mScanner.downloadTemplateAndStoreOnChip(id, mLastFeatures[0], mLastFeatures[1], mLastFeatures[2]);
            if (error == FingerprintScanner.RESULT_OK) {
                showInformation(getString(R.string.download_template_to_chip_success), null);
            } else {
                showError(getString(R.string.download_template_to_chip_failed), getFingerprintErrorString(error));
            }
        } catch (NumberFormatException e) {
            showError(getString(R.string.download_template_to_chip_failed), getString(R.string.error_number_format));
        }
    }

    private void captureFeature() {
        mTask = new FingerprintTask();
        mTask.execute("captureFeature");

    }

    private void extractFeature() {
        mTask = new FingerprintTask();
        mTask.execute("extractFeature");
    }

    private void captureISOFeature() {
        mTask = new FingerprintTask();
        mTask.execute("captureISO");
    }

    private void extractISOFeature() {
        mTask = new FingerprintTask();
        mTask.execute("extractISO");
    }

    private void showProgressDialog(String title, String message) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SHOW_PROGRESS_DIALOG, new String[]{title, message}));
    }

    private void dismissProgressDialog() {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_DISMISS_PROGRESS_DIALOG));
    }

    private void showError(String info, String details) {
        Bundle bundle = new Bundle();
        bundle.putString("information", info);
        bundle.putString("details", details);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SHOW_ERROR, bundle));
    }

    private void showInformation(String info, String details) {
        Bundle bundle = new Bundle();
        bundle.putString("information", info);
        bundle.putString("details", details);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SHOW_INFO, bundle));
    }

    private void showCaptureTime(String info) {
        Bundle bundle = new Bundle();
        bundle.putString("time", info);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SHOW_CAPTURE_TIME, bundle));
    }

    private String getFingerprintErrorString(int error) {
        int strid;
        switch (error) {
            case FingerprintScanner.RESULT_OK:
                strid = R.string.operation_successful;
                break;
            case FingerprintScanner.RESULT_FAIL:
                strid = R.string.error_operation_failed;
                break;
            case FingerprintScanner.WRONG_CONNECTION:
                strid = R.string.error_wrong_connection;
                break;
            case FingerprintScanner.DEVICE_BUSY:
                strid = R.string.error_device_busy;
                break;
            case FingerprintScanner.DEVICE_NOT_OPEN:
                strid = R.string.error_device_not_open;
                break;
            case FingerprintScanner.TIMEOUT:
                strid = R.string.error_timeout;
                break;
            case FingerprintScanner.NO_PERMISSION:
                strid = R.string.error_no_permission;
                break;
            case FingerprintScanner.WRONG_PARAMETER:
                strid = R.string.error_wrong_parameter;
                break;
            case FingerprintScanner.DECODE_ERROR:
                strid = R.string.error_decode;
                break;
            case FingerprintScanner.INIT_FAIL:
                strid = R.string.error_initialization_failed;
                break;
            case FingerprintScanner.UNKNOWN_ERROR:
                strid = R.string.error_unknown;
                break;
            case FingerprintScanner.NOT_SUPPORT:
                strid = R.string.error_not_support;
                break;
            case FingerprintScanner.NOT_ENOUGH_MEMORY:
                strid = R.string.error_not_enough_memory;
                break;
            case FingerprintScanner.DEVICE_NOT_FOUND:
                strid = R.string.error_device_not_found;
                break;
            case FingerprintScanner.DEVICE_REOPEN:
                strid = R.string.error_device_reopen;
                break;
            case FingerprintScanner.NO_FINGER:
                strid = R.string.error_no_finger;
                break;
            case Bione.BAD_IMAGE:
                strid = R.string.error_bad_image;
                break;
            case Bione.NOT_MATCH:
                strid = R.string.error_not_match;
                break;
            case Bione.LOW_POINT:
                strid = R.string.error_low_point;
                break;
            case Bione.NO_RESULT:
                strid = R.string.error_no_result;
                break;
            case Bione.OUT_OF_BOUND:
                strid = R.string.error_out_of_bound;
                break;
            case Bione.DATABASE_FULL:
                strid = R.string.error_database_full;
                break;
            case Bione.LIBRARY_MISSING:
                strid = R.string.error_library_missing;
                break;
            case Bione.UNINITIALIZE:
                strid = R.string.error_algorithm_uninitialize;
                break;
            case Bione.REINITIALIZE:
                strid = R.string.error_algorithm_reinitialize;
                break;
            case Bione.REPEATED_ENROLL:
                strid = R.string.error_repeated_enroll;
                break;
            case Bione.NOT_ENROLLED:
                strid = R.string.error_not_enrolled;
                break;
            default:
                strid = R.string.error_other;
                break;
        }
        if (strid != R.string.error_other) {
            return getString(strid);
        }
        return getString(strid, error);
    }

    private void save2File(String path, byte[] data) {

    }
    private int mId;




    private class FingerprintTask extends AsyncTask<String, Integer, Void> {
        private boolean mIsDone = false;

        @Override
        protected void onPreExecute() {
            enableControl(false);
        }

        @Override
        protected Void doInBackground(String... params) {
            long startTime;
            FingerprintImage fi = null;
            byte[] fpFeat = null, fpTemp = null;
            Result res;

            int error = 0;
            try {
                // Your existing code
                if (params[0].equals("show") || params[0].equals("enroll") || params[0].equals("verify") || params[0].equals("identify")) {
                    showProgressDialog(getString(R.string.loading), getString(R.string.press_finger));
                    mScanner.prepare();

                    int capRetry = 0;
                    long timeout = 100 * 10;
                    do {
                        long time = System.currentTimeMillis();
                        res = mScanner.capture();
                        long endTime = System.currentTimeMillis();
                        showCaptureTime("capture time:" + (endTime - time));
                        Log.i("Sanny", "capture time:" + (endTime - time));
                        error = res.error;

                        if (error == FingerprintScanner.TIMEOUT) {
                            timeout--;
                            if (timeout == 0) break;
                            Thread.sleep(10);
                            continue;
                        }

                        fi = (FingerprintImage) res.data;
                        if (fi != null) {
                            int quality = Bione.getFingerprintQuality(fi);
                            Log.i(TAG, "Fingerprint image quality is " + quality);

                            if (quality < 50 && capRetry < 3 && !isCancelled()) {
                                capRetry++;
                                continue;
                            }
                        }

                        if (error != FingerprintScanner.NO_FINGER || isCancelled()) {
                            Log.i("Sanny", "error 2222:" + error);
                            break;
                        }
                    } while (true);

                    mScanner.finish();
                    if (!isCancelled() && error != FingerprintScanner.RESULT_OK) {
                        showError(getString(R.string.capture_image_failed), getFingerprintErrorString(error));
                    } else {
                        mLastImage = fi;
                        updateFingerprintImage(fi);
                        showInformation(getString(R.string.capture_image_success), null);
                    }
                }

                if (params[0].equals("enroll") || params[0].equals("verify") || params[0].equals("identify")) {
                    if (fi != null) {
                        startTime = System.currentTimeMillis();
                        res = Bione.extractFeature(fi);
                        if (res.error != Bione.RESULT_OK) {
                            showError(getString(R.string.enroll_failed_because_of_extract_feature), getFingerprintErrorString(res.error));
                        } else {
                            fpFeat = (byte[]) res.data;
                        }
                    }
                }

                if (params[0].equals("enroll")) {
                    int ret;
                    int id = Bione.getFreeID();
                    if (id < 0) {
                        showError(getString(R.string.enroll_failed_because_of_get_id), getFingerprintErrorString(id));
                    } else {
                        startTime = System.currentTimeMillis();
                        res = Bione.makeTemplate(fpFeat, fpFeat, fpFeat);
                        if (res.error != Bione.RESULT_OK) {
                            showError(getString(R.string.enroll_failed_because_of_make_template), getFingerprintErrorString(res.error));
                        } else {
                            fpTemp = (byte[]) res.data;
                            ret = Bione.enroll(id, fpTemp);
                            if (ret != Bione.RESULT_OK) {
                                showError(getString(R.string.enroll_failed_because_of_error), getFingerprintErrorString(ret));
                            } else {
                                mId = id;
                                showInformation(getString(R.string.enroll_success), getString(R.string.enrolled_id, id));
                            }
                        }
                    }
                } else if (params[0].equals("verify")) {
                    startTime = System.currentTimeMillis();
                    res = Bione.verify(mId, fpFeat);
                    if (res.error != Bione.RESULT_OK) {
                        showError(getString(R.string.verify_failed_because_of_error), getFingerprintErrorString(res.error));
                    } else {
                        if ((Boolean) res.data) {
                            showInformation(getString(R.string.fingerprint_match), getString(R.string.fingerprint_similarity, res.arg1));
                        } else {
                            showError(getString(R.string.fingerprint_not_match), getString(R.string.fingerprint_similarity, res.arg1));
                        }
                    }
                } else if (params[0].equals("identify")) {
                    startTime = System.currentTimeMillis();
                    int id = Bione.identify(fpFeat);
                    if (id < 0) {
                        showError(getString(R.string.identify_failed_because_of_error), getFingerprintErrorString(id));
                    } else {
                        showInformation(getString(R.string.identify_match), getString(R.string.matched_id, id));
                    }
                } else if (params[0].equals("captureISO")) {
                    byte[] feature;
                    showProgressDialog(getString(R.string.loading), getString(R.string.press_finger));
                    mScanner.prepare();
                    do {
                        res = mScanner.captureAndGetIsoFeature();
                        error = res.error;
                        feature = (byte[]) res.data;

                        if (error != FingerprintScanner.NO_FINGER || isCancelled()) {
                            break;
                        }
                    } while (true);

                    mScanner.finish();
                    if (!isCancelled() && error != FingerprintScanner.RESULT_OK) {
                        showError(getString(R.string.capture_image_failed), getFingerprintErrorString(error));
                    } else {
                        showInformation(getString(R.string.capture_image_success), null);
                    }
                } else if (params[0].equals("extractISO")) {
                    if (mLastImage == null) {
                        showError(getString(R.string.extract_feature_failed), getString(R.string.finger_image_not_captured));
                    } else {
                        byte[] feature;
                        showProgressDialog(getString(R.string.loading), null);
                        do {
                            res = mScanner.extractIsoFeatureOnChip(mLastImage);
                            error = res.error;
                            feature = (byte[]) res.data;

                            if (error != FingerprintScanner.NO_FINGER || isCancelled()) {
                                break;
                            }
                        } while (true);

                        if (!isCancelled() && error != FingerprintScanner.RESULT_OK) {
                            showError(getString(R.string.extract_feature_failed), getFingerprintErrorString(error));
                        } else {
                            showInformation(getString(R.string.extract_feature_success), null);
                        }
                    }
                } else if (params[0].equals("captureFeature")) {
                    byte[] feature;
                    showProgressDialog(getString(R.string.loading), getString(R.string.press_finger));
                    mScanner.prepare();
                    do {
                        res = mScanner.captureAndGetFeature();
                        error = res.error;
                        feature = (byte[]) res.data;

                        if (error != FingerprintScanner.NO_FINGER || isCancelled()) {
                            break;
                        }
                    } while (true);

                    mScanner.finish();
                    if (!isCancelled() && error != FingerprintScanner.RESULT_OK) {
                        showError(getString(R.string.capture_image_failed), getFingerprintErrorString(error));
                    } else {
                        showInformation(getString(R.string.capture_image_success), null);
                    }
                } else if (params[0].equals("extractFeature")) {
                    if (mLastImage == null) {
                        showError(getString(R.string.extract_feature_failed), getString(R.string.finger_image_not_captured));
                    } else {
                        byte[] feature;
                        showProgressDialog(getString(R.string.loading), null);
                        do {
                            res = mScanner.extractFeatureOnChip(mLastImage);
                            error = res.error;
                            feature = (byte[]) res.data;

                            if (error != FingerprintScanner.NO_FINGER || isCancelled()) {
                                break;
                            }
                        } while (true);

                        if (!isCancelled() && error != FingerprintScanner.RESULT_OK) {
                            showError(getString(R.string.extract_feature_failed), getFingerprintErrorString(error));
                        } else {
                            showInformation(getString(R.string.extract_feature_success), null);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in FingerprintTask", e);
                showError("unexpected_error:: ", e.getMessage());
            } finally {
                enableControl(true);
                dismissProgressDialog();
                mIsDone = true;
            }
            return null;
        }



//        @Override
        protected Void doInBackground12(String... params) {
            long startTime, captureTime = -1, extractTime = -1, generalizeTime = -1, verifyTime = -1;
            FingerprintImage fi = null;
            byte[] fpFeat = null, fpTemp = null;
            Result res;

            int error = 0;
            do{
            if (params[0].equals("show") || params[0].equals("enroll") || params[0].equals("verify") || params[0].equals("identify")) {

                showProgressDialog(getString(R.string.loading), getString(R.string.press_finger));
                int capRetry = 0;
                mScanner.prepare();
              //  long time = System.currentTimeMillis();
                long timeout=100*10;

                do {
                    long time = System.currentTimeMillis();
                    res = mScanner.capture();
                    long endTime =  System.currentTimeMillis();
                    showCaptureTime("capture time:"+ (endTime - time));
                    Log.i("Sanny","capture time:"+ (endTime - time));
                    error = res.error;

                    if (error == FingerprintScanner.TIMEOUT)
                    {
                        timeout--;

                        if(timeout==0) break;
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        continue;
                    }


                    fi = (FingerprintImage) res.data;
                    int quality;
                    if (fi != null) {
//                        break;
                        quality = Bione.getFingerprintQuality(fi);
                        Log.i(TAG, "Fingerprint image quality is " + quality);

                        if (quality < 50 && capRetry < 3 && !isCancelled()) {
                            capRetry++;
                            continue;
                        }
                    }


                    if (error != FingerprintScanner.NO_FINGER || isCancelled()) {

                        Log.i("Sanny","error 2222:"+error);

                        break;
                    }
                    if (isCancelled()) {

                        Log.i("Sanny","error 3333333333:"+error);
                        break;
                    }


                } while (true);
                mScanner.finish();
                if (!isCancelled() && error != FingerprintScanner.RESULT_OK) {
                    showError(getString(R.string.capture_image_failed), getFingerprintErrorString(error));
                } else {
                    mLastImage = fi;

                    updateFingerprintImage(fi);
                    showInformation(getString(R.string.capture_image_success), null);
                }
            }
            if (params[0].equals("enroll") || params[0].equals("verify") || params[0].equals("identify")) {

                if (fi != null) {
                    startTime = System.currentTimeMillis();

                    res = Bione.extractFeature(fi);

                    extractTime = System.currentTimeMillis() - startTime;
                    if (res.error != Bione.RESULT_OK) {
                        showError(getString(R.string.enroll_failed_because_of_extract_feature), getFingerprintErrorString(res.error));
                        break;
                    }
                    fpFeat = (byte[]) res.data;
                }
            }

            if (params[0].equals("enroll")) {
                int ret;
                int id = Bione.getFreeID();
                if (id < 0) {
                    showError(getString(R.string.enroll_failed_because_of_get_id), getFingerprintErrorString(id));
                    break;
                }
                if(fpFeat == null)
                    break;
                    startTime = System.currentTimeMillis();
                    res = Bione.makeTemplate(fpFeat, fpFeat, fpFeat);
                    generalizeTime = System.currentTimeMillis() - startTime;
                    if (res.error != Bione.RESULT_OK) {
                        showError(getString(R.string.enroll_failed_because_of_make_template), getFingerprintErrorString(res.error));
                        break;
                    }
                    fpTemp = (byte[]) res.data;
                    ret = Bione.enroll(id, fpTemp);

                if (ret != Bione.RESULT_OK) {
                    showError(getString(R.string.enroll_failed_because_of_error), getFingerprintErrorString(ret));
                    break;
                }
                mId = id;
                showInformation(getString(R.string.enroll_success), getString(R.string.enrolled_id, id));

            }
            else if (params[0].equals("verify")) {

                startTime = System.currentTimeMillis();

                res = Bione.verify(mId, fpFeat);

                verifyTime = System.currentTimeMillis() - startTime;
                if (res.error != Bione.RESULT_OK) {
                    showError(getString(R.string.verify_failed_because_of_error), getFingerprintErrorString(res.error));
                    break;
                }
                if ((Boolean) res.data) {
                    showInformation(getString(R.string.fingerprint_match), getString(R.string.fingerprint_similarity, res.arg1));
                } else {
                    showError(getString(R.string.fingerprint_not_match), getString(R.string.fingerprint_similarity, res.arg1));
                }
            }
            else if (params[0].equals("identify")) {
                startTime = System.currentTimeMillis();
                int id = Bione.identify(fpFeat);
                verifyTime = System.currentTimeMillis() - startTime;
                if (id < 0) {
                    showError(getString(R.string.identify_failed_because_of_error), getFingerprintErrorString(id));
                    break;
                }
                showInformation(getString(R.string.identify_match), getString(R.string.matched_id, id));
            }
            else if (params[0].equals("captureISO")) {

                byte[] feature;
                showProgressDialog(getString(R.string.loading), getString(R.string.press_finger));
                mScanner.prepare();
                do {
                    res = mScanner.captureAndGetIsoFeature();
                    error = res.error;

                    feature = (byte[]) res.data;

                    if (error != FingerprintScanner.NO_FINGER || isCancelled()) {
                        break;
                    }
                } while (true);
                mScanner.finish();
                if (!isCancelled() && error != FingerprintScanner.RESULT_OK) {
                    showError(getString(R.string.capture_image_failed), getFingerprintErrorString(error));
                } else {
                    showInformation(getString(R.string.capture_image_success), null);
                }
            }
            else if (params[0].equals("extractISO")) {
                if (mLastImage == null) {
                    showError(getString(R.string.extract_feature_failed), getString(R.string.finger_image_not_captured));
                } else {
                    byte[] feature;
                    showProgressDialog(getString(R.string.loading), null);
                    do {
                        res = mScanner.extractIsoFeatureOnChip(mLastImage);
                        error = res.error;

                        feature = (byte[]) res.data;

                        if (error != FingerprintScanner.NO_FINGER || isCancelled()) {
                            break;
                        }
                    } while (true);
                    if (!isCancelled() && error != FingerprintScanner.RESULT_OK) {
                        showError(getString(R.string.extract_feature_failed), getFingerprintErrorString(error));
                    } else {

                        showInformation(getString(R.string.extract_feature_success), null);
                    }
                }
            }
            else if (params[0].equals("captureFeature")) {

                byte[] feature;
                showProgressDialog(getString(R.string.loading), getString(R.string.press_finger));
                mScanner.prepare();
                do {
                    res = mScanner.captureAndGetFeature();
                    error = res.error;

                    feature = (byte[]) res.data;

                    if (error != FingerprintScanner.NO_FINGER || isCancelled()) {
                        break;
                    }
                } while (true);
                mScanner.finish();
                if (!isCancelled() && error != FingerprintScanner.RESULT_OK) {
                    showError(getString(R.string.capture_image_failed), getFingerprintErrorString(error));
                } else {

                    showInformation(getString(R.string.capture_image_success), null);
                }
            }
            else if (params[0].equals("extractFeature")) {
                if (mLastImage == null) {
                    showError(getString(R.string.extract_feature_failed), getString(R.string.finger_image_not_captured));
                } else {

                    byte[] feature;
                    showProgressDialog(getString(R.string.loading), null);
                    do {
                        res = mScanner.extractFeatureOnChip(mLastImage);
                        error = res.error;

                        feature = (byte[]) res.data;

                        if (error != FingerprintScanner.NO_FINGER || isCancelled()) {
                            break;
                        }
                    } while (true);
                    if (!isCancelled() && error != FingerprintScanner.RESULT_OK) {
                        showError(getString(R.string.extract_feature_failed), getFingerprintErrorString(error));
                    } else {

                        showInformation(getString(R.string.extract_feature_success), null);
                    }
                }
            }
        } while (false);
            enableControl(true);
            dismissProgressDialog();
            mIsDone = true;
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
        }

        @Override
        protected void onCancelled() {
        }

        public void waitForDone() {
            while (!mIsDone) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
