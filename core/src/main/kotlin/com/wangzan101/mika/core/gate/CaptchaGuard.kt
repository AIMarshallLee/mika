package com.wangzan101.mika.core.gate

import com.wangzan101.mika.core.tool.Verb
import com.wangzan101.mika.core.uistate.Hant
import com.wangzan101.mika.core.uistate.ScreenSnapshot
import com.wangzan101.mika.core.uistate.UiElement

/**
 * 咔哒(昆仑增长)修改:新增文件。验证码硬护栏。
 *
 * 屏上(App 或网页)正显示人机验证(滑块、拼图、点选)时,不让助手在这一屏上点、拖、输入或跑页面脚本去「解」它;
 * 只放行离开它的动作:返回、关闭/取消/跳过这类按钮、打开别的网址或 App。原来只在提示词里说「不要自己解验证码」,
 * 真机上助手仍去拖过滑块,所以在执行入口按屏上字样硬拦(见 [com.wangzan101.mika.core.action.ConfirmDecision])。
 */
object CaptchaGuard {
    /** 验证正挂在屏上时才有的说法(比对时繁转简、不分大小写)。只说到「验证码」(登录短信码、文章里提到)的不算。 */
    val WORDS = listOf(
        "拖动滑块", "滑块验证", "向右滑动完成", "向右拖动", "滑动完成验证", "拖动下方滑块", "完成拼图", "拼图验证",
        "请完成验证", "请完成安全验证", "请完成下列验证", "人机验证", "请依次点击", "按顺序点击", "点击完成验证",
        "verify you are human", "i'm not a robot", "not a robot", "slide to verify", "drag the slider", "complete the puzzle",
    )

    /** 在验证屏上仍放行的点击:落在这类关闭、返回按钮上(按钮名等于或以它开头,且很短)。 */
    private val LEAVE_WORDS = listOf("关闭", "返回", "取消", "跳过", "换一个", "×", "✕", "x", "close", "back", "cancel", "dismiss")
    private const val LEAVE_MAX_LEN = 6

    /** 会在这一屏上「做验证」的动作。返回、打开网址、打开 App、滚动、读屏不在内。 */
    val BLOCKED_VERBS: Set<Verb> = setOf(
        Verb.Tap, Verb.TypeText, Verb.ClearText, Verb.Swipe, Verb.PressKey, Verb.PhoneGesture,
        Verb.Interact, Verb.Script, Verb.EvaluateJs, Verb.SiteCommand,
    )

    /** 屏上第一个验证字样;没有为 null。 */
    fun captchaText(screen: ScreenSnapshot?): String? {
        val s = screen ?: return null
        val text = Hant.fold(s.elements.joinToString("\n") { it.name }).lowercase()
        return WORDS.firstOrNull { it in text }
    }

    /** 点的是关闭、返回一类按钮(离开验证,不是在做验证)。 */
    fun leaves(target: UiElement?): Boolean {
        val n = target?.name?.trim()?.lowercase() ?: return false
        if (n.isEmpty() || n.length > LEAVE_MAX_LEN) return false
        return LEAVE_WORDS.any { n == it || n.startsWith(it) }
    }
}
