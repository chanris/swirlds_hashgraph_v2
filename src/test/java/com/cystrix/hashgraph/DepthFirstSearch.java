package com.cystrix.hashgraph;

import java.util.ArrayList;
import java.util.List;

 class Node {
    int val;
    List<Node> neighbors; // 邻接链表
    boolean visited;

    public Node(int val) {
        this.val = val;
        neighbors = new ArrayList<>();
        visited = false;
    }
}



public class DepthFirstSearch {

    public static List<List<Node>> findAllPaths(Node node, Node target) {
        List<List<Node>> paths = new ArrayList<>();
        List<Node> path = new ArrayList<>();
        findPath(node, target, path, paths);
        return paths;
    }

    public static void findPath(Node node, Node target, List<Node> path, List<List<Node>> paths) {
        // 标记节点为已访问
        node.visited = true;
        path.add(node);

        // 如果当前节点是目标节点，则将当前路径添加到结果列表中
        if (node == target) {
            paths.add(new ArrayList<>(path));
        }

        // 递归遍历相邻节点
        for (Node neighbor : node.neighbors) {
            if (!neighbor.visited) {
                findPath(neighbor, target, path, paths);
            }
        }

        // 回溯，移除当前节点
        path.remove(path.size() - 1);
        node.visited = false;
    }

    // 使用广度优先搜索找到一条路径
    public static boolean findPath(Node startNode, Node targetNode, List<Node> path) {
        startNode.visited = true;
        path.add(startNode);
        if (startNode == targetNode) {
            return true;
        }
        // 递归遍历相邻节点
        for (Node neighbor : startNode.neighbors) {
            if (!neighbor.visited) {
                boolean found = findPath(neighbor, targetNode, path);
                if (found) {
                    return true;
                }
            }
        }

        // 回溯，移除当前节点
        path.remove(path.size() - 1);
        return false;
    }

    public static void main(String[] args) {
        Node node = new Node(0);
        Node node1 = new Node(1);
        Node node2 = new Node(2);
        Node node3 = new Node(3);
        Node node4 = new Node(4);
        Node node5 = new Node(5);
        Node node6 = new Node(6);
        Node node7 = new Node(7);
        Node node8 = new Node(8);
        Node node9 = new Node(9);
        Node node10 = new Node(10);
        Node node12 = new Node(12);
        Node node14 = new Node(14);

        List<Node> nodeList = new ArrayList<>(); //邻接表
        nodeList.add(node);
        nodeList.add(node1);
        nodeList.add(node2);
        nodeList.add(node3);
        nodeList.add(node4);
        nodeList.add(node5);
        nodeList.add(node6);
        nodeList.add(node7);
        nodeList.add(node8);
        nodeList.add(node9);
        nodeList.add(node10);
        nodeList.add(node12);
        nodeList.add(node14);

        node4.neighbors.add(node1);
        node4.neighbors.add(node);

        node5.neighbors.add(node4);
        node5.neighbors.add(node1);

        node6.neighbors.add(node2);
        node6.neighbors.add(node3);

        node7.neighbors.add(node5);
        node7.neighbors.add(node3);

        node8.neighbors.add(node4);
        node8.neighbors.add(node6);

        node9.neighbors.add(node5);
        node9.neighbors.add(node10);

        node10.neighbors.add(node5);
        node10.neighbors.add(node6);

        node12.neighbors.add(node8);
        node12.neighbors.add(node14);

        node14.neighbors.add(node9);
        node14.neighbors.add(node10);


//        List<Node> path = new ArrayList<>();
//        dfs(node12, node1, path);
//        path.forEach(item->{
//            System.out.print(item.val + " ");
//        });


        List<List<Node>> allPaths = findAllPaths(node1, node12);
        for (List<Node> lst : allPaths) {
            for (Node item : lst) {
                System.out.print(item.val+" ");
            }
            System.out.println();
        }
    }
}