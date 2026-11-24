package code;

import arc.graphics.g2d.Font;
import mindustry.ui.Styles;

/**
 * 全部 UI 尺寸的唯一定义处。
 *
 * 单位一律是「逻辑像素」，取值时已经过 {@link BarTheme#u} 的 Scl 换算，调用方拿到的就是屏幕像素，
 * 所以组件里不该再出现任何布局数字——要调某个窗口/按钮/内边距，只改这个文件。
 *
 * 每项都是「桌面值 / 手机值」成对写在同一个 getter 里：手机的逻辑屏只有 500~640 高，
 * 桌面那套（300 高的面板、540 高的选择窗）会直接顶满屏幕，压住准星和建造区。
 * 文字本身不跟着缩（盒子小了会裁字），所以缩的是盒子、间距与外层留白。
 *
 * 通用小间距统一走 sp*() 刻度：桌面值与改造前的写法一一对应（sp8 = 8px），
 * 手机整体收紧一档（sp8 = 6px），这样「手机上下留空太多」只调刻度就能一起变小。
 */
public final class BarMetrics{

    private BarMetrics(){
    }

    // ================================================================ 基础换算

    /** 逻辑像素 → 屏幕像素。 */
    public static float u(float v){
        return BarTheme.u(v);
    }

    /** 双设备取值：桌面用 desktop、手机用 mobile，单位逻辑像素。 */
    public static float d(float desktop, float mobile){
        return BarTheme.d(desktop, mobile);
    }

    private static float lineHeight = -1f;

    /** 一行文字真正占的高度（屏幕像素；字体在加载时已被 Scl 放大）。 */
    public static float lineH(){
        if(lineHeight < 0f){
            Font font = Styles.defaultLabel.font;
            lineHeight = font != null ? font.getLineHeight() : u(16f);
        }
        return lineHeight;
    }

    /** 行高下限：设计高度 vs 字高 + 上下各 3 逻辑像素余量，取大者（用户改 UI 缩放后行不会被裁）。 */
    private static float row(float h){
        return Math.max(h, lineH() + u(6f));
    }

    /** 图标按钮（× / ⚙ / 三条横线）的边长。 */
    public static float icon(){
        return d(18f, 24f);
    }

    // ================================================================ 通用间距刻度

    public static float sp1(){
        return d(1f, 1f);
    }

    public static float sp2(){
        return d(2f, 1f);
    }

    public static float sp3(){
        return d(3f, 2f);
    }

    public static float sp4(){
        return d(4f, 3f);
    }

    public static float sp6(){
        return d(6f, 4f);
    }

    public static float sp8(){
        return d(8f, 6f);
    }

    public static float sp10(){
        return d(10f, 8f);
    }

    public static float sp12(){
        return d(12f, 8f);
    }

    public static float sp14(){
        return d(14f, 10f);
    }

    public static float sp16(){
        return d(16f, 12f);
    }

    public static float sp20(){
        return d(20f, 16f);
    }

    public static float sp24(){
        return d(24f, 20f);
    }

    public static float sp32(){
        return d(32f, 24f);
    }

    public static float sp40(){
        return d(40f, 32f);
    }

    // ================================================================ 主面板 BarPanel

    /** 一列蓝图按钮的宽度。 */
    public static float panelColW(){
        return d(72f, 54f);
    }

    /** 列间距：只留 2，宽度主要让给蓝图按钮本身。 */
    public static float panelColGap(){
        return u(2f);
    }

    /** 蓝图按钮高（原来 24 太扁，文字几乎贴框）。 */
    public static float panelEntryH(){
        return row(d(28f, 24f));
    }

    /** 栏目按钮高。 */
    public static float panelTabH(){
        return row(d(30f, 24f));
    }

    /** 列头高（也是序列名行、"新建序列"按钮的高）。 */
    public static float panelHeadH(){
        return row(d(26f, 20f));
    }

    /** 标题行高：要同时容得下文字与图标按钮。 */
    public static float panelTitleH(){
        return Math.max(row(d(26f, 20f)), icon() + sp2());
    }

