package io.github.ashr123.warmestdata.controller;

import io.github.ashr123.warmestdata.dto.WarmestDataStructureInterface;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class WarmestDataController {

	private final WarmestDataStructureInterface dataStructure;

	public WarmestDataController(WarmestDataStructureInterface dataStructure) {
		this.dataStructure = dataStructure;
	}

	@PutMapping("/data/{key}")
	public ResponseEntity<Integer> put(@PathVariable String key, @RequestBody int value) {
		return ResponseEntity.ok(dataStructure.put(key, value));
	}

	@GetMapping("/data/{key}")
	public ResponseEntity<Integer> get(@PathVariable String key) {
		Integer value = dataStructure.get(key);
		return value == null ?
				ResponseEntity.notFound().build() :
				ResponseEntity.ok(value);
	}

	@DeleteMapping("/data/{key}")
	public ResponseEntity<Integer> remove(@PathVariable String key) {
		return ResponseEntity.ok(dataStructure.remove(key));
	}

	@GetMapping("/warmest")
	public ResponseEntity<String> getWarmest() {
		return ResponseEntity.ok(dataStructure.getWarmest());
	}
}
