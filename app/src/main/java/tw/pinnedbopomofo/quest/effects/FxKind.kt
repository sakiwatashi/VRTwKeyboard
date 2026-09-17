package tw.pinnedbopomofo.quest.effects

/**
 * 可以單獨開關的特效。
 *
 * [heavy] 標記的是會讓整塊面板每幀重算的，開了就該量一次；
 * 其他的要嘛跑在 RenderThread（漣漪、輝光），要嘛只動一層覆蓋層（粒子、飛字）。
 */
enum class FxKind(val label: String, val heavy: Boolean = false) {
    BLOOM("輝光"),
    PARTICLES("粒子"),
    FLY("飛字"),
    RIPPLE("漣漪"),
    TILT("傾斜"),
    SHEEN("流光"),
    VOICE("聲波"),
    MIST("煙霧"),
    SHOCK("衝擊波", heavy = true),
    ;

    companion object {
        /** 建議的預設組合：成本低、把握高，開著不會影響打字。 */
        val recommended = setOf(BLOOM, PARTICLES, FLY, RIPPLE, TILT, VOICE, MIST)
    }
}
