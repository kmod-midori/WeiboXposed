package moe.reimu.weiboxposed

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AndroidAppHelper
import android.content.ComponentName
import android.content.Intent
import android.content.res.XResources
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView

import java.util.ArrayList

import de.robv.android.xposed.IXposedHookInitPackageResources
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam
import de.robv.android.xposed.callbacks.XC_LoadPackage

import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getObjectField

class Module : IXposedHookInitPackageResources, IXposedHookLoadPackage, IXposedHookZygoteInit {
    companion object {
        private val MOD_PACKAGE_NAME = Module::class.java.`package`.name
        private val WB_PACKAGE_NAME = "com.sina.weibo"
    }

    lateinit private var prefs: XSharedPreferences
    private var remove_hot = false
    private var debug_mode = false
    private val enabled_feature = arrayListOf("Night_Mode")
    private val disabled_feature = arrayListOf<String>()
    private var content_keyword = listOf<String>()
    private var user_keyword = listOf<String>()


    override fun handleInitPackageResources(resparam: InitPackageResourcesParam) {
        if (resparam.packageName != Module.WB_PACKAGE_NAME)
            return

        // Disable Special BG touch event by setting width to 0
        resparam.res.setReplacement(WB_PACKAGE_NAME, "dimen", "feed_title_specialbg_width",
                XResources.DimensionReplacement(0f, TypedValue.COMPLEX_UNIT_PX))
    }


    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        prefs = XSharedPreferences(MOD_PACKAGE_NAME, GeneralPreferenceFragment.PREF_NAME)
        prefs.makeWorldReadable()

