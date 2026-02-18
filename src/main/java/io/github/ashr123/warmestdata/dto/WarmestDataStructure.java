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
	private Node head = null;  // Oldest (coldest)
	private Node tail = null;  // Newest (warmest)

	/**
	 * Detaches a node from its current position in the linked list.
	 * Must be called while holding write lock.
	 *
	 * @param node the node to detach
	 */
	private void detach(Node node) {
		if (node.prev != null) {
			node.prev.next = node.next;
		} else {
			// Node was head
			head = node.next;
		}

		if (node.next != null) {
			node.next.prev = node.prev;
		} else {
			// Node was tail
			tail = node.prev;
		}

		node.prev = null;
		node.next = null;
	}

	/**
	 * Attaches a node to the tail of the linked list (making it the warmest).
	 * Must be called while holding write lock.
	 *
	 * @param node the node to attach to tail
	 */
	private void attachToTail(Node node) {
		node.prev = tail;
		node.next = null;

		if (tail != null) {
			tail.next = node;
		}
		tail = node;

		if (head == null) {
			head = node;
		}
	}

	/**
	 * Moves an existing node to the tail position (making it the warmest).
	 * Must be called while holding write lock.
	 *
	 * @param node the node to move to tail
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

			if (existingNode != null) {
				// Key exists: update value and move to tail
				int previousValue = existingNode.value;
				existingNode.value = value;
				moveToTail(existingNode);
				return previousValue;
			} else {
				// Key doesn't exist: create new node and add to tail
				Node newNode = new Node(key, value);
				map.put(key, newNode);
				attachToTail(newNode);
				return null;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public Integer get(String key) {
		lock.writeLock().lock();
		try {
			Node node = map.get(key);

			if (node != null) {
				moveToTail(node);
				return node.value;
			} else {
				return null;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public Integer remove(String key) {
		lock.writeLock().lock();
		try {
			Node node = map.remove(key);

			if (node != null) {
				detach(node);
				return node.value;
			} else {
				return null;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public String getWarmest() {
		lock.readLock().lock();
		try {
			if (tail == null) {
				return null;
			}
			return tail.key;
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Node class for the doubly linked list.
	 * Stores key, value, and references to previous and next nodes.
	 */
	private static class Node {
		String key;
		int value;
		Node prev;
		Node next;

		Node(String key, int value) {
			this.key = key;
			this.value = value;
		}
	}
}
