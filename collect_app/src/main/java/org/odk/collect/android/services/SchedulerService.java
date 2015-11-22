package org.odk.collect.android.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.TaskParams;

import org.odk.collect.android.activities.FormChooserList;
import org.odk.collect.android.preferences.PreferencesActivity;
import org.odk.collect.android.provider.FormsProviderAPI;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SchedulerService  extends GcmTaskService {

    private static final int notificationId = 321748;
    private static Boolean isScheduled = false;
    private static final String TAG = SchedulerService.class.getSimpleName();
    public static final String GCM_SCHEDULER_TAG = "scheduler|[7200,1800]";


    @Override
    public void onInitializeTasks() {
        //called when app is updated to a new version, reinstalled etc.
        //you have to schedule your repeating tasks again
        super.onInitializeTasks();
        isScheduled = false;
        refreshRepeat(this);
    }

    @Override
    public int onRunTask(TaskParams taskParams) {
        String[] data = new String[]{
                FormsProviderAPI.FormsColumns._ID, FormsProviderAPI.FormsColumns.FILL_NEXT_DATE, FormsProviderAPI.FormsColumns.FILL_FREQUENCY
        };

        Cursor c = this.getContentResolver().query(FormsProviderAPI.FormsColumns.CONTENT_URI, data, null, null, null);

        Boolean displayNotification = false;
        try {
            while (c.moveToNext()) {
                try {
                    int idx_date = c.getColumnIndexOrThrow(FormsProviderAPI.FormsColumns.FILL_NEXT_DATE);
                    int idx_survey = c.getColumnIndexOrThrow(FormsProviderAPI.FormsColumns._ID);
                    int idx_status= c.getColumnIndexOrThrow(FormsProviderAPI.FormsColumns.FILL_STATUS_NOT_SCHEDULED);

                    int surveyID = c.getInt(idx_survey);

                    if (!c.isNull(idx_date) && c.getString(idx_status) != FormsProviderAPI.FormsColumns.FILL_STATUS_SCHEDULED) {
                        String storedDate = c.getString(idx_date);
                        DateFormat iso8601Format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        try {
                            Date storedDateParsed = iso8601Format.parse(storedDate);
                            Date currentDate = new Date();
                            if (storedDateParsed.before(currentDate)) {
                                ContentValues cv = new ContentValues();
                                cv.put(FormsProviderAPI.FormsColumns.FILL_NEXT_DATE, iso8601Format.format(new Date()));
                                cv.put(FormsProviderAPI.FormsColumns.FILL_STATUS, FormsProviderAPI.FormsColumns.FILL_STATUS_SCHEDULED);

                                this.getContentResolver().update(
                                        FormsProviderAPI.FormsColumns.CONTENT_URI,
                                        cv,
                                        FormsProviderAPI.FormsColumns._ID
                                                + "="
                                                + surveyID
                                        , null);
                                displayNotification = true;
                            }
                        } catch (ParseException e) {
                            Log.e(TAG, "Parsing ISO8601 datetime failed", e);
                        }
                    }

                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Trying to schedule on a database without date", e);
                    return GcmNetworkManager.RESULT_RESCHEDULE;
                }
            }
        }
        catch (Exception e)
        {
            Log.e(TAG, "Problem with accesubg internal database", e);
        }

        if (displayNotification) {

            Handler h = new Handler(getMainLooper());
            final Context ctx = this;
            try {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        NotificationManager notificationMgr = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                        Intent intent = new Intent(ctx, FormChooserList.class);
                        intent.putExtra("FilledSurveyList", true);

                        PendingIntent pIntent = PendingIntent.getActivity(ctx, 0, intent, 0);

                        Notification notification = new NotificationCompat.Builder(ctx)
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
            return GcmNetworkManager.RESULT_SUCCESS;
        }
        return GcmNetworkManager.RESULT_RESCHEDULE;
    }

    /*
     * @param context The Context of the app to be passed
     * @param forced Flag if to force the rescheduling. Setting it to true will reset the current waiting period
     */
    public static void refreshRepeat(Context context) {
        if (!isScheduled) {
            scheduleRepeat(context);
            isScheduled = true;
        }
    }

 /*
 * Schedule repeat of the service responsible for informing about new surveys to fill
 */
    public static void scheduleRepeat(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String period_string = settings.getString(PreferencesActivity.KEY_SCHEDULE_FREQUENCY, "3600");
        Boolean enabled_boolean = settings.getBoolean(PreferencesActivity.KEY_SURVEY_SCHEDULE, false);

        if (enabled_boolean.equals(false)) {
            try {
                GcmNetworkManager.getInstance(context).cancelAllTasks(SchedulerService.class);
            }
            catch (Exception e)
            {
                Log.e(TAG, "Could not cancel not existing service or other GCM issue");
            }
            return;
        }
        int period = Integer.parseInt(period_string);

        //in this method, single Repeating task is scheduled (the target service that will be called is SchedulerService.class)
        try {
            PeriodicTask.Builder periodic = new PeriodicTask.Builder()
                    //specify target service - must extend GcmTaskService
                    .setService(SchedulerService.class)
                            //set repeat period to one of the predefined values
                    .setPeriod(period)
                            // set the tolerance
                    .setFlex(period/10)
                            //mandatory tag
                    .setTag(GCM_SCHEDULER_TAG)
                            //whether the task persists after device reboot
                    .setPersisted(true)
                            //if another task with same tag is already scheduled, replace it with this task
                    .setUpdateCurrent(true)
                            //set required network state, this line is optional
                    .setRequiresCharging(false);


            PeriodicTask task = periodic.build();

            GcmNetworkManager.getInstance(context).schedule(task);
        } catch (Exception e) {
            Log.e(TAG, "scheduling failed");
            e.printStackTrace();
        }
        return;
    }
}
