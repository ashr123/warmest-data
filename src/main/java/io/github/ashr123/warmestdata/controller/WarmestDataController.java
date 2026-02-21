package io.github.ashr123.warmestdata.controller;

import io.github.ashr123.warmestdata.dto.WarmestDataStructureInterface;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class WarmestDataController {

	private final WarmestDataStructureInterface dataStructure;

	public WarmestDataController(WarmestDataStructureInterface dataStructure) {
		this.dataStructure = dataStructure;
	}

	@PutMapping("/data/{key}")
	@ResponseStatus(HttpStatus.OK)
	public Integer put(@PathVariable String key, @RequestBody int value) {
		return dataStructure.put(key, value);
	}

	@GetMapping("/data/{key}")
	@ResponseStatus(HttpStatus.OK)
	public Integer get(@PathVariable String key) {
		Integer value = dataStructure.get(key);
		if (value == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Key not found: " + key);
		}
		return value;
	}

	@DeleteMapping("/data/{key}")
	@ResponseStatus(HttpStatus.OK)
	public Integer remove(@PathVariable String key) {
		return dataStructure.remove(key);
	}

	@GetMapping("/warmest")
	@ResponseStatus(HttpStatus.OK)
	public String getWarmest() {
		return dataStructure.getWarmest();
	}
}
