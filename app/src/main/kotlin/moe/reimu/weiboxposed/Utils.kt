package moe.reimu.weiboxposed

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method

fun XC_LoadPackage.LoadPackageParam.find(name: String) : Class<*>
        = XposedHelpers.findClass(name, this.classLoader)

fun Class<*>.method(name: String, vararg types: Class<*>): Method {
    return XposedHelpers.findMethodBestMatch(this, name, *types)
}

fun Class<*>.hookAll(name: String, body: HookBuilder.() -> Unit): Set<XC_MethodHook.Unhook> {
    return XposedBridge.hookAllMethods(this, name, HookBuilder(body).build())
}

fun Method.hook(body: HookBuilder.() -> Unit) : XC_MethodHook.Unhook {
    return XposedBridge.hookMethod(this, HookBuilder(body).build())
}

fun Method.hook(body: XC_MethodHook) : XC_MethodHook.Unhook {
    return XposedBridge.hookMethod(this, body)
}

class HookBuilder(body: HookBuilder.() -> Unit) {
    private var _before: ((XC_MethodHook.MethodHookParam) -> Unit)? = null
    private var _after:  ((XC_MethodHook.MethodHookParam) -> Unit)? = null

    init {
        this.body()
    }

    fun before(body: (XC_MethodHook.MethodHookParam) -> Unit) {
        _before = body
    }

    fun after(body: (XC_MethodHook.MethodHookParam) -> Unit) {
        _after = body
    }

    fun build(): XC_MethodHook {
        return object : XC_MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                _before?.invoke(param)
            }
            override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                _after?.invoke(param)
            }
        }
    }
}

