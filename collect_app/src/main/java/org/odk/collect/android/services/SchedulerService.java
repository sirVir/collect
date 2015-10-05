//package org.odk.collect.android.services;
//
//import android.content.SharedPreferences;
//import android.preference.PreferenceManager;
//
//import com.google.android.gms.gcm.GcmNetworkManager;
//import com.google.android.gms.gcm.GcmTaskService;
//import com.google.android.gms.gcm.TaskParams;
//
//import java.io.File;
//
//public class SchedulerService  extends GcmTaskService {
//
///*
//
//    public static final String SCHEDULER_STORAGE_NAME = "SchedulerStorage";
//
//
//    private static final String TAG = SchedulerService.class.getSimpleName();
//
//    @Override
//    public void onInitializeTasks() {
//        //called when app is updated to a new version, reinstalled etc.
//        //you have to schedule your repeating tasks again
//        super.onInitializeTasks();
//
//        // schedule! no matter what
//    }
//
//
//
//    public void addSchedule (File f)
//    {
//        f.
//
//        /* PARSE THE FILE
//        TAKE THE PARAMETERS - id AND SCHEDULE
//        Add them to database
//        that's it
//         */
//    }
//
//
//    @Override
//    public int onRunTask(TaskParams taskParams) {
//
//        SharedPreferences.Editor editor = getSharedPreferences(SCHEDULER_STORAGE_NAME, MODE_PRIVATE).edit();
//
//        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
//        String choosen = settings.getString(PreferencesActivity.KEY_AUTOPULL_NEW, "connected");
//        String period_string = settings.getString(PreferencesActivity.KEY_AUTOPULL_FREQUENCY, "3600");
//
//            return GcmNetworkManager.RESULT_SUCCESS;
//    }
//
//
//    /*
//     * @param context The Context of the app to be passed
//     * @param forced Flag if to force the rescheduling. Setting it to true will reset the current waiting period
//     */
//    public static void refreshRepeat(Context context, boolean forced) {
//        if (forced)
//            scheduleRepeat(context, false);
//        else if (!isScheduled)
//        {
//            scheduleRepeat(context, false);
//        }
//        isScheduled = true;
//    }
//
//    private static void scheduleRepeat(Context context, Boolean isDisabled) {
//        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
//        String choosen = settings.getString(PreferencesActivity.KEY_AUTOPULL_NEW, "connected");
//        String period_string = settings.getString(PreferencesActivity.KEY_AUTOPULL_FREQUENCY, "3600");
//
//        int period = Integer.parseInt(period_string);
//        if (choosen.equals("disabled") || isDisabled) {
//            GcmNetworkManager.getInstance(context).cancelAllTasks(SurveyCheckService.class);
//            return;
//        }
//
//        Log.e(TAG, choosen);
//        //in this method, single Repeating task is scheduled (the target service that will be called is SurveyCheckService.class)
//        try {
//            OneoffTask.Builder periodic = new OneoffTask.Builder()
//                    //specify target service - must extend GcmTaskService
//                    .setService(SurveyCheckService.class)
//                            //set repeat period to one of the predefined values
//                    .setExecutionWindow(period - period / 10, period + period/10)
//                            //10% of flexibility allowed
//                    .setTag(GCM_REPEAT_TAG)
//                            //whether the task persists after device reboot
//                    .setPersisted(true)
//                            //if another task with same tag is already scheduled, replace it with this task
//                    .setUpdateCurrent(true)
//                            //set required network state, this line is optional
//                    .setRequiresCharging(false);
//
//            if (choosen.equals("connected")) {
//                periodic.setRequiredNetwork(Task.NETWORK_STATE_CONNECTED);
//
//            } else if (choosen.equals("unmetered")) {
//                periodic.setRequiredNetwork(Task.NETWORK_STATE_UNMETERED);
//
//            } else {
//                throw new IllegalArgumentException("Unsupported scheduling command");
//            }
//
//            OneoffTask task = periodic.build();
//
//            GcmNetworkManager.getInstance(context).schedule(task);
//        } catch (Exception e) {
//            Log.e(TAG, "scheduling failed");
//            e.printStackTrace();
//        }
//        return;
//    }
//
//
//    public void formListDownloadingComplete(final HashMap<String, FormDetails> value, Boolean silent) {
//
//        if (!value.isEmpty()) {
//
//            Handler h = new Handler(getMainLooper());
//            final Context c = this;
//            try {
//                h.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        NotificationManager notificationMgr = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
//                        Intent intent = new Intent(c, FormDownloadList.class);
//                        intent.putExtra("SurveyList", value);
//
//                        PendingIntent pIntent = PendingIntent.getActivity(c, 0, intent, 0);
//
//                        Notification notification = new NotificationCompat.Builder(c)
//                                .setContentTitle("ODK Collect")
//                                .setContentText("Click to see unfilled surveys")
//                                .setTicker("Pending surveys to fill")
//                                .setSmallIcon(android.R.drawable.stat_notify_more)
//                                .setContentIntent(pIntent)
//                                .build();
//                        notification.defaults |= Notification.DEFAULT_SOUND;
//
//                        notificationMgr.notify(notificationId, notification);
//                    }
//                });
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//            isScheduled = false;
//        }
//    }
//
//    /**
//     * Cancel the pending notification.
//     * @param c Context to be passed
//     */
//    public static void cancelNotification(final Context c) {
//        new Thread() {
//            @Override
//            public void run() {
//                NotificationManager notificationMgr = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
//                notificationMgr.cancel(notificationId);
//            }
//        }.start();
//    }
//
//
//}
