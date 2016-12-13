package org.secuso.privacyfriendlytlsmetric.ConnectionAnalysis;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import org.secuso.privacyfriendlytlsmetric.Assistant.AsyncDNS;
import org.secuso.privacyfriendlytlsmetric.Assistant.Const;
import org.secuso.privacyfriendlytlsmetric.Assistant.RunStore;
import org.secuso.privacyfriendlytlsmetric.R;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Collector class collects data from the services and processes it for inter process communication
 * with the UI.
 */
public class Collector {

    //public Member for collecting non-serializable packet information like icons
    public static HashMap<String, PackageInfo> mPackageMap = new HashMap<>();

    //Data processing maps
    private static ArrayList<Report> mReportList;
    private static HashMap<String, List<Report>> mReportsByApp;
    private static HashMap<Integer, PackageInfo> mUidPackageMap;

    //Pushed the newest availiable information as deep copy.
    public static HashMap<String, List<Report>> provideSimpleReports(){
        updateReports();
        filterReports();
        return mReportsByApp;
        //return mFilteredReportsByApp;
    }

    public static HashMap<String, List<Report>> provideFullReports() {
        updateReports();
        return mReportsByApp;
    }

    //Generate an overview List, with only one report per remote address per app
    private static void filterReports() {
        HashMap<String, List<Report>> filteredReportsByApp = new HashMap<>();

        for (String key:mReportsByApp.keySet()){
            filteredReportsByApp.put(key, new ArrayList<Report>());
            ArrayList<Report> list = (ArrayList<Report>) mReportsByApp.get(key);
            ArrayList<Report> filteredList = (ArrayList<Report>) filteredReportsByApp.get(key);
            boolean isPresent = false;

            for (int i = 0; i < list.size(); i++){
                String add = list.get(i).getRemoteAdd().getHostAddress();
                for (int j = 0; j < filteredList.size(); j++){
                    if(add.equals(filteredList.get(j).getRemoteAdd().getHostAddress())){
                        isPresent = true;
                    }
                    break;
                }
                if (!isPresent) {filteredList.add(list.get(i));}
            }
        }
    }

    private static void updateReports(){
        //update reports
        pull();
        //process reports (passive mode)
        fillPackageInformation();
        //resolve remote hosts (in cache or permission.INTERNET required)
        new AsyncDNS().execute("");
        //sorting
        sortReportsToMap();
        //update package info
        updatePI();
    }

    private static void updatePI() {
        for (Integer i : mUidPackageMap.keySet()) {
            PackageInfo pi = mUidPackageMap.get(i);
            mPackageMap.put(pi.applicationInfo.name, pi);
        }

    }

    //Sorts the reports by app package name to a HashMap
    private static void sortReportsToMap() {
        mReportsByApp = new HashMap<>();

        for (int i = 0; i < mReportList.size(); i++) {
            Report r = mReportList.get(i);

            if (!mReportsByApp.containsKey(r.getAppName())) {
                mReportsByApp.put(r.getAppName(), new ArrayList<Report>());
            }
            mReportsByApp.get(r.getAppName()).add(r);
        }
    }


    //pull records from detector and make a deep copy for frontend - usage
    private static void pull() {
        ArrayList<Report> reportList = new ArrayList<>();
        Set<Integer> keySet = Detector.sReportMap.keySet();
        for (int i : keySet) {
            reportList.add(Detector.sReportMap.get(i));
        }
        mReportList = deepCloneReportList(reportList);
    }

    //Make an async reverse DNS request
    public static void resolveHosts() {
        for (Report r : mReportList){
            try {
                r.getRemoteAdd().getHostName();
                r.setRemoteResolved(true);
            } catch(RuntimeException e) {
                r.setRemoteResolved(false);
                Log.e(Const.LOG_TAG, "Attempt to resolve host name failed");
                e.printStackTrace();
            }
        }
    }

    private static void fillPackageInformation() {
        //Get Package Information
        for (Report r : mReportList) {
            updatePackageCache();
            if(mUidPackageMap.containsKey(r.getUid())){
                PackageInfo pi = mUidPackageMap.get(r.getUid());
                r.setAppName(pi.applicationInfo.name);
                r.setPackageName(pi.packageName);
            } else {
                r.setAppName("Unknown App");
                r.setAppName("app.unknown");
            }
        }
    }

    //Make a deep copy of the report list
    private static ArrayList<Report> deepCloneReportList(ArrayList<Report> reportList) {
        ArrayList<Report> cloneList = new ArrayList<>();
        try {
            for (int i = 0; i < reportList.size(); i++) {
                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(byteOut);
                out.writeObject(reportList.get(i));
                out.flush();
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(byteOut.toByteArray()));
                cloneList.add(Report.class.cast(in.readObject()));
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return cloneList;
    }

    //Updates the PkgInfo hash map with new entries.
    private static void updatePackageCache() {
        mUidPackageMap = new HashMap();

        if(Const.IS_DEBUG){ printAllPackages(); }
        ArrayList<PackageInfo> infoList = (ArrayList<PackageInfo>) getPackages(RunStore.getContext());
        for (PackageInfo i : infoList) {
            if (i != null) {
                mUidPackageMap.put(i.applicationInfo.uid, i);
            }
        }
    }

    private static List<PackageInfo> getPackages(Context context) {
        synchronized (context.getApplicationContext()) {
                PackageManager pm = context.getPackageManager();
            return new ArrayList<>(pm.getInstalledPackages(0));
        }
    }

    //degub print: Print all reachable active processes
    private static void printAllPackages() {
            ArrayList<PackageInfo> infoList = (ArrayList<PackageInfo>) getPackages(RunStore.getContext());
            for (PackageInfo i : infoList) {
                Log.d(Const.LOG_TAG, i.packageName + " uid_" + i.applicationInfo.uid);
            }
    }

}
