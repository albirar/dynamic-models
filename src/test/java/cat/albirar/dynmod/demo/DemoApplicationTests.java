package cat.albirar.dynmod.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.application.models.filtered=false")
class DemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
