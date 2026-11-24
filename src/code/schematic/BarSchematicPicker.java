package code;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.event.Touchable;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.game.Schematic;
import mindustry.gen.Icon;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 蓝图选择窗：照原版蓝图界面的样子重做——搜索、标签筛选、卡片网格、悬停详情。
 *
 * 为什么不直接开原版对话框：原版没有对外暴露“用户选了哪一张”的回调
 * （点卡片直接 useSchematic() 然后 hide()），挂 capture 监听反查按钮里的 Label
 * 在重名时会绑错。这里自己列一遍，绑定语义最确定；预览图仍复用原版的
 * {@link mindustry.ui.dialogs.SchematicsDialog.SchematicImage}，生成逻辑与原版一致。
 *
 * 状态存在哪：
 *   - 窗口位置 → BarConfig.pickerWindow（贴边/比例两轴，和主面板、设置窗同一套）
 *   - 窗口尺寸 → BarConfig.pickerWidth / pickerHeight（桌面）与 pickerWidthMobile / pickerHeightMobile（手机），
 *     拖右下角松手时按当前设备落盘
 *   - 搜索词、选中的标签 → static 字段（只在本次运行内有效，重启即丢）
 */
public class BarSchematicPicker extends BarWindow{

    /** 默认/最小尺寸，都是逻辑像素（真实施加前要过 BarTheme.u）。 */
    public static final float DEFAULT_W = 760f, DEFAULT_H = 540f;
    public static final float MIN_W = 400f, MIN_H = 280f;
    public static final float CARD_PAD = 4f;

    /** 原版过滤标点的方式：先去掉符号再比名字，避免 “wall-1” 搜不到 “wall1”。 */
    private static final Pattern ignoreSymbols = Pattern.compile("[`~!@#$%^&*()\\-_=+{}|;:'\",<.>/?]");
    /** 悬停多久出详情、移开多久收起。短一点才跟手，长了像“没反应”。 */
    private static final float HOVER_DELAY = 0.12f, HIDE_DELAY = 0.06f, POLL = 0.5f;

    // ------------------------------------------------------------------ 会话级状态
    private static String sessionSearch = "";
    private static final Seq<String> selectedTags = new Seq<>();

    private final BarPickerHost host;

    private final Table grid = new Table(), chips = new Table();
    private final BarTextField search;
    private final Label countLabel, hint;

    private BarButton allChip;
    private BarTagOverview overview;
    private BarSchematicInfoPopup popup;
    private BarSchematicCard hoverCard;
    private float hoverTime, hideTime, pollTime;
    /** 搜索防抖：连续打字时不重建网格，停手 0.2 秒才理一次。 */
    private float searchDelay;

    private int shownLibrary = -1, shownTagCount = -1;
    /** 当前网格排了几列。列数变了才重建卡片，拖动缩放时不至于每像素重建一次。 */
    private int columns = -1;

    /**
     * 默认尺寸：桌面上 760×540 逻辑；手机上这两个值会超过逻辑屏高（该机只有 500~640），
     * 被夹成整屏后标题/搜索/标签/网格全挤在一起，所以按屏占比算。
     */
    private static float defaultW(){
        return BarTheme.mobile
            ? Math.min(BarTheme.u(DEFAULT_W), Core.graphics.getWidth() * 0.8f)
            : BarTheme.u(DEFAULT_W);
    }

    private static float defaultH(){
        return BarTheme.mobile
            ? Math.min(BarTheme.u(DEFAULT_H), Core.graphics.getHeight() * 0.8f)
            : BarTheme.u(DEFAULT_H);
    }

    /** 存档里的尺寸（逻辑单位，按设备分两套）；没存过（0）就用默认值。这里顺手换成屏幕像素。 */
    private static float storedW(){
        float v = BarConfig.pickerW();
        return v > 0f ? BarTheme.u(v) : defaultW();
    }

    private static float storedH(){
        float v = BarConfig.pickerH();
        return v > 0f ? BarTheme.u(v) : defaultH();
    }

    /** 最小尺寸也要夹进画布：手机上逻辑 400 宽换算过来可能已经超过半屏。 */
    private static float minW(){
        float w = BarTheme.u(BarTheme.mobile ? 320f : MIN_W);
        float sw = Core.graphics.getWidth();
        return sw > 0f ? Math.min(w, sw) : w;
    }

