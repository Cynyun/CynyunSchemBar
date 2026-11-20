package code;

import arc.struct.Seq;

/**
 * 蓝图序列（设计文档里的 Sequence）：一列同类蓝图，列头是名字，下面竖直滚动。
 * 名字可以改，{@link #id} 是稳定标识，改名不动它。
 */
public class BarSequence{

    public String id;
    public String name;
    public final Seq<BarEntry> entries = new Seq<>();

    public BarSequence(String id, String name){
        this.id = id;
        this.name = name;
    }
}