    /** 标题行按钮高：比行高矮一点，看起来不顶满。 */
    public static float panelTitleBtnH(){
        return panelTitleH() - sp4();
    }

    /** "编辑 / 完成"按钮宽。 */
    public static float panelEditW(){
        return d(46f, 44f);
    }

    /** 内容表左右内边距。 */
    public static float panelPadX(){
        return d(8f, 6f);
    }

    /** 内容表上下内边距：手机压到 2，否则默认高只有屏高 30% 时上下各空一截。 */
    public static float panelPadY(){
        return d(8f, 2f);
    }

    /** 内容表的默认 cell 间距。 */
    public static float panelCellPad(){
        return d(2f, 1f);
    }

    /** 第一条分隔线的上/下间距。 */
    public static float panelDividerTopA(){
        return d(2f, 1f);
    }

    public static float panelDividerBottomA(){
        return d(4f, 1f);
    }

    /** 第二条分隔线的上/下间距。 */
    public static float panelDividerTopB(){
        return d(4f, 1f);
    }

    public static float panelDividerBottomB(){
        return d(4f, 1f);
    }

    /** 栏目按钮之间的间距。 */
    public static float panelTabGap(){
        return sp4();
    }

    /** 栏目行高（比按钮高一点，留滚动条的位置）。 */
    public static float panelTabRowH(){
        return row(d(38f, 24f));
    }

    /** 栏目滚动区高。 */
    public static float panelTabScrollH(){
        return row(d(34f, 24f));
    }

    /** 收起态窄条的上下内边距。 */
    public static float panelCollapsedPadY(){
        return d(4f, 2f);
    }

    /** 收起态窄条的左右内边距。 */
    public static float panelCollapsedPadX(){
        return d(10f, 8f);
    }

    /** 收起态窄条的最小宽/高。 */
    public static float panelCollapsedMinW(){
        return u(80f);
    }

    public static float panelCollapsedMinH(){
        return u(20f);
    }

    /** 最小宽 = 标题行 pref + 这段余量。 */
    public static float panelMinWPad(){
        return d(20f, 16f);
    }

    /** 最小高 = 最小宽 × 这个比例。 */
    public static float panelMinHRatio(){
        return BarTheme.mobile ? 0.42f : 0.5f;
    }

    /** 面板最小高（不含标题行算法）。 */
    public static float panelMinHFloor(){
        return u(60f);
    }

    /** 默认宽 = 3 列 + 间距 + 滚动条 + 内容边距；手机上不超过屏宽 45%。 */
    public static float panelDefaultW(float screenW){
        float natural = panelColW() * 3f + panelColGap() * 2f + scrollBarW() + panelPadX() * 2f;
        return BarTheme.mobile ? Math.min(natural, screenW * 0.45f) : natural;
    }

    /** 默认高：桌面 300；手机取屏高 30%（再大就占掉半个屏）。 */
    public static float panelDefaultH(float screenH){
        return BarTheme.mobile ? Math.min(u(180f), screenH * 0.30f) : u(300f);
    }

    /** 可用高度上限：手机 42% 屏高。 */
    public static float panelHeightLimit(float screenH){
        return BarTheme.mobile
            ? Math.min(screenH - u(8f), screenH * 0.42f)
            : screenH - u(8f);
    }

    /** 初始落点：左上角留一点、顶部躲开 HUD。 */
    public static float panelSpawnX(){
        return d(12f, 8f);
    }

    public static float panelSpawnTop(){
        return d(48f, 32f);
    }

    /** 尺寸落盘/还原时的边界。 */
    public static float panelInitWMaxInset(){
        return u(16f);
    }

    public static float panelInitWMin(){
        return u(120f);
    }

    public static float panelInitHMin(){
        return u(80f);
    }

    /** 窗口被拖到屏幕外、切分辨率时夹回来的边界。 */
    public static float panelLayoutInset(){
        return u(8f);
    }

    public static float panelLayoutMinW(){
        return u(80f);
    }

