// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.mlkit;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityCompat.OnRequestPermissionsResultCallback;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.google.android.gms.common.annotation.KeepName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Demo app showing the various features of ML Kit for Firebase. This class is used to
 * set up continuous frame processing on frames from a camera source. */
@KeepName
public final class LivePreviewActivity extends AppCompatActivity
    implements OnRequestPermissionsResultCallback {
  private static final String FACE_DETECTION = "Face Detection";
  private static final String TAG = "LivePreviewActivity";
  private static final int PERMISSION_REQUESTS = 1;
  private static final long MAX_FACE_MISSING_PERIOD = 500; // Maximum permitted "Face missing" period in miliseconds
  private static final long MIN_FACE_FOUND_PERIOD = 3000; // "Face found!" period to start the "waving hello" execution

  private CameraSource cameraSource = null;
  private CameraSourcePreview preview;
  private GraphicOverlay graphicOverlay;
  private String selectedModel = FACE_DETECTION;

  private long lastFaceFoundTime = 0;
  private long lastFaceLostTime = 0;
  private Handler mFaceFoundHandler = new Handler();
  private Handler mFaceLostHandler = new Handler();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.d(TAG, "onCreate");

    setContentView(R.layout.activity_live_preview);

    preview = findViewById(R.id.firePreview);
    if (preview == null) {
      Log.d(TAG, "Preview is null");
    }
    graphicOverlay = findViewById(R.id.fireFaceOverlay);
    if (graphicOverlay == null) {
      Log.d(TAG, "graphicOverlay is null");
    }

    if (allPermissionsGranted()) {
      createCameraSource(selectedModel);
    } else {
      getRuntimePermissions();
    }
  }

  private void createCameraSource(String model) {
    // If there's no existing cameraSource, create one.
    if (cameraSource == null) {
      cameraSource = new CameraSource(this, graphicOverlay);
      cameraSource.setFacing(CameraSource.CAMERA_FACING_FRONT);
    }

    FaceDetectionProcessor processor = new FaceDetectionProcessor(new FaceDetectionProcessor.ProcessorSuccessListener() {
      @Override
      public void onSuccess(int numFaces) {
        if(numFaces > 0) {
          if(lastFaceFoundTime == 0) {
            // First time the face has been found
            lastFaceFoundTime = System.currentTimeMillis();
            mFaceFoundHandler.postDelayed(foundFacePeriodFinishedChecker, MIN_FACE_FOUND_PERIOD);
            Log.d(TAG, "I can see your face!!");
          } else if(lastFaceLostTime != 0) {
            cancelLostFace();
          }
        } else {
          if(lastFaceFoundTime != 0 && lastFaceLostTime == 0) {
            // Face was preoviously found but now is lost
            lastFaceLostTime = System.currentTimeMillis();
            mFaceLostHandler.postDelayed(lostFacePeriodFinishedChecker, MAX_FACE_MISSING_PERIOD);
          }
        }
      }
    });

    cameraSource.setMachineLearningFrameProcessor(processor);

    Log.e(TAG, "Unknown model: " + model);
  }

  private Runnable foundFacePeriodFinishedChecker = new Runnable() {
    public void run() {
      // If 3 minutes have passed since the face was found...
      if (System.currentTimeMillis() > lastFaceFoundTime + MIN_FACE_FOUND_PERIOD - 50) {
        startActivity(new Intent(LivePreviewActivity.this, HelloUserActivity.class));
        Log.d(TAG, "Activity!");
      }
    }
  };

  private Runnable lostFacePeriodFinishedChecker = new Runnable() {
    public void run() {
      // If 3 minutes have passed since the face was found...
      if (System.currentTimeMillis() > lastFaceLostTime + MAX_FACE_MISSING_PERIOD - 50) {
        seCancelaTodo();
        Log.d(TAG, "You're gone");
      }
    }
  };

  private void seCancelaTodo() {
    mFaceFoundHandler.removeCallbacks(foundFacePeriodFinishedChecker);
    lastFaceFoundTime = 0;
    cancelLostFace();
  }

  private void cancelLostFace() {
    mFaceLostHandler.removeCallbacks(lostFacePeriodFinishedChecker);
    lastFaceLostTime = 0;
  }

  /**
   * Starts or restarts the camera source, if it exists. If the camera source doesn't exist yet
   * (e.g., because onResume was called before the camera source was created), this will be called
   * again when the camera source is created.
   */
  private void startCameraSource() {
    if (cameraSource != null) {
      try {
        if (preview == null) {
          Log.d(TAG, "resume: Preview is null");
        }
        if (graphicOverlay == null) {
          Log.d(TAG, "resume: graphOverlay is null");
        }
        preview.start(cameraSource, graphicOverlay);
      } catch (IOException e) {
        Log.e(TAG, "Unable to start camera source.", e);
        cameraSource.release();
        cameraSource = null;
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.d(TAG, "onResume");
    startCameraSource();
    seCancelaTodo();
  }

  /** Stops the camera. */
  @Override
  protected void onPause() {
    super.onPause();
    preview.stop();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (cameraSource != null) {
      cameraSource.release();
    }
  }

  // region PERMISSIONS REQUEST
  private String[] getRequiredPermissions() {
    try {
      PackageInfo info =
          this.getPackageManager()
              .getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
      String[] ps = info.requestedPermissions;
      if (ps != null && ps.length > 0) {
        return ps;
      } else {
        return new String[0];
      }
    } catch (Exception e) {
      return new String[0];
    }
  }

  private boolean allPermissionsGranted() {
    for (String permission : getRequiredPermissions()) {
      if (!isPermissionGranted(this, permission)) {
        return false;
      }
    }
    return true;
  }

  private void getRuntimePermissions() {
    List<String> allNeededPermissions = new ArrayList<>();
    for (String permission : getRequiredPermissions()) {
      if (!isPermissionGranted(this, permission)) {
        allNeededPermissions.add(permission);
      }
    }

    if (!allNeededPermissions.isEmpty()) {
      ActivityCompat.requestPermissions(
          this, allNeededPermissions.toArray(new String[0]), PERMISSION_REQUESTS);
    }
  }

  @Override
  public void onRequestPermissionsResult(
          int requestCode, String[] permissions, int[] grantResults) {
    Log.i(TAG, "Permission granted!");
    if (allPermissionsGranted()) {
      createCameraSource(selectedModel);
    }
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  private static boolean isPermissionGranted(Context context, String permission) {
    if (ContextCompat.checkSelfPermission(context, permission)
        == PackageManager.PERMISSION_GRANTED) {
      Log.i(TAG, "Permission granted: " + permission);
      return true;
    }
    Log.i(TAG, "Permission NOT granted: " + permission);
    return false;
  }

  //endregion
}
