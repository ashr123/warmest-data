package io.github.ashr123.warmestdata;

import io.github.ashr123.warmestdata.dto.WarmestDataStructureInterface;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * Integration tests for WarmestDataController.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WarmestDataControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private WarmestDataStructureInterface dataStructure;

	@Test
	void put_whenNewKey_returnsNullPreviousValue() throws Exception {
		Mockito.when(dataStructure.put("a", 100)).thenReturn(null);

		mockMvc.perform(MockMvcRequestBuilders.put("/data/a")
						.contentType(MediaType.APPLICATION_JSON)
						.content("100"))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$").doesNotExist());
	}

	@Test
	void put_whenExistingKey_returnsPreviousValue() throws Exception {
		Mockito.when(dataStructure.put("a", 101)).thenReturn(100);

		mockMvc.perform(MockMvcRequestBuilders.put("/data/a")
						.contentType(MediaType.APPLICATION_JSON)
						.content("101"))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$").value(100));
	}

	// ==================== GET /data/{key} Tests ====================

	@Test
	void get_whenKeyExists_returnsValue() throws Exception {
		Mockito.when(dataStructure.get("a")).thenReturn(100);

		mockMvc.perform(MockMvcRequestBuilders.get("/data/a"))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$").value(100));
	}

	@Test
	void get_whenKeyNotExists_returns404() throws Exception {
		Mockito.when(dataStructure.get("nonexistent")).thenReturn(null);

		mockMvc.perform(MockMvcRequestBuilders.get("/data/nonexistent"))
				.andExpect(MockMvcResultMatchers.status().isNotFound());
	}

	// ==================== DELETE /data/{key} Tests ====================

	@Test
	void remove_whenKeyExists_returnsPreviousValue() throws Exception {
		Mockito.when(dataStructure.remove("a")).thenReturn(100);

		mockMvc.perform(MockMvcRequestBuilders.delete("/data/a"))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$").value(100));
	}

	@Test
	void remove_whenKeyNotExists_returnsNullValue() throws Exception {
		Mockito.when(dataStructure.remove("nonexistent")).thenReturn(null);

		mockMvc.perform(MockMvcRequestBuilders.delete("/data/nonexistent"))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$").doesNotExist());
	}

	// ==================== GET /warmest Tests ====================

	@Test
	void getWarmest_whenDataExists_returnsWarmestKey() throws Exception {
		Mockito.when(dataStructure.getWarmest()).thenReturn("a");

		mockMvc.perform(MockMvcRequestBuilders.get("/warmest"))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$").value("a"));
	}

	@Test
	void getWarmest_whenEmpty_returnsEmptyKey() throws Exception {
		Mockito.when(dataStructure.getWarmest()).thenReturn(null);

		mockMvc.perform(MockMvcRequestBuilders.get("/warmest"))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$").doesNotExist());
	}
}
