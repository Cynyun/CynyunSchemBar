package code;

import arc.Core;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.util.Time;
import mindustry.game.Schematic;

/**
 * 序列里的一格蓝图按钮。文字只用用户写的标题（未绑定 → “未绑定”，绑定没起名 → “未命名”）。
 *
 * 交互分两态，由面板的编辑开关决定：
 *   普通态  点击 = 用这张蓝图；悬停 0.3 秒 / 长按 0.5 秒 = 弹详情浮层，移开或松手收起
 *   编辑态  点击 = 弹编辑菜单（改标题 / 重新选择 / 上移 / 下移 / 移除）
 *
 * 详情浮层由面板统一管：这里只负责“什么时候该开”，位置和生命周期都在 BarPanel。
 * 悬停判定用 Button 自带的 isOver()（ClickListener 里的字段，O(1)），不做每帧场景树 hit。
 * 不用 enter/exit 累时间：ScrollPane 里子元素的重排/裁剪会让 enter/exit 顺序不可靠，
 * 表现为“要抖一下鼠标才出浮层”。
 */
public class BarEntryButton extends BarButton{

    /** 鼠标停多久出详情。比原版卡片快一点，按钮小、扫过去更频繁。 */
    private static final float HOVER_DELAY = 0.30f;
    /** 手指按住多久算长按。 */
    private static final float LONG_PRESS = 0.5f;
    /** 长按之后这么久内的点击都当“看详情”而不是“用蓝图”。 */
    private static final float LONG_GUARD = 0.8f;

    private final BarPanel owner;
    private final BarSequence sequence;
    private final BarEntry entry;
    /** 解析结果，null = 未绑定或库里已经找不到。 */
    private final Schematic schematic;

    private boolean touchOver;
    /** 是否已经让面板弹出了自己的浮层（防止每帧重复创建）。 */
    private boolean shown;
    private float hoverTime, pressTime;
    private float longAt = -100f;

    public BarEntryButton(BarPanel owner, BarSequence sequence, BarEntry entry){
        super(entry.displayTitle());
        this.owner = owner;
        this.sequence = sequence;
        this.entry = entry;
        this.schematic = BarLibrary.find(entry.file);
        entry.schematic = schematic;

        ellipsis(true);
        onClick(this::clicked);

        // 只跟踪“手指按在按钮上”这一件事：鼠标悬停改成每帧读 isOver()，见 act()
        addListener(new InputListener(){
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Element fromActor){
                if(pointer != -1) touchOver = true;
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Element toActor){
                if(pointer != -1 && !inside(toActor)) touchOver = false;
            }
        });
    }

    /** target 是自己或自己的后代。移到子元素上不算离开。 */
    private boolean inside(Element target){
        for(Element e = target; e != null; e = e.parent){
            if(e == this) return true;
        }
        return false;
    }

    @Override
    public void act(float delta){
        super.act(delta);

        if(owner.isEditing()){
            hoverTime = 0f;
            release();
            return;
        }

        // 鼠标悬停：停够时间才弹，扫过去不弹。
        // 指针在按钮上 → 先告诉面板“我现在指着谁”，面板据此保留浮层、避免格子之间来回闪。
        if(isOver() && schematic != null){
            owner.hovering(this);
            hoverTime += delta;
            if(!shown && hoverTime >= HOVER_DELAY){
                shown = true;
                owner.showPreview(this);
            }
        }else{
            hoverTime = 0f;
            release();
        }

        // 触摸长按：不依赖按钮内部的 ClickListener（它默认会吃掉事件），
        // 只看“手指还在按着”和 enter 时记下的 touchOver。
        if(touchOver && Core.input.isTouched() && schematic != null){
            pressTime += delta;
            if(pressTime >= LONG_PRESS){
                pressTime = 0f;
                longAt = Time.time;
                shown = true;
                owner.showPreview(this);
            }
        }else{
            pressTime = 0f;
        }
    }

    /** 指针离开：浮层不立刻收，交给面板判断（可能正挪到浮层上）。 */
    private void release(){
        if(shown){
            shown = false;
            owner.releasePreview(this);
        }
    }

    private void clicked(){
        // 长按松手后浏览器/引擎还会补一个 click，这一下只当“刚才在看详情”
        if(Time.time - longAt < LONG_GUARD) return;

        owner.entryClicked(sequence, entry, this);
    }

    public BarEntry entry(){
        return entry;
    }

    public BarSequence sequence(){
        return sequence;
    }

    public Schematic schematic(){
        return schematic;
    }
}