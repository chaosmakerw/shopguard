package com.codesprint.lesson0831.agent.toolcalling;

import java.util.List;

public  record ModelTurn (
        String text,
        List<ToolCall> toolCalls
){
    public ModelTurn{
        if(toolCalls == null){
            throw new IllegalArgumentException("工具调用列表不能为空");
        }
        toolCalls = List.copyOf(toolCalls);
        if(toolCalls.isEmpty()&&(text==null||text.isBlank())){
            throw new IllegalArgumentException("模型响应不能为空");
        }
    }
    public boolean hasToolCalls(){
        return !toolCalls.isEmpty();
    }
}
