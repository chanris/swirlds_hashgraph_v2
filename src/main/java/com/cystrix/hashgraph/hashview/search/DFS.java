package com.cystrix.hashgraph.hashview.search;

import com.cystrix.hashgraph.exception.BusinessException;
import com.cystrix.hashgraph.hashview.Event;

import java.util.ArrayList;
import java.util.List;

public class DFS {

    public static List<List<Event>> findAllPaths(Event node, Event target) {
        List<List<Event>> paths = new ArrayList<>();
        List<Event> path = new ArrayList<>();
        findPath(node, target, path, paths);
        return paths;
    }

    private static void findPath(Event node, Event target, List<Event> path, List<List<Event>> paths) {
        // 标记节点为已访问
        node.setVisited(true);
        path.add(node);

        // 如果当前节点是目标节点，则将当前路径添加到结果列表中
        if (node == target) {
            paths.add(new ArrayList<>(path));
        }

        if (node.getNeighbors() != null && node.getNeighbors().size() == 2
                && node.getNeighbors().get(0) != null && node.getNeighbors().get(1) != null) {
            // 递归遍历相邻节点
            for (Event neighbor : node.getNeighbors()) {
                if (!neighbor.isVisited()) {
                    findPath(neighbor, target, path, paths);
                }
            }
        }

        // 回溯，移除当前节点
        path.remove(path.size() - 1);
        node.setVisited(false);
    }

    // 使用广度优先搜索找到一条路径
    public static boolean findPath(Event startNode, Event targetNode, List<Event> path) {
        startNode.setVisited(true);
        path.add(startNode);
        if (startNode == targetNode) {
            return true;
        }

        if (startNode.getNeighbors() != null && startNode.getNeighbors().size() == 2
                && startNode.getNeighbors().get(0) != null && startNode.getNeighbors().get(1) != null) {
            // 递归遍历相邻节点
            for (Event neighbor : startNode.getNeighbors()) {
                if (!neighbor.isVisited()) {
                    boolean found = findPath(neighbor, targetNode, path);
                    if (found) {
                        return true;
                    }
                }
            }
        }
        // 回溯，移除当前节点
        path.remove(path.size() - 1);
        return false;
    }

}
