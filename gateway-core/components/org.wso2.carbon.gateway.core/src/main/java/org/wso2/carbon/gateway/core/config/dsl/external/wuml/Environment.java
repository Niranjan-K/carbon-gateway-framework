package org.wso2.carbon.gateway.core.config.dsl.external.wuml;

import org.wso2.carbon.gateway.core.flow.mediators.builtin.flowcontrollers.filter.FilterMediator;

import java.util.Stack;

/**
 * Created by ravi on 5/24/16.
 */
public class Environment {

    Stack<String> pipelineStack = new Stack<String>();
    Stack<FilterMediator> filterMediatorStack = new Stack<FilterMediator>();
    boolean ifMultiThenBlockStarted = false;
    boolean ifElseBlockStarted = false;
    boolean insideGroup = false;
    private String groupPath;

    public Environment() { }

    public Stack<String> getPipelineStack() {
        return pipelineStack;
    }

    public void setPipelineStack(Stack<String> pipelineStack) {
        this.pipelineStack = pipelineStack;
    }

    public Stack<FilterMediator> getFilterMediatorStack() {
        return filterMediatorStack;
    }

    public void setFilterMediatorStack(Stack<FilterMediator> filterMediatorStack) {
        this.filterMediatorStack = filterMediatorStack;
    }

    public boolean isIfMultiThenBlockStarted() {
        return ifMultiThenBlockStarted;
    }

    public void setIfMultiThenBlockStarted(boolean ifMultiThenBlockStarted) {
        this.ifMultiThenBlockStarted = ifMultiThenBlockStarted;
    }

    public boolean isIfElseBlockStarted() {
        return ifElseBlockStarted;
    }

    public void setIfElseBlockStarted(boolean ifElseBlockStarted) {
        this.ifElseBlockStarted = ifElseBlockStarted;
    }

    public boolean isInsideGroup() {
        return insideGroup;
    }

    public void setInsideGroup(boolean insideGroup) {
        this.insideGroup = insideGroup;
    }

    public String getGroupPath() {
        return groupPath;
    }

    public void setGroupPath(String groupPath) {
        this.groupPath = groupPath;
    }
}