    public static float panelLayoutMinH(){
        return u(60f);
    }

    /** 栏目按钮的宽窄范围（跟着文字走，但两头都限住）。 */
    public static float panelTabMinW(){
        return d(40f, 36f);
    }

    public static float panelTabMaxW(){
        return d(150f, 110f);
    }

    /** 编辑态栏目行末尾的 "+"。 */
    public static float panelTabAddW(){
        return d(32f, 30f);
    }

    /** 编辑态固定在最右的"新建序列"按钮宽。 */
    public static float panelSeqAddW(){
        return d(30f, 26f);
    }

    /** 空状态（没有栏目/没有序列）的提示行与按钮。 */
    public static float panelEmptyHintPad(){
        return sp8();
    }

    public static float panelEmptyBtnW(){
        return d(140f, 120f);
    }

    public static float panelEmptyBtnH(){
        return row(d(30f, 28f));
    }

    /** 编辑态列头（序列名按钮）与普通态列头的间距。 */
    public static float panelColHeadPadBottom(){
        return sp3();
    }

    public static float panelColHeadPadLeft(){
        return sp3();
    }

    /** 普通态空列里的"空"字行高。 */
    public static float panelEmptyEntryH(){
        return row(d(20f, 20f));
    }

    /** 列内滚动区的最小高（再小就没法滚了）。 */
    public static float panelColScrollMinH(){
        return u(40f);
    }

    /** 蓝图详情浮层与按钮的间距。 */
    public static float panelPreviewGap(){
        return sp6();
    }

    // ================================================================ 设置窗 BarSettingsWindow

    public static float settingsW(){
        return d(400f, 400f);
    }

    public static float settingsH(){
        return d(236f, 200f);
    }

    public static float settingsMargin(){
        return d(8f, 6f);
    }

    /** 标题行高。 */
    public static float settingsHeadH(){
        return row(d(20f, 20f));
    }

    /** 每一行设置的高度：手机上从 22 收到 18，行距明显变紧。 */
    public static float settingsRowH(){
        return row(d(18f, 18f));
    }

    /** 标题行下面的分组标题行高。 */
    public static float settingsGroupH(){
        return u(15f);
    }

    /** 颜色行里名称列宽。 */
    public static float settingsNameW(){
        return d(100f, 84f);
    }

    /** 底部按钮高。 */
    public static float settingsBtnH(){
        return row(d(24f, 24f));
    }

    /** 滑条与数值列。 */
    public static float settingsSliderH(){
        return u(16f);
    }

    public static float settingsValueW(){
        return d(34f, 30f);
    }

    /** 分隔线的上/下间距。 */
    public static float settingsSepTop(){
        return sp3();
    }

    public static float settingsSepBottom(){
        return sp4();
    }

    /** 底部按钮行的上间距。 */
    public static float settingsBtnRowPad(){
        return sp4();
    }

    // ================================================================ 取色器 BarColorPicker

    public static float colorW(){
        return d(236f, 220f);
    }

    public static float colorH(){
        return d(216f, 200f);
    }

    /** 预设色块行高/色块边长。 */
    public static float colorRowH(){
        return row(d(18f, 18f));
    }

    public static float colorSwatch(){
        return d(18f, 18f);
    }

    /** 预设色块每行几个。 */
    public static int colorCols(){
        return 8;
    }

    /** R/G/B 行：名称列、滑条、数值列。 */
    public static float colorNameW(){
        return d(16f, 14f);
    }

    public static float colorValueW(){
        return d(30f, 26f);
    }

    public static float colorSliderH(){
        return u(16f);
    }

    /** 按钮高与按钮间距。 */
    public static float colorBtnH(){
        return d(22f, 24f);
    }

    public static float colorGap(){
        return d(8f, 6f);
    }

    /** 当前颜色行与预设区之间的上间距。 */
    public static float colorCurrentPadTop(){
        return sp3();
    }

    /** 取色器与它锚定的色块之间的间距。 */
    public static float colorPickerGap(){
        return d(8f, 6f);
    }

    // ================================================================ 槽位 BarSlot

