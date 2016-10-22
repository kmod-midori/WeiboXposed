package moe.reimu.weiboxposed;

import android.app.Activity;
import android.app.AndroidAppHelper;
import android.content.Intent;
import android.content.res.XResources;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RelativeLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

public class Module implements IXposedHookInitPackageResources, IXposedHookLoadPackage, IXposedHookZygoteInit {
	public XSharedPreferences prefs;

	private static String MOD_PACKAGE_NAME = Module.class.getPackage().getName();
	private static String WB_PACKAGE_NAME = "com.sina.weibo";
	private boolean remove_hot = false;

	@Override
	public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable {
		if (!resparam.packageName.equals(Module.WB_PACKAGE_NAME))
			return;

		// Disable Special BG touch event by setting width to 0
		resparam.res.setReplacement("com.sina.weibo", "dimen", "feed_title_specialbg_width",
				new XResources.DimensionReplacement(0, TypedValue.COMPLEX_UNIT_PX));
	}

	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		prefs = new XSharedPreferences(MOD_PACKAGE_NAME, SettingsActivity.PREF_NAME);
		prefs.makeWorldReadable();

		log("Pref Init");
	}

	@Override
	public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
		if (lpparam.packageName.equals(WB_PACKAGE_NAME))
			hookWeibo(lpparam);

		if (lpparam.packageName.equals(MOD_PACKAGE_NAME)) {
			hookSelf(lpparam);
		}

	}

	private void hookSelf(final XC_LoadPackage.LoadPackageParam lpparam) {
		// "Enable" the module
		findAndHookMethod(MOD_PACKAGE_NAME + ".SettingsActivity", lpparam.classLoader, "isModuleEnabled",
				XC_MethodReplacement.returnConstant(true));
	}

	public boolean isPromotion(Object mblog) {
		try {
			Object promotion = getObjectField(mblog, "promotion");

			String scheme = (String)getObjectField(mblog, "scheme");
			Object title = getObjectField(mblog, "title");
			boolean is_friend_hot = false;

			if (promotion != null) {
				String ad_type = (String)getObjectField(promotion, "adtype");
				log(scheme + " detected as promotion: adtype");
				if(remove_hot) {
					return true;
				} else {
					if (!"8".equals(ad_type)) {
						return true;
					} else {
						is_friend_hot = true;
					}
				}
			}

			if (title != null && !is_friend_hot) {
				String text = (String)getObjectField(title, "text");
				if (!"".equals(text)) {
					log(scheme + " detected as promotion: non-empty title");
					return true;
				}
			}

		} catch(NoSuchFieldError e) {
			log(e.getMessage());
		}

		return false;
	}

	private void log(String text) {
		XposedBridge.log("[WeiboXposed] " + text);
	}

	XC_MethodHook removeAD = new XC_MethodHook() {
		@Override
		protected void afterHookedMethod(MethodHookParam param) throws Throwable {
			ArrayList<Object> origResult = (ArrayList<Object>)param.getResult();
			for (Iterator<Object> iterator = origResult.iterator(); iterator.hasNext(); ) {
				Object mblog = iterator.next();
				if (isPromotion(mblog)) {
					iterator.remove();
				}
			}
		}
	};

	XC_MethodHook callbackCancel = XC_MethodReplacement.returnConstant(null);

	private void hookAD(final XC_LoadPackage.LoadPackageParam lpparam) {
		final String LIST_BASE = "com.sina.weibo.models.MBlogListBaseObject";
		findAndHookMethod(LIST_BASE, lpparam.classLoader, "setTrends", List.class, callbackCancel);
		findAndHookMethod(LIST_BASE, lpparam.classLoader, "getTrends", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				param.setResult(new ArrayList<>());
			}
		});
		findAndHookMethod(LIST_BASE, lpparam.classLoader, "insetTrend", callbackCancel);
		findAndHookMethod(LIST_BASE, lpparam.classLoader, "getStatuses", removeAD);
		findAndHookMethod(LIST_BASE, lpparam.classLoader, "getStatusesCopy", removeAD);
	}

	private void hookNightMode(final XC_LoadPackage.LoadPackageParam lpparam) {
		findAndHookMethod("com.sina.weibo.models.ThemeList", lpparam.classLoader, "initFromJsonObject", JSONObject.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				JSONObject json = (JSONObject) param.args[0];
				JSONArray themeList = json.optJSONArray("list");
				if (themeList == null) {
					return;
				}
				JSONObject nightTheme = new JSONObject("{\"skinname\":\"Night\",\"packagename\":\"com.sina.weibo.nightdream\",\"downloadlink\":\"\",\"iconurl\":\"\",\"previewimgurl\":\"\",\"platform\":\"Android\",\"version\":\"9.1.0\",\"filesize\":\"\",\"md5\":\"\",\"addtime\":\"\",\"isvip\":\"0\",\"showimg\":\"\"}");
				themeList.put(nightTheme);
				json.put("list", themeList);
			}
		});
		findAndHookMethod("com.sina.weibo.MoreItemsActivity", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				Object act = param.thisObject;
				// Night Mode Item -> o
				RelativeLayout layout = (RelativeLayout)getObjectField(act, "o");
				layout.setVisibility(View.VISIBLE);
			}
		});
	}

	private void hookBrowser(final XC_LoadPackage.LoadPackageParam lpparam) {
		final String EXTRAS_KEY = "com_sina_weibo_weibobrowser_url";
		findAndHookMethod(Activity.class, "startActivityForResult", Intent.class, int.class, Bundle.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				Intent intent = (Intent)param.args[0];
				if (intent.getBooleanExtra(MOD_PACKAGE_NAME, false)) {
					log("[Browser] Loop detected, skipping");
					return;
				}

				Bundle extras = intent.getExtras();
				if (extras == null) return;

				Uri url;
				log("extras" + extras.toString());
				String urlStr = extras.getString(EXTRAS_KEY);

				if (urlStr == null || "".equals(urlStr)) {
					url = intent.getData();
					if (url == null) return;
					log("origUrl " + url.toString());
					if (url.getScheme().equals("sinaweibo")) {
						String tmpUrl = url.getQueryParameter("url");
						if (tmpUrl == null) {
							tmpUrl = url.getQueryParameter("showurl");
						}
						if (tmpUrl == null) return;
						url = Uri.parse(tmpUrl);
					}

					if (!url.getScheme().startsWith("http")) return;
				} else {
					url = Uri.parse(urlStr);
				}

				if (url.getHost().endsWith("weibo.com") || url.getHost().endsWith("weibo.cn")) return;
				param.setResult(null);
				openUrl(url);
			}
		});
	}

	private void hookWeibo(final XC_LoadPackage.LoadPackageParam lpparam) {
		prefs.reload();

		log("App Weibo Loaded");
		remove_hot = prefs.getBoolean("remove_hot", false);
		hookAD(lpparam);

		hookNightMode(lpparam);

		boolean forceBrowser = prefs.getBoolean("force_browser", false);
		if (forceBrowser) hookBrowser(lpparam);

	}

	private void openUrl(Uri url) {
		Intent intent = new Intent();

		intent.setAction(Intent.ACTION_VIEW);
		intent.setData(url);
		intent.putExtra(MOD_PACKAGE_NAME, true);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		AndroidAppHelper.currentApplication().startActivity(intent);
	}
}