    private static float minH(){
        float h = BarTheme.u(BarTheme.mobile ? 240f : MIN_H);
        float sh = Core.graphics.getHeight();
        return sh > 0f ? Math.min(h, sh) : h;
    }

    public BarSchematicPicker(BarPickerHost host){
        super(storedW(), storedH());
        this.host = host;

        margin(BarTheme.d(8f, 6f));
        defaults().pad(BarTheme.u(2f));
        top();

        // ---------------------------------------------------------------- 标题
        Table head = new Table();
        head.add(BarTheme.accentText("选 择 蓝 图")).growX().left().minWidth(0f);
        countLabel = BarTheme.dimText("");
        countLabel.touchable = Touchable.disabled;
        head.add(countLabel).right().padRight(BarTheme.u(10f));
        head.add(new BarCloseButton().onClick(this::close)).size(BarTheme.icon());
        add(head).growX()
            .height(Math.max(BarTheme.rowH(22f, 22f), BarTheme.icon() + BarTheme.u(4f))).row();

        add(new BarDivider()).growX().height(1f).padTop(BarTheme.u(2f)).padBottom(BarTheme.u(4f)).row();

        // ---------------------------------------------------------------- 搜索行
        Table srow = new Table();

        search = new BarTextField(sessionSearch, "搜索蓝图名", BarTextField.MAX_LENGTH);
        srow.add(search).growX().height(BarTheme.rowH(24f, 26f));

        // 三条横线自己画：原版 Icon.list 是贴图，设置里换色时不会跟着变
        float icon = BarTheme.icon();
        srow.add(new BarMenuButton(icon).onClick(this::openVanilla)).size(icon).padLeft(BarTheme.u(4f));
        add(srow).growX().height(Math.max(BarTheme.rowH(26f, 28f), icon + BarTheme.u(4f))).row();

        // ---------------------------------------------------------------- 标签行
        chips.left();
        chips.defaults().padRight(BarTheme.u(4f));
        add(new BarScroll(chips, true)).growX().height(BarTheme.rowH(26f, 26f)).row();

        // ---------------------------------------------------------------- 卡片网格
        // Table 不会自己换行：它的列是逐格 add 出来的，放在 ScrollPane 里宽度还会取“一整行的
        // pref”，所以必须按窗口宽度自己算列数、拆行（原版蓝图界面也是这么做的）。
        grid.top();
        grid.defaults().pad(BarTheme.u(CARD_PAD));
        add(new BarScroll(grid)).grow().row();

        // 手机没有“悬停”，对应的入口是长按（BarSchematicCard/BarEntryButton 里的 0.5s 计时）
        hint = BarTheme.dimText(BarTheme.mobile
            ? "点卡片绑定 · 长按看详情 · 拖右下角缩放"
            : "点卡片绑定 · 悬停看详情 · 拖右下角缩放");
        hint.touchable = Touchable.disabled;
        hint.setEllipsis(true);
        add(hint).growX().height(BarTheme.rowH(16f, 18f)).minWidth(0f).row();

        rebuildChips();
        rebuildCards();

        // 缩放手柄统一交给 BarWindow：拖右下角、左上角固定、松手才落盘
        enableResize(minW(), minH());
        setSize(Mathf.clamp(getWidth(), minW(), Core.graphics.getWidth()),
            Mathf.clamp(getHeight(), minH(), Core.graphics.getHeight()));
    }

    /**
     * 整窗裁切（含右下角手柄）。
     *
     * 不能用 {@code setClip(true)}：Table 自带的那条路径是按内边距裁的
     * （Table.java:98-106，从 padLeft/padBottom 起算），窗口有 8px 内边距，贴边的手柄
     * 就整个落在裁剪区外——线条确实画了，只是被裁掉了，看上去像没有手柄。
     */
    @Override
    public void draw(){
        Draw.flush();
        if(clipBegin(x, y, width, height)){
            super.draw();
            Draw.flush();
            clipEnd();
        }
    }

    /** 当前宽度下能排几列。滚动条宽度由 BarScroll 预留，这里要一起减掉。 */
    private int columnsFor(){
        float avail = getWidth() - getMarginLeft() - getMarginRight() - BarScroll.barWidth();
        return Math.max(1, (int)Math.floor(avail / (BarSchematicCard.w() + BarTheme.u(CARD_PAD) * 2f)));
    }

