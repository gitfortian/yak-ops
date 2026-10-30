package io.yak.ops.common.bean.vo.tag;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** One dimension of the dictionary with every term it holds. */
@Getter
@Setter
public class TagKeyVO {

    /** Key identity. */
    private String id;

    /** Dimension name. */
    private String name;

    /** Terms of this key in name order. */
    private List<TagValueVO> values = new ArrayList<>();
}
