package com.fmr.ec3.oscc.ivr.validation;

import com.fmr.ec3.oscc.ivr.model.FlowDefinition;
import com.fmr.ec3.oscc.ivr.model.FlowEdge;
import com.fmr.ec3.oscc.ivr.model.FlowNode;
import com.fmr.ec3.oscc.ivr.model.FlowVariable;
import com.fmr.ec3.oscc.ivr.model.NodeType;
import com.fmr.ec3.oscc.ivr.model.config.HttpRequestConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Pure-logic validator for IVR flow definitions. No Spring dependencies.
 */
public class FlowValidator {

    private static final Set<NodeType> INPUT_NODES = Set.of(
            NodeType.COLLECT_DTMF, NodeType.MENU, NodeType.ASR_COLLECT
    );

    private static final Set<NodeType> TERMINAL_NODES = Set.of(
            NodeType.HANGUP, NodeType.TRANSFER
    );

    /**
     * Validates a flow definition and returns all issues found.
     */
    public ValidationResult validate(FlowDefinition flow) {
        List<ValidationIssue> issues = new ArrayList<>();

        Map<String, FlowNode> nodeMap = buildNodeMap(flow, issues);
        validateEntryNode(flow, nodeMap, issues);
        validateEdgeTargets(flow, nodeMap, issues);
        validateInputNodeEdges(flow, nodeMap, issues);
        validateHttpTimeouts(flow, nodeMap, issues);
        validateConditionVariables(flow, nodeMap, issues);
        validateMaxSteps(flow, issues);
        detectOrphanedNodes(flow, nodeMap, issues);
        detectInfiniteLoops(flow, nodeMap, issues);

        boolean valid = issues.stream()
                .noneMatch(i -> i.getSeverity() == ValidationSeverity.ERROR);
        return new ValidationResult(valid, issues);
    }

    private Map<String, FlowNode> buildNodeMap(FlowDefinition flow, List<ValidationIssue> issues) {
        Map<String, FlowNode> nodeMap = new HashMap<>();
        for (FlowNode node : flow.getNodes()) {
            if (nodeMap.containsKey(node.getId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, node.getId(),
                        "DUPLICATE_NODE_ID", "Duplicate node ID: " + node.getId()));
            }
            nodeMap.put(node.getId(), node);
        }

