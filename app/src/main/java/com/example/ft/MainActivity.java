package com.example.ft;

import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

/**
 * MainActivity 是应用的主活动类，负责处理用户交互和启动悬浮窗服务。
 * 它提供了一个界面，允许用户输入文本并启动悬浮窗显示该文本。
 */
public class MainActivity extends AppCompatActivity {

    // 声明一个 EditText 对象，用于获取用户输入的文本
    private EditText editText;
    
    // 声明 ActivityResultLauncher 用于处理权限请求结果
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;

    /**
     * 活动创建时调用，进行界面初始化和按钮点击事件的设置。
     *
     * @param savedInstanceState 如果活动是重新创建的，则包含之前保存的状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置活动的布局为 activity_main.xml
        setContentView(R.layout.activity_main);
    
        // 初始化悬浮窗权限请求的 ActivityResultLauncher
        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // 检查系统版本是否为 Android 6.0 及以上，并且应用已经获得悬浮窗权限
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                        // 权限已授予，启动悬浮窗服务
                        startFloatingTextService();
                    } else {
                        // 权限被拒绝，显示一个短时间的提示信息
                        Toast.makeText(this, "权限被拒绝，无法显示悬浮窗", Toast.LENGTH_SHORT).show();
                    }
                });
    
        // 初始化通知权限请求的 ActivityResultLauncher (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher = registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            // 权限已授予，启动悬浮窗服务
                            startFloatingTextService();
                        } else {
                            // 权限被拒绝
                            Toast.makeText(this, "通知权限被拒绝，应用功能可能受限", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    
        // 通过 findViewById 方法获取布局中的 EditText 控件
        editText = findViewById(R.id.edit_text);
        // 通过 findViewById 方法获取布局中的 Button 控件
        Button showButton = findViewById(R.id.show_button);

        // 为显示按钮设置点击事件监听器
        showButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 检查是否有悬浮窗权限，如果有则启动悬浮窗服务
                if (checkOverlayPermission()) {
                    startFloatingTextService();
                }
            }
        });
    }

    /**
     * 检查应用是否具有显示悬浮窗的权限。
     * 如果没有权限，会引导用户到系统设置中授予权限。
     *
     * @return 如果有悬浮窗权限返回 true，否则返回 false
     */
    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            
            // 使用新的 ActivityResultLauncher 启动权限设置页面
            overlayPermissionLauncher.launch(intent);
            
            // 显示一个短时间的提示信息，提醒用户授予悬浮窗权限
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        // Android 12 (API 31) 及以上版本需要额外检查通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 检查通知权限
            if (!areNotificationsEnabled()) {
                requestNotificationPermission();
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 检查应用是否有通知权限
     * @return 如果有通知权限返回 true，否则返回 false
     */
    private boolean areNotificationsEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                return notificationManager.areNotificationsEnabled();
            }
            return false;
        }
        return true; // 对于低版本Android，默认返回true
    }
    
    // 声明通知权限请求的 ActivityResultLauncher
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    
    /**
     * 请求通知权限
     */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) { // Android 13+
            if (notificationPermissionLauncher != null) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android 8.0+
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
            Toast.makeText(this, "请开启通知权限", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 启动 FloatingTextService 服务，并将用户输入的文本传递给服务。
     * 如果用户没有输入文本，则使用默认文本"悬浮文字"。
     */
    private void startFloatingTextService() {
        // 获取 EditText 中的文本内容
        String text = editText.getText().toString();
        // 如果文本为空，则使用默认文本
        if (text.isEmpty()) {
            text = "悬浮文字";
        }

        // 创建一个 Intent，用于启动 FloatingTextService 服务
        Intent intent = new Intent(MainActivity.this, FloatingTextService.class);
        // 将文本内容作为额外数据添加到 Intent 中
        intent.putExtra("text", text);
        // 启动服务
        startService(intent);
    }
}