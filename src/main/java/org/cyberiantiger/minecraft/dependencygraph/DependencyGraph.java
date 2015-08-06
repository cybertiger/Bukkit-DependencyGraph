package org.cyberiantiger.minecraft.dependencygraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.plugin.PluginDescriptionFile;

/**
 * Turn Bukkit's PluginDescriptionFile into something resembling a directed graph.
 */
final class DependencyGraph {

    private final Map<String,PluginNode> pluginNodeMap = new HashMap<String,PluginNode>();
    private final SyntheticNode worldLoad = new SyntheticNode();

    Collection<String> getPlugins() {
        List<String> result = new ArrayList<String>(pluginNodeMap.values().size());
        for (PluginNode node : pluginNodeMap.values()) {
            result.add(node.getDescription().getName());
        }
        return result;
    }

    List<String> directDependencies(String name) {
        if (pluginNodeMap.containsKey(name)) {
            PluginNode node = pluginNodeMap.get(name);
            PluginDescriptionFile description = node.getDescription();
            List<String> result = new ArrayList<String>(description.getDepend().size() + description.getSoftDepend().size());
            for (Map.Entry<Node, Set<EdgeType>> e : node.getChildEdges().entrySet()) {
                Set<EdgeType> type = e.getValue();
                if (type.contains(EdgeType.DEPENDENCY) || type.contains(EdgeType.SOFT_DEPENDENCY)) {
                    PluginNode depNode =  (PluginNode) e.getKey();
                    result.add(depNode.getDescription().getName());
                }
            }
            return result;
        } else {
            throw new IllegalStateException(name + " is not in the dependency map");
        }
    }

    List<String> allDependencies(String name) {
        if (pluginNodeMap.containsKey(name)) {
            Node node = pluginNodeMap.get(name);
            Set<Node> deps = new LinkedHashSet<Node>();
            deepChildDependencies(deps, node, EnumSet.of(EdgeType.DEPENDENCY, EdgeType.SOFT_DEPENDENCY));
            deps.remove(node);
            ArrayList<String> result = new ArrayList<String>(deps.size());
            for (Node dep : deps) {
                result.add(((PluginNode)dep).getDescription().getName());
            }
            return result;
        } else {
            throw new IllegalStateException(name + " is not in the dependency map");
        }
    }

    boolean dependenciesSatisfied(String name) {
        if (pluginNodeMap.containsKey(name)) {
            PluginNode node = pluginNodeMap.get(name);
            Map<Node, Set<EdgeType>> edges = node.getChildEdges();
            for (String dep : node.getDescription().getDepend()) {
                if (!pluginNodeMap.containsKey(dep))
                    return false;
            }
            return true;
        } else {
            throw new IllegalStateException(name + " is not in the dependency graph");
        }
    }

