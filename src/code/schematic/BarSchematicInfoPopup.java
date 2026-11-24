package code;

import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Table;
import mindustry.game.Schematic;

/**
 * 卡片详情浮层：悬停即出，指针离开就收。
 *
 * 不是 BarWindow：它跟着卡片走、不能拖动、也不该抢焦点。只能看，不能改任何东西
 * （改名/绑定都在槽位窗那边），所以点它自己不做任何事。
 *
 * 正文全部交给 {@link BarSchematicInfo}——与详情窗共用同一份实现，这里只负责外框、
 * 悬停状态和尺寸。
 */
public class BarSchematicInfoPopup extends Table{

    private final Schematic schem;
    /** 指针是否停在浮层上。由 enter/exit 维护：Picker 每帧问它来决定要不要收起浮层。 */
    private boolean over;

    public BarSchematicInfoPopup(Schematic schem){
        this.schem = schem;

        touchable = Touchable.enabled;
        float w = BarMetrics.popupW(), margin = BarMetrics.popupMargin();
        margin(margin);
        top();

        add(new BarSchematicInfo(schem, w - margin * 2f, BarSchematicInfo.popupDescChars())).growX();

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

        setSize(w, getPrefHeight());
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
