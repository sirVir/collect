package org.odk.collect.android.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.Task;
import com.google.android.gms.gcm.TaskParams;

public class SurveyCheckService extends GcmTaskService {

    public static final String GCM_REPEAT_TAG = "repeat|[7200,1800]";
    private static final String TAG = SurveyCheckService.class.getSimpleName();

    @Override
    public void onInitializeTasks() {
        //called when app is updated to a new version, reinstalled etc.
        //you have to schedule your repeating tasks again
        super.onInitializeTasks();
        scheduleRepeat(this);
    }


    @Override
    public int onRunTask(TaskParams taskParams) {
        //do some stuff (mostly network) - executed in background thread (async)

        Handler h = new Handler(getMainLooper());
        final Context c = this;
        if(taskParams.getTag().equals(GCM_REPEAT_TAG)) {
            h.post(new Runnable() {
                @Override
                public void run() {
                    // Toast.makeText(SurveyCheckService.this, "REPEATING executed", Toast.LENGTH_SHORT).show();
                    NotificationManager notificationMgr = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);

                    Notification notification = new NotificationCompat.Builder(c)
                            .setContentTitle("Smart Survey")
                            .setContentText("Click to see the new incoming survey")
                            .setTicker("Survey info update")
                            .setSmallIcon(android.R.drawable.stat_notify_more)
                            .build();
                    notification.flags |= Notification.FLAG_AUTO_CANCEL;
                    notification.defaults |= Notification.DEFAULT_SOUND;
                    notificationMgr.notify(0, notification);
                }
            });
            return GcmNetworkManager.RESULT_SUCCESS;

        }
        else
            return GcmNetworkManager.RESULT_FAILURE;
    }



    public static void scheduleRepeat(Context context) {
        //in this method, single Repeating task is scheduled (the target service that will be called is SurveyCheckService.class)
        try {
            PeriodicTask periodic = new PeriodicTask.Builder()
                    //specify target service - must extend GcmTaskService
                    .setService(SurveyCheckService.class)
                            //repeat every 30 seconds
                    .setPeriod(30)
                            //specify how much earlier the task can be executed (in seconds)
                    .setFlex(20)
                            //tag that is unique to this task (can be used to cancel task)
                    .setTag(GCM_REPEAT_TAG)
                            //whether the task persists after device reboot
                    .setPersisted(true)
                            //if another task with same tag is already scheduled, replace it with this task
                    .setUpdateCurrent(true)
                            //set required network state, this line is optional
                    .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                            //request that charging must be connected, this line is optional
                    .setRequiresCharging(false)
                    .build();
            GcmNetworkManager.getInstance(context).schedule(periodic);
        } catch (Exception e) {
            Log.e(TAG, "scheduling failed");
            e.printStackTrace();
        }
    }

    public static void cancelRepeat(Context context) {
        GcmNetworkManager.getInstance(context).cancelTask(GCM_REPEAT_TAG, SurveyCheckService.class);
    }

}