    boolean addPluginDescription(PluginDescriptionFile description) {
        String name = description.getName();
        if (pluginNodeMap.containsKey(name)) {
            return false;
        }
        PluginNode pluginNode = new PluginNode(description);
        pluginNodeMap.put(name, pluginNode);
        switch (description.getLoad()) {
            case STARTUP:
                worldLoad.addChildEdge(pluginNode, EdgeType.WORLD_LOAD);
                pluginNode.addParentEdge(worldLoad, EdgeType.WORLD_LOAD);
                break;
            case POSTWORLD:
                pluginNode.addChildEdge(worldLoad, EdgeType.WORLD_LOAD);
                worldLoad.addParentEdge(pluginNode, EdgeType.WORLD_LOAD);
                break;
        }
        for (PluginNode otherNode : pluginNodeMap.values()) {
            PluginDescriptionFile otherDescriptionFile = otherNode.getDescription();
            for (String depend : otherDescriptionFile.getDepend()) {
                if (name.equals(depend)) {
                    otherNode.addChildEdge(pluginNode, EdgeType.DEPENDENCY);
                    pluginNode.addParentEdge(otherNode, EdgeType.DEPENDENCY);
                }
            }
            for (String softDepend : otherDescriptionFile.getSoftDepend()) {
                if (name.equals(softDepend)) {
                    otherNode.addChildEdge(pluginNode, EdgeType.SOFT_DEPENDENCY);
                    pluginNode.addParentEdge(otherNode, EdgeType.SOFT_DEPENDENCY);
                }
            }
            for (String loadBefore : otherDescriptionFile.getLoadBefore()) {
                if (name.equals(loadBefore)) {
                    // Reversed since load-before is backwards.
                    pluginNode.addChildEdge(otherNode, EdgeType.LOAD_BEFORE);
                    otherNode.addParentEdge(pluginNode, EdgeType.LOAD_BEFORE);
                }
            }
        }
        for (String depend : description.getDepend()) {
            if (pluginNodeMap.containsKey(depend)) {
                PluginNode dep = pluginNodeMap.get(depend);
                pluginNode.addChildEdge(dep, EdgeType.DEPENDENCY);
                dep.addParentEdge(pluginNode, EdgeType.DEPENDENCY);
            }
        }
        for (String softdepend : description.getSoftDepend()) {
            if (pluginNodeMap.containsKey(softdepend)) {
                PluginNode dep = pluginNodeMap.get(softdepend);
                pluginNode.addChildEdge(dep, EdgeType.SOFT_DEPENDENCY);
                dep.addParentEdge(pluginNode, EdgeType.SOFT_DEPENDENCY);
            }
        }
        for (String loadBefore : description.getLoadBefore()) {
            if (pluginNodeMap.containsKey(loadBefore)) {
                PluginNode dep = pluginNodeMap.get(loadBefore);
                dep.addChildEdge(pluginNode, EdgeType.LOAD_BEFORE); // Backwards
                pluginNode.addParentEdge(dep, EdgeType.LOAD_BEFORE);
            }
        }
        return true;
    }

    boolean removePluginDescription(PluginDescriptionFile description) {
        if (!pluginNodeMap.containsKey(description.getName())) {
            return false;
        }
        Node removed = pluginNodeMap.remove(description.getName());
        for (Node node : pluginNodeMap.values()) {
            node.getChildEdges().remove(removed);
            node.getParentEdges().remove(removed);
        }
        worldLoad.getChildEdges().remove(removed);
        worldLoad.getParentEdges().remove(removed);
        return true;
    }

    List<String> getInitOrder() {
        // Depth first topological sort.
        Set<Node> sorted = new LinkedHashSet<Node>();
        List<Node> remaining = new LinkedList<Node>(pluginNodeMap.values());
        remaining.add(worldLoad);
        // Do some really dumb stuff to break cyclic dependencies.
        depthFirstChildSort(sorted, remaining, EnumSet.of(EdgeType.DEPENDENCY, EdgeType.SOFT_DEPENDENCY, EdgeType.LOAD_BEFORE, EdgeType.WORLD_LOAD));
        depthFirstChildSort(sorted, remaining, EnumSet.of(EdgeType.DEPENDENCY, EdgeType.SOFT_DEPENDENCY, EdgeType.LOAD_BEFORE));
        depthFirstChildSort(sorted, remaining, EnumSet.of(EdgeType.DEPENDENCY, EdgeType.SOFT_DEPENDENCY));
        depthFirstChildSort(sorted, remaining, EnumSet.of(EdgeType.DEPENDENCY));
        sorted.addAll(remaining);
        List<String> result = new ArrayList<String>(sorted.size()-1);
        for (Node node : sorted) {
            if (node instanceof PluginNode) {
                result.add(((PluginNode)node).getDescription().getName());
            }
        }
        return result;
    }

    List<String>[] getEnableOrder() {
        // Depth first topological sort.
        Set<Node> sorted = new LinkedHashSet<Node>();
        List<Node> remaining = new LinkedList<Node>(pluginNodeMap.values());
        remaining.add(worldLoad);
        // Do some really dumb stuff to break cyclic dependencies.
        depthFirstChildSort(sorted, remaining, EnumSet.of(EdgeType.WORLD_LOAD, EdgeType.DEPENDENCY, EdgeType.SOFT_DEPENDENCY, EdgeType.LOAD_BEFORE));
        depthFirstChildSort(sorted, remaining, EnumSet.of(EdgeType.WORLD_LOAD, EdgeType.DEPENDENCY, EdgeType.SOFT_DEPENDENCY));
        depthFirstChildSort(sorted, remaining, EnumSet.of(EdgeType.WORLD_LOAD, EdgeType.DEPENDENCY));
        depthFirstChildSort(sorted, remaining, EnumSet.of(EdgeType.WORLD_LOAD));
        sorted.addAll(remaining); // Should probably be assert(remaining.isEmpty())
        List<String> startup = new ArrayList<String>(sorted.size());
        List<String> postworld = new ArrayList<String>(sorted.size());
        boolean postWorld = false;
        for (Node node : sorted) {
            if (node == worldLoad) {
                postWorld = true;
            } else {
                PluginNode pluginNode = (PluginNode) node;
                if (postWorld) {
                    postworld.add(pluginNode.getDescription().getName());
                } else {
                    startup.add(pluginNode.getDescription().getName());
                }
            }
        }
        return new List[] { startup, postworld };
    }

