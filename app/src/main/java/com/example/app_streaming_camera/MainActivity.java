package com.example.app_streaming_camera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.example.app_streaming_camera.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding mBinding;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private String S_IP = "192.168.68.108";
    private int S_PORT = 46464;
    private int C_PORT = 46463;
    private UDPClient client;
    private boolean isRecording = false;

    final ActivityResultLauncher<String[]> activityResultLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean cameraGranted = result.get(Manifest.permission.CAMERA);
                Boolean audioGranted = result.get(Manifest.permission.RECORD_AUDIO);
                if (cameraGranted != null && cameraGranted) {
                    startCamera(cameraFacing);
                } else {
                    Toast.makeText(this, "Ứng dụng cần quyền CAMERA để hoạt động!", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(mBinding.getRoot());

        if (client == null) {
            client = new UDPClient(C_PORT, S_PORT);
        }

        requestPermissions();

        mBinding.btnSwitchCamera.setOnClickListener(view -> {
            cameraFacing = (cameraFacing == CameraSelector.LENS_FACING_BACK) ?
                    CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
            startCamera(cameraFacing);
        });
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
            });
        } else {
            startCamera(cameraFacing);
        }
    }

    private void startCamera(int cameraFacing) {
        int aspectRatio = aspectRatio(mBinding.cameraView.getWidth(), mBinding.cameraView.getHeight());
        ListenableFuture<ProcessCameraProvider> listenableFuture = ProcessCameraProvider.getInstance(this);

        listenableFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = listenableFuture.get();
                Preview preview = new Preview.Builder().setTargetAspectRatio(aspectRatio).build();
                CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(cameraFacing).build();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(executor, image -> {
                    client.Send(image.toBitmap(), S_IP, cameraFacing);
                    image.close();
                });

                cameraProvider.unbindAll();
                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
                preview.setSurfaceProvider(mBinding.cameraView.getSurfaceProvider());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private int aspectRatio(int width, int height) {
        double previewRatio = (double) Math.max(width, height) / Math.min(width, height);
        if (Math.abs(previewRatio - 4.0 / 3.0) <= Math.abs(previewRatio - 16.0 / 9.0)) {
            return AspectRatio.RATIO_4_3;
        }
        return AspectRatio.RATIO_16_9;
    }
}
