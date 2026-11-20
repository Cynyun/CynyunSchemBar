package test;

import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import mindustry.game.Schematic;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SchematicsDialog;

/**
 * 卡片详情浮层：悬停即出，指针离开就收。
 *
 * 不是 BarWindow：它跟着卡片走、不能拖动、也不该抢焦点。只能看，不能改任何东西
 * （改名/绑定都在槽位窗那边），所以点它自己不做任何事。
 *
 * 宽度是写死的：所有行都必须按 {@link #WIDTH} 排得下。arc 的 Label 最小宽度等于文字宽度
 * （Label.java:231-233），一行塞不下的话不是换行、而是整张表往右溢出——这就是上一版
 * “文字跑到浮窗外边”的原因。所以消耗表按固定列宽切，电量单独成行。
 */
public class BarSchematicInfoPopup extends Table{

    public static final float WIDTH = 300f;
    /** 左右内边距。 */
    private static final float MARGIN = 8f;
    /** 内容区宽度。 */
    private static final float INNER = WIDTH - MARGIN * 2f;

    private final Schematic schem;
    /** 指针是否停在浮层上。由 enter/exit 维护：Picker 每帧问它来决定要不要收起浮层。 */
    private boolean over;

    public BarSchematicInfoPopup(Schematic schem){
        this.schem = schem;

        touchable = Touchable.enabled;
        margin(MARGIN);
        defaults().pad(1f);
        top();

        add(new SchematicsDialog.SchematicImage(schem)).growX().height(150f).padBottom(4f).row();

        add(BarTheme.text(schem.name())).growX().left().ellipsis(true).row();
        add(BarTheme.dimText(schem.width + " × " + schem.height + "    方块 " + schem.tiles.size))
            .growX().left().row();

        // ---------------------------------------------------------------- 消耗
        ItemStack[] reqs = schem.requirements().toArray();
        // 原版 SchematicsDialog.java:808 就是这么算的：内部值按 tick 计，乘 60 换成每秒
        float cons = schem.powerConsumption() * 60f, prod = schem.powerProduction() * 60f;
        boolean hasPower = cons > 0.001f || prod > 0.001f;

        if(reqs.length > 0 || hasPower){
            add(BarTheme.accentText("· 消耗")).growX().left().padTop(4f).row();
        }

        if(reqs.length > 0){
            Table cost = new Table();
            cost.defaults().pad(0f);
            float cellW = (INNER - 6f) / 4f;
            int i = 0;
            for(ItemStack st : reqs){
                Table cell = new Table();
                cell.add(new Image(st.item.uiIcon)).size(14f).padRight(3f);
                Label amount = new Label(String.valueOf(st.amount), Styles.defaultLabel);
                amount.setEllipsis(true);
                // 数量列给固定宽度，五位数的数量也压得住，不会把整行顶宽
                cell.add(amount).width(cellW - 19f).left();
                cost.add(cell).width(cellW).padBottom(2f);
                if(++i % 4 == 0) cost.row();
            }
            add(cost).growX().left().row();
        }

        if(hasPower){
            Table power = new Table();
            power.defaults().pad(0f);
            if(prod > 0.001f){
                power.image(Icon.powerSmall).color(Pal.powerLight).size(13f).padRight(3f);
                power.add(powerText("+" + Strings.autoFixed(prod, 2) + "/s", Pal.powerLight)).left();
            }
            if(cons > 0.001f){
                if(prod > 0.001f) power.add().width(14f);
                power.image(Icon.powerSmall).color(Pal.remove).size(13f).padRight(3f);
                power.add(powerText("-" + Strings.autoFixed(cons, 2) + "/s", Pal.remove)).left();
            }
            add(power).growX().left().padTop(1f).row();
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
    }

    /** 电量文字要跟原版一样带颜色，所以这里不用 BarTheme 的会自换色的 Label。 */
    private static Label powerText(String text, arc.graphics.Color color){
        Label label = new Label(text, Styles.defaultLabel);
        label.setColor(color);
        return label;
    }

    public Schematic schematic(){
        return schem;
    }

    /** 指针是否停在浮层上（含子元素）。 */
    public boolean hovered(){
        return over;
    }

    public void hide(){
        remove();
    }

    @Override
    public void draw(){
        float a = parentAlpha;
        BarTheme.fill(x, y, width, height, 0.62f * a);
        super.draw();
        BarTheme.frame(x, y, width + 1f, height + 1f, 0.55f * a);
    }
}
