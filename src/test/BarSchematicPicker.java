package test;

import arc.Core;
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
 *   - 窗口尺寸、搜索词、选中的标签 → static 字段（游戏进程内有效，重启即丢，符合当前测试阶段）
 *   - 窗口位置 → 每个实例一个 WindowState，不落盘
 */
public class BarSchematicPicker extends BarWindow{

    public static final float DEFAULT_W = 760f, DEFAULT_H = 540f;
    public static final float MIN_W = 480f, MIN_H = 360f;
    public static final float CARD_W = BarSchematicCard.W, CARD_H = BarSchematicCard.H, CARD_PAD = 4f;
    public static final float GRIP = BarResizeGrip.SIZE;

    /** 原版过滤标点的方式：先去掉符号再比名字，避免 “wall-1” 搜不到 “wall1”。 */
    private static final Pattern ignoreSymbols = Pattern.compile("[`~!@#$%^&*()\\-_=+{}|;:'\",<.>/?]");
    private static final float HOVER_DELAY = 0.5f, HIDE_DELAY = 0.2f, POLL = 0.5f;

    // ------------------------------------------------------------------ 会话级状态
    private static String sessionSearch = "";
    private static float sessionW = DEFAULT_W, sessionH = DEFAULT_H;
    private static final Seq<String> selectedTags = new Seq<>();

    private final BarSlotWindow slot;
    private final BarConfig.WindowState state = new BarConfig.WindowState();

    private final Table grid = new Table(), chips = new Table();
    private final BarTextField search;
    private final BarButton tagButton;
    private final Label countLabel, hint;
    private final BarResizeGrip grip;

    private BarButton allChip;
    private BarTagOverview overview;
    private BarSchematicInfoPopup popup;
    private BarSchematicCard hoverCard;
    private float hoverTime, hideTime, pollTime;

    private int shownLibrary = -1, shownTagCount = -1;

    public BarSchematicPicker(BarSlotWindow slot){
        super(sessionW, sessionH);
        this.slot = slot;

        margin(8f);
        defaults().pad(2f);
        top();

        // ---------------------------------------------------------------- 标题
        Table head = new Table();
        head.add(BarTheme.accentText("选 择 蓝 图")).growX().left();
        countLabel = BarTheme.dimText("");
        countLabel.touchable = Touchable.disabled;
        head.add(countLabel).right().padRight(10f);
        head.add(new BarCloseButton().onClick(this::close)).size(18f);
        add(head).growX().height(22f).row();

        add(new BarDivider()).growX().height(1f).padTop(2f).padBottom(4f).row();

        // ---------------------------------------------------------------- 搜索行
        Table srow = new Table();
        srow.add(new Image(Icon.zoom)).size(14f).padRight(5f);

        search = new BarTextField(sessionSearch, "搜索蓝图名", 40);
        srow.add(search).growX().height(24f);

        tagButton = new BarButton("标签 ▾").borderless(true).onClick(this::toggleOverview);
        srow.add(tagButton).size(84f, 22f).padLeft(6f);

        srow.add(new BarIconButton(Icon.list, 12f).onClick(this::openVanilla)).size(22f).padLeft(2f);
        add(srow).growX().height(26f).row();

        // ---------------------------------------------------------------- 标签行
        chips.left();
        chips.defaults().padRight(4f);
        add(new BarScroll(chips, true)).growX().height(26f).row();

        // ---------------------------------------------------------------- 卡片网格
        // Table 自己会在宽度不够时换行，所以窗口变宽变窄都不用重建卡片
        grid.left().top();
        grid.defaults().pad(CARD_PAD);
        add(new BarScroll(grid)).grow().row();

        hint = BarTheme.dimText("点卡片绑定 · 悬停看详情 · 拖右下角缩放");
        hint.touchable = Touchable.disabled;
        add(hint).growX().height(14f).row();

        // 缩放手柄：不是 cell，浮在右下角，不参与 Table 布局
        grip = new BarResizeGrip(this, MIN_W, MIN_H, new BarResizeGrip.Handler(){
            @Override
            public void resize(float w, float h){
                applySize(w, h);
            }

            @Override
            public void resized(){}
        });
        addChild(grip);
        grip.setPosition(width - GRIP, 0f);

        rebuildChips();
        rebuildCards();
    }

    @Override
    protected BarConfig.WindowState state(){
        return state;
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

        float w = Mathf.clamp(sessionW, Math.min(MIN_W, sw), sw);
        float h = Mathf.clamp(sessionH, Math.min(MIN_H, sh), sh);
        sessionW = w;
        sessionH = h;
        if(getWidth() != w || getHeight() != h) setSize(w, h);

        super.applyLayout();
    }

    /**
     * 拖右下角改尺寸：左上角固定不动。
     *
     * 夹的范围要按“左上角已经占掉的空间”算——顶端固定时能长多高，取决于上边界到屏幕顶还有多远。
     */
    private void applySize(float w, float h){
        float sw = Core.graphics.getWidth(), sh = Core.graphics.getHeight();
        float top = y + getHeight();

        w = Mathf.clamp(w, MIN_W, Math.max(MIN_W, sw - x));
        h = Mathf.clamp(h, MIN_H, Math.max(MIN_H, top));

        sessionW = w;
        sessionH = h;
        setSize(w, h);
        setPosition(x, Mathf.round(top - h));
        hidePopup();
    }

