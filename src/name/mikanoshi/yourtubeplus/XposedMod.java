package name.mikanoshi.yourtubeplus;

import java.util.ArrayList;

import android.content.Context;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

public class XposedMod implements IXposedHookLoadPackage {
	private static final String PREF_DEFAULT_PANE = "pref_default_pane";
	private static final String PREF_PLAYLIST = "pref_playlist";
	private static final String PREF_SUBSCRIPTION = "pref_subscription";
	private static final String PREF_OVERRIDE_DEVICE_SUPPORT = "pref_override_device_support";
	private static final String PREF_MAXIMUM_STREAM_QUALITY = "pref_maximum_stream_quality";
	private static final String DEFAULT_PANE = "FEsubscriptions";
	private static final String DEFAULT_STREAM_QUALITY = "-2";
	private static final String PANE_PLAYLIST = "0";
	private static final String PANE_SUBSCRIPTION = "1";

	private static boolean sNewVideo = true;
	private static ArrayList<Integer> sStreamQualities;

	@Override
	public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
		if (!lpparam.packageName.equals("com.google.android.youtube")) return;

		final XSharedPreferences prefs = new XSharedPreferences("name.mikanoshi.yourtubeplus");

		// Default pane.

		findAndHookMethod("dhj", lpparam.classLoader, "R", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				prefs.reload();
				String paneString = prefs.getString(PREF_DEFAULT_PANE, DEFAULT_PANE);
				/*	Pane ID:
					Trending:				FEtrending
					What to watch:			FEwhat_to_watch
					Subscriptions:			FEsubscriptions
					Browse channels:		FEguide_builder
					Uploads:				FEmy_videos
					Account:				FEaccount
					Library:				FElibrary (same as account?)
					Shared:					FEshared (blank page)
					Add audio tracks:		FEaudio_tracks (blank too)
					Watch Later:			VLWL
					Playlist:				VL + <playlist_id> (get playlist id from: https://www.youtube.com/playlist?list=playlist_id)
					Liked videos:			same as above
					Subscriptions:			<subscription_id> (get subscriptions id from: https://www.youtube.com/channel/subscription_id) */

				if (paneString.equals(PANE_PLAYLIST))
					paneString = "VL" + prefs.getString(PREF_PLAYLIST, "");
				else if (paneString.equals(PANE_SUBSCRIPTION))
					paneString = prefs.getString(PREF_SUBSCRIPTION, "");

				Object result = XposedHelpers.callStaticMethod(XposedHelpers.findClass("ond", lpparam.classLoader), "a", paneString);
				param.setResult(XposedHelpers.callStaticMethod(XposedHelpers.findClass("dhj", lpparam.classLoader), "a", result, true));
			}
		});

		// Override compatibility checks

		XC_MethodHook deviceSupportHook = new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				//XposedBridge.log("Check: " + String.valueOf((Integer)param.args[param.args.length == 1 ? 0 : 1]));
				prefs.reload();
				if (prefs.getBoolean(PREF_OVERRIDE_DEVICE_SUPPORT, false)) param.setResult(true);
			}
		};

		findAndHookMethod("mwu", lpparam.classLoader, "a", int.class, deviceSupportHook);
		findAndHookMethod("mwu", lpparam.classLoader, "a", Context.class, int.class, deviceSupportHook);

		// Default resolution.

		// We don't want to override the resolution when it's manually changed by the user, so we need to know
		// if the video was just opened (in which case the next time the resolution is set would be automatic) or not.
		findAndHookMethod("dig", lpparam.classLoader, "E", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				//XposedBridge.log("sNewVideo = true");
				sNewVideo = true;
			}
		});
/*        
		// Unknown
		findAndHookMethod("dig", lpparam.classLoader, "F", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
		});

		// Rotate
		findAndHookMethod("dig", lpparam.classLoader, "a", "cmt", "cmt", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
		});
*/
		// We also want to get a list of the available qualities for this video, because the one that is passed below is localized, so not comparable easily.
		// More classes with handleFormatStreamChangeEvent: qss, ttq, ttw, ukh
		findAndHookMethod("tyd", lpparam.classLoader, "handleFormatStreamChangeEvent", "rke", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				Object[] info = (Object[])getObjectField(param.args[0], "e");
				sStreamQualities = new ArrayList<Integer>();
				for (Object streamQuality: info)
				sStreamQualities.add(getIntField(streamQuality, "a"));
				//XposedBridge.log("Available resolutions: " + sStreamQualities.toString());
			}
		});

		// Override the default quality
		findAndHookMethod("eem", lpparam.classLoader, "a", "osk[]", int.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				if (sNewVideo) {
					sNewVideo = false;
					prefs.reload();
					/* Stream qualities:
						-2 is used for "Auto".
						Other qualities have their respective values (e.g. 720, 1080, etc). */
					int maximumStreamQuality = Integer.parseInt(prefs.getString(PREF_MAXIMUM_STREAM_QUALITY, DEFAULT_STREAM_QUALITY));
					int quality = -2;
					for (int streamQuality: sStreamQualities)
					if (streamQuality <= maximumStreamQuality) quality = streamQuality;

					if (quality == -2)
						return;
					else
						param.args[1] = sStreamQualities.indexOf(quality) + 1;

					/* This method only controls the list shown to the user and the current selection.
						It's called by handleFormatStreamChangeEvent, which in turn seems to be called by native code
						*after* the quality has been changed.
						This means that changing will only affect what's shown to the user as the selected quality, but
						not the *actual* quality. */

					// This method is the one called when the user presses the button, and actually causes the quality to change.
					callMethod(getObjectField(getObjectField(param.thisObject, "c"), "aa"), "a", quality);
					//XposedBridge.log("New quality: " + String.valueOf(quality));
				}    
			}
		});
/*        
		findAndHookMethod("eem", lpparam.classLoader, "handlePendingVideoQualityChangeEvent", "szh", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
		});
*/        
	}
}
