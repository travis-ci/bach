package test.projects;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JigsawQuickStartWorldWithTestsTests {

  @Test
  void build() throws Exception {
    var project = TestProject.of("JigsawQuickStartWorldWithTests");
    assertEquals(0, project.build().waitFor());
  }
}