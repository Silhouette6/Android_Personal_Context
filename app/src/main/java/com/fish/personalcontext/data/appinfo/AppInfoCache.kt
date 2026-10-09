package com.fish.personalcontext.data.appinfo

import android.content.Intent
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

/** PackageManager label 解析缓存，避免每条事件都查询系统 */
class AppInfoCache(private val pm: PackageManager) {

    private val labels = ConcurrentHashMap<String, String>()

    @Volatile
    private var homePackages: Set<String>? = null

    /** 测试注入桌面包集合（Robolectric 下 PackageManager 查不到真实 launcher） */
    internal fun setHomePackagesForTest(packages: Set<String>) {
        homePackages = packages
    }

    fun appName(packageName: String): String = labels.getOrPut(packageName) {
        try {
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    /**
     * 桌面/系统 UI 的 RESUMED 对时间轴是噪音（每次回桌面都会触发），仅在展示层过滤；
     * 原始事件照常入库。注意只过滤「当前默认桌面」——部分设备的 Settings 等应用
     * 也会声明 HOME category，全量过滤会误杀真实使用记录（模拟器上实测踩过）。
     */
    fun isDisplayNoise(packageName: String?): Boolean {
        if (packageName == null) return false
        if (packageName == "com.android.systemui") return true
        val home = homePackages ?: run {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            val set = if (resolved != null) setOf(resolved) else emptySet()
            homePackages = set
            set
        }
        return packageName in home
    }
}
