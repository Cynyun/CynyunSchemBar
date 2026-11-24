package code;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;

/**
 * 取色器小浮窗：16 个预设 + R/G/B 三条 0-255 滑条，只改 RGB，不碰 alpha。
 *
 * 只有“面板不透明度”能改透明度是有意的：颜色本身没有 alpha 可调，
 * 就不可能出现“把界面全调透明之后再也找不到设置窗”的死锁。
 *
 * 行为：拖动滑条/点预设立刻写回 BarTheme 的 Color 实例（实时预览）；
 * 取消时用打开前的快照整体回滚；确定只是关窗（颜色已经是最终值）。
 * 位置每次贴着被点的字段出现，不持久化——字段挪了，窗口也跟着走。
 */
public class BarColorPicker extends BarWindow{

    /** 逻辑单位（乘 Scl 后才是像素）。 */
    public static final float WIDTH = 236f, HEIGHT = 216f;
    /** 行高 / 色块边长 / 间距（逻辑单位）。 */
    private static final float ROW_H = 18f, SWATCH = 18f, PAD = 2f, GAP = 8f;

    private static final String[] PRESETS = {
        "ffffff", "c8d0d4", "9fb0b6", "6b7378", "000000", "7fd7e8", "4aa6d8", "3f6ee0",
        "8a6cf0", "d573d8", "ef5f6b", "f08f4b", "f2c94c", "6fcf6f", "3fae8f", "b0793a"
    };

    private final BarColorField field;
    private final Color snapshot = new Color();
    private final BarConfig.WindowState dummy = new BarConfig.WindowState();
    private final BarSlider[] sliders = new BarSlider[3];
    private final Label[] values = new Label[3];
    private final Label hexLabel;
    private int shown = Integer.MIN_VALUE;

    public BarColorPicker(BarColorField field){
        super(BarTheme.u(WIDTH), BarTheme.u(HEIGHT));
        this.field = field;
        snapshot.set(field.color);

        margin(BarTheme.u(GAP));
        defaults().pad(BarTheme.u(PAD));
        top();

        Table head = new Table();
        head.add(BarTheme.accentText("颜色 · " + field.title)).growX().left();
        head.add(new BarCloseButton().onClick(this::confirm)).size(BarTheme.u(ROW_H));
        add(head).growX().height(BarTheme.u(ROW_H)).row();

        add(new BarDivider()).growX().height(1f)
            .padTop(BarTheme.u(3f)).padBottom(BarTheme.u(4f)).row();

        // 2 行 x 8 个预设色块
        Table presets = new Table();
        presets.defaults().size(BarTheme.u(SWATCH)).pad(BarTheme.u(PAD));
        for(int i = 0; i < PRESETS.length; i++){
            final Color c = Color.valueOf(PRESETS[i]);
            BarSwatch sw = new BarSwatch(c);
            sw.touchable = Touchable.enabled;
            sw.clicked(() -> applyPreset(c));
            presets.add(sw);
            if(i % 8 == 7) presets.row();
        }
        add(presets).row();

        Table cur = new Table();
        cur.add(new BarSwatch(field.color)).size(BarTheme.u(SWATCH)).padRight(BarTheme.u(6f));
        hexLabel = BarTheme.text("");
        hexLabel.touchable = Touchable.disabled;
        cur.add(hexLabel).left().growX();
        add(cur).growX().height(BarTheme.u(ROW_H)).padTop(BarTheme.u(3f)).row();

        for(int i = 0; i < 3; i++){
            final int ch = i;
            String name = i == 0 ? "R" : i == 1 ? "G" : "B";

            BarSlider s = new BarSlider(0f, 255f, 0f);
            s.changed = () -> onSlider(ch);
            sliders[i] = s;

            Label value = BarTheme.dimText("0");
            value.touchable = Touchable.disabled;
            values[i] = value;

            Table row = new Table();
            row.add(BarTheme.dimText(name)).width(BarTheme.u(16f)).left();
            row.add(s).growX().height(BarTheme.u(16f))
                .padLeft(BarTheme.u(PAD)).padRight(BarTheme.u(6f));
            row.add(value).width(BarTheme.u(30f)).right();
            add(row).growX().height(BarTheme.u(ROW_H)).row();
        }

        Table btns = new Table();
        btns.defaults().growX().height(BarTheme.u(22f)).pad(BarTheme.u(PAD));
        btns.add(new BarButton("取消").onClick(this::cancel));
        btns.add(new BarButton("确定").accent(true).onClick(this::confirm));
        add(btns).growX().padTop(BarTheme.u(4f)).row();

        // 高度取表格自己的 pref（同设置窗：手写常量会漏掉每行 pad）
        setSize(BarTheme.u(WIDTH), getPrefHeight());

        syncSliders();
    }

    @Override
    protected BarConfig.WindowState state(){
        return dummy;
    }

    @Override
    protected void defaultSpawn(){
        float sx = 0f, sy = 0f;

        if(field != null && field.parent != null){
            Vec2 p = field.localToStageCoordinates(new Vec2());
            // 默认贴在字段右侧 8px，顶部对齐；右边放不下就翻到左侧
            float gap = BarTheme.u(GAP);
            sx = p.x + field.getWidth() + gap;
            sy = p.y + field.getHeight() - height;
            if(sx + width > Core.graphics.getWidth()) sx = p.x - width - gap;
        }

        float maxX = Math.max(0f, Core.graphics.getWidth() - width);
        float maxY = Math.max(0f, Core.graphics.getHeight() - height);
        setPosition(Mathf.round(Mathf.clamp(sx, 0f, maxX)), Mathf.round(Mathf.clamp(sy, 0f, maxY)));
    }

    /** 取色器是临时窗：开着的时候常驻面板不能盖住它。 */
    @Override
    protected boolean transientWindow(){
        return true;
    }

    @Override
    public void act(float delta){
        super.act(delta);

        int rgba = field.color.rgba8888();
        if(rgba == shown) return;
        shown = rgba;
        hexLabel.setText(String.format("#%02X%02X%02X",
            Math.round(field.color.r * 255f),
            Math.round(field.color.g * 255f),
            Math.round(field.color.b * 255f)));
    }

    private void onSlider(int channel){
        float r = channel == 0 ? sliders[0].value() / 255f : field.color.r;
        float g = channel == 1 ? sliders[1].value() / 255f : field.color.g;
        float b = channel == 2 ? sliders[2].value() / 255f : field.color.b;
        field.color.set(r, g, b, field.color.a);
        values[channel].setText(Integer.toString(Mathf.round(sliders[channel].value())));
    }

    private void applyPreset(Color c){
        // 预设只改 RGB，alpha 不是这里管的
        field.color.set(c.r, c.g, c.b, field.color.a);
        syncSliders();
    }

    /** 把当前颜色写回三条滑条与数值文字，不触发回调。 */
    private void syncSliders(){
        sliders[0].setValueSilent(Mathf.round(field.color.r * 255f));
        sliders[1].setValueSilent(Mathf.round(field.color.g * 255f));
        sliders[2].setValueSilent(Mathf.round(field.color.b * 255f));
        for(int i = 0; i < 3; i++){
            values[i].setText(Integer.toString(Mathf.round(sliders[i].value())));
        }
    }

    /** 颜色已经是实时应用的，确定只负责关窗。 */
    private void confirm(){
        Start.closePicker();
    }

    /** 回滚到打开前的快照，再关窗。 */
    private void cancel(){
        field.color.set(snapshot);
        Start.closePicker();
    }
}
