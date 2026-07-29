package com.dfamaya.concentric.companion

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import java.util.concurrent.Executor

/** The standalone watch face's applicationId — what we point Play at. Must match
 *  :app's and :mobile's applicationId. */
const val WATCH_FACE_PACKAGE = "com.dfamaya.concentric"

/** Where "Send feedback" mail is addressed. */
const val FEEDBACK_EMAIL = "developerdfa@gmail.com"

/**
 * Data Layer capability advertised by the watch face's res/values/wear.xml. A
 * reachable node providing it means the face is installed on a connected watch.
 * Must match the string-array item there.
 */
const val WATCH_FACE_CAPABILITY = "concentric_watchface"

/** What the companion FAB knows about the paired watch. */
enum class WatchState { CHECKING, NO_WATCH, NOT_INSTALLED, INSTALLED }

/**
 * Queries the Wearable Data Layer for the current [WatchState]: whether a watch
 * is reachable and, if so, whether the face is installed on it. Safe to call
 * repeatedly (e.g. on every resume). Any Data Layer failure degrades to
 * [WatchState.NO_WATCH] rather than throwing.
 */
suspend fun queryWatchState(context: Context): WatchState = try {
    val nodes = Wearable.getNodeClient(context).connectedNodes.await()
    if (nodes.isEmpty()) {
        WatchState.NO_WATCH
    } else {
        val capability = Wearable.getCapabilityClient(context)
            .getCapability(WATCH_FACE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .await()
        if (capability.nodes.isNotEmpty()) WatchState.INSTALLED else WatchState.NOT_INSTALLED
    }
} catch (e: Exception) {
    WatchState.NO_WATCH
}

enum class InstallResult { SENT, NO_WATCH }

private val mainExecutor = Executor { command -> Handler(Looper.getMainLooper()).post(command) }

/**
 * Opens the watch face's Play Store listing on the paired Wear OS watch using
 * [RemoteActivityHelper] — the documented way to prompt a watch-side install
 * from a phone. [onResult] is delivered on the main thread.
 */
fun installOnWatch(context: Context, onResult: (InstallResult) -> Unit) {
    val intent = Intent(Intent.ACTION_VIEW)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .setData(Uri.parse("market://details?id=$WATCH_FACE_PACKAGE"))
    val future = RemoteActivityHelper(context).startRemoteActivity(intent)
    future.addListener({
        val result = try {
            future.get()
            InstallResult.SENT
        } catch (e: Exception) {
            InstallResult.NO_WATCH
        }
        onResult(result)
    }, mainExecutor)
}

/**
 * Opens the watch face's review section in the Play Store on the phone, falling
 * back to the browser. Returns false only if neither can be launched.
 */
fun openReview(context: Context): Boolean {
    val market = viewIntent("market://details?id=$WATCH_FACE_PACKAGE&showAllReviews=true")
    val web = viewIntent("https://play.google.com/store/apps/details?id=$WATCH_FACE_PACKAGE&showAllReviews=true")
    return startFirstAvailable(context, market, web)
}

/**
 * Opens the device's email composer addressed to [FEEDBACK_EMAIL], prefilled
 * with a subject and the app version (so reports are easy to triage). Uses
 * ACTION_SENDTO with a mailto: URI — the documented way to target email apps
 * only. Returns false if no email app is available.
 */
fun sendFeedback(context: Context): Boolean {
    val subject = Uri.encode(context.getString(R.string.feedback_subject))
    val body = Uri.encode(context.getString(R.string.feedback_body, appVersion(context)))
    val intent = Intent(
        Intent.ACTION_SENDTO,
        Uri.parse("mailto:$FEEDBACK_EMAIL?subject=$subject&body=$body"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** "versionName (versionCode)" for this app, e.g. "1.0 (100001)". */
private fun appVersion(context: Context): String = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION") info.versionCode.toLong()
    }
    "${info.versionName} ($code)"
} catch (e: Exception) {
    "unknown"
}

private fun viewIntent(uri: String) =
    Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun startFirstAvailable(context: Context, vararg intents: Intent): Boolean {
    for (intent in intents) {
        try {
            context.startActivity(intent)
            return true
        } catch (e: ActivityNotFoundException) {
            // try the next fallback
        }
    }
    return false
}
