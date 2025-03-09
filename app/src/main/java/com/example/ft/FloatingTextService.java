package com.example.ft;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import java.util.Collections;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
// ServiceInfo类在不同Android版本中可能位于不同包中
import android.content.pm.ServiceInfo;
import androidx.core.app.NotificationCompat;

/**
 * FloatingTextService 是一个继承自 Service 的类，用于在 Android 系统中创建并管理一个悬浮窗。
 * 该悬浮窗包含一个可显示文本的 TextView 和一个用于关闭悬浮窗的 ImageButton，并且支持拖动操作。
 */
public class FloatingTextService extends Service {

    // 声明 WindowManager 对象，用于管理窗口的显示和布局
    private WindowManager windowManager;
    // 声明悬浮窗的根视图对象
    private View floatingView;
    // 声明用于显示文本的 TextView 对象
    private TextView floatingText;
    // 声明 WindowManager 的布局参数对象，用于设置悬浮窗的大小、位置、类型等属性
    private WindowManager.LayoutParams params;
    // 声明拖动操作时记录初始位置的变量
    private float initialX, initialY;
    // 声明拖动操作时记录初始触摸位置的变量
    private int initialTouchX, initialTouchY;

    /**
     * 当绑定服务时调用，由于此服务不支持绑定，所以返回 null。
     *
     * @param intent 启动服务时传递的 Intent 对象
     * @return 返回 null，表示不支持绑定操作
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 服务创建时调用，用于初始化悬浮窗的相关设置。
     */
    @Override
    public void onCreate() {
        super.onCreate();
    
        // 初始化 WindowManager，通过系统服务获取 WindowManager 实例
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    
        // 如果是 Android 8.0 及以上版本，创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "floating_text_channel",
                    "悬浮文字服务",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    
        // 创建前台服务通知
        Notification notification = new NotificationCompat.Builder(this, "floating_text_channel")
                .setContentTitle("悬浮文字服务")
                .setContentText("正在显示悬浮文字")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    
        // 将服务设为前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14 (API 34)
            // 使用specialUse前台服务类型
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, notification);
        }
    
        // 加载悬浮窗的布局文件 layout_floating_text，将其转换为 View 对象
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_text, null);

        // 设置WindowManager.LayoutParams
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        // 初始化 WindowManager 的布局参数
        params = new WindowManager.LayoutParams(
                // 设置悬浮窗的宽度为包裹内容，即根据内容自动调整宽度
                WindowManager.LayoutParams.WRAP_CONTENT,
                // 设置悬浮窗的高度为包裹内容，即根据内容自动调整高度
                WindowManager.LayoutParams.WRAP_CONTENT,
                // 设置窗口类型
                layoutFlag,
                // 设置窗口不获取焦点，避免影响其他应用的操作
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                // 设置窗口的像素格式为半透明
                PixelFormat.TRANSLUCENT);

        // 在 Android 12 (API 31) 及以上版本，需要特别处理
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 确保窗口不会干扰系统手势
            params.setFitInsetsTypes(0);
            // Android 10 及以上版本支持 setSystemGestureExclusionRects 方法
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 设置窗口行为，使用空列表表示不排除任何区域
                floatingView.setSystemGestureExclusionRects(Collections.emptyList());
            }
        }

        // 设置悬浮窗的初始位置，位于屏幕左上角
        params.gravity = Gravity.TOP | Gravity.START;
        // 设置悬浮窗的初始 X 坐标
        params.x = 0;
        // 设置悬浮窗的初始 Y 坐标
        params.y = 100;

        // 从悬浮窗布局中获取 TextView 对象，用于显示文本
        floatingText = floatingView.findViewById(R.id.floating_text);

        // 从悬浮窗布局中获取关闭按钮 ImageButton 对象
        ImageButton closeButton = floatingView.findViewById(R.id.close_button);
        // 为关闭按钮设置点击事件监听器，点击时停止当前服务，从而关闭悬浮窗
        closeButton.setOnClickListener(v -> stopSelf());

        // 为悬浮窗设置触摸事件监听器，实现拖动功能
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    // 当手指按下时，记录初始位置和触摸位置
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = (int) event.getRawX();
                        initialTouchY = (int) event.getRawY();
                        return true;
                    // 当手指移动时，根据手指移动的距离更新悬浮窗的位置
                    case MotionEvent.ACTION_MOVE:
                        params.x = (int) (initialX + (event.getRawX() - initialTouchX));
                        params.y = (int) (initialY + (event.getRawY() - initialTouchY));
                        // 更新悬浮窗的布局
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });

        // 将悬浮窗添加到 WindowManager 中进行显示
        windowManager.addView(floatingView, params);

        // Android 10 及以上版本支持 setSystemGestureExclusionRects 方法
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 设置窗口行为，使用空列表表示不排除任何区域
            floatingView.setSystemGestureExclusionRects(Collections.emptyList());
        }
    }

    /**
     * 服务启动时调用，用于处理启动服务时传递的 Intent 数据。
     *
     * @param intent  启动服务时传递的 Intent 对象
     * @param flags   启动服务的标志
     * @param startId 启动服务的 ID
     * @return 返回 START_STICKY，表示服务被意外终止后，系统会尝试重新创建服务
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 检查 Intent 是否不为空且包含 "text" 额外数据
        if (intent != null && intent.hasExtra("text")) {
            // 获取 Intent 中 "text" 对应的数据
            String text = intent.getStringExtra("text");
            // 检查 floatingText 对象是否不为空
            if (floatingText != null) {
                // 设置 floatingText 的文本内容
                floatingText.setText(text);
            }
        }
        return START_STICKY;
    }

    /**
     * 服务销毁时调用，用于释放资源，移除悬浮窗。
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        // 检查悬浮窗视图和 WindowManager 是否不为空
        if (floatingView != null && windowManager != null) {
            // 从 WindowManager 中移除悬浮窗视图
            windowManager.removeView(floatingView);
        }
    }
    
    /**
     * 通过反射获取ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM常量值
     * 避免直接使用硬编码值，提高代码的可维护性和兼容性
     *
     * @return ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM常量值
     */
    private int getForegroundServiceTypeSystem() {
        try {
            // 通过反射获取ServiceInfo类中的FOREGROUND_SERVICE_TYPE_SYSTEM常量
            java.lang.reflect.Field field = ServiceInfo.class.getDeclaredField("FOREGROUND_SERVICE_TYPE_SYSTEM");
            field.setAccessible(true);
            return field.getInt(null);
        } catch (Exception e) {
            // 如果反射失败，使用已知的值作为备选方案
            // 0x00000400 (1024) 是ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM的值
            return 0x00000400;
        }
    }
}