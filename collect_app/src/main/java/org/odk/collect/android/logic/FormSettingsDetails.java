package org.odk.collect.android.logic;

import java.io.Serializable;

public class FormSettingsDetails implements Serializable {

    private static final long serialVersionUID = 1L;

    public final String errorStr;

    public final String formName;
    public final String downloadUrl;
    public final String manifestUrl;
    public final String formID;
    public final String formVersion;


    public FormSettingsDetails(String error) {
        manifestUrl = null;
        downloadUrl = null;
        formName = null;
        formID = null;
        formVersion = null;
        errorStr = error;
    }


    public FormSettingsDetails(String name, String url, String manifest, String id, String version) {
        manifestUrl = manifest;
        downloadUrl = url;
        formName = name;
        formID = id;
        formVersion = version;
        errorStr = null;
    }

}
