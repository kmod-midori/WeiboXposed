package moe.reimu.weiboxposed;

import android.app.Activity;
import android.app.AndroidAppHelper;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.XResources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
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

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

public class Module implements IXposedHookInitPackageResources, IXposedHookLoadPackage, IXposedHookZygoteInit {
	private XSharedPreferences prefs;

	private static String MOD_PACKAGE_NAME = Module.class.getPackage().getName();
	private static String WB_PACKAGE_NAME = "com.sina.weibo";
	private boolean remove_hot = false;
	private List<String> enabled_feature = Arrays.asList("Night_Mode", "");
	private List<String> disabled_feature;

	@Override
	public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable {
		if (!resparam.packageName.equals(Module.WB_PACKAGE_NAME))
			return;

		// Disable Special BG touch event by setting width to 0
		resparam.res.setReplacement(WB_PACKAGE_NAME, "dimen", "feed_title_specialbg_width",
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

	private boolean isPromotion(Object mblog) {
		try {
			Object promotion = getObjectField(mblog, "promotion");

			String scheme = (String) getObjectField(mblog, "scheme");
			Object title = getObjectField(mblog, "title");
			boolean is_friend_hot = false;

			if (promotion != null) {
				String ad_type = (String) getObjectField(promotion, "adtype");
				logd(scheme + " detected as promotion: adtype");
				if (remove_hot) {
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
				String text = (String) getObjectField(title, "text");
				if (!"".equals(text)) {
					logd(scheme + " detected as promotion: non-empty title");
					return true;
				}
			}

		} catch (NoSuchFieldError e) {
			log(e.getMessage());
		}

		return false;
	}

	private void log(String text) {
		XposedBridge.log("[WeiboXposed] " + text);
	}

	private void logd(String text) {
		if (BuildConfig.DEBUG) XposedBridge.log("[WeiboXposed] " + text);
	}

	private XC_MethodHook removeAD = new XC_MethodHook() {
		@Override
		protected void afterHookedMethod(MethodHookParam param) throws Throwable {
			ArrayList<Object> origResult = (ArrayList<Object>) param.getResult();
			for (Iterator<Object> iterator = origResult.iterator(); iterator.hasNext(); ) {
				Object mblog = iterator.next();
				if (isPromotion(mblog)) {
					iterator.remove();
				}
			}
		}
	};

	private XC_MethodHook callbackCancel = XC_MethodReplacement.returnConstant(null);

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

	private void hookGreyScale(final XC_LoadPackage.LoadPackageParam lpparam) {
		Class<?> greyScaleClass = findClass("com.sina.weibo.utils.GreyScaleUtils", lpparam.classLoader);
		hookAllMethods(greyScaleClass, "isFeatureEnabled", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				String feature = (String) param.args[0];
				if (enabled_feature.contains(feature)) {
					param.setResult(true);
				}
				if (disabled_feature.contains(feature)) {
					param.setResult(false);
				}
			}
		});
	}

	private void hookBrowser() {
		final String EXTRAS_KEY = "com_sina_weibo_weibobrowser_url";
		findAndHookMethod(Activity.class, "startActivityForResult", Intent.class, int.class, Bundle.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				Intent intent = (Intent) param.args[0];
				if (intent.getBooleanExtra(MOD_PACKAGE_NAME, false)) {
					logd("[Browser] Loop detected, skipping");
					return;
				}

				Bundle extras = intent.getExtras();
				if (extras == null) return;

				Uri url;
				logd("extras" + extras.toString());
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

				if (url.getHost().endsWith("weibo.com") || url.getHost().endsWith("weibo.cn"))
					return;
				param.setResult(null);
				openUrl(url);
			}
		});
	}

	private void hookMoreItems(final XC_LoadPackage.LoadPackageParam lpparam) {
		findAndHookMethod("com.sina.weibo.MoreItemsActivity", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				final Activity thisAct = (Activity) param.thisObject;
				Resources res = thisAct.getResources();
				int scrId = res.getIdentifier("scrMore", "id", WB_PACKAGE_NAME);
				ScrollView scrMore = (ScrollView) thisAct.findViewById(scrId);

				TextView tv = new TextView(thisAct);
				tv.setText("WeiboXposed v" + BuildConfig.VERSION_NAME);
				tv.setTextColor(Color.BLACK);
				tv.setPadding(20, 10, 0, 0);
				((ViewGroup)scrMore.getChildAt(0)).addView(tv);
				tv.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						Intent intent = new Intent(Intent.ACTION_MAIN);
						intent.setComponent(new ComponentName(MOD_PACKAGE_NAME, SettingsActivity.class.getName()));
						thisAct.startActivity(intent);
					}
				});
			}
		});
	}

	private void hookWeibo(final XC_LoadPackage.LoadPackageParam lpparam) {
		prefs.reload();
		log("Weibo loaded");

		disabled_feature = new ArrayList<>();
		disabled_feature.add("gif_video_player");
		disabled_feature.add("ad_pull_refresh_enable");
		if (prefs.getBoolean("disable_new_message_flow", false)) {
			disabled_feature.add("wb_message_new_flow_android_enable");
		}

		remove_hot = prefs.getBoolean("remove_hot", false);
		hookAD(lpparam);

		hookGreyScale(lpparam);

		boolean forceBrowser = prefs.getBoolean("force_browser", false);
		if (forceBrowser) hookBrowser();

		hookMoreItems(lpparam);

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