    @Override
    protected BarConfig.WindowState state(){
        return BarConfig.pickerWindow;
    }

    /** 默认屏幕正中。 */
    @Override
    protected void defaultSpawn(){
        float w = Core.graphics.getWidth(), h = Core.graphics.getHeight();
        setPosition(Mathf.round(Math.max(0f, (w - width) / 2f)), Mathf.round(Math.max(0f, (h - height) / 2f)));
    }

    /** 先把尺寸夹进屏幕，再交给基类决定落点（首次打开 → 正中）。 */
    @Override
    public void applyLayout(){
        float sw = Core.graphics.getWidth(), sh = Core.graphics.getHeight();
        if(sw <= 0f || sh <= 0f) return;

        // 存档尺寸按逻辑单位存，构造时已经乘过 Scl；这里只负责夹进当前画布
        float w = Mathf.clamp(getWidth(), minW(), sw);
        float h = Mathf.clamp(getHeight(), minH(), sh);
        if(getWidth() != w || getHeight() != h) setSize(w, h);

        super.applyLayout();
    }

    /** 拖动手柄改尺寸：只关掉浮层，列数变化由 act() 统一处理，避免每像素重建一次网格。 */
    @Override
    protected void onResize(float w, float h){
        hidePopup();
    }

    /** 松手才落盘：尺寸按逻辑单位存，换屏幕/换 UI 缩放后仍然合适。 */
    @Override
    protected void onResizeEnd(){
        float lw = getWidth() / BarTheme.u(1f), lh = getHeight() / BarTheme.u(1f);
        if(BarTheme.mobile){
            BarConfig.pickerWidthMobile = lw;
            BarConfig.pickerHeightMobile = lh;
        }else{
            BarConfig.pickerWidth = lw;
            BarConfig.pickerHeight = lh;
        }
        BarConfig.save();
    }

    /** 恢复默认：位置和尺寸一起清掉，下次打开回到屏幕正中、默认大小。 */
    @Override
    public void resetToDefault(){
        BarConfig.pickerWidth = 0f;
        BarConfig.pickerHeight = 0f;
        BarConfig.pickerWidthMobile = 0f;
        BarConfig.pickerHeightMobile = 0f;
        super.resetToDefault();
    }

    /** 选择窗是临时窗：开着的时候常驻面板拖不动自己的层级，不能把它盖住。 */
    @Override
    protected boolean transientWindow(){
        return true;
    }

    // ------------------------------------------------------------------ 数据

    private int libraryCount(){
        return Vars.schematics == null ? 0 : Vars.schematics.all().size;
    }

    /** 标签总表：先按原版记的顺序，再补上蓝图里出现但表里没有的（原版标签表是全局键）。 */
    public List<String> allTags(){
        List<String> out = new ArrayList<>();
        if(Vars.schematics == null) return out;

        Seq<String> saved = Core.settings.getJson("schematic-tags", Seq.class, String.class, Seq::new);
        if(saved != null){
            for(String t : saved){
                if(t != null && !t.isEmpty() && !out.contains(t)) out.add(t);
            }
        }
        for(Schematic s : Vars.schematics.all()){
            if(s.labels == null) continue;
            for(String t : s.labels){
                if(t != null && !t.isEmpty() && !out.contains(t)) out.add(t);
            }
        }
        return out;
    }

    public int countOf(String tag){
        if(Vars.schematics == null) return 0;
        int n = 0;
        for(Schematic s : Vars.schematics.all()){
            if(s.labels != null && s.labels.contains(tag)) n++;
        }
        return n;
    }

    public boolean isTagSelected(String tag){
        return selectedTags.contains(tag);
    }

    public void toggleTag(String tag){
        if(selectedTags.contains(tag)) selectedTags.remove(tag);
        else selectedTags.add(tag);
    }

    public void clearTags(){
        selectedTags.clear();
    }

    /** 筛选变了：标签行、卡片、浮层三处一起同步。 */
    public void refreshFilters(){
        rebuildChips();
        rebuildCards();
        if(overview != null && overview.parent != null) overview.sync();
    }