    /** 槽位按钮的最小宽。 */
    public static float slotActionMin(){
        return d(132f, 120f);
    }

    public static float slotRowH(){
        return row(d(24f, 26f));
    }

    public static float slotEditW(){
        return d(40f, 44f);
    }

    public static float slotPad(){
        return d(2f, 1f);
    }

    /** 槽位里"编辑"开关与按钮的间距。 */
    public static float slotGap(){
        return d(8f, 6f);
    }

    // ================================================================ 蓝图卡片 BarSchematicCard

    public static float cardW(){
        return d(176f, 124f);
    }

    public static float cardH(){
        return d(218f, 160f);
    }

    public static float cardPad(){
        return d(6f, 4f);
    }

    /** 预览图与文件名之间的缝。 */
    public static float cardGap(){
        return u(2f);
    }

    public static float cardNameH(){
        return d(18f, 20f);
    }

    public static float cardMetaH(){
        return d(14f, 16f);
    }

    public static float cardTagsH(){
        return d(13f, 14f);
    }

    /** 预览图最矮能压到多高（卡片再矮也不能把图挤没）。 */
    public static float cardMinPreviewH(){
        return u(48f);
    }

    // ================================================================ 蓝图详情 BarSchematicInfo

    /** 预览图高。 */
    public static float infoPreviewH(){
        return d(150f, 72f);
    }

    /** 浮层里描述最多显示多少字。 */
    public static int infoDescChars(){
        return BarTheme.mobile ? 60 : 160;
    }

    /** 表内 cell 间距。 */
    public static float infoCellPad(){
        return u(1f);
    }

    public static float infoPreviewPadBottom(){
        return d(4f, 3f);
    }

    /** 消耗网格的列数。 */
    public static int infoCostCols(){
        return BarTheme.mobile ? 2 : 4;
    }

    /** 消耗格：图标边长、图标到数字的间距、格内右边距、格底边距。 */
    public static float infoCostIcon(){
        return d(14f, 12f);
    }

    public static float infoCostIconGap(){
        return d(19f, 17f);
    }

    public static float infoCostIconPad(){
        return sp3();
    }

    public static float infoCostCellPadBottom(){
        return sp2();
    }

    /** 消耗网格两侧的余量（算列宽用）。 */
    public static float infoCostInset(){
        return sp6();
    }

    /** 电量行：图标、图标间距、上间距。 */
    public static float infoPowerIcon(){
        return d(13f, 12f);
    }

    public static float infoPowerGap(){
        return d(14f, 10f);
    }

    public static float infoPowerPadTop(){
        return sp1();
    }

    /** 每一段（名字、消耗、电量、标签、描述）之间的上间距。 */
    public static float infoRowPadTop(){
        return d(4f, 3f);
    }

    /** 行可用宽度 = 表宽 - 这段余量。 */
    public static float infoRowInset(){
        return sp2();
    }

    // ================================================================ 蓝图详情浮层 BarSchematicInfoPopup

    public static float popupW(){
        return d(300f, 180f);
    }

    public static float popupMargin(){
        return d(8f, 6f);
    }

    // ================================================================ 蓝图信息窗 BarSchematicInfoWindow

    public static float infoWinW(){
        return d(380f, 280f);
    }

    public static float infoWinH(){
        return d(400f, 300f);
    }

    public static float infoWinMinW(){
        return d(280f, 260f);
    }

    public static float infoWinMinH(){
        return d(180f, 160f);
    }

    public static float infoWinMargin(){
        return d(8f, 6f);
    }

    public static float infoWinPad(){
        return u(2f);
    }

    /** 标题下分隔线的下间距（比上间距多 1，和别处分隔线一致）。 */
    public static float infoWinDividerBottom(){
        return u(3f);
    }

    /** 标题行高：要容得下关闭按钮。 */
    public static float infoWinTitleH(){
        return Math.max(row(d(20f, 20f)), icon() + sp4());
    }

    /** 正文滚动区：最小/最大高、以及"最多占屏高多少"。 */
    public static float infoWinBodyMinH(){
        return u(120f);
    }

