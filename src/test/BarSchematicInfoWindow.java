package test;

import arc.Core;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Table;
import mindustry.gen.Icon;
import mindustry.type.ItemStack;
import mindustry.ui.dialogs.SchematicsDialog;
import mindustry.game.Schematic;

/**
 * 已绑定蓝图的详情窗：只读原版信息，唯一可改的是别名。
 *
 * 关闭方式三条：右上 ×、点窗外任意处、Esc。
 * 点窗外关闭靠 Scene 级 capture 监听——窗口自己的 InputListener 拿不到窗外的事件，
 * 而 capture 阶段总是在窗口之前触发，所以能确定“这一下点在窗口外面”。
 *
 * 可拖动（继承 BarWindow），位置只存会话：每个实例一个 WindowState，不进 config.json。
 */
public class BarSchematicInfoWindow extends BarWindow{

    public static final float WIDTH = 380f, HEIGHT = 400f;

    private final BarSlotWindow slot;
    private final Schematic schem;
    private final BarConfig.WindowState state = new BarConfig.WindowState();
    private final BarTextField alias;

    /** 上次同步给槽位的别名，用来判断输入框内容有没有变化。 */
    private String shownAlias;

    /** 点窗外 / Esc 关闭。capture 监听挂在 Scene 上，窗口移除时必须摘掉。 */
    private final InputListener outside = new InputListener(){
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
            for(Element e = event.targetActor; e != null; e = e.parent){
                if(e == BarSchematicInfoWindow.this) return false;
            }

            close();
            // 吃掉这一下：否则关窗的同时又点到了底下的按钮（可能直接把蓝图放下去）
            return true;
        }

        @Override
        public boolean keyDown(InputEvent event, KeyCode key){
            if(key == KeyCode.escape || key == KeyCode.back){
                close();
                return true;
            }
            return false;
        }
    };

    public BarSchematicInfoWindow(BarSlotWindow slot){
        super(WIDTH, HEIGHT);
        this.slot = slot;
        this.schem = slot.bound();

        margin(8f);
        defaults().pad(2f);
        top();

        // ---------------------------------------------------------------- 标题
        Table head = new Table();
        head.add(BarTheme.accentText("蓝 图 信 息")).growX().left();
        head.add(new BarCloseButton().onClick(this::close)).size(18f);
        add(head).growX().height(20f).row();

        add(new BarDivider()).growX().height(1f).padTop(2f).padBottom(3f).row();

        // ---------------------------------------------------------------- 内容
        Table body = new Table();
        body.top();
        body.defaults().pad(2f);

        body.add(new SchematicsDialog.SchematicImage(schem)).growX().height(168f).padBottom(6f).row();

        body.add(BarTheme.text(schem.name())).growX().left().ellipsis(true).row();
        body.add(BarTheme.dimText(schem.width + " × " + schem.height + "    方块 " + schem.tiles.size))
            .growX().left().padTop(2f).row();

        body.add(BarTheme.accentText("· 消耗")).growX().left().padTop(8f).padBottom(2f).row();

        ItemStack[] reqs = schem.requirements().toArray();
        float cons = schem.powerConsumption(), prod = schem.powerProduction();
        boolean hasPower = cons > 0.001f || prod > 0.001f;

        if(reqs.length == 0 && !hasPower){
            body.add(BarTheme.dimText("无")).growX().left().row();
        }

        if(reqs.length > 0){
            Table cost = new Table();
            cost.defaults().pad(2f);
            int i = 0;
            for(ItemStack st : reqs){
                cost.image(st.item.uiIcon).size(18f);
                cost.add(BarTheme.text(String.valueOf(st.amount))).padRight(12f);
                if(++i % 4 == 0) cost.row();
            }
            body.add(cost).growX().left().row();
        }

        if(hasPower){
            Table power = new Table();
            power.defaults().pad(2f);
            power.image(Icon.powerSmall).size(16f).padRight(2f);
            String p = "";
            if(cons > 0.001f) p += String.format("%.1f/s 消耗", cons);
            if(prod > 0.001f) p += (p.isEmpty() ? "" : "    ") + String.format("%.1f/s 产出", prod);
            power.add(BarTheme.text(p)).left();
            body.add(power).growX().left().padTop(2f).row();
        }

        String desc = schem.description();
        if(desc != null && !desc.isEmpty()){
            body.add(BarTheme.dimText(desc)).growX().left().wrap().padTop(8f).row();
        }

        // 预览图是固定高的，消耗/描述行数不定：滚动区高度按内容定，窗口再跟着收缩
        float bodyH = Mathf.clamp(body.getPrefHeight() + 4f, 120f, 300f);
        add(new BarScroll(body)).growX().height(bodyH).row();

        // ---------------------------------------------------------------- 别名
        shownAlias = slot.alias() == null ? "" : slot.alias();

        Table arow = new Table();
        arow.add(BarTheme.dimText("别名")).width(44f).left();
        alias = new BarTextField(shownAlias, "留空则用蓝图原名", BarTextField.MAX_LENGTH);
        arow.add(alias).growX().height(24f);
        add(arow).growX().height(26f).padTop(2f).row();

        // ---------------------------------------------------------------- 按钮
        Table btns = new Table();
        btns.defaults().growX().height(24f).pad(2f);
        btns.add(new BarButton("取消绑定").onClick(() -> {
            slot.unbind();
            close();
        }));
        btns.add(new BarButton("关闭").accent(true).onClick(this::close));
        add(btns).growX().padTop(2f).row();

        // 高度直接取表格自己的 pref：上面各行的 pad 都是手写的，别用常量去猜
        setSize(WIDTH, getPrefHeight());

        // 构造完就挂监听：窗口还没进 hudGroup，这之前不可能有事件
        if(Core.scene != null) Core.scene.addCaptureListener(outside);
    }

    @Override
    protected BarConfig.WindowState state(){
        return state;
    }

    /** 默认贴槽位窗右侧，越界翻左侧。 */
    @Override
    protected void defaultSpawn(){
        float maxX = Math.max(0f, Core.graphics.getWidth() - width);
        float maxY = Math.max(0f, Core.graphics.getHeight() - height);

        float gx = slot.x + slot.getWidth() + 8f;
        if(gx > maxX) gx = Math.max(0f, slot.x - width - 8f);
        float gy = slot.y + slot.getHeight() - height;

        setPosition(Mathf.round(Mathf.clamp(gx, 0f, maxX)), Mathf.round(Mathf.clamp(gy, 0f, maxY)));
    }

    /** 输入框内容变了就同步给槽位：槽位按钮上的文字是别名优先的。 */
    @Override
    public void act(float delta){
        super.act(delta);

        String cur = alias.text();
        if(!cur.equals(shownAlias)){
            shownAlias = cur;
            slot.alias(cur.isEmpty() ? null : cur);
        }
    }

    public void close(){
        remove();
    }

    @Override
    public boolean remove(){
        // 一定要摘掉 Scene 上的监听，否则窗口关掉后点哪儿都还会触发 close()
        if(Core.scene != null){
            Core.scene.removeCaptureListener(outside);
            if(Core.scene.getKeyboardFocus() == alias.field) Core.scene.setKeyboardFocus(null);
        }
        return super.remove();
    }
}
