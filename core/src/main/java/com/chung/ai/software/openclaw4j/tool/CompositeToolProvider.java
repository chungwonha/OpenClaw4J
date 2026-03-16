package com.chung.ai.software.openclaw4j.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.*;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom;

@Slf4j
public class CompositeToolProvider implements ToolProvider {

    private final List<ToolProvider> toolProviders;
    private final Map<ToolSpecification, ToolExecutor> staticTools = new HashMap<>();

    public CompositeToolProvider(List<ToolProvider> toolProviders) {
        this.toolProviders = new ArrayList<>(toolProviders);
    }

    public void addStaticTools(Object objectWithTools) {
        for (Method method : objectWithTools.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                ToolSpecification toolSpecification = toolSpecificationFrom(method);
                ToolExecutor toolExecutor = new DefaultToolExecutor(objectWithTools, method);
                staticTools.put(toolSpecification, toolExecutor);
                log.info("Added static tool: {}", toolSpecification.name());
            }
        }
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        ToolProviderResult.Builder builder = ToolProviderResult.builder();

        // Add static tools
        staticTools.forEach(builder::add);

        // Add tools from other providers
        for (ToolProvider provider : toolProviders) {
            try {
                ToolProviderResult result = provider.provideTools(request);
                if (result != null && result.tools() != null) {
                    builder.addAll(result.tools());
                }
            } catch (Exception e) {
                log.error("Error providing tools from provider: {}", provider.getClass().getName(), e);
            }
        }

        return builder.build();
    }
}
