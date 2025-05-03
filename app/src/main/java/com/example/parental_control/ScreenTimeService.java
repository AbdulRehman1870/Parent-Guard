package com.example.parental_control;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class ScreenTimeService extends Service {

    private static final String CHANNEL_ID = "ScreenTimeServiceChannel";
    private static final long SCREEN_TIME_LIMIT = 600000; // 10 minutes (in milliseconds)
    private Handler handler = new Handler();
    private Runnable screenTimeRunnable;
    private long startTime;

    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();

        // Create a notification channel for foreground service (for Android O and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Screen Time Service";
            String description = "This service monitors screen time and triggers lock screen.";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        // Create notification to keep the service running in the foreground
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Time Monitor")
                .setContentText("Monitoring your screen time.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)  // Ensure you have this icon in your resources
                .build();

        startForeground(1, notification); // Start the service in the foreground

        // Initialize start time
        startTime = System.currentTimeMillis();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start monitoring screen time with a handler and runnable
        screenTimeRunnable = new Runnable() {
            @Override
            public void run() {
                // Calculate screen time elapsed
                long elapsedTime = System.currentTimeMillis() - startTime;

                // If the screen time exceeds the limit, trigger the lock screen
                if (elapsedTime >= SCREEN_TIME_LIMIT) {
                    Intent lockIntent = new Intent(ScreenTimeService.this, lock_screen.class);
                    lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(lockIntent);  // Start the lock screen activity
                    stopSelf();  // Stop the service once the lock screen is shown
                } else {
                    // Schedule the next check after 1 second
                    handler.postDelayed(this, 1000);
                }
            }
        };

        handler.post(screenTimeRunnable);  // Start the screen time tracking

        return START_NOT_STICKY;  // Service will stop once its task is completed
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(screenTimeRunnable);  // Clean up the handler
        Toast.makeText(this, "Screen Time Service Stopped", Toast.LENGTH_SHORT).show();
    }
}
