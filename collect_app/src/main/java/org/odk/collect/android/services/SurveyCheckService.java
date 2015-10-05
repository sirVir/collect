package org.odk.collect.android.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.OneoffTask;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.Task;
import com.google.android.gms.gcm.TaskParams;

import org.odk.collect.android.activities.FormDownloadList;
import org.odk.collect.android.listeners.FormListDownloaderListener;
import org.odk.collect.android.logic.FormDetails;
import org.odk.collect.android.preferences.PreferencesActivity;
import org.odk.collect.android.tasks.DownloadFormListTask;

import java.util.HashMap;
import java.util.concurrent.ExecutionException;

public class SurveyCheckService extends GcmTaskService implements FormListDownloaderListener {

    private static final int notificationId = 420734;
    private static Boolean isScheduled = false;
    private static Boolean isDownloaded = false;


    private DownloadFormListTask mDownloadFormListTask;
    public static final String GCM_REPEAT_TAG = "repeat|[7200,1800]";
    private static final String TAG = SurveyCheckService.class.getSimpleName();

    @Override
    public void onInitializeTasks() {
        //called when app is updated to a new version, reinstalled etc.
        //you have to schedule your repeating tasks again
        super.onInitializeTasks();
        isScheduled = false;
        refreshRepeat(this, true);
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
            try {
                mDownloadFormListTask.execute().get();
            } catch (InterruptedException e) {
                return GcmNetworkManager.RESULT_RESCHEDULE;
            } catch (ExecutionException e) {
                return GcmNetworkManager.RESULT_RESCHEDULE;
            }

            return GcmNetworkManager.RESULT_SUCCESS;
        }
    }

    /*
     * @param context The Context of the app to be passed
     * @param forced Flag if to force the rescheduling. Setting it to true will reset the current waiting period
     */
    public static void refreshRepeat(Context context, boolean forced) {
        if (forced)
            scheduleRepeat(context, false);
        else if (!isScheduled)
        {
            scheduleRepeat(context, false);
        }
        isScheduled = true;
    }

    private static void scheduleRepeat(Context context, Boolean isDisabled) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String choosen = settings.getString(PreferencesActivity.KEY_AUTOPULL_NEW, "connected");
        String period_string = settings.getString(PreferencesActivity.KEY_AUTOPULL_FREQUENCY, "3600");

        int period = Integer.parseInt(period_string);
        if (choosen.equals("disabled") || isDisabled) {
            GcmNetworkManager.getInstance(context).cancelAllTasks(SurveyCheckService.class);
            return;
        }

        Log.e(TAG, choosen);
        //in this method, single Repeating task is scheduled (the target service that will be called is SurveyCheckService.class)
        try {
            OneoffTask.Builder periodic = new OneoffTask.Builder()
                    //specify target service - must extend GcmTaskService
                    .setService(SurveyCheckService.class)
                            //set repeat period to one of the predefined values
                    .setExecutionWindow(period - period / 10, period + period/10)
                            //10% of flexibility allowed
                    .setTag(GCM_REPEAT_TAG)
                            //whether the task persists after device reboot
                    .setPersisted(true)
                            //if another task with same tag is already scheduled, replace it with this task
                    .setUpdateCurrent(true)
                            //set required network state, this line is optional
                    .setRequiresCharging(false);

            if (choosen.equals("connected")) {
                periodic.setRequiredNetwork(Task.NETWORK_STATE_CONNECTED);

            } else if (choosen.equals("unmetered")) {
                periodic.setRequiredNetwork(Task.NETWORK_STATE_UNMETERED);

            } else {
                throw new IllegalArgumentException("Unsupported scheduling command");
            }

            OneoffTask task = periodic.build();

            GcmNetworkManager.getInstance(context).schedule(task);
        } catch (Exception e) {
            Log.e(TAG, "scheduling failed");
            e.printStackTrace();
        }
        return;
    }


    public void formListDownloadingComplete(final HashMap<String, FormDetails> value, Boolean silent) {

        if (!value.isEmpty()) {

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
                                .setContentText("Click to see unfilled surveys")
                                .setTicker("Pending surveys to fill")
                                .setSmallIcon(android.R.drawable.stat_notify_more)
                                .setContentIntent(pIntent)
                                .build();
                        notification.defaults |= Notification.DEFAULT_SOUND;

                        notificationMgr.notify(notificationId, notification);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            isScheduled = false;
        }
    }

    /**
     * Cancel the pending notification.
     * @param c Context to be passed
     */
    public static void cancelNotification(final Context c) {
        new Thread() {
            @Override
            public void run() {
                NotificationManager notificationMgr = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationMgr.cancel(notificationId);
            }
        }.start();
    }


}
