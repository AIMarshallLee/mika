package com.wangzan101.mika.core.action

import com.wangzan101.mika.core.prompt.Feedback
import com.wangzan101.mika.core.guard.GuardVerdict
import com.wangzan101.mika.core.risk.IrrevAction
import com.wangzan101.mika.core.gate.Irreversible
import com.wangzan101.mika.core.uistate.ScreenSnapshot
import com.wangzan101.mika.core.tool.ToolExecution
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 一次动作走完执行入口的结局。 */
sealed interface ActionOutcome {
    /** 执行前的守卫结论。 */
    val before: List<GuardVerdict>

    /**
     * 没有执行。[code]:guard_stopped / guard_warning / confirmation_required / confirmation_mismatch / confirmation_declined /
     * approval_unavailable / target_unresolved / own_app。
     * [content] 是交给这次调用方的结果(脚本子步、模型单步照它回;批和回放按 [code] 自己说明)。
     */
    data class Refused(
        val code: String, val content: JsonObject, override val before: List<GuardVerdict>, val hit: Irreversible? = null,
    ) : ActionOutcome

    /**
     * 执行了。[exec] 是最终结果(模型单步可能已换成等用户之后的结果或核查退回);[after] 是执行后的守卫结论
     * (页面脚本的子步结论排在前面);[content] 是交给模型、也记进运行记录的内容。
     * [shotId]、[picture]:[Frames.Caller] 时留给调用方存的画面;[seen] 是记路线锚点时动作后读的一屏(没读为 null)。
     */
    data class Ran(
        val exec: ToolExecution, override val before: List<GuardVerdict>, val after: List<GuardVerdict>, val content: JsonObject,
        val durationMs: Long, val shotId: Int? = null, val picture: ByteArray? = null, val seen: ScreenSnapshot? = null,
    ) : ActionOutcome {
        val verdicts: List<GuardVerdict> get() = before + after
    }

    /** 等用户时任务被取消了。 */
    data class Cancelled(override val before: List<GuardVerdict>) : ActionOutcome
}

/** 这次调用对调用方的结果:执行了就是执行结果,没执行就是拒绝说明。 */
fun ActionOutcome.execution(): ToolExecution = when (this) {
    is ActionOutcome.Ran -> exec
    is ActionOutcome.Refused -> ToolExecution(content)
    is ActionOutcome.Cancelled -> ToolExecution(buildJsonObject { put("ok", false); put("error", "cancelled"); put("executed", false) })
}

/** 被拦下时交给调用方的说明,按来源的说法写。 */
internal object Refusals {
    const val SINGLE_USE = "One approval lets through only the next matching action, and that action uses it up whether it succeeds or not."
    const val OWN_APP = "own_app"
    /** 咔哒(昆仑增长)修改:屏上挂着人机验证,这一步在做验证,不执行(见 CaptchaGuard)。 */
    const val CAPTCHA = "captcha_refused"
    /** 咔哒(昆仑增长)修改:不花钱的对外动作在非手机发起、无人值守的运行里不做时的说明。 */
    private val OUTWARD_NOT_MONEY = setOf(IrrevAction.send, IrrevAction.submit, IrrevAction.delete)
    private const val OUTWARD_UNATTENDED = "This step sends, posts, submits or deletes something, and in a task started from a computer, " +
        "a schedule or a channel (or while nobody is at the phone) such actions are not allowed. The action was not run. " +
        "Do not retry it or look for another way to do it; finish the read-only part and tell the user exactly what still needs their own hands."
    /** 回车、空格在认不出是搜索框的输入框里按发送算:真是搜索框时点页面上的搜索按钮(查找不是对外动作)。 */
    private const val ENTER_HINT = " If this field is only a search box, do not press Enter: tap the page's own search button (搜索, 查询, Search) instead; " +
        "searching is allowed, Enter in a text field is treated as sending."

    private fun outwardUnattended(hit: Irreversible) =
        if (hit.matched.startsWith("回车:") || hit.matched.startsWith("空格:")) OUTWARD_UNATTENDED + ENTER_HINT else OUTWARD_UNATTENDED
    const val PAYMENT_TAKEOVER = "The user completes payment with their own hands; do not use request_confirmation: after opening the payment page, call hand_to_user(reason=payment), " +
        "with item as the product (variant, quantity), amount as the amount due shown on screen, and address as the delivery address."

