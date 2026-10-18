package cat.albirar.dynmod.demo.response;

import java.util.List;

import lombok.Data;

@Data
public class PagedResponse<T> {
    private List<T> content;
    private long totalElements;
}
