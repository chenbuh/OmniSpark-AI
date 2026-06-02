package com.example.aihub.infrastructure.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssetStatsVO {
    private long total;
    private long imageCount;
    private long videoCount;
    private long referenceCount;
    private long favoriteCount;
}
