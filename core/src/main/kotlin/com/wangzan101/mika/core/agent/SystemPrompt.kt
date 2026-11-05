package com.wangzan101.mika.core.agent

import com.wangzan101.mika.core.capability.PromptEnv
import com.wangzan101.mika.core.capability.PromptSections
import com.wangzan101.mika.core.prompt.Prompts

/**
 * 系统提示词:P0 身份(prompts/system/identity.md)、P1 通用准则(system/principles.md,不点名工具),
 * 接通用工具的总规则(capabilities/general.md,通用工具一直都在),再接核心自己与各能力给的段落。
 * 段落只在那组工具出现在本任务发给模型的工具列表里时才有:说明和工具出自同一个判断。
 * 任务开始时拼一次,任务内一字不变。
 */
object SystemPrompt {
    /** 咔哒(昆仑增长)修改:App 对外的名字;原版是 Mika,换了名字时身份段说明自称什么。 */
    @Volatile var brandName: String = "Mika"

    fun identity(): String = listOf(branded(Prompts.system("identity")), Prompts.system("principles"), read(GENERAL)).joinToString("\n\n")

    /** 原版名字不改动一个字;换名时把开头的「You are Mika」换掉,并说明其余提示里的 Mika 指的就是这个 App。 */
    internal fun branded(identity: String, name: String = brandName): String =
        if (name == "Mika") identity
        else identity.replaceFirst("You are Mika,", "You are $name,") +
            "\n\nThis app is called $name (it is built on the open-source Mika agent). Tool results and notes may still say \"Mika\"; " +
            "that is this same app. When you talk to the user about yourself or this app, always call it $name, never Mika."

    fun assemble(identity: String, sections: List<String>): String = (listOf(identity) + sections).joinToString("\n\n")

    private const val GENERAL = "general"

    /** 一段能力说明。 */
    internal fun read(name: String): String = Prompts.capability(name)
}

/**
 * 核心自己的段落,排在各能力之前:用到才展开的几组从哪个入口进(groups,只列本任务给了入口的;展开后照旧留着,系统提示词少变);
 * 有两块以上的屏时怎么选、怎么在两块屏之间配合(channels);有屏展开后交给用户、下单发送前的核对(handover);
 * 动作结果自带屏幕时怎么读它(inline-screen);一次多步(batch)。
 */
internal object CoreSections : PromptSections {
    override fun sections(env: PromptEnv): List<String> = listOfNotNull(
        env.gate(SystemPrompt.read("groups")).takeIf { it.lines().size > 1 },
        SystemPrompt.read("channels").takeIf { env.screens >= 2 },
        SystemPrompt.read("handover").takeIf { env.openScreens >= 1 },
        SystemPrompt.read("inline-screen").takeIf { env.inlineScreen && env.openScreens >= 1 },
        SystemPrompt.read("batch").takeIf { DO_STEPS_TOOL in env.offered },
    ).map(env::gate)
}