    public static float infoWinBodyMaxH(){
        return u(320f);
    }

    public static float infoWinBodyScreenRatio(){
        return 0.6f;
    }

    /** 别名行：名称列宽、行高。 */
    public static float infoWinAliasNameW(){
        return d(44f, 36f);
    }

    public static float infoWinAliasRowH(){
        return row(d(26f, 28f));
    }

    /** 输入框高。 */
    public static float infoWinFieldH(){
        return row(d(24f, 26f));
    }

    public static float infoWinBtnH(){
        return row(d(24f, 26f));
    }

    // ================================================================ 选择蓝图窗 BarSchematicPicker

    /** 默认尺寸：手机上按屏占比（80%），桌面上是固定值。 */
    public static float pickerDefaultW(float screenW){
        return BarTheme.mobile ? Math.min(u(760f), screenW * 0.8f) : u(760f);
    }

    public static float pickerDefaultH(float screenH){
        return BarTheme.mobile ? Math.min(u(540f), screenH * 0.8f) : u(540f);
    }

    /** 最小尺寸。 */
    public static float pickerMinW(float screenW){
        float v = BarTheme.mobile ? u(320f) : u(400f);
        return screenW > 0f ? Math.min(v, screenW) : v;
    }

    public static float pickerMinH(float screenH){
        float v = BarTheme.mobile ? u(240f) : u(280f);
        return screenH > 0f ? Math.min(v, screenH) : v;
    }

    public static float pickerMargin(){
        return d(8f, 6f);
    }

    /** 卡片格子的内边距（也是列宽计算里的缝）。 */
    public static float pickerCardPad(){
        return u(4f);
    }

    /** 标题行高。 */
    public static float pickerTitleH(){
        return Math.max(row(d(22f, 22f)), icon() + sp4());
    }

    /** "共 N 张"与关闭按钮的间距。 */
    public static float pickerCountPad(){
        return d(10f, 8f);
    }

    /** 搜索行高（含输入框）。 */
    public static float pickerSearchRowH(){
        return Math.max(row(d(26f, 28f)), icon() + sp4());
    }

    public static float pickerSearchFieldH(){
        return row(d(24f, 26f));
    }

    /** 标签 chips 行高、chip 高度。 */
    public static float pickerChipsRowH(){
        return row(d(26f, 26f));
    }

    public static float pickerChipH(){
        return d(20f, 22f);
    }

    public static float pickerChipGap(){
        return sp4();
    }

    /** 底部提示行高。 */
    public static float pickerHintH(){
        return row(d(16f, 18f));
    }

    /** 空状态（蓝图库为空）的内边距。 */
    public static float pickerEmptyPad(){
        return sp24();
    }

    /** 详情浮层与卡片的间距。 */
    public static float pickerPopupGap(){
        return sp8();
    }

    // ================================================================ 窗口 BarWindow / 缩放手柄 BarResizeGrip

    /** 贴边吸附阈值。 */
    /** 标签浮层：宽度、初始高度、列表最小高度（无标题栏时按内容收高）。 */
    public static float tagOverviewW(){
        return d(300f, 260f);
    }

    public static float tagOverviewH(){
        return d(200f, 170f);
    }

    public static float tagListMinH(){
        return d(60f, 50f);
    }

    public static float tagListMaxH(){
        return d(240f, 200f);
    }

    /** 关闭按钮两条斜线的线宽。 */
    public static float closeStroke(){
        return u(1.2f);
    }

    /** 复选框：方框边长、勾的线宽、左侧留白（12×12 框 + 8 空隙）。 */
    public static float checkBox(){
        return d(12f, 14f);
    }

    public static float checkStroke(){
        return u(1.6f);
    }

    /** 1 物理像素的线（分隔线、边框）：**不过 Scl**，高 DPI 上也要是一条实线。 */
    public static float hairline(){
        return 1f;
    }

    public static float snap(){
        return d(10f, 10f);
    }