        Set<String> edgeIds = new HashSet<>();
        for (FlowEdge edge : flow.getEdges()) {
            if (!edgeIds.add(edge.getId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, null,
                        "DUPLICATE_EDGE_ID", "Duplicate edge ID: " + edge.getId()));
            }
        }

        return nodeMap;
    }

    private void validateEntryNode(FlowDefinition flow, Map<String, FlowNode> nodeMap,
                                   List<ValidationIssue> issues) {
        if (flow.getEntryNodeId() == null || flow.getEntryNodeId().isBlank()) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR, null,
                    "MISSING_ENTRY_NODE", "Flow must have an entry node"));
            return;
        }
        if (!nodeMap.containsKey(flow.getEntryNodeId())) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR, flow.getEntryNodeId(),
                    "ENTRY_NODE_NOT_FOUND", "Entry node not found in nodes list"));
        }
    }

    private void validateEdgeTargets(FlowDefinition flow, Map<String, FlowNode> nodeMap,
                                     List<ValidationIssue> issues) {
        for (FlowEdge edge : flow.getEdges()) {
            if (!nodeMap.containsKey(edge.getSourceNodeId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, edge.getSourceNodeId(),
                        "DANGLING_EDGE_SOURCE",
                        "Edge " + edge.getId() + " references non-existent source node"));
            }
            if (!nodeMap.containsKey(edge.getTargetNodeId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, edge.getTargetNodeId(),
                        "DANGLING_EDGE_TARGET",
                        "Edge " + edge.getId() + " references non-existent target node"));
            }
        }
    }

    private void validateInputNodeEdges(FlowDefinition flow, Map<String, FlowNode> nodeMap,
                                        List<ValidationIssue> issues) {
        Set<NodeType> requireTimeoutEdge = Set.of(NodeType.COLLECT_DTMF, NodeType.MENU);
        Map<String, Set<String>> edgeConditions = new HashMap<>();

        for (FlowEdge edge : flow.getEdges()) {
            edgeConditions
                    .computeIfAbsent(edge.getSourceNodeId(), k -> new HashSet<>())
                    .add(edge.getCondition() != null ? edge.getCondition() : "");
        }

        for (FlowNode node : flow.getNodes()) {
            if (requireTimeoutEdge.contains(node.getType())) {
                Set<String> conditions = edgeConditions.getOrDefault(node.getId(), Set.of());
                if (!conditions.contains("timeout")) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR, node.getId(),
                            "MISSING_TIMEOUT_EDGE",
                            node.getType() + " node must have a 'timeout' edge"));
                }
                if (!conditions.contains("invalid")) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR, node.getId(),
                            "MISSING_INVALID_EDGE",
                            node.getType() + " node must have an 'invalid' edge"));
                }
            }
        }
    }

    private void validateHttpTimeouts(FlowDefinition flow, Map<String, FlowNode> nodeMap,
                                      List<ValidationIssue> issues) {
        for (FlowNode node : flow.getNodes()) {
            if (node.getType() == NodeType.HTTP_REQUEST) {
                Object timeout = node.getConfig().get("timeoutSeconds");
                if (timeout instanceof Number) {
                    int seconds = ((Number) timeout).intValue();
                    if (seconds > HttpRequestConfig.MAX_TIMEOUT_SECONDS) {
                        issues.add(new ValidationIssue(
                                ValidationSeverity.ERROR, node.getId(),
                                "HTTP_TIMEOUT_EXCEEDED",
                                "HTTP timeout " + seconds + "s exceeds max "
                                        + HttpRequestConfig.MAX_TIMEOUT_SECONDS + "s"));
                    }
                }
            }
        }
    }

    private void validateConditionVariables(FlowDefinition flow, Map<String, FlowNode> nodeMap,
                                            List<ValidationIssue> issues) {
        Set<String> declaredVars = new HashSet<>();
        for (FlowVariable var : flow.getVariables()) {
            declaredVars.add(var.getName());
        }

        for (FlowNode node : flow.getNodes()) {
            if (node.getType() == NodeType.CONDITION) {
                Object varName = node.getConfig().get("variableName");
                if (varName instanceof String && !declaredVars.contains(varName)) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR, node.getId(),
                            "UNDECLARED_VARIABLE",
                            "Condition references undeclared variable: " + varName));
                }
            }
        }
    }

    private void validateMaxSteps(FlowDefinition flow, List<ValidationIssue> issues) {
        if (flow.getMaxSteps() <= 0) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR, null,
                    "INVALID_MAX_STEPS", "maxSteps must be greater than 0"));
        }
    }

    private void detectOrphanedNodes(FlowDefinition flow, Map<String, FlowNode> nodeMap,
                                     List<ValidationIssue> issues) {
        if (flow.getEntryNodeId() == null || !nodeMap.containsKey(flow.getEntryNodeId())) {
            return;
        }

        Map<String, List<String>> adjacency = new HashMap<>();
        for (FlowEdge edge : flow.getEdges()) {
            adjacency.computeIfAbsent(edge.getSourceNodeId(), k -> new ArrayList<>())
                    .add(edge.getTargetNodeId());
        }

        Set<String> reachable = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(flow.getEntryNodeId());
        reachable.add(flow.getEntryNodeId());

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String neighbor : adjacency.getOrDefault(current, List.of())) {
                if (reachable.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        for (FlowNode node : flow.getNodes()) {
            if (!reachable.contains(node.getId())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.WARNING, node.getId(),
                        "ORPHANED_NODE", "Node is not reachable from entry node"));
            }
        }
    }

    private void detectInfiniteLoops(FlowDefinition flow, Map<String, FlowNode> nodeMap,
                                     List<ValidationIssue> issues) {
        Map<String, List<String>> adjacency = new HashMap<>();
        for (FlowEdge edge : flow.getEdges()) {
            adjacency.computeIfAbsent(edge.getSourceNodeId(), k -> new ArrayList<>())
                    .add(edge.getTargetNodeId());
        }

        // Find all cycles using DFS, then verify each cycle passes through an input or terminal node
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        List<String> stack = new ArrayList<>();

        for (FlowNode node : flow.getNodes()) {
            if (!visited.contains(node.getId())) {
                if (dfsFindUnsafeCycle(node.getId(), adjacency, nodeMap, visited, inStack, stack, issues)) {
                    return;
                }
            }
        }
    }

    private boolean dfsFindUnsafeCycle(String nodeId, Map<String, List<String>> adjacency,
                                       Map<String, FlowNode> nodeMap,
                                       Set<String> visited, Set<String> inStack,
                                       List<String> stack, List<ValidationIssue> issues) {
        visited.add(nodeId);
        inStack.add(nodeId);
        stack.add(nodeId);

        for (String neighbor : adjacency.getOrDefault(nodeId, List.of())) {
            if (!visited.contains(neighbor)) {
                if (dfsFindUnsafeCycle(neighbor, adjacency, nodeMap, visited, inStack, stack, issues)) {
                    return true;
                }
            } else if (inStack.contains(neighbor)) {
                // Found a cycle — extract it and check for input/terminal nodes
                int cycleStart = stack.indexOf(neighbor);
                List<String> cycle = stack.subList(cycleStart, stack.size());

                boolean hasSafeNode = cycle.stream().anyMatch(id -> {
                    FlowNode n = nodeMap.get(id);
                    return n != null && (INPUT_NODES.contains(n.getType())
                            || TERMINAL_NODES.contains(n.getType()));
                });

                if (!hasSafeNode) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR, neighbor,
                            "INFINITE_LOOP",
                            "Cycle detected without input/terminal node: "
                                    + String.join(" -> ", cycle) + " -> " + neighbor));
                    return true;
                }
            }
        }

        stack.remove(stack.size() - 1);
        inStack.remove(nodeId);
        return false;
    }
}
