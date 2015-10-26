package org.odk.collect.android.services;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;

import java.io.File;

public class SchedulerService  extends GcmTaskService {

    public enum SurveyStatus
    {

    }

    @Override
    public int onRunTask(TaskParams taskParams) {
        return 0;
    }


    String getStatus(String surveyId)
    {
        return "";
    }
}