    List<String> getDependentPlugins(String name) {
        if (pluginNodeMap.containsKey(name)) {
            PluginNode node = pluginNodeMap.get(name);
            Set<Node> deps = new HashSet<Node>();
            deepParentDependencies(deps, node, EnumSet.of(EdgeType.DEPENDENCY));
            deps.remove(node);
            List<String> result = new ArrayList<String>();
            for (Node dep : deps) {
                result.add(((PluginNode)dep).getDescription().getName());
            }
            return result;
        } else {
            throw new IllegalStateException(name + " is not in the dependency graph");
        }
    }

    private static void deepChildDependencies(Set<Node> result, Node node, Set<EdgeType> followTypes) {
        if (!result.add(node)) {
            return;
        }
        for (Map.Entry<Node, Set<EdgeType>> e : node.getChildEdges().entrySet()) {
            Node child = e.getKey();
            Set<EdgeType> types = e.getValue();
            for (EdgeType followType : followTypes) {
                if (types.contains(followType)) {
                    deepChildDependencies(result, child, followTypes);
                    break;
                }
            }
        }
    }

    // Used to calculate loaded plugins which depend on the passed in node.
    private static void deepParentDependencies(Set<Node> result, Node node, Set<EdgeType> followTypes) {
        if (!result.add(node)) {
            return;
        }
        for (Map.Entry<Node, Set<EdgeType>> e : node.getParentEdges().entrySet()) {
            Node child = e.getKey();
            Set<EdgeType> types = e.getValue();
            for (EdgeType followType : followTypes) {
                if (types.contains(followType)) {
                    deepChildDependencies(result, child, followTypes);
                    break;
                }
            }
        }
        result.add(node);
    }

    // Used to calculate load order
    private static void depthFirstChildSort(Set<Node> sorted, List<Node> remaining, Set<EdgeType> types) {
        while (!remaining.isEmpty()) {
            boolean progress = false;
            Iterator<Node> i = remaining.iterator();
LOOP:
            while (i.hasNext()) {
                Node node = i.next();
                for (Map.Entry<Node, Set<EdgeType>> e : node.getChildEdges().entrySet()) {
                    Node child = e.getKey();
                    Set<EdgeType> edgeTypes = e.getValue();
                    boolean testEdge = false;
                    for (EdgeType type : types) {
                        if (edgeTypes.contains(type)) {
                            testEdge = true;
                            break;
                        }
                    }
                    if (testEdge && !sorted.contains(child)) {
                        continue LOOP;
                    }
                }
                sorted.add(node);
                i.remove();
                progress = true;
            }
            if (!progress) {
                break;
            }
        }
    }

    // No actual use for this, since it's the reverse of what we need.
    private static void depthFirstParentSort(Set<Node> sorted, List<Node> remaining, Set<EdgeType> types) {
        while (!remaining.isEmpty()) {
            boolean progress = false;
            Iterator<Node> i = remaining.iterator();
LOOP:
            while (i.hasNext()) {
                Node node = i.next();
                for (Map.Entry<Node, Set<EdgeType>> e : node.getParentEdges().entrySet()) {
                    Node parent = e.getKey();
                    Set<EdgeType> edgeTypes = e.getValue();
                    boolean testEdge = false;
                    for (EdgeType type : types) {
                        if (edgeTypes.contains(type)) {
                            testEdge = true;
                            break;
                        }
                    }
                    if (testEdge && !sorted.contains(parent)) {
                        continue LOOP;
                    }
                }
                sorted.add(node);
                i.remove();
                progress = true;
            }
            if (!progress) {
                break;
            }
        }
    }