    private boolean matches(Schematic s){
        if(selectedTags.size > 0){
            if(s.labels == null) return false;
            for(String t : selectedTags){
                if(!s.labels.contains(t)) return false;
            }
        }

        if(sessionSearch.isEmpty()) return true;
        String q = ignoreSymbols.matcher(sessionSearch.toLowerCase()).replaceAll("");
        if(q.isEmpty()) return true;

        String name = s.name().toLowerCase();
        return name.contains(q) || ignoreSymbols.matcher(name).replaceAll("").contains(q);
    }

    private List<Schematic> filtered(){
        List<Schematic> out = new ArrayList<>();
        if(Vars.schematics == null) return out;
        for(Schematic s : Vars.schematics.all()){
            if(matches(s)) out.add(s);
        }
        return out;
    }

    // ------------------------------------------------------------------ 卡片

    private void rebuildCards(){
        grid.clearChildren();
        // 卡片全被换掉了，旧的悬停目标不能留着（新卡片压在指针上的话，引擎下一帧会重新发 enter）
        hoverCard = null;
        hoverTime = 0f;
        shownLibrary = libraryCount();
        shownTagCount = allTags().size();
        columns = columnsFor();

        List<Schematic> list = filtered();
        countLabel.setText("共 " + shownLibrary + " 张 · 显示 " + list.size() + " 张");

        if(list.isEmpty()){
            grid.add(BarTheme.dimText(shownLibrary == 0 ? "蓝图库是空的：先用原版蓝图界面做一张" : "没有符合条件的蓝图"))
                .growX().pad(BarTheme.u(24f)).row();
            return;
        }

        int index = 0;
        for(Schematic s : list){
            BarSchematicCard card = new BarSchematicCard(s);
            card.setSelected(s == host.currentSchematic());

            // 绑定由槽位窗负责，它会在 picked() 里顺手把这个选择窗关掉
            card.onClick(() -> host.picked(s));

            card.onHover(() -> {
                hoverCard = card;
                hoverTime = 0f;
                hideTime = 0f;
            });

            card.onUnhover(() -> {
                if(hoverCard == card) hoverCard = null;
                if(popup != null) hideTime = HIDE_DELAY;
            });

            // 触摸端：长按立刻出详情（松手后由 act 里的 isTouched 判断收起）
            card.onLongPress(() -> {
                hoverCard = card;
                hideTime = 0f;
                showPopup(card);
            });

            grid.add(card).size(BarSchematicCard.w(), BarSchematicCard.h());
            // 满一列就换行；最后一张不换，免得网格尾部多出一行空高度
            if(++index < list.size() && index % columns == 0) grid.row();
        }
    }

    private void rebuildChips(){
        chips.clearChildren();

        List<String> tags = allTags();

        allChip = new BarButton("全部").borderless(true).stateful(true);
        allChip.setChecked(selectedTags.isEmpty());
        allChip.onClick(() -> {
            clearTags();
            refreshFilters();
        });
        chips.add(allChip).height(BarTheme.u(20f)).padRight(BarTheme.u(4f));

        for(String tag : tags){
            BarButton b = new BarButton(tag + " " + countOf(tag)).borderless(true).stateful(true);
            b.setChecked(isTagSelected(tag));
            b.onClick(() -> {
                toggleTag(tag);
                refreshFilters();
            });
            chips.add(b).height(BarTheme.u(20f)).padRight(BarTheme.u(4f));
        }

    }

    // ------------------------------------------------------------------ 浮层
// 不使用标签下拉栏
//    private void toggleOverview(){
//        if(overview != null && overview.parent != null){
//            overview.remove();
//            overview = null;
//            return;
//        }
//        if(Vars.ui == null || Vars.ui.hudGroup == null) return;
//
//        overview = new BarTagOverview(this);
//        Vars.ui.hudGroup.addChild(overview);
//        overview.toFront();
//
//        // 锚点是标签按钮的右下角：浮层右边缘对齐按钮，向下展开
//        Vec2 v = tagButton.localToStageCoordinates(new Vec2(0f, tagButton.getHeight()));
//        Vars.ui.hudGroup.stageToLocalCoordinates(v);
//        overview.placeAt(v.x, v.y - 6f);
//    }