    /** 手柄命中区（右下角三角）边长。 */
    public static float gripSize(){
        return d(24f, 30f);
    }

    /** 手柄点阵：点的大小、点间距、距边缘。 */
    public static float gripDot(){
        return d(2f, 2f);
    }

    public static float gripDotGap(){
        return d(3f, 3f);
    }

    public static float gripDotMargin(){
        return d(4f, 4f);
    }

    /** 点阵行列数。 */
    public static int gripDots(){
        return BarTheme.mobile ? 3 : 4;
    }

    // ================================================================ 通用小组件

    /** 普通按钮的文字内边距。 */
    public static float btnPadX(){
        return d(8f, 6f);
    }

    public static float btnPadY(){
        return d(2f, 1f);
    }

    /** 无边框按钮的文字内边距（没有四角括号要躲）。 */
    public static float btnBarePadX(){
        return d(5f, 4f);
    }

    public static float btnBarePadY(){
        return d(1f, 1f);
    }

    /** 输入框：文字左边距（底线样式 / 侧标样式）、右边距、上下内边距。 */
    public static float fieldInset(){
        return d(8f, 6f);
    }

    public static float fieldInsetSide(){
        return d(10f, 8f);
    }

    public static float fieldRightPad(){
        return d(6f, 4f);
    }

    public static float fieldPadY(){
        return d(3f, 2f);
    }

    /** 侧标：竖条与文字的距离、竖条宽。 */
    public static float fieldSideEdge(){
        return u(3f);
    }

    public static float fieldSideW(){
        return d(2f, 2f);
    }

    /** 聚焦时底线的粗细。 */
    public static float fieldFocusStroke(){
        return u(2f);
    }

    /** 滑条：左右留白、滑块宽、滑块最大高。 */
    public static float sliderPad(){
        return d(4f, 3f);
    }

    public static float sliderKnobW(){
        return d(5f, 4f);
    }

    public static float sliderKnobMaxH(){
        return d(14f, 12f);
    }

    /** 滚动条：宽度、滑块厚度、距边、最小可点尺寸、最短滑块。 */
    public static float scrollBarW(){
        return u(6f);
    }

    public static float scrollThumbW(){
        return u(2f);
    }

    public static float scrollInset(){
        return u(3f);
    }

    public static float scrollMinSize(){
        return u(16f);
    }

    public static float scrollPad(){
        return d(4f, 3f);
    }

    public static float scrollMinThumb(){
        return u(8f);
    }

    /** 关闭按钮（×）：线长内缩、线粗、悬停底色。 */
    public static float closeMargin(){
        return d(5f, 4f);
    }

    /** 三条横线按钮：线长、行距、线粗。 */
    public static float menuLinesW(){
        return d(13f, 11f);
    }

    public static float menuLinesGap(){
        return d(4f, 3f);
    }

    public static float menuLinesThick(){
        return u(1.4f);
    }

    /** 弹出菜单：行高、最小宽、行内边距、与光标的距离。 */
    public static float menuItemH(){
        return row(d(22f, 24f));
    }

    public static float menuMinW(){
        return d(104f, 96f);
    }

    public static float menuPad(){
        return u(1f);
    }

    public static float menuMargin(){
        return d(6f, 4f);
    }

    /** 确认框 / 输入框。 */
    public static float dialogW(){
        return d(300f, 280f);
    }

    public static float dialogBtnH(){
        return row(d(24f, 26f));
    }

    public static float dialogFieldH(){
        return row(d(24f, 26f));
    }

    public static float dialogMargin(){
        return d(10f, 8f);
    }

    /** 确认框/输入框里分隔线的上/下间距、按钮行上间距。 */
    public static float dialogSepTop(){
        return sp4();
    }

    public static float dialogSepBottom(){
        return sp6();
    }

    public static float dialogBtnRowPad(){
        return d(10f, 8f);
    }

    /** 颜色色块：边长、与文字的间距。 */
    public static float swatchSize(){
        return d(14f, 16f);
    }

    public static float swatchPadRight(){
        return d(6f, 4f);
    }
}
