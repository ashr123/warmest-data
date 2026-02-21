package io.github.ashr123.warmestdata.dto;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe implementation of WarmestDataStructureInterface using a custom doubly linked list
 * and HashMap for O(1) operations.
 */
public class WarmestDataStructure implements WarmestDataStructureInterface {

	private final Map<String, Node> map = new HashMap<>();
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private Node tail = null;  // Newest (warmest)

	/**
	 * Detaches a node from its current position in the linked list.
	 *
	 * @param node the node to detach
	 * @implNote Must be called while holding write lock.
	 */
	private void detach(Node node) {
		if (node.prev != null) {
			node.prev.next = node.next;
		}

		if (node.next == null) {
			// Node was tail
			tail = node.prev;
		} else {
			node.next.prev = node.prev;
		}

		node.prev = null;
		node.next = null;
	}

	/**
	 * Attaches a node to the tail of the linked list (making it the warmest).
	 *
	 * @param node the node to attach to tail
	 * @implNote Must be called while holding write lock.
	 */
	private void attachToTail(Node node) {
		node.prev = tail;
		node.next = null;

		if (tail != null) {
			tail.next = node;
		}
		tail = node;
	}

	/**
	 * Moves an existing node to the tail position (making it the warmest).
	 *
	 * @param node the node to move to tail
	 * @implNote Must be called while holding write lock.
	 */
	private void moveToTail(Node node) {
		if (node == tail) {
			// Already at tail, nothing to do
			return;
		}
		detach(node);
		attachToTail(node);
	}

	@Override
	public Integer put(String key, int value) {
		lock.writeLock().lock();
		try {
			Node existingNode = map.get(key);
			return existingNode == null
					? insertNewNode(key, value)
					: updateExistingNode(existingNode, value);
		} finally {
			lock.writeLock().unlock();
		}
	}

	private Integer insertNewNode(String key, int value) {
		Node newNode = new Node(key, value);
		map.put(key, newNode);
		attachToTail(newNode);
		return null;
	}

	private Integer updateExistingNode(Node node, int newValue) {
		int previousValue = node.value;
		node.value = newValue;
		moveToTail(node);
		return previousValue;
	}

	@Override
	public Integer get(String key) {
		Node node = findNodeWithReadLock(key);
		if (node == null) {
			return null;
		}

		if (isAlreadyWarmest(node)) {
			return node.value;
		}

		return moveNodeAndGetValue(key, node);
	}

	private Node findNodeWithReadLock(String key) {
		lock.readLock().lock();
		try {
			return map.get(key);
		} finally {
			lock.readLock().unlock();
		}
	}

	private boolean isAlreadyWarmest(Node node) {
		return node == tail;
	}

	private Integer moveNodeAndGetValue(String key, Node node) {
		lock.writeLock().lock();
		try {
			if (!map.containsKey(key)) {
				return null;
			}

			if (isAlreadyWarmest(node)) {
				return node.value;
			}

			moveToTail(node);
			return node.value;
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public Integer remove(String key) {
		lock.writeLock().lock();
		try {
			Node node = map.remove(key);

			if (node == null) {
				return null;
			}
			detach(node);
			return node.value;
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public String getWarmest() {
		lock.readLock().lock();
		try {
			return tail == null ?
					null :
					tail.key;
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Node class for the doubly linked list.
	 * Stores key, value, and references to previous and next nodes.
	 */
	private static class Node {
		private final String key;
		private int value;
		private Node prev;
		private Node next;

		Node(String key, int value) {
			this.key = key;
			this.value = value;
		}
	}
}
