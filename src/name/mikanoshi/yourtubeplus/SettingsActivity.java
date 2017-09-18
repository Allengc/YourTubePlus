package name.mikanoshi.yourtubeplus;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

import name.mikanoshi.yourtubeplus.R;

public class SettingsActivity extends Activity {
	private static final String PANE_PLAYLIST = "0";
	private static final String PANE_SUBSCRIPTION = "1";
	
	public static AlertDialog aboutdlg;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState == null)
		getFragmentManager().beginTransaction().replace(android.R.id.content, new PrefsFragment()).commit();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.actions, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		aboutdlg.show();
		return true;
	}

	public static class PrefsFragment extends PreferenceFragment {
		@SuppressWarnings("deprecation")
		@Override
		@SuppressLint("InflateParams")
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preferences);

			ListPreference defaultPanePref = (ListPreference) findPreference("pref_default_pane");
			final EditTextPreference playlistPref = (EditTextPreference) findPreference("pref_playlist");
			final EditTextPreference subscriptionPref = (EditTextPreference) findPreference("pref_subscription");
			Preference showAppIconPref = findPreference("pref_show_app_icon");

			if (defaultPanePref.getValue().equals(PANE_PLAYLIST))
				playlistPref.setEnabled(true);
			else if (defaultPanePref.getValue().equals(PANE_SUBSCRIPTION))
				subscriptionPref.setEnabled(true);

			defaultPanePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object o) {
					if (o.equals(PANE_PLAYLIST)) {
						playlistPref.setEnabled(true);
						subscriptionPref.setEnabled(false);
					} else if (o.equals(PANE_SUBSCRIPTION)) {
						subscriptionPref.setEnabled(true);
						playlistPref.setEnabled(false);
					} else {
						subscriptionPref.setEnabled(false);
						playlistPref.setEnabled(false);
					}
					return true;
				}
			});

			showAppIconPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					Activity context = getActivity();
					int state = (Boolean) newValue ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
					final ComponentName alias = new ComponentName(context, "name.mikanoshi.yourtubeplus.SettingsActivity-Alias");
					context.getPackageManager().setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP);
					return true;
				}
			});
			
			final Activity context = getActivity();
			AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
			View dialogView = context.getLayoutInflater().inflate(R.layout.about, null);
			TextView ver = (TextView)dialogView.findViewById(R.id.textViewVersion);
			try {
				ver.setText(getResources().getString(R.string.about_version) + ": " + context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
			} catch (Exception e) {}
			Button close = (Button)dialogView.findViewById(R.id.buttonClose);
			close.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					aboutdlg.dismiss();
				}
			});
			Button support = (Button)dialogView.findViewById(R.id.buttonSupport);
			support.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent uriIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://forum.xda-developers.com/xposed/modules/yourtube-t3544684"));
					if (uriIntent.resolveActivity(context.getPackageManager()) != null)
					context.startActivity(uriIntent);
				}
			});
			Button donate = (Button)dialogView.findViewById(R.id.buttonDonate);
			donate.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent uriIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://sensetoolbox.com/donate"));
					if (uriIntent.resolveActivity(context.getPackageManager()) != null)
					context.startActivity(uriIntent);
				}
			});
			
			dialogBuilder.setView(dialogView);
			aboutdlg = dialogBuilder.create();
		}

		@Override
		public void onPause() {
			super.onPause();

			// Set preferences permissions to be world readable
			// Workaround for Android N and above since MODE_WORLD_READABLE will cause security exception and FC.
			final File dataDir = new File(getActivity().getApplicationInfo().dataDir);
			final File prefsDir = new File(dataDir, "shared_prefs");
			final File prefsFile = new File(prefsDir, getPreferenceManager().getSharedPreferencesName() + ".xml");

			if (prefsFile.exists()) {
				dataDir.setReadable(true, false);
				dataDir.setExecutable(true, false);

				prefsDir.setReadable(true, false);
				prefsDir.setExecutable(true, false);

				prefsFile.setReadable(true, false);
				prefsFile.setExecutable(true, false);
			}
		}
	}
}
