package moe.reimu.weiboxposed;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p/>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
	public static String PREF_NAME = "prefs";

	public boolean isModuleEnabled() {
		return false;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getSharedPreferences(PREF_NAME, MODE_WORLD_READABLE).registerOnSharedPreferenceChangeListener(this);
		getFragmentManager().beginTransaction().replace(android.R.id.content, new GeneralPreferenceFragment()).commit();

		if (!isModuleEnabled()) {
			Toast.makeText(this, R.string.module_not_enabled, Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * This method stops fragment injection in malicious applications.
	 * Make sure to deny any unknown fragments here.
	 */
	protected boolean isValidFragment(String fragmentName) {
		return PreferenceFragment.class.getName().equals(fragmentName)
				|| GeneralPreferenceFragment.class.getName().equals(fragmentName);
	}


	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
										  String key){
		Toast.makeText(this, R.string.restart_weibo, Toast.LENGTH_SHORT).show();
	}

	public static class GeneralPreferenceFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			PreferenceManager manager = getPreferenceManager();
			manager.setSharedPreferencesMode(MODE_WORLD_READABLE);
			manager.setSharedPreferencesName(PREF_NAME);
			addPreferencesFromResource(R.xml.pref_general);
		}
	}
}