        log("initialized")
    }


    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            WB_PACKAGE_NAME -> hookWeibo(lpparam)
            MOD_PACKAGE_NAME -> hookSelf(lpparam)
        }
    }

    private fun hookSelf(lpparam: XC_LoadPackage.LoadPackageParam) {
        // "Enable" the module
        findAndHookMethod("$MOD_PACKAGE_NAME.GeneralPreferenceFragment", lpparam.classLoader,
                "isModuleEnabled", XC_MethodReplacement.returnConstant(true))
    }

    private fun isPromotion(mblog: Any): Boolean {
        try {
            val promotion = getObjectField(mblog, "promotion")

            val title = getObjectField(mblog, "title")
            var is_friend_hot = false

            if (promotion != null) {
                val ad_type = getObjectField(promotion, "adtype") as String
                logd("detected promotion: adtype")
                if (remove_hot) {
                    return true
                } else {
                    // Check for Hot
                    if ("8" != ad_type) {
                        return true
                    } else {
                        is_friend_hot = true
                    }
                }
            }

            if (title != null && !is_friend_hot) {
                val text = getObjectField(title, "text") as String
                if ("" != text) {
                    logd("detected promotion: non-empty title")
                    return true
                }
            }

        } catch (e: NoSuchFieldError) {
            log(e.message!!)
        }

        return false
    }

    private fun checkText(text: String, keywords: List<String>): Boolean {
        for (keyword in keywords) {
            if (text.contains(keyword)){
                logd("Keyword hit: $keyword inside $text")
                return true
            }
        }
        return false
    }

    private fun shouldRemove(mblog: Any): Boolean {
        if (isPromotion(mblog)) return true

        val text = getObjectField(mblog, "text") as? String
        if (text != null) {
            if (checkText(text, content_keyword)) return true
        }

        val user = getObjectField(mblog, "user")
        if (user != null) {
            val name = getObjectField(user, "screen_name") as String
            if (checkText(name, user_keyword)) return true
        }

        val retweeted = getObjectField(mblog, "retweeted_status")
        if (retweeted != null) {
            if (shouldRemove(retweeted)) return true
        }

        return false
    }

    private fun log(text: String) {
        XposedBridge.log("[WeiboXposed] " + text)
    }

    private fun logd(text: String) {
        if (debug_mode) XposedBridge.log("[WeiboXposed] " + text)
    }

    private val removeAD = object : XC_MethodHook() {
        override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
            val origResult = param.result as ArrayList<*>
            val iterator = origResult.iterator()
            while (iterator.hasNext()) {
                val mblog = iterator.next()
                if (shouldRemove(mblog)) {
                    val scheme = getObjectField(mblog, "scheme") as String
                    logd("Removed $scheme")
                    iterator.remove()
                }
            }
        }
    }

    private val callbackCancel = XC_MethodReplacement.returnConstant(null)

    private fun hookAD(lpparam: XC_LoadPackage.LoadPackageParam) {
        val LIST_BASE = "com.sina.weibo.models.MBlogListBaseObject"
        findAndHookMethod(LIST_BASE, lpparam.classLoader, "setTrends", List::class.java, callbackCancel)
        findAndHookMethod(LIST_BASE, lpparam.classLoader, "getTrends", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                param.result = arrayListOf<Any>()
            }
        })

        findAndHookMethod(LIST_BASE, lpparam.classLoader, "insetTrend", callbackCancel)
        findAndHookMethod(LIST_BASE, lpparam.classLoader, "getStatuses", removeAD)
        findAndHookMethod(LIST_BASE, lpparam.classLoader, "getStatusesCopy", removeAD)
    }

    private fun hookGreyScale(lpparam: XC_LoadPackage.LoadPackageParam) {
        val greyScaleClass = findClass("com.sina.weibo.utils.GreyScaleUtils", lpparam.classLoader)
        hookAllMethods(greyScaleClass, "isFeatureEnabled", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val feature = param.args[0] as String

                if (enabled_feature.contains(feature)) {
                    param.result = true
                }
                if (disabled_feature.contains(feature)) {
                    param.result = false
                }
            }
        })
    }

    private fun hookBrowser() {
        val EXTRAS_KEY = "com_sina_weibo_weibobrowser_url"
        findAndHookMethod(Activity::class.java, "startActivityForResult",
                Intent::class.java,
                Int::class.javaPrimitiveType,
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                        val intent = param.args[0] as Intent
                        if (intent.getBooleanExtra(MOD_PACKAGE_NAME, false)) {
                            logd("[Browser] Loop detected, skipping")
                            return
                        }

                        val extras = intent.extras ?: return

                        var url: Uri?
                        logd("extras" + extras.toString())
                        val urlStr = extras.getString(EXTRAS_KEY)

                        if (urlStr == null || "" == urlStr) {
                            url = intent.data ?: return

                            logd("origUrl is $url")

                            if (url.scheme == "sinaweibo") {
                                var tmpUrl: String? = url.getQueryParameter("url")
                                if (tmpUrl == null) {
                                    tmpUrl = url.getQueryParameter("showurl")
                                }
                                if (tmpUrl == null) return
                                url = Uri.parse(tmpUrl)
                            }

                            if (!url!!.scheme.startsWith("http")) return
                        } else {
                            url = Uri.parse(urlStr)
                        }

                        if (url!!.host.endsWith("weibo.com") || url.host.endsWith("weibo.cn"))
                            return
                        param.result = null
                        openUrl(url)
                    }
                })
    }

    private fun hookMoreItems(lpparam: XC_LoadPackage.LoadPackageParam) {
        findAndHookMethod("com.sina.weibo.MoreItemsActivity", lpparam.classLoader,
                "onCreate",
                Bundle::class.java,
                object : XC_MethodHook() {
                    @SuppressLint("SetTextI18n")
                    override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                        val thisAct = param.thisObject as Activity
                        val res = thisAct.resources
                        val scrId = res.getIdentifier("scrMore", "id", WB_PACKAGE_NAME)
                        val scrMore = thisAct.findViewById(scrId) as ScrollView

                        val tv = TextView(thisAct)
                        tv.text = "WeiboXposed v" + BuildConfig.VERSION_NAME
                        tv.setTextColor(Color.BLACK)
                        tv.setPadding(20, 10, 0, 0)
                        (scrMore.getChildAt(0) as ViewGroup).addView(tv)
                        tv.setOnClickListener {
                            val intent = Intent(Intent.ACTION_MAIN)
                            intent.component = ComponentName(MOD_PACKAGE_NAME, SettingsActivity::class.java.name)
                            thisAct.startActivity(intent)
                        }
                    }
                })
    }

    private fun dumpPrefs() {
        val keys = prefs.all

        for ((key, value) in keys) {
            logd("Pref: $key = ${value.toString()}")
        }
    }

    private fun hookWeibo(lpparam: XC_LoadPackage.LoadPackageParam) {
        reloadPrefs()
        hookAD(lpparam)
        hookGreyScale(lpparam)

        if (prefs.getBoolean("force_browser", false)){
            hookBrowser()
        }

        hookMoreItems(lpparam)

    }

    private fun reloadPrefs() {
        prefs.reload()
        log("loaded")
        debug_mode = prefs.getBoolean("debug_mode", false)
        if (debug_mode) {
            dumpPrefs()
        }

        disabled_feature.clear()
        disabled_feature.add("gif_video_player")
        disabled_feature.add("ad_pull_refresh_enable")
        if (prefs.getBoolean("disable_new_message_flow", false)) {
            disabled_feature.add("wb_message_new_flow_android_enable")
        }

        remove_hot = prefs.getBoolean("remove_hot", false)

        content_keyword = prefs.getString("content_keyword", "").split("\n")
        content_keyword = content_keyword.filter(String::isNotBlank)

        user_keyword = prefs.getString("user_keyword", "").split("\n")
        user_keyword = user_keyword.filter(String::isNotBlank)
    }

    private fun openUrl(url: Uri) {
        val intent = Intent()

        intent.action = Intent.ACTION_VIEW
        intent.data = url
        intent.putExtra(MOD_PACKAGE_NAME, true)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        AndroidAppHelper.currentApplication().startActivity(intent)
    }
}
