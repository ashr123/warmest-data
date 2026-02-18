package io.github.ashr123.warmestdata.dto;

/**
 * WarmestDataStructure interface with O(1) complexity for all operations.
 */
public interface WarmestDataStructureInterface {
	/**
	 * This method associates the specified value with the specified key
	 * Complexity: O(1)
	 *
	 * @param key   This is the key with which the specified value is to be associated.
	 * @param value This is the value to be associated with the specified key.
	 * @return the previous value associated with key, or null if there was no mapping for key.
	 */
	Integer put(String key, int value);

	/**
	 * This method removes the mapping for the specified key if present.
	 * Complexity: O(1)
	 *
	 * @param key This is the key whose mapping is to be removed from the map.
	 * @return the previous value associated with key, or null if there was no mapping for key.
	 */
	Integer remove(String key);

	/**
	 * This method returns the value to which the specified key is mapped, or null if this map contains no mapping for the key.
	 * Complexity: O(1)
	 *
	 * @param key This is the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key.
	 */
	Integer get(String key);

	/**
	 * Get the "warmest" key in the system
	 * Complexity: O(1)
	 *
	 * @return the last key that was passed in methods put or get, and was not removed
	 */
	String getWarmest();
}
