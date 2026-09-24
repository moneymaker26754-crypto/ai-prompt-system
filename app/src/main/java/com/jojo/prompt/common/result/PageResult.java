package com.jojo.prompt.common.result;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jojo.prompt.common.constant.PromptVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页结果")
public class PageResult<T> {

    @Schema(description = "当前页码")
    private Long current;

    @Schema(description = "每页大小")
    private Long size;

    @Schema(description = "总记录数")
    private Long total;

    @Schema(description = "总页数")
    private Long pages;

    @Schema(description = "数据列表")
    private List<T> records;
    //MybatisPlus的page对象转换
    public  static <T> PageResult<T> of(Page<T> page) {
        return new PageResult<>(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getPages(),
                page.getRecords()
        );
    }
    //自定义构建，用于手动分页
    public static <T> PageResult<T> of(Long current, Long size, Long total, List<T> records) {
        long pages = (total + size -1) / size;
        return new PageResult<>(current, size, total, pages, records);
    }
}
