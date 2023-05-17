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

    public static void dfs(Node startNode, Node targetNode, List<Integer> path, List<List<Integer>> paths) {
        // 标记节点为已访问
        startNode.visited = true;
        path.add(startNode.val);

        // 如果当前节点是目标节点，则将当前路径添加到结果列表中
        if (startNode == targetNode) {
            paths.add(new ArrayList<>(path));
        }

        // 递归遍历相邻节点
        for (Node neighbor : startNode.neighbors) {
            if (!neighbor.visited) {
                dfs(neighbor, targetNode, path, paths);
            }
        }

        // 回溯，移除当前节点
        path.remove(path.size() - 1);
        startNode.visited = false;
    }


    public static void dfs(Node startNode, Node targetNode, List<Integer> path) {
        startNode.visited = true;
        System.out.println(startNode.val + " ");
        path.add(startNode.val);
        if (startNode == targetNode) {
            return;
        }
        // 递归遍历相邻节点
        for (Node neighbor : startNode.neighbors) {
            if (!neighbor.visited) {
                dfs(neighbor, targetNode, path);
            }
        }

        // 回溯，移除当前节点
        path.remove(path.size() - 1);
        startNode.visited = false;
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



    }
}