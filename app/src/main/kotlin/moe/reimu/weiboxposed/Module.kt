package moe.reimu.weiboxposed

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AndroidAppHelper
import android.app.Application
import android.content.*
import android.content.res.XResources
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import de.robv.android.xposed.*

import java.util.ArrayList

import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam
import de.robv.android.xposed.callbacks.XC_LoadPackage

import de.robv.android.xposed.XposedHelpers.*
import de.robv.android.xposed.callbacks.XC_LayoutInflated

class Module : IXposedHookInitPackageResources, IXposedHookLoadPackage {
    companion object {
        private val MOD_PACKAGE_NAME = Module::class.java.`package`.name
    }

    private var remove_hot = false
    private var debug_mode = false
    private var force_browser = false
    private var no_story = false
    private val enabled_feature = arrayListOf("Night_Mode")
    private val disabled_feature = arrayListOf<String>()
    private var content_keyword = listOf<String>()
    private var user_keyword = listOf<String>()
    private var comment_filters = listOf<Int>()
    private var receiver: BroadcastReceiver? = null


    override fun handleInitPackageResources(resparam: InitPackageResourcesParam) {
        if (resparam.packageName != WB_PACKAGE_NAME)
            return
        initPref()

        // Disable Special BG touch event by setting width to 0
        resparam.res.setReplacement(WB_PACKAGE_NAME, "dimen", "feed_title_specialbg_width",
                XResources.DimensionReplacement(0f, TypedValue.COMPLEX_UNIT_PX))
        resparam.res.hookLayout(WB_PACKAGE_NAME, "layout", "story_feed_horiz_photo_list", object : XC_LayoutInflated() {
            override fun handleLayoutInflated(liparam: LayoutInflatedParam) {
                if (no_story) liparam.view.visibility = View.GONE
            }
        })
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

            if (promotion != null) {
                val ad_type = getObjectField(promotion, "adtype") as String?
                logd("detected promotion: adtype")
                if (remove_hot) {
                    return true
                } else {
                    // Check for Hot
                    return "8" != ad_type
                }
            }

        } catch (e: NoSuchFieldError) {
            log(e.message!!)
        }

        return false
    }

    private fun checkText(text: String, keywords: List<String>): Boolean {
        for (keyword in keywords) {
            if (text.contains(keyword)) {
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

    private val removeAD = HookBuilder {
        after {
            val origResult = it.result as ArrayList<*>
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
    }.build()

    private val callbackCancel = XC_MethodReplacement.DO_NOTHING
    private val callbackEmptyList = HookBuilder {
        before {
            it.result = arrayListOf<Any>()
        }
    }.build()

    private fun hookAD(lpparam: XC_LoadPackage.LoadPackageParam) {
        val LIST_BASE = "$WB_PACKAGE_NAME.models.MBlogListBaseObject"
        findAndHookMethod(LIST_BASE, lpparam.classLoader, "setTrends", List::class.java, callbackCancel)
        findAndHookMethod(LIST_BASE, lpparam.classLoader, "getTrends", callbackEmptyList)
        findAndHookMethod(LIST_BASE, lpparam.classLoader, "insetTrend", callbackCancel)

        findAndHookMethod(LIST_BASE, lpparam.classLoader, "getStatuses", removeAD)
        findAndHookMethod(LIST_BASE, lpparam.classLoader, "getStatusesCopy", removeAD)
    }

    private fun hookGreyScale(lpparam: XC_LoadPackage.LoadPackageParam) {
        lpparam.find("$WB_PACKAGE_NAME.utils.GreyScaleUtils")
                .method("isFeatureEnabled", String::class.java)
                .hook {
                    before {
                        val feature = it.args[0] as String

                        if (enabled_feature.contains(feature)) {
                            it.result = true
                        }
                        if (disabled_feature.contains(feature)) {
                            it.result = false
                        }
                    }
                }
    }

    private fun hookBrowser() {
        val EXTRAS_KEY = "com_sina_weibo_weibobrowser_url"
        Activity::class.java
                .method("startActivityForResult", Intent::class.java,
                        Int::class.javaPrimitiveType!!,
                        Bundle::class.java)
                .hook(object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                        if (!force_browser) return
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
        logd("Hooked external browser.")
    }

    @SuppressLint("SetTextI18n")
    private fun hookMoreItems(lpparam: XC_LoadPackage.LoadPackageParam) {
        lpparam.find("$WB_PACKAGE_NAME.MoreItemsActivity")
                .method("onCreate", Bundle::class.java)
                .hook {
                    after {
                        val thisAct = it.thisObject as Activity
                        val res = thisAct.resources
                        val scrId = res.getIdentifier("scrMore", "id", WB_PACKAGE_NAME)
                        val scrMore: ScrollView = thisAct.findViewById(scrId)

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
                }
    }

    fun hookComment(lpparam: XC_LoadPackage.LoadPackageParam) {
        lpparam.find("$WB_PACKAGE_NAME.models.JsonCommentMessageList")
                .method("getCommentMessageList")
                .hook {
                    after {
                        val origResult = it.result as ArrayList<*>
                        val iterator = origResult.iterator()
                        while (iterator.hasNext()) {
                            val comment = iterator.next()
                            try {
                                val comment_type = getObjectField(comment, "comment_type_new") as Int
                                if (comment_filters.contains(comment_type)) {
                                    logd("Comment type = $comment_type, removed.")
                                    iterator.remove()
                                }
                            } catch (e: NoSuchFieldError) {
                            }
                        }
                    }
                }
    }

    private fun hookWeibo(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!reloadPrefs()) {
            log("Module disabled")
            return
        }
        hookAD(lpparam)
        hookGreyScale(lpparam)

        hookBrowser()
        hookMoreItems(lpparam)

        hookComment(lpparam)
        hookReload(lpparam)
    }

    private fun hookReload(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cls = lpparam.find("$WB_PACKAGE_NAME.MainTabActivity")
        cls.method("onCreate", Bundle::class.java).hook {
            after {
                val activity = it.thisObject as Activity
                receiver = object: BroadcastReceiver() {
                    override fun onReceive(p0: Context?, p1: Intent?) {
                        log("Got reload command")
                        reloadPrefs()
                    }
                }
                activity.registerReceiver(receiver, IntentFilter(BROADCAST))
            }
        }

        cls.method("onDestroy").hook {
            after {
                val activity = it.thisObject as Activity
                receiver ?: activity.unregisterReceiver(receiver)
            }
        }
    }

    object Settings : Preferences() {
        val global_enabled by booleanPref(null, true)
        val force_browser by booleanPref()
        val no_story by booleanPref()
        val debug_mode by booleanPref()
        val disable_new_message_flow by booleanPref()
        val remove_hot by booleanPref()
        val content_keyword by stringPref(null, "")
        val user_keyword by stringPref(null, "")
        // 2 -> WBCommmonCommentTypeLikedByMe
        // 3 -> WBCommmonCommentTypeForwardedByMe
        // 4 -> WBCommmonCommentTypeCommmentedByBlogger
        // 5 -> WBCommmonCommentTypeReplyByBlogger
        // 6 -> WBCommmonCommentTypeLikedByBlogger
        val comment_filters by stringSetPref(null, setOf("2", "3", "4", "5", "6"))
    }

    private fun initPref() {
        if (!Preferences.checkInit()) {
            val activityThread = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null), "currentActivityThread")
            val context = XposedHelpers.callMethod(activityThread, "getSystemContext") as Context
            Preferences.init(context)
        }
    }

    private fun reloadPrefs(): Boolean {
        initPref()
        debug_mode = Settings.debug_mode
        force_browser = Settings.force_browser
        no_story = Settings.no_story

        if (!Settings.global_enabled) {
            return false
        }

        disabled_feature.clear()
        disabled_feature.add("gif_video_player")
        disabled_feature.add("ad_pull_refresh_enable")
        if (Settings.disable_new_message_flow) {
            disabled_feature.add("wb_message_new_flow_android_enable")
        }

        remove_hot = Settings.remove_hot
        content_keyword = Settings.content_keyword!!.split("\n").filter(String::isNotBlank)
        user_keyword = Settings.user_keyword!!.split("\n").filter(String::isNotBlank)

        comment_filters = Settings.comment_filters.map(String::toInt)

        log("loaded")
        return true
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
