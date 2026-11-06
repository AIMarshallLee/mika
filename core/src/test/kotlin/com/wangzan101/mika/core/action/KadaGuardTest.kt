package com.wangzan101.mika.core.action

// 咔哒(昆仑增长)修改:新增测试。验证码硬护栏、非手机发起运行里的对外动作不做。

import com.wangzan101.mika.core.agent.AgentLoop
import com.wangzan101.mika.core.agent.FakeModel
import com.wangzan101.mika.core.assembly.toolDispatcher
import com.wangzan101.mika.core.gate.CaptchaGuard
import com.wangzan101.mika.core.gate.ConfirmationGate
import com.wangzan101.mika.core.model.ModelReply
import com.wangzan101.mika.core.model.Part
import com.wangzan101.mika.core.model.Role
import com.wangzan101.mika.core.model.StopReason
import com.wangzan101.mika.core.run.Attendance
import com.wangzan101.mika.core.run.RunId
import com.wangzan101.mika.core.run.RunInbox
import com.wangzan101.mika.core.run.RunOrigin
import com.wangzan101.mika.core.run.RunProvenance
import com.wangzan101.mika.core.run.RunSpec
import com.wangzan101.mika.core.uistate.ActionResult
import com.wangzan101.mika.core.uistate.ScreenPort
import com.wangzan101.mika.core.uistate.ScreenSnapshot
import com.wangzan101.mika.core.uistate.UiElement
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun el(ref: String, role: String, name: String, b: List<Int>, editable: Boolean = false) =
    UiElement(ref, role, name, 0, 0, b, null, !editable, editable, false)

/** 网站弹出的滑块验证:说明文字、滑块、关闭按钮。 */
private val captchaPage = listOf(
    el("@e1", "text", "请完成安全验证", listOf(0, 300, 999, 360)),
    el("@e2", "text", "向右拖动滑块填充拼图", listOf(0, 380, 999, 440)),
    el("@e3", "button", "滑块", listOf(0, 600, 120, 680)),
    el("@e4", "button", "关闭", listOf(900, 250, 990, 300)),
)

/** 聊天页:标题、输入框、发送。 */
private val chatPage = listOf(
    el("@e1", "text", "张三", listOf(0, 0, 999, 80)),
    el("@e2", "edit", "明天见", listOf(0, 900, 790, 999), editable = true),
    el("@e3", "button", "发送", listOf(800, 900, 990, 990)),
    el("@e4", "button", "表情", listOf(400, 300, 600, 360)),
)

private class Screen(var page: List<UiElement>) : ScreenPort {
    val taps = mutableListOf<String>()
    val typed = mutableListOf<String>()
    private var reads = 0
    private fun read(id: String) = ScreenSnapshot(id, "com.site", page, 1080, 2400)
    override suspend fun snapshot(): ScreenSnapshot = read("s${++reads}")
    override suspend fun peek(): ScreenSnapshot = read("p$reads")
    override suspend fun screenshotPng(): ByteArray? = null
    override suspend fun tapRef(ref: String, snapshotId: String): ActionResult { taps += ref; return ActionResult.Ok(changed = true) }
    override suspend fun tapXY(x: Int, y: Int): ActionResult { taps += "$x,$y"; return ActionResult.Ok(changed = true) }
    override suspend fun typeText(ref: String?, snapshotId: String?, text: String): ActionResult { typed += text; return ActionResult.Ok(changed = true) }
    override suspend fun clearText(ref: String?, snapshotId: String?) = ActionResult.Ok()
    override suspend fun scroll(ref: String?, snapshotId: String?, down: Boolean) = ActionResult.Ok()
    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) = ActionResult.Ok()
    override suspend fun back() = ActionResult.Ok()
    override suspend fun pressKey(keyCode: Int) = ActionResult.Ok()
    override suspend fun openApp(packageName: String, cold: Boolean) = ActionResult.Ok()
    override suspend fun waitStable(timeoutMs: Long) = true
    override suspend fun listApps() = listOf("com.site" to "Site")
}

private var seq = 0
private fun call(name: String, args: String = "{}") = Part.ToolCall("k${++seq}", name, args)
private fun tools(vararg c: Part.ToolCall) = ModelReply(c.toList(), StopReason.ToolUse, null)
private val OBSERVE get() = tools(call("phone_observe"))
private val FINISH get() = com.wangzan101.mika.core.testing.answerReply("好了")
private fun tap(ref: String) = tools(call("phone_tap", """{"ref":"$ref","snapshotId":"s1"}"""))
private fun type(ref: String, text: String) = tools(call("phone_type_text", """{"text":"$text","ref":"$ref","snapshotId":"s1"}"""))

private fun FakeModel.result(i: Int): JsonObject =
    Json.parseToJsonElement(requests[i].last { it.role == Role.Tool }.parts.filterIsInstance<Part.ToolResult>().first().content).jsonObject
private fun JsonObject.s(k: String) = this[k]!!.jsonPrimitive.content

class KadaGuardTest {
    @After fun reset() { ApprovalPolicy.outwardNeedsUser = false }

