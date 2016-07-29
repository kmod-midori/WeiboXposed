package moe.reimu.weiboxposed;

import android.content.res.XResources;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RelativeLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

public class Module implements IXposedHookInitPackageResources, IXposedHookLoadPackage {

	@Override
	public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable {
		if (!resparam.packageName.equals("com.sina.weibo"))
			return;

		// Disable Special BG touch event by setting width to 0
		resparam.res.setReplacement("com.sina.weibo", "dimen", "feed_title_specialbg_width",
				new XResources.DimensionReplacement(0, TypedValue.COMPLEX_UNIT_PX));
	}

	public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
		if (!lpparam.packageName.equals("com.sina.weibo"))
			return;

		XC_MethodHook callbackCancel = new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				param.setResult(null);
			}
		};

		/**
		 * Remove AD
		 */
		final String LIST_BASE = "com.sina.weibo.models.MBlogListBaseObject";
		findAndHookMethod(LIST_BASE, lpparam.classLoader, "setTrends", List.class, callbackCancel);
		findAndHookMethod(LIST_BASE, lpparam.classLoader, "getTrends", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				param.setResult(new ArrayList<>());
			}
		});
		findAndHookMethod(LIST_BASE, lpparam.classLoader, "insetTrend", callbackCancel);

		XC_MethodHook removeAD = new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				ArrayList<Object> origResult = (ArrayList<Object>)param.getResult();
				ArrayList<Object> result = new ArrayList<>();

				for (Object mblog :
						origResult) {
					Object promotion = getObjectField(mblog, "promotion");
					if (promotion != null) {
						String adType = (String)getObjectField(promotion, "adtype");

						if (adType == null) continue;
						// Exclude "热门"
						if (!"8".equals(adType)) continue;
					}
					result.add(mblog);
				}

				param.setResult(result);
			}
		};

		findAndHookMethod(LIST_BASE, lpparam.classLoader, "getStatuses", removeAD);
		findAndHookMethod(LIST_BASE, lpparam.classLoader, "getStatusesCopy", removeAD);



		/**
		 * Force enable night mode
		 */
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
}
