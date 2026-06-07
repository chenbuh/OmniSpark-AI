package com.example.aihub.common.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AuditActionCatalog {
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    public List<String> listActions() {
        return requestMappingHandlerMapping.getHandlerMethods().values().stream()
                .map(this::resolveAction)
                .filter(action -> action != null && !action.isBlank())
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    private String resolveAction(HandlerMethod handlerMethod) {
        return AuditActionSupport.resolveAction(handlerMethod.getBeanType(), handlerMethod.getMethod());
    }
}