    private fun loop(m: FakeModel, s: Screen, autonomous: Boolean = false) =
        AgentLoop(m, toolDispatcher(s), "BASE", confirmations = if (autonomous) null else ConfirmationGate(),
            approvals = ApprovalSettings { autonomous })

    @Test fun captchaWordsAreRecognisedButPlainMentionsAreNot() {
        assertEquals("拖动滑块", CaptchaGuard.captchaText(ScreenSnapshot("a", "x", captchaPage, 1, 1)))
        val article = listOf(el("@e1", "text", "登录时请输入短信验证码", listOf(0, 0, 9, 9)))
        assertNull(CaptchaGuard.captchaText(ScreenSnapshot("b", "x", article, 1, 1)))
        assertTrue(CaptchaGuard.leaves(captchaPage[3]))
        assertTrue(!CaptchaGuard.leaves(captchaPage[2]))
    }

    @Test fun tappingTheSliderIsRefusedButClosingIsAllowed() = runTest {
        val s = Screen(captchaPage)
        val m = FakeModel(OBSERVE, tap("@e3"), tap("@e4"), FINISH)
        loop(m, s).run("t")
        assertEquals(listOf("@e4"), s.taps)
        val r = m.result(2)
        assertEquals(Refusals.CAPTCHA, r.s("error"))
        assertEquals("false", r.s("executed"))
        assertTrue(r.s("message"), "not allowed" in r.s("message"))
    }

    @Test fun autonomousRunsAreRefusedToo() = runTest {
        val s = Screen(captchaPage)
        val m = FakeModel(OBSERVE, tap("@e3"), FINISH)
        loop(m, s, autonomous = true).run("t")
        assertTrue(s.taps.isEmpty())
        assertEquals(Refusals.CAPTCHA, m.result(2).s("error"))
    }

    @Test fun normalPagesAreUntouched() = runTest {
        val s = Screen(chatPage)
        val m = FakeModel(OBSERVE, tap("@e4"), type("@e2", "hi"), FINISH)
        loop(m, s).run("t")
        assertEquals(listOf("@e4"), s.taps)
        assertEquals(listOf("hi"), s.typed)
    }

    @Test fun policyDeniesOutwardActionsOnlyForRemoteRunsWhenSwitchedOn() {
        val remote = RunProvenance(RunOrigin.Mcp, Attendance.Present)
        val unattended = RunProvenance(RunOrigin.Ui, Attendance.Unattended)
        val phone = RunProvenance(RunOrigin.Ui, Attendance.Present)
        // 默认(原版):不花钱的都直接做
        assertEquals(Approval.Allow, ApprovalPolicy.decide(Risk(ActionRisk.External), remote))
        ApprovalPolicy.outwardNeedsUser = true
        for (level in listOf(ActionRisk.External, ActionRisk.Irreversible)) {
            assertEquals("$level", Approval.Deny, ApprovalPolicy.decide(Risk(level), remote))
            assertEquals("$level", Approval.Deny, ApprovalPolicy.decide(Risk(level), unattended))
            assertEquals("$level", Approval.Allow, ApprovalPolicy.decide(Risk(level), phone))
        }
        for (level in listOf(ActionRisk.Read, ActionRisk.Change, ActionRisk.Unknown)) {
            assertEquals("$level", Approval.Allow, ApprovalPolicy.decide(Risk(level), remote))
        }
        assertEquals(Approval.Ask, ApprovalPolicy.decide(Risk(ActionRisk.Irreversible, payment = true), phone))
    }

    @Test fun computerDispatchedTaskCannotSendAMessage() = runTest {
        ApprovalPolicy.outwardNeedsUser = true
        val s = Screen(chatPage)
        val m = FakeModel(OBSERVE, tap("@e3"), FINISH)
        val spec = RunSpec(RunId("r"), "t", "th", provenance = RunProvenance(RunOrigin.Mcp, Attendance.Present))
        loop(m, s).run(spec, RunInbox())
        assertTrue(s.taps.isEmpty())
        val r = m.result(2)
        assertEquals("approval_unavailable", r.s("error"))
        assertTrue(r.s("message"), "computer" in r.s("message"))
    }

    @Test fun autonomousScheduledTaskCannotSendEither() = runTest {
        ApprovalPolicy.outwardNeedsUser = true
        val s = Screen(chatPage)
        val m = FakeModel(OBSERVE, tap("@e3"), FINISH)
        val spec = RunSpec(RunId("r"), "t", "th", provenance = RunProvenance(RunOrigin.Trigger, Attendance.Unattended))
        loop(m, s, autonomous = true).run(spec, RunInbox())
        assertTrue(s.taps.isEmpty())
        assertEquals("approval_unavailable", m.result(2).s("error"))
    }

    @Test fun phoneStartedTaskStillSendsAsBefore() = runTest {
        ApprovalPolicy.outwardNeedsUser = true
        val s = Screen(chatPage)
        val m = FakeModel(OBSERVE, tap("@e3"), FINISH)
        loop(m, s).run("t")
        assertEquals(listOf("@e3"), s.taps)
    }
}