    @Override
    protected void sizeChanged(){
        super.sizeChanged();
        if(grip != null) grip.setPosition(width - GRIP, 0f);
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
        shownLibrary = libraryCount();
        shownTagCount = allTags().size();

        List<Schematic> list = filtered();
        countLabel.setText("共 " + shownLibrary + " 张 · 显示 " + list.size() + " 张");

        if(list.isEmpty()){
            grid.add(BarTheme.dimText(shownLibrary == 0 ? "蓝图库是空的：先用原版蓝图界面做一张" : "没有符合条件的蓝图"))
                .growX().pad(24f).row();
            return;
        }

        for(Schematic s : list){
            BarSchematicCard card = new BarSchematicCard(s);
            card.setSelected(s == slot.bound());

            // 绑定由槽位窗负责，它会在 picked() 里顺手把这个选择窗关掉
            card.onClick(() -> slot.picked(s));

            card.onHover(() -> {
                hoverCard = card;
                hoverTime = 0f;
                hideTime = 0f;
            });

            card.onUnhover(() -> {
                if(hoverCard == card) hoverCard = null;
                if(popup != null && !popup.pinned()) hideTime = HIDE_DELAY;
            });

            card.onInfo(() -> showPopup(card, true));

            grid.add(card).size(CARD_W, CARD_H);
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
        chips.add(allChip).height(20f).padRight(4f);

        for(String tag : tags){
            BarButton b = new BarButton(tag + " " + countOf(tag)).borderless(true).stateful(true);
            b.setChecked(isTagSelected(tag));
            b.onClick(() -> {
                toggleTag(tag);
                refreshFilters();
            });
            chips.add(b).height(20f).padRight(4f);
        }

        // 按钮上的字数就是“当前筛了几项”，一眼能看出筛选是开着的
        tagButton.text(selectedTags.isEmpty() ? "标签 ▾" : "标签 " + selectedTags.size + " ▾");
    }

    // ------------------------------------------------------------------ 浮层

    private void toggleOverview(){
        if(overview != null && overview.parent != null){
            overview.remove();
            overview = null;
            return;
        }
        if(Vars.ui == null || Vars.ui.hudGroup == null) return;

        overview = new BarTagOverview(this);
        Vars.ui.hudGroup.addChild(overview);
        overview.toFront();

        // 锚点是标签按钮的右下角：浮层右边缘对齐按钮，向下展开
        Vec2 v = tagButton.localToStageCoordinates(new Vec2(0f, tagButton.getHeight()));
        Vars.ui.hudGroup.stageToLocalCoordinates(v);
        overview.placeAt(v.x, v.y - 6f);
    }

    private void showPopup(BarSchematicCard card, boolean pinned){
        hidePopup();
        if(Vars.ui == null || Vars.ui.hudGroup == null) return;

        popup = new BarSchematicInfoPopup(card.schematic(), pinned);
        Vars.ui.hudGroup.addChild(popup);

        // 卡片在滚动区里，x/y 是网格容器的局部坐标，必须换算到 hudGroup
        Vec2 v = card.localToStageCoordinates(new Vec2(0f, 0f));
        Vars.ui.hudGroup.stageToLocalCoordinates(v);

        float w = popup.getWidth(), h = popup.getHeight();
        float px = v.x + card.getWidth() + 8f;
        if(px + w > Core.graphics.getWidth()) px = Math.max(0f, v.x - w - 8f);
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

        // 搜索词统一在这里读：粘贴、清空、中文输入法都能覆盖，不必依赖 TextField 的回调时机
        String cur = search.text();
        if(!cur.equals(sessionSearch)){
            sessionSearch = cur;
            rebuildCards();
            hidePopup();
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
            if(popup != null && !popup.pinned()) hidePopup();
        }else if(hoverCard != null && hoverCard.parent != null && popup == null){
            hoverTime += delta;
            if(hoverTime >= HOVER_DELAY) showPopup(hoverCard, false);
        }

        // 悬停浮层的收起：指针在浮层上就一直留着（浮层可能被夹到卡片上面，此时卡片已经“失去悬停”，
        // 只看卡片状态会导致浮层一出就把自己关掉、然后卡片又触发悬停的反复闪烁）
        if(hideTime > 0f) hideTime -= delta;
        if(popup != null && !popup.pinned()){
            boolean overPopup = BarTheme.pointerOver(popup);
            boolean overCard = hoverCard != null && hoverCard.parent != null && BarTheme.pointerOver(hoverCard);

            if(overPopup) hideTime = HIDE_DELAY;
            else if(!overCard && hideTime <= 0f) hidePopup();
        }

        if(popup != null && popup.parent == null) popup = null;
    }

    // ------------------------------------------------------------------ 其它

    private void openVanilla(){
        if(Vars.ui != null && Vars.ui.schematics != null) Vars.ui.schematics.show();
    }

    private void close(){
        slot.closePicker();
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