    fun content(code: String, style: RefusalStyle, before: List<GuardVerdict>, hit: Irreversible?, reason: String?): JsonObject =
        if (code == CAPTCHA) captcha(hit?.matched) else when (style) {
        // 模型单步被守卫叫停时任务就此结束,用不到说明
        RefusalStyle.Model -> if (code == "approval_unavailable") notRun(code,
            // 咔哒(昆仑增长)修改:不花钱的对外动作(发送、提交、删除)被拦下时照实说原因
            if (hit != null && hit.action in OUTWARD_NOT_MONEY) outwardUnattended(hit) else
            "This step spends money, but this run cannot collect the user's approval. The action was not run. " +
                "Do not retry this action in this run; explain the limitation to the user.") {
            hit?.let { put("action", it.action.name); put("matched", it.matched) }
            put("counts_as_failure", false)
        } else if (hit == null) notRun(code, if (code == OWN_APP) Feedback["own_app"] else code) else {
            val blocked = if (reason != null) mismatch(hit, reason) else required(hit)
            if (before.isEmpty()) blocked else JsonObject(blocked + ("guard" to JsonArray(before.map { it.toJson() })))
        }
        RefusalStyle.Script -> {
            val message = when (code) {
                "guard_stopped" -> "Mika's operation guard stopped the task."
                "guard_warning" -> "Mika's operation guard flagged this action; it was not run."
                "confirmation_required" -> "this step spends money (${hit?.matched})"
                "confirmation_mismatch" -> "this payment step (${hit?.matched}) does not match what the user approved ($reason)"
                "approval_unavailable" -> if (hit != null && hit.action in OUTWARD_NOT_MONEY) outwardUnattended(hit)
                    else "this step spends money and this run cannot get the user's approval; the action was not run"
                "target_unresolved" -> "that ref is not on the latest snapshot; call mika.snapshot() again"
                OWN_APP -> Feedback["own_app"]
                else -> error("unknown refusal $code")
            }
            notRun(code, message) { hit?.let { put("action", it.action.name); put("matched", it.matched) } }
        }
        RefusalStyle.Code -> notRun(code, code)
    }

    /** 咔哒(昆仑增长)修改:验证码硬护栏的说明;各来源同一句。 */
    private fun captcha(matched: String?) = buildJsonObject {
        put("ok", false); put("error", CAPTCHA); put("executed", false)
        matched?.let { put("matched", it) }
        put("message", "This screen shows a human-verification check" + (matched?.let { " (\"$it\")" } ?: "") +
            ", so this action was not run: solving or getting around verification is not allowed. Leave it (go back, close it, " +
            "or open a different site or source) and continue the task without it. If the task cannot go on without this page, " +
            "call hand_to_user with reason captcha, or end_not_done and say the site requires verification.")
        put("recoverable", true); put("counts_as_failure", false)
    }

    /** 要用户同意的一步(不在屏上、工具声明要问的),这次运行却问不到人。 */
    fun unavailable(): JsonObject = buildJsonObject {
        put("ok", false); put("error", "approval_unavailable"); put("message", Feedback["approval.unavailable"])
        put("executed", false); put("recoverable", false); put("counts_as_failure", false)
    }

    private fun required(hit: Irreversible) = buildJsonObject {
        put("ok", false); put("error", "confirmation_required")
        put("action", hit.action.name); put("matched", hit.matched)
        put("message", if (hit.action == IrrevAction.pay) "This step is a payment, so it was not run. $PAYMENT_TAKEOVER"
            else "This step spends money (${hit.action.name}), so it was not run. First call request_confirmation saying where, what is bought and how much it costs, and do it after the user approves. $SINGLE_USE")
        put("recoverable", true); put("counts_as_failure", false)
    }

    private fun mismatch(hit: Irreversible, reason: String) = buildJsonObject {
        put("ok", false); put("error", "confirmation_mismatch")
        put("action", hit.action.name); put("matched", hit.matched); put("reason", reason)
        put("message", "This step does not match the action the user approved ($reason). That approval is void, and this step was not run. " +
            "If it still needs doing, call request_confirmation again describing this step truthfully, and do it right after the user approves.")
        put("recoverable", true); put("counts_as_failure", false)
    }

    private fun notRun(error: String, message: String, extra: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
        put("ok", false); put("error", error); put("message", message); put("executed", false)
        extra()
    }
}
