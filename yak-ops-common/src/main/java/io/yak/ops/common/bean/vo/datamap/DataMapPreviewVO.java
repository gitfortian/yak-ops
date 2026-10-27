package io.yak.ops.common.bean.vo.datamap;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Bounded sample of an asset's rows. */
@Getter
@Setter
public class DataMapPreviewVO {

    /** Projected columns in result-set order. */
    private List<PreviewColumn> columns = new ArrayList<>();

    /** Sampled rows; each row is aligned with {@link #columns}. */
    private List<List<String>> rows = new ArrayList<>();

    /** ROWS when the row cap was reached, SIZE when the response budget stopped the sample. */
    private String truncated;

    /** Fixed server-side warning about personal data. */
    private String notice;

    /** One projected column of the preview. */
    @Getter
    public static class PreviewColumn {

        /** Column name. */
        private final String name;

        /** Engine-reported native type name. */
        private final String typeName;

        public PreviewColumn(String name, String typeName) {
            this.name = name;
            this.typeName = typeName;
        }
    }
}
