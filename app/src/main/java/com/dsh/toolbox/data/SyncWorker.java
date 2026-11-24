package com.dsh.toolbox.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Background refresh of every selected course (runs every few hours via SyncScheduler). */
public class SyncWorker extends Worker {

    public static final String NAME = "periodic-sync";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        try {
            if (!CourseStore.anySelected(ctx)) {
                Log.w(NAME, "skipped, no course selected");
                return Result.success();
            }
            SyncAll.Report r = SyncAll.run(ctx);
            Log.i(NAME, "kept=" + r.kept + " sessionLost=" + r.sessionLost);
            SyncAlarm.schedule(ctx);
            return Result.success();
        } catch (Throwable t) {
            Log.w(NAME, "sync failed", t);
            return Result.retry();
        }
    }
}