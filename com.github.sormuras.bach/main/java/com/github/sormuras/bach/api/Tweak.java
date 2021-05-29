package com.github.sormuras.bach.api;

import com.github.sormuras.bach.internal.Strings;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public record Tweak(Set<CodeSpace> spaces, String trigger, List<String> arguments) {

  public static Tweak of(ProjectInfo.Tweak info) {
    var spaces = Set.of(info.spaces());
    var trigger = info.tool();
    var arguments = new ArrayList<String>();
    Strings.unroll(info.with()).forEach(arguments::add);
    Strings.unroll(info.more()).forEach(arguments::add);
    return new Tweak(spaces, trigger, List.copyOf(arguments));
  }

  public static Tweak ofCommandLine(String tweaks) {
    var lines = tweaks.lines().toList();
    var namesOfSpaces = Stream.of(lines.get(0).split(","));
    var spaces = EnumSet.copyOf(namesOfSpaces.map(CodeSpace::ofCli).toList());
    var trigger = lines.get(1);
    return new Tweak(spaces, trigger, lines.size() <= 2 ? List.of() : lines.subList(2, lines.size()));
  }

  public boolean isForSpace(CodeSpace space) {
    return spaces.contains(space);
  }
}
