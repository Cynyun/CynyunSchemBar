package test;

import arc.Core;
import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Table;
import mindustry.game.Schematic;
import mindustry.gen.Icon;
import mindustry.type.ItemStack;
import mindustry.ui.dialogs.SchematicsDialog;

/**
 * 卡片详情浮层：悬停 0.5 秒弹出，或点卡片右上角的 i（手机上没有悬停，i 是唯一入口）。
 *
 * 不是 BarWindow：它跟着卡片走、不能拖动、也不该抢焦点。只能看，不能改任何东西
 * （改名/绑定都在槽位窗那边），所以点它自己不做任何事。
 *
 * 悬停版（pinned=false）在指针离开卡片后由 Picker 计时关掉；i 打开的版本（pinned=true）
 * 挂一个 Scene 级 capture 监听，点外面或 Esc 关闭。
 */
public class BarSchematicInfoPopup extends Table{

    public static final float WIDTH = 268f;

    private final boolean pinned;
    /** 指针是否停在浮层上。由 enter/exit 维护：Picker 每帧问它来决定要不要收起浮层。 */
    private boolean over;

    private final InputListener outside = new InputListener(){
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
            for(Element e = event.targetActor; e != null; e = e.parent){
                if(e == BarSchematicInfoPopup.this) return false;
            }
            hide();
            return true; // 吃掉：否则关浮层的同时点到了底下的东西
        }

        @Override
        public boolean keyDown(InputEvent event, KeyCode key){
            if(key == KeyCode.escape || key == KeyCode.back){
                hide();
                return true;
            }
            return false;
        }
    };

    public BarSchematicInfoPopup(Schematic schem, boolean pinned){
        this.pinned = pinned;

        touchable = Touchable.enabled;
        margin(8f);
        defaults().pad(1f);
        top();

        add(new SchematicsDialog.SchematicImage(schem)).growX().height(150f).padBottom(4f).row();

        add(BarTheme.text(schem.name())).growX().left().ellipsis(true).row();
        add(BarTheme.dimText(schem.width + " × " + schem.height + "    方块 " + schem.tiles.size))
            .growX().left().row();

        // ---------------------------------------------------------------- 消耗
        ItemStack[] reqs = schem.requirements().toArray();
        float cons = schem.powerConsumption(), prod = schem.powerProduction();
        boolean hasPower = cons > 0.001f || prod > 0.001f;

        if(reqs.length > 0 || hasPower){
            add(BarTheme.accentText("· 消耗")).growX().left().padTop(4f).row();
        }

        if(reqs.length > 0){
            Table cost = new Table();
            cost.defaults().pad(1f);
            int i = 0;
            for(ItemStack st : reqs){
                cost.image(st.item.uiIcon).size(14f);
                cost.add(BarTheme.text(String.valueOf(st.amount))).padRight(8f);
                if(++i % 5 == 0) cost.row();
            }
            add(cost).growX().left().row();
        }

        if(hasPower){
            Table power = new Table();
            power.image(Icon.powerSmall).size(13f).padRight(3f);
            String p = "";
            if(cons > 0.001f) p += String.format("%.1f/s 消耗", cons);
            if(prod > 0.001f) p += (p.isEmpty() ? "" : "    ") + String.format("%.1f/s 产出", prod);
            power.add(BarTheme.text(p)).left();
            add(power).growX().left().row();
        }

        // ---------------------------------------------------------------- 标签
        if(schem.labels != null && !schem.labels.isEmpty()){
            StringBuilder sb = new StringBuilder();
            for(int i = 0; i < schem.labels.size; i++){
                if(i > 0) sb.append(" · ");
                sb.append(schem.labels.get(i));
            }
            add(BarTheme.dimText(sb.toString())).growX().left().padTop(4f).ellipsis(true).row();
        }

        String desc = schem.description();
        if(desc != null && !desc.isEmpty()){
            if(desc.length() > 160) desc = desc.substring(0, 160) + "…";
            add(BarTheme.dimText(desc)).growX().left().padTop(4f).wrap().row();
        }

        addListener(new InputListener(){
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Element fromActor){
                if(pointer == -1) over = true;
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Element toActor){
                if(pointer == -1) over = false;
            }
        });

        setSize(WIDTH, getPrefHeight());

        if(pinned && Core.scene != null) Core.scene.addCaptureListener(outside);
    }

    public boolean pinned(){
        return pinned;
    }

    /** 指针是否停在浮层上（含子元素）。 */
    public boolean hovered(){
        return over;
    }

    public void hide(){
        remove();
    }

    @Override
    public boolean remove(){
        if(pinned && Core.scene != null) Core.scene.removeCaptureListener(outside);
        return super.remove();
    }

    @Override
    public void draw(){
        float a = parentAlpha;
        BarTheme.fill(x, y, width, height, 0.62f * a);
        super.draw();
        BarTheme.frame(x, y, width + 1f, height + 1f, 0.55f * a);
    }
}
