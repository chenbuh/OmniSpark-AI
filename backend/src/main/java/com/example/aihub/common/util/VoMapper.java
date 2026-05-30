package com.example.aihub.common.util;

import cn.hutool.core.bean.BeanUtil;

import java.util.List;
import java.util.stream.Collectors;

public final class VoMapper {
    private VoMapper() {
    }

    public static <T> T copy(Object source, Class<T> targetType) {
        return BeanUtil.toBean(source, targetType);
    }

    public static <S, T> List<T> copyList(List<S> source, Class<T> targetType) {
        return source.stream().map(item -> copy(item, targetType)).collect(Collectors.toList());
    }
}
