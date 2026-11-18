package test;

import arc.Core;
import arc.math.Mathf;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.game.Schematic;
import mindustry.ui.dialogs.SchematicsDialog;

/**
 * 蓝图选择窗：把 {@code Vars.schematics.all()} 摊成一列，点哪张绑哪张。
 *
 * 不复用原版蓝图对话框，是因为原版没有对外暴露“用户选了哪一张”的回调
 * （SchematicsDialog 的点击分支直接 useSchematic() 然后 hide()），
 * 挂 capture 监听反查按钮里的 Label 又会在重名时失效。自己列一遍最确定。
 *
 * 预览图复用原版的 {@link SchematicsDialog.SchematicImage}，所以预览生成、
 * 刷新图标、边框高亮这些行为都和原版一致。
 */
public class BarSchematicPicker extends BarWindow{

    public static final float WIDTH = 300f, HEIGHT = 364f;

    private final BarSlotWindow slot;
    private final BarConfig.WindowState state = new BarConfig.WindowState();
    private final Table list = new Table();

    /** 上次重建时库里的蓝图数量：数量变了就重建（原版界面可能刚加了新蓝图）。 */
    private int shownCount = -1;

    public BarSchematicPicker(BarSlotWindow slot){
        super(WIDTH, HEIGHT);
        this.slot = slot;

        margin(8f);
        defaults().pad(2f);
        top();

        Table head = new Table();
        head.add(BarTheme.accentText("选 择 蓝 图")).growX().left();
        head.add(new BarCloseButton().onClick(this::close)).size(18f);
        add(head).growX().height(20f).row();

        add(new BarDivider()).growX().height(1f).padTop(2f).padBottom(3f).row();

        list.top();
        list.defaults().padBottom(3f);
        BarScroll scroll = new BarScroll(list);
        add(scroll).growX().height(280f).row();

        Table btns = new Table();
        btns.defaults().growX().height(24f).pad(2f);
        btns.add(new BarButton("打开原版蓝图界面").onClick(this::openVanilla));
        add(btns).growX().padTop(2f).row();

        rebuild();

        // 高度取各行的 pref 之和，免得手算 pad 少算几像素、按钮被挤到框外
        setSize(WIDTH, getPrefHeight());
    }

    @Override
    protected BarConfig.WindowState state(){
        return state;
    }

    /** 默认贴在槽位窗右侧，越界就翻到左侧。 */
    @Override
    protected void defaultSpawn(){
        float maxX = Math.max(0f, Core.graphics.getWidth() - width);
        float maxY = Math.max(0f, Core.graphics.getHeight() - height);

        float gx = slot.x + slot.getWidth() + 8f;
        if(gx > maxX) gx = Math.max(0f, slot.x - width - 8f);
        float gy = slot.y + slot.getHeight() - height;

        setPosition(Mathf.round(Mathf.clamp(gx, 0f, maxX)), Mathf.round(Mathf.clamp(gy, 0f, maxY)));
    }

    // ------------------------------------------------------------------ 列表

    private int count(){
        return Vars.schematics == null ? 0 : Vars.schematics.all().size;
    }

    private void rebuild(){
        list.clearChildren();
        shownCount = count();

        if(shownCount == 0){
            list.add(BarTheme.dimText("蓝图库是空的")).growX().left().pad(6f).row();
            list.add(BarTheme.dimText("先用原版蓝图界面做一个")).growX().left().pad(0f, 6f, 6f, 6f).row();
            return;
        }

        for(Schematic s : Vars.schematics.all()){
            list.add(new Row(s)).growX().height(52f).row();
        }
    }

    private void openVanilla(){
        if(Vars.ui != null && Vars.ui.schematics != null) Vars.ui.schematics.show();
    }

    private void close(){
        remove();
    }

    @Override
    public void act(float delta){
        super.act(delta);

        // 拖动中不重建：clearChildren() 会把正在被按住的按钮一起拆掉
        if(!isDragging() && count() != shownCount) rebuild();
    }

    /** 列表项：预览图 + 名字，整行可点。 */
    private class Row extends Button{

        Row(Schematic s){
            // 空样式：底图和线全部由 draw() 画，和 BarButton 一个思路
            super(new ButtonStyle());
            touchable = Touchable.enabled;

            Table t = new Table();
            t.add(new SchematicsDialog.SchematicImage(s)).size(42f).padRight(6f);
            t.add(BarTheme.text(s.name())).growX().left().padRight(4f).ellipsis(true);
            add(t).growX().pad(3f, 5f, 3f, 5f);

            changed(() -> {
                if(!isDisabled()) slot.picked(s);
            });
        }

        @Override
        public void draw(){
            float a = parentAlpha;
            if(isOver()) BarTheme.fill(x, y, width, height, (isPressed() ? 0.26f : 0.16f) * a);
            super.draw();
            BarTheme.brackets(x, y, width, height, isOver() ? 0.80f * a : 0.28f * a);
        }
    }
}
