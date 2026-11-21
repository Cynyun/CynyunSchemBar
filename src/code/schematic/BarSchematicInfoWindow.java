package code;

import arc.Core;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Table;
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

    /** 逻辑单位（乘 Scl 后才是像素）；HEIGHT 只是初值，最终高度由内容 pref 决定。 */
    public static final float WIDTH = 380f, HEIGHT = 400f;
    /** 缩放下限（逻辑单位）：正文在滚动区里，可以压得比内容矮。 */
    private static final float MIN_W = 280f, MIN_H = 180f;
    /** 标题行高、按钮行高、别名行高（逻辑单位）。 */
    private static final float HEAD_H = 20f, BTN_H = 24f, ALIAS_H = 26f, PAD = 2f, GAP = 8f;

    private final BarSlot slot;
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

    public BarSchematicInfoWindow(BarSlot slot){
        // super 必须在第一句，所以尺寸在这里一次性算好：宽度固定，高度先给个估值，构造完再按 pref 收紧
        super(BarTheme.u(WIDTH), BarTheme.u(HEIGHT));
        this.slot = slot;
        this.schem = slot.bound();

        margin(BarTheme.u(GAP));
        defaults().pad(BarTheme.u(PAD));
        top();

        // ---------------------------------------------------------------- 标题
        Table head = new Table();
        head.add(BarTheme.accentText("蓝 图 信 息")).growX().left();
        head.add(new BarCloseButton().onClick(this::close)).size(BarTheme.u(18f));
        add(head).growX().height(BarTheme.u(HEAD_H)).row();

        add(new BarDivider()).growX().height(1f)
            .padTop(BarTheme.u(PAD)).padBottom(BarTheme.u(PAD + 1f)).row();

        // ---------------------------------------------------------------- 内容
        // 正文直接用悬停浮层那一份实现（BarSchematicInfo）：预览、消耗、电量（含 ×60）、
        // 标签、描述都与浮层逐像素一致，不再各写一套。描述传 0 = 不截断，交给下面的滚动区
        // 正文宽度 = 窗口内宽（减去 margin）再让出滚动条；这个值只用于算消耗格的列宽
        float inner = BarTheme.u(WIDTH) - BarTheme.u(GAP) * 2f - BarScroll.barWidth();
        BarSchematicInfo body = new BarSchematicInfo(schem, inner, 0);
        // 预览图是固定高的，消耗/描述行数不定：滚动区高度按内容定，窗口再跟着收缩
        float bodyH = Mathf.clamp(body.getPrefHeight() + BarTheme.u(4f),
            BarTheme.u(120f), Math.min(BarTheme.u(320f), Core.graphics.getHeight() * 0.6f));
        add(new BarScroll(body)).growX().height(bodyH).row();

        // ---------------------------------------------------------------- 别名
        shownAlias = slot.alias() == null ? "" : slot.alias();

        Table arow = new Table();
        arow.add(BarTheme.dimText("别名")).width(BarTheme.u(44f)).left();
        alias = new BarTextField(shownAlias, "留空则用蓝图原名", BarTextField.MAX_LENGTH);
        arow.add(alias).growX().height(BarTheme.u(BTN_H));
        add(arow).growX().height(BarTheme.u(ALIAS_H)).padTop(BarTheme.u(PAD)).row();

        // ---------------------------------------------------------------- 按钮
        Table btns = new Table();
        btns.defaults().growX().height(BarTheme.u(BTN_H)).pad(BarTheme.u(PAD));
        btns.add(new BarButton("取消绑定").onClick(() -> {
            slot.unbind();
            close();
        }));
        btns.add(new BarButton("关闭").accent(true).onClick(this::close));
        add(btns).growX().padTop(BarTheme.u(PAD)).row();

        // 高度直接取表格自己的 pref：上面各行的 pad 都是手写的，别用常量去猜
        float w = BarTheme.u(WIDTH);
        setSize(w, Math.min(getPrefHeight(), Core.graphics.getHeight()));

        // 也给个右下角手柄：正文在滚动区里，压矮一点不至于坏掉
        enableResize(Math.min(BarTheme.u(MIN_W), w), Math.min(BarTheme.u(MIN_H), getHeight()));

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
        float gap = BarTheme.u(GAP);

        // 槽位是面板里的子元素，x/y 是面板内坐标：换算到 stage 才能和本窗口的屏幕坐标对齐
        Vec2 p = slot.localToStageCoordinates(new Vec2(0f, 0f));
        float gx = p.x + slot.getWidth() + gap;
        if(gx > maxX) gx = Math.max(0f, p.x - width - gap);
        float gy = p.y + slot.getHeight() - height;

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
