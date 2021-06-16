package test.integration.trait;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.sormuras.bach.Bach;
import java.lang.module.FindException;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import test.base.resource.ResourceManager;
import test.base.resource.ResourceManager.Singleton;
import test.base.resource.TempDir;
import test.base.resource.WebServer;
import test.integration.Auxiliary;
import test.integration.VolatileServer;

@ExtendWith(ResourceManager.class)
class ResolveTraitTests {

  @Test
  void loadFooFails() {
    var bach = Auxiliary.newEmptyBach();
    assertThrows(FindException.class, () -> bach.loadExternalModules("foo"));
  }

  @Test
  @Disabled
  void loadFoo(@Singleton(VolatileServer.class) WebServer server, @TempDir Path temp) {
    var bach = new Bach(null, null);
    //        Bach.of(
    //            Logbook.ofErrorPrinter(),
    //            Options.of()
    //                .with("--chroot", temp.toString())
    //                .with("--external-module-location", "foo=" + server.uri("foo.jar")));

    bach.loadExternalModules("foo");

    var finder = ModuleFinder.of(bach.project().folders().externalModules());
    assertTrue(finder.find("foo").isPresent());
  }

  @Test
  @Disabled
  void loadMissingExternalModules(
      @Singleton(VolatileServer.class) WebServer server, @TempDir Path temp) {
    var bach = new Bach(null, null);
    //        Bach.of(
    //            Logbook.ofErrorPrinter(),
    //            Options.of()
    //                .with("--chroot", temp.toString())
    //                .with("--project-requires", "bar") // bar requires foo
    //                .with("--external-module-location", "bar=" + server.uri("bar.jar"))
    //                .with("--external-module-location", "foo=" + server.uri("foo.jar")));

    bach.loadMissingExternalModules();

    var finder = ModuleFinder.of(bach.project().folders().externalModules());
    assertTrue(finder.find("bar").isPresent());
    assertTrue(finder.find("foo").isPresent());
  }
}