    private void showPopup(BarSchematicCard card){
        hidePopup();
        if(Vars.ui == null || Vars.ui.hudGroup == null) return;

        popup = new BarSchematicInfoPopup(card.schematic());
        Vars.ui.hudGroup.addChild(popup);

        // 卡片在滚动区里，x/y 是网格容器的局部坐标，必须换算到 hudGroup
        Vec2 v = card.localToStageCoordinates(new Vec2(0f, 0f));
        Vars.ui.hudGroup.stageToLocalCoordinates(v);

        float w = popup.getWidth(), h = popup.getHeight(), gap = BarTheme.u(8f);
        float px = v.x + card.getWidth() + gap;
        if(px + w > Core.graphics.getWidth()) px = Math.max(0f, v.x - w - gap);
        float py = v.y + card.getHeight() - h;

        popup.setPosition(Mathf.round(px),
            Mathf.round(Mathf.clamp(py, 0f, Math.max(0f, Core.graphics.getHeight() - h))));
        popup.toFront();
    }

    private void hidePopup(){
        if(popup != null){
            popup.hide();
            popup = null;
        }
    }

    // ------------------------------------------------------------------ 帧更新

    @Override
    public void act(float delta){
        super.act(delta);

        // 拖右下角缩放 / 切分辨率后列数变了，重排一次卡片
        if(shownLibrary >= 0 && columnsFor() != columns) rebuildCards();

        // 搜索词统一在这里读：粘贴、清空、中文输入法都能覆盖，不必依赖 TextField 的回调时机。
        // 但重建要防抖——每变一个字就重造几百张卡片（每张 3 个 Label）会明显卡一下。
        String cur = search.text();
        if(!cur.equals(sessionSearch)){
            sessionSearch = cur;
            searchDelay = 0.2f;
            hidePopup();
        }else if(searchDelay > 0f){
            searchDelay -= delta;
            if(searchDelay <= 0f) rebuildCards();
        }

        // 蓝图库/标签可能被原版界面改动，半秒查一次就行
        pollTime -= delta;
        if(pollTime <= 0f){
            pollTime = POLL;
            if(libraryCount() != shownLibrary) rebuildCards();
            if(allTags().size() != shownTagCount){
                refreshFilters();
                if(overview != null && overview.parent != null) overview.rebuild();
            }
        }

        if(isDragging()){
            // 拖窗口时指针必然扫过卡片，别让它触发悬停详情
            hoverCard = null;
            if(popup != null) hidePopup();
        }else if(hoverCard != null && hoverCard.parent != null){
            hoverTime += delta;
            // 换了另一张卡就重新计时后直接换浮层，不用等旧浮层先收起来
            boolean other = popup != null && popup.schematic() != hoverCard.schematic();
            if(hoverTime >= HOVER_DELAY && (popup == null || other)) showPopup(hoverCard);
        }

        // 悬停浮层的收起：指针在浮层上就一直留着（浮层可能被夹到卡片上面，此时卡片已经“失去悬停”，
        // 只看卡片状态会导致浮层一出就把自己关掉、然后卡片又触发悬停的反复闪烁）
        if(hideTime > 0f) hideTime -= delta;
        if(popup != null && popup.parent != null){
            // 悬停状态是卡片/浮层自己用 enter/exit 维护的，这里只读布尔，不再每帧做 hit
            boolean overPopup = popup.hovered();
            boolean overCard = hoverCard != null && hoverCard.parent != null && hoverCard.isOver();

            if(overPopup) hideTime = HIDE_DELAY;
            // 手指还按着就留着：触摸端没有悬停状态，一松手才收
            else if(!overCard && !Core.input.isTouched() && hideTime <= 0f) hidePopup();
        }

        if(popup != null && popup.parent == null) popup = null;
    }

    // ------------------------------------------------------------------ 其它

    private void openVanilla(){
        if(Vars.ui != null && Vars.ui.schematics != null) Vars.ui.schematics.show();
    }

    private void close(){
        host.closePicker();
    }

    @Override
    public boolean remove(){
        hidePopup();
        if(overview != null){
            overview.remove();
            overview = null;
        }
        if(search != null && Core.scene != null && Core.scene.getKeyboardFocus() == search.field){
            Core.scene.setKeyboardFocus(null);
        }
        return super.remove();
    }
}
