package org.odk.collect.android.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.Task;
import com.google.android.gms.gcm.TaskParams;

import org.odk.collect.android.activities.FormDownloadList;
import org.odk.collect.android.listeners.FormListDownloaderListener;
import org.odk.collect.android.logic.FormDetails;
import org.odk.collect.android.tasks.DownloadFormListTask;

import java.util.HashMap;

public class SurveyCheckService extends GcmTaskService implements FormListDownloaderListener {

    private DownloadFormListTask mDownloadFormListTask;
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
        //some rare cases of concurrency might occur - to investigate
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = connectivityManager.getActiveNetworkInfo();

        if (ni == null || !ni.isConnected()) {
            return GcmNetworkManager.RESULT_RESCHEDULE;
        } else {

            HashMap<String, FormDetails> mFormNamesAndURLs = new HashMap<String, FormDetails>();


            if (mDownloadFormListTask != null &&
                    mDownloadFormListTask.getStatus() != AsyncTask.Status.FINISHED) {
                return GcmNetworkManager.RESULT_FAILURE;


            } else if (mDownloadFormListTask != null) {
                mDownloadFormListTask.setDownloaderListener(null);
                mDownloadFormListTask.cancel(true);
                mDownloadFormListTask = null;
            }

            mDownloadFormListTask = new DownloadFormListTask();
            mDownloadFormListTask.setDownloaderListener(this);
            mDownloadFormListTask.execute();
            return GcmNetworkManager.RESULT_SUCCESS;
        }
    }



    public static void scheduleRepeat(Context context) {
        //in this method, single Repeating task is scheduled (the target service that will be called is SurveyCheckService.class)
        try {
            PeriodicTask periodic = new PeriodicTask.Builder()
                    //specify target service - must extend GcmTaskService
                    .setService(SurveyCheckService.class)
                            //repeat every 30 seconds
                    .setPeriod(120)
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

    @Override
    public void formListDownloadingComplete(final HashMap<String, FormDetails> value, Boolean silent) {


        if (!value.isEmpty())
        {
            Handler h = new Handler(getMainLooper());
            final Context c = this;
            try {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        NotificationManager notificationMgr = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                        Intent intent = new Intent(c, FormDownloadList.class);
                        intent.putExtra("SurveyList", value);
                        PendingIntent pIntent = PendingIntent.getActivity(c, 0, intent, 0);

                        Notification notification = new NotificationCompat.Builder(c)
                                .setContentTitle("ODK Collect")
                                .setContentText("Click to new incomming surveys")
                                .setTicker("New surveys to fill")
                                .setSmallIcon(android.R.drawable.stat_notify_more)
                                .setContentIntent(pIntent)
                                .build();
                        notification.flags |= Notification.FLAG_AUTO_CANCEL;
                        notification.defaults |= Notification.DEFAULT_SOUND;

                        notificationMgr.notify(0, notification);
                    }
                });
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
}
