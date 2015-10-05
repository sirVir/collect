/*
 * Copyright (C) 2009 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.tasks;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.javarosa.xform.parse.XFormParser;
import org.kxml2.kdom.Element;
import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.exception.TaskCancelledException;
import org.odk.collect.android.listeners.FormDownloaderListener;
import org.odk.collect.android.logic.FormDetails;
import org.odk.collect.android.preferences.PreferencesActivity;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.utilities.DocumentFetchResult;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.WebUtils;
import org.opendatakit.httpclientandroidlib.Header;
import org.opendatakit.httpclientandroidlib.HttpEntity;
import org.opendatakit.httpclientandroidlib.HttpResponse;
import org.opendatakit.httpclientandroidlib.HttpStatus;
import org.opendatakit.httpclientandroidlib.client.HttpClient;
import org.opendatakit.httpclientandroidlib.client.methods.HttpGet;
import org.opendatakit.httpclientandroidlib.protocol.HttpContext;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Background task for downloading a given list of forms, together wit settings associated with them. We assume right now that both setting and the forms are
 * coming from the same server that presented the form list, but theoretically that won't always be
 * true.
 *
 * @author msundt
 * @author carlhartung
 */
public class DownloadCollectSettingsTask extends
        AsyncTask<List<String>, String, Boolean> {

    private static final String TEMP_DOWNLOAD_EXTENSION = ".tempDownload";
    private static final String t = "DownloadCollectSettingsTask";

    @Override
    protected Boolean doInBackground(List<String>... values) {
        List<String> toDownload = values[0];

        boolean result = true;

        for (String s : toDownload) {

            if (isCancelled()) {
                break;
            }

            try {
                // get the xml file
                // if we've downloaded a duplicate, this gives us the file
                downloadCollectorSetting(s);
            } catch (Exception e) {
                e.printStackTrace();
                result = false;
            }
        }
        return result;

    }

    /**
     * Common routine to download a document from the downloadUrl and save the contents in the file
     * 'file'. Used only to download (optional) settings, application settings and admin settings (thus static). Returns boolean on result, does not interfere
     * the normal execution.
     *
     * @param file        the final file
     * @param downloadUrl the url to get the contents from.
     * @throws Exception
     */
    public static boolean TryDownloadFile (File file, String downloadUrl) throws Exception {
        File tempFile = File.createTempFile(file.getName(), TEMP_DOWNLOAD_EXTENSION, new File(Collect.CACHE_PATH));

        URI uri;
        try {
            // assume the downloadUrl is escaped properly
            URL url = new URL(downloadUrl);
            uri = url.toURI();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw e;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            throw e;
        }

        // WiFi network connections can be renegotiated during a large form download sequence.
        // This will cause intermittent download failures.  Silently retry once after each
        // failure.  Only if there are two consecutive failures, do we abort.
        boolean success = false;
        int attemptCount = 0;
        final int MAX_ATTEMPT_COUNT = 2;
        while ( !success && ++attemptCount <= MAX_ATTEMPT_COUNT ) {


            {
                Log.i(t, "Started downloading to " + tempFile.getAbsolutePath() + " from " + downloadUrl);
            }

            // get shared HttpContext so that authentication and cookies are retained.
            HttpContext localContext = Collect.getInstance().getHttpContext();

            HttpClient httpclient = WebUtils.createHttpClient(WebUtils.CONNECTION_TIMEOUT);

            // set up request...
            HttpGet req = WebUtils.createOpenRosaHttpGet(uri);
            req.addHeader(WebUtils.ACCEPT_ENCODING_HEADER, WebUtils.GZIP_CONTENT_ENCODING);

            HttpResponse response;
            try {
                response = httpclient.execute(req, localContext);
                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode != HttpStatus.SC_OK) {
                    WebUtils.discardEntityBytes(response);
                    if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
                        // clear the cookies -- should not be necessary?
                        Collect.getInstance().getCookieStore().clear();
                    }
                    if (statusCode == HttpStatus.SC_NOT_FOUND) {
                        throw new FileNotFoundException();
                    }
                    String errMsg =
                            Collect.getInstance().getString(R.string.file_fetch_failed, downloadUrl,
                                    response.getStatusLine().getReasonPhrase(), statusCode);
                    Log.e(t, errMsg);
                    throw new Exception(errMsg);
                }

                // write connection to file
                InputStream is = null;
                OutputStream os = null;
                try {
                    HttpEntity entity = response.getEntity();
                    is = entity.getContent();
                    Header contentEncoding = entity.getContentEncoding();
                    if ( contentEncoding != null && contentEncoding.getValue().equalsIgnoreCase(WebUtils.GZIP_CONTENT_ENCODING) ) {
                        is = new GZIPInputStream(is);
                    }
                    os = new FileOutputStream(tempFile);
                    byte buf[] = new byte[4096];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        os.write(buf, 0, len);
                    }
                    os.flush();
                    success = true;
                } finally {
                    if (os != null) {
                        try {
                            os.close();
                        } catch (Exception e) {
                        }
                    }
                    if (is != null) {
                        try {
                            // ensure stream is consumed...
                            final long count = 1024L;
                            while (is.skip(count) == count)
                                ;
                        } catch (Exception e) {
                            // no-op
                        }
                        try {
                            is.close();
                        } catch (Exception e) {
                        }
                    }
                }
            }catch (FileNotFoundException e) {
                // Log.e(t, e.toString());
                // silently retry unless this is the last attempt,
                // in which case we rethrow the exception.

                FileUtils.deleteAndReport(tempFile);

                // if ( attemptCount == MAX_ATTEMPT_COUNT ) {
                //     throw e;
                // }
                return false;
            }

            catch (Exception e) {
                Log.e(t, e.toString());
                // silently retry unless this is the last attempt,
                // in which case we rethrow the exception.

                FileUtils.deleteAndReport(tempFile);

                if ( attemptCount == MAX_ATTEMPT_COUNT ) {
                    throw e;
                }
            }

        }

        Log.d(t, "Completed downloading of " + tempFile.getAbsolutePath() + ". It will be moved to the proper path...");

        FileUtils.deleteAndReport(file);

        String errorMessage = FileUtils.copyFile(tempFile, file);

        if (file.exists()) {
            Log.w(t, "Copied " + tempFile.getAbsolutePath() + " over " + file.getAbsolutePath());
            FileUtils.deleteAndReport(tempFile);
        } else {
            String msg = Collect.getInstance().getString(R.string.fs_file_copy_error, tempFile.getAbsolutePath(), file.getAbsolutePath(), errorMessage);
            Log.w(t, msg);
            throw new RuntimeException(msg);
        }
        return true;
    }



    private File downloadCollectorSetting(String settingName) throws Exception {

        SharedPreferences settings =
                PreferenceManager.getDefaultSharedPreferences(Collect.getInstance().getBaseContext());
        String serverURL =
                settings.getString(PreferencesActivity.KEY_SERVER_URL,
                        Collect.getInstance().getString(R.string.default_server_url));
        String formSettingsUrl = Collect.getInstance().getApplicationContext().getString(R.string.default_collector_setting);

        String downloadSettingUrl = serverURL + formSettingsUrl + "?settings_name=" + settingName;


        // proposed name of xml file...
        String path = Collect.SETTINGS_PATH + File.separator + settingName + ".xml";
        File f = new File(path);

        boolean gotSettings = TryDownloadFile(f, downloadSettingUrl);

        if (gotSettings == false)
        {
            f.delete();
            return null;
        }


        return f;
    }

}