    public String toDot() {
        Set<Node> nodes = new HashSet(pluginNodeMap.values());
        nodes.add(worldLoad);
        return toDotFile(nodes);
    }

    public String toSimpleDot() {
        Set<Node> nodes = new HashSet(pluginNodeMap.values());
        return toDotFile(nodes);
    }

    public String toCircularDot() {
        List<Node> nodes = new ArrayList(pluginNodeMap.values());
        nodes.add(worldLoad);
        Set<Node> done = new LinkedHashSet<Node>();
        depthFirstChildSort(done, nodes, EnumSet.allOf(EdgeType.class));
        depthFirstParentSort(done, nodes, EnumSet.allOf(EdgeType.class));
        Set<Node> remaining = new HashSet<Node>(nodes);
        return toDotFile(remaining);
    }

    private static String toDotFile(Set<Node> nodes) {
        StringBuilder result = new StringBuilder();
        result.append("digraph PluginDeps {\n");
        for (Node node : nodes) {
            if (node.isSynthetic()) {
                result.append("node [ shape = circle ] ");
                result.append(node.getName());
                result.append(";\n");
            } else {
                result.append("node [ shape = box ] ");
                result.append(node.getName());
                result.append(";\n");
            }
        }
        result.append('\n');
        for (Node node : nodes) {
            for (Map.Entry<Node, Set<EdgeType>> e : node.getChildEdges().entrySet()) {
                Node child = e.getKey();
                if (!nodes.contains(child)) continue;
                for (EdgeType et : e.getValue()) {
                    result.append(node.getName());
                    result.append(" -> ");
                    result.append(e.getKey().getName());
                    result.append(" [ color=");
                    switch (et) {
                        case DEPENDENCY:
                            result.append("red");
                            break;
                        case LOAD_BEFORE:
                            result.append("green");
                            break;
                        case SOFT_DEPENDENCY:
                            result.append("yellow");
                            break;
                        case WORLD_LOAD:
                            result.append("blue");
                            break;
                    }
                    result.append(" ];");
                }
            }
        }
        result.append("}\n");
        return result.toString();
    }

    static enum EdgeType {
        WORLD_LOAD,
        DEPENDENCY,
        SOFT_DEPENDENCY,
        LOAD_BEFORE;
    }

    final static class Edge {
        private final Node target;
        private final Set<EdgeType> types;

        public Edge(Node target, Set<EdgeType> types) {
            this.target = target;
            this.types = types;
        }

        public Node getTarget() {
            return target;
        }

        public Set<EdgeType> getTypes() {
            return types;
        }
    }

    static abstract class Node {
        private final Map<Node,Set<EdgeType>> children = new HashMap<Node,Set<EdgeType>>();
        private final Map<Node,Set<EdgeType>> parents = new HashMap<Node,Set<EdgeType>>();

        public Map<Node, Set<EdgeType>> getChildEdges() {
            return children;
        }

        public Map<Node, Set<EdgeType>> getParentEdges() {
            return parents;
        }

        public void addChildEdge(Node child, EdgeType type) {
            if (children.containsKey(child)) {
                children.get(child).add(type);
            } else {
                children.put(child, EnumSet.of(type));
            }
        }

        public void addParentEdge(Node parent, EdgeType type) {
            if (parents.containsKey(parent)) {
                parents.get(parent).add(type);
            } else {
                parents.put(parent, EnumSet.of(type));
            }
        }

        public abstract String getName();
        public abstract boolean isSynthetic();
    }

    final static class SyntheticNode extends Node {
        @Override
        public String getName() {
            return "__WORLD_LOAD__";
        }

        @Override
        public boolean isSynthetic() {
            return true;
        }
    }

    final static class PluginNode extends Node {
        private final PluginDescriptionFile description;

        public PluginNode(PluginDescriptionFile description) {
            this.description = description;
        }

        public PluginDescriptionFile getDescription() {
            return description;
        }

        @Override
        public String getName() {
            return description.getName().replace('-', '_');
        }

        @Override
        public boolean isSynthetic() {
            return false;
        }
    }
}
