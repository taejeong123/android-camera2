package com.example.bitflow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CameraFragmentActivity extends Fragment implements View.OnClickListener, NumberPicker.OnValueChangeListener {

    private static final int PERMISSIONS_REQUEST_CODE = 100;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static final String TAG = "== Camera2BasicFragment ==";

    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_LOCK = 1;
    private static final int STATE_WAITING_PRECAPTURE = 2;
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    private static final int STATE_PICTURE_TAKEN = 4;

    private String mCameraId;
    private AutoFitTextureView mTextureView;
    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice;
    private Size mPreviewSize;

    private boolean buttonPressed = false;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private ImageReader mImageReader;

    private File mFile;

    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;

    private int mState = STATE_PREVIEW;

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private TextView fName;
    private EditText idx;
    private NumberPicker typePicker;
    private NumberPicker natCodePicker;
    private NumberPicker unitPicker;
    private NumberPicker fbPicker;
    private NumberPicker distancePicker;
    private NumberPicker degreePicker;

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
    };

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }
    };

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @TargetApi(Build.VERSION_CODES.M)
        @Override
        public void onImageAvailable(ImageReader reader) {
            String fileName = Utils.getFileName(getInfo());

            String folder;
            if (natCodePicker.getDisplayedValues() == null) { folder = "MIX"; }
            else { folder = natCodePicker.getDisplayedValues()[natCodePicker.getValue()]; }
            mFile = new File(getActivity().getExternalFilesDir(null) + File.separator + folder + File.separator + fileName);

            if (fileName == null || fName.getText().equals("")) {
                Utils.displayMessage(getContext(), "empty value");
            } else {
                mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
            }
        }
    };

    private MoneyVO getInfo() {
        MoneyVO moneyVO = new MoneyVO();
        try {
            moneyVO.setIdx(idx.getText().toString());
            moneyVO.setType(typePicker.getDisplayedValues()[typePicker.getValue()]);

            if (natCodePicker.getDisplayedValues() == null) { moneyVO.setNatCode(null); }
            else { moneyVO.setNatCode(natCodePicker.getDisplayedValues()[natCodePicker.getValue()]); }

            if (unitPicker.getDisplayedValues() == null) { moneyVO.setUnit(null); }
            else { moneyVO.setUnit(unitPicker.getDisplayedValues()[unitPicker.getValue()]); }

            moneyVO.setFb(fbPicker.getDisplayedValues()[fbPicker.getValue()]);
            moneyVO.setDistance(distancePicker.getDisplayedValues()[distancePicker.getValue()]);
            moneyVO.setDegree(degreePicker.getDisplayedValues()[degreePicker.getValue()]);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return moneyVO;
    }

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    int afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_WAITING_NON_PRECAPTURE;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            process(result);
        }
    };

    @SuppressLint("LongLogTag")
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<Size>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width &&
                    option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public static Fragment newInstance() {
        CameraFragmentActivity fragment = new CameraFragmentActivity();
        fragment.setRetainInstance(true);
        return fragment;
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_camera_fragment, container, false);

        fName = view.findViewById(R.id.file_name);
        idx = view.findViewById(R.id.idx);
        typePicker = view.findViewById(R.id.type_picker);
        natCodePicker = view.findViewById(R.id.nat_code_picker);
        unitPicker = view.findViewById(R.id.unit_picker);
        fbPicker = view.findViewById(R.id.fb_picker);
        distancePicker = view.findViewById(R.id.distance_picker);
        degreePicker = view.findViewById(R.id.degree_picker);

        typePicker.setOnValueChangedListener(this);
        natCodePicker.setOnValueChangedListener(this);
        unitPicker.setOnValueChangedListener(this);
        fbPicker.setOnValueChangedListener(this);
        distancePicker.setOnValueChangedListener(this);
        degreePicker.setOnValueChangedListener(this);

        idx.addTextChangedListener(textWatcher);

        Utils.setPicker(typePicker, ItemList.typeList);
        Utils.setPicker(natCodePicker, ItemList.getNatCodeNUnitJson(getResources()));
        Utils.setPicker(unitPicker, ItemList.getNatCodeNUnitJson(getResources()));
        Utils.setPicker(fbPicker, ItemList.fbList);
        Utils.setPicker(distancePicker, ItemList.distanceCoinList);
        Utils.setPicker(degreePicker, ItemList.getDegreeList());

        idx.setText(PreferenceManager.getString(getContext(), "idx"));
        typePicker.setValue(PreferenceManager.getInt(getContext(), "type"));
        fbPicker.setValue(PreferenceManager.getInt(getContext(), "fb"));
        distancePicker.setValue(PreferenceManager.getInt(getContext(), "distance"));
        degreePicker.setValue(PreferenceManager.getInt(getContext(), "degree"));

        int code = PreferenceManager.getInt(getContext(), "code");
        int unit = PreferenceManager.getInt(getContext(), "unit");

        if (code != -1 && unit != -1) {
            natCodePicker.setValue(code);
            onValueChange(natCodePicker, 0, code);
            unitPicker.setValue(unit);

            String fileName = Utils.getFileName(getInfo());
            fName.setText(fileName);
        }

        return view;
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        view.findViewById(R.id.picture).setOnClickListener(this);
        mTextureView = view.findViewById(R.id.texture);
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.picture: {
                takePicture();
                break;
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onValueChange(NumberPicker numberPicker, int i, int i1) {
        String fileName = Utils.getFileName(getInfo());
        fName.setText(fileName);

        String folder;
        if (natCodePicker.getDisplayedValues() == null) { folder = "MIX"; }
        else { folder = natCodePicker.getDisplayedValues()[natCodePicker.getValue()]; }
        mFile = new File(getActivity().getExternalFilesDir(null) + File.separator + folder + File.separator + fileName);

        String type = typePicker.getDisplayedValues()[typePicker.getValue()];

        switch (numberPicker.getId()) {
            case R.id.type_picker: {
                try {
                    Utils.clearPicker(natCodePicker);
                    Utils.clearPicker(unitPicker);
                    Utils.clearPicker(distancePicker);

                    JSONObject jsonObject = ItemList.getNatCodeNUnitJson(getResources());
                    JSONArray arr;
                    if (type.equals("Coin")) {
                        arr = jsonObject.getJSONArray("coin");
                        Utils.setPicker(distancePicker, ItemList.distanceCoinList);
                    } else if (type.equals("Paper")) {
                        arr = jsonObject.getJSONArray("paper");
                        Utils.setPicker(distancePicker, ItemList.distanceOtherList);
                    } else {
                        Utils.setPicker(distancePicker, ItemList.distanceOtherList);
                        return;
                    }
                    JSONArray unit = arr.getJSONObject(i1).getJSONArray("unit");

                    ArrayList<String> codeList = new ArrayList<>();
                    for (int j = 0; j < arr.length(); j++) {
                        JSONObject jsonItem = arr.getJSONObject(j);
                        String code = jsonItem.getString("code");
                        codeList.add(code);
                    }

                    String[] newCodeList = new String[codeList.size()];
                    for (int j = 0; j < codeList.size(); j++) {
                        newCodeList[j] = codeList.get(j);
                    }

                    String[] newUnitList = new String[unit.length()];
                    for (int j = 0; j < unit.length(); j++) {
                        newUnitList[j] = unit.getString(j);
                    }

                    Utils.setPicker(natCodePicker, newCodeList);
                    Utils.setPicker(unitPicker, newUnitList);
                    fName.setText("");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                break;
            }
            case R.id.nat_code_picker: {
                try {
                    Utils.clearPicker(unitPicker);

                    JSONObject jsonObject = ItemList.getNatCodeNUnitJson(getResources());
                    JSONArray arr;
                    if (type.equals("Coin")) {
                        arr = jsonObject.getJSONArray("coin");
                    } else if (type.equals("Paper")) {
                        arr = jsonObject.getJSONArray("paper");
                    } else {
                        return;
                    }
                    JSONArray unit = arr.getJSONObject(i1).getJSONArray("unit");

                    String[] newUnitList = new String[unit.length()];
                    for (int j = 0; j < unit.length(); j++) {
                        newUnitList[j] = unit.getString(j);
                    }

                    Utils.setPicker(unitPicker, newUnitList);
                    fName.setText("");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
    }

    private final TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            String fileName = Utils.getFileName(getInfo());
            fName.setText(fileName);

            String folder;
            if (natCodePicker.getDisplayedValues() == null) { folder = "MIX"; }
            else { folder = natCodePicker.getDisplayedValues()[natCodePicker.getValue()]; }
            mFile = new File(getActivity().getExternalFilesDir(null) + File.separator + folder + File.separator + fileName);
        }

        @Override
        public void afterTextChanged(Editable editable) {}
    };

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        if (mTextureView.isAvailable()) { openCamera(mTextureView.getWidth(), mTextureView.getHeight()); }
        else { mTextureView.setSurfaceTextureListener(mSurfaceTextureListener); }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                // For still image captures, we use the largest available size.
                Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, /*maxImages*/2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, largest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            new ErrorDialog().show(getFragmentManager(), "dialog");
            e.printStackTrace();
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void openCamera(int width, int height) {
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            if (getActivity().checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CODE);
            } else {
                manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    // The camera is already closed
                    if (null == mCameraDevice) {
                        return;
                    }

                    // When the session is ready, we start displaying the preview.
                    mCaptureSession = cameraCaptureSession;
                    try {
                        // Auto focus should be continuous for camera preview.
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        // Flash is automatically enabled when necessary.
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                        // Finally, we start displaying the camera preview.
                        mPreviewRequest = mPreviewRequestBuilder.build();
                        mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Activity activity = getActivity();
                    if (null != activity) {
                        Utils.displayMessage(activity, "Failed");
                    }
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float) viewHeight / mPreviewSize.getHeight(), (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    private void takePicture() {
        lockFocus();
    }

    private void lockFocus() {
        try {
            buttonPressed = true;

            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void captureStillPicture() {
        try {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            // Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void unlockFocus() {
        try {
            buttonPressed = false;

            // Reset the autofucos trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);

            PreferenceManager.setString(getContext(), "idx", idx.getText().toString());
            PreferenceManager.setInt(getContext(), "type", typePicker.getValue());
            PreferenceManager.setInt(getContext(), "code", natCodePicker.getValue());
            PreferenceManager.setInt(getContext(), "unit", unitPicker.getValue());
            PreferenceManager.setInt(getContext(), "fb", fbPicker.getValue());
            PreferenceManager.setInt(getContext(), "distance", distancePicker.getValue());
            PreferenceManager.setInt(getContext(), "degree", degreePicker.getValue());

            // restart activity
            PackageManager packageManager = getActivity().getPackageManager();
            Intent intent = packageManager.getLaunchIntentForPackage(getActivity().getPackageName());
            ComponentName componentName = intent.getComponent();
            Intent mainIntent = Intent.makeRestartActivityTask(componentName);
            startActivity(mainIntent);
            System.exit(0);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private class ImageSaver implements Runnable {
        private final Image mImage;
        private final File mFile;

        public ImageSaver(Image i, File f) {
            mImage = i;
            mFile = f;
        }

        @Override
        public void run() {
            if (!buttonPressed) {
                return;
            }

            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                File dir = new File(mFile.getParentFile().getPath());
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try { output.close(); }
                    catch (IOException e) { e.printStackTrace(); }
                }
            }
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    public static class ErrorDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage("This device doesn't support Camera2 API.")
                    .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> activity.finish())
                    .create();
        }
    }
}