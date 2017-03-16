package name.mikanoshi.yourtubeplus;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
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

	boolean wasStartedFromHome(Intent intent) {
		return (intent.hasExtra("alias") && intent.getStringExtra("alias").equals("com.google.android.apps.youtube.app.application.Shell$HomeActivity"));
	}
	
	byte[] getEndpoint(ClassLoader clsldr, String pane) {
		Object paneObj = XposedHelpers.callStaticMethod(XposedHelpers.findClass("pie", clsldr), "a", pane);
		return (byte[])XposedHelpers.callStaticMethod(XposedHelpers.findClass("aavb", clsldr), "toByteArray", paneObj);
	}
	
	void openPane(ClassLoader clsldr, String pane, Object wwActivity) {
		Object paneObj = XposedHelpers.callStaticMethod(XposedHelpers.findClass("pie", clsldr), "a", pane);
		Object paneParcelable = XposedHelpers.callStaticMethod(XposedHelpers.findClass("dol", clsldr), "a", paneObj, true);
		XposedHelpers.callMethod(wwActivity, "b", paneParcelable);
	}
	
	@Override
	public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
		if (!lpparam.packageName.equals("com.google.android.youtube")) return;

		final XSharedPreferences prefs = new XSharedPreferences("name.mikanoshi.yourtubeplus");

		// Default pane.

		findAndHookMethod("com.google.android.apps.youtube.app.WatchWhileActivity", lpparam.classLoader, "a", Intent.class, boolean.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				Intent intent = (Intent)param.args[0];
				if (!wasStartedFromHome(intent)) return;
				
				prefs.reload();
				// Pane to get back to
				String paneString = prefs.getString(PREF_DEFAULT_PANE, DEFAULT_PANE);
				if (paneString.equals("VLWL") || paneString.equals("FEmy_videos"))
				intent.putExtra("navigation_endpoint", getEndpoint(lpparam.classLoader, "FEaccount"));
				
				param.args[0] = intent;
			}
			
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				Intent intent = (Intent)param.args[0];
				if (!wasStartedFromHome(intent)) return;
				
				String paneString = prefs.getString(PREF_DEFAULT_PANE, DEFAULT_PANE);
				/*	Pane ID:
					Trending:				FEtrending
					What to watch:			FEwhat_to_watch
					Subscriptions:			FEsubscriptions
					Browse channels:		FEguide_builder
					Uploads:				FEmy_videos
					Account:				FEaccount
					Shared:					FEshared
					Library:				FElibrary (same as account?)
					Add audio tracks:		FEaudio_tracks (blank)
					Watch Later:			VLWL
					Playlist:				VL + <playlist_id> (get playlist id from: https://www.youtube.com/playlist?list=playlist_id)
					Liked videos:			same as above
					Subscriptions:			<subscription_id> (get subscriptions id from: https://www.youtube.com/channel/subscription_id) */

				if (paneString.equals(PANE_PLAYLIST))
					paneString = "VL" + prefs.getString(PREF_PLAYLIST, "");
				else if (paneString.equals(PANE_SUBSCRIPTION))
					paneString = prefs.getString(PREF_SUBSCRIPTION, "");
			
				Activity act = (Activity)param.thisObject;
				Intent paneIntent = new Intent(act.getBaseContext(), act.getClass());
				paneIntent.putExtra("navigation_endpoint", getEndpoint(lpparam.classLoader, paneString));
				paneIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
				act.startActivity(paneIntent);
			}
		});
/*		
		// Returns pane to be used in original method above
		findAndHookMethod("dol", lpparam.classLoader, "R", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
			}
		});
*/
		// Override compatibility checks

		XC_MethodHook deviceSupportHook = new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				//XposedBridge.log("Check: " + String.valueOf((Integer)param.args[param.args.length == 1 ? 0 : 1]));
				prefs.reload();
				if (prefs.getBoolean(PREF_OVERRIDE_DEVICE_SUPPORT, false)) param.setResult(true);
			}
		};

		try {
			findAndHookMethod("nqf", lpparam.classLoader, "a", int.class, deviceSupportHook);
			findAndHookMethod("nqf", lpparam.classLoader, "a", Context.class, int.class, deviceSupportHook);
		} catch(Throwable t)  {
			XposedBridge.log(t);
		}

		// Default resolution.

		// We don't want to override the resolution when it's manually changed by the user, so we need to know
		// if the video was just opened (in which case the next time the resolution is set would be automatic) or not.
		findAndHookMethod("drv", lpparam.classLoader, "E", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				//XposedBridge.log("sNewVideo = true");
				sNewVideo = true;
			}
		});
/*        
		// Unknown
		findAndHookMethod("drv", lpparam.classLoader, "G", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
		});

		// Rotate
		findAndHookMethod("drv", lpparam.classLoader, "a", "csd", "csd", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
		});
*/
		// We also want to get a list of the available qualities for this video, because the one that is passed below is localized, so not comparable easily.
		findAndHookMethod("uzu", lpparam.classLoader, "handleFormatStreamChangeEvent", "sjk", new XC_MethodHook() {
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
		findAndHookMethod("eos", lpparam.classLoader, "a", "pnn[]", int.class, new XC_MethodHook() {
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
		findAndHookMethod("eos", lpparam.classLoader, "handlePendingVideoQualityChangeEvent", "uat", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
		});
*/        
	}
}
