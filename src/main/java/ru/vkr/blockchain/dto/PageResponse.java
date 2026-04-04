package ru.vkr.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int number;      // текущая страница (0-based)
    private int size;        // размер страницы

    @JsonProperty("first")
    public boolean isFirst() {
        return number == 0;
    }

    @JsonProperty("last")
    public boolean isLast() {
        return number >= totalPages - 1;
    }
}
