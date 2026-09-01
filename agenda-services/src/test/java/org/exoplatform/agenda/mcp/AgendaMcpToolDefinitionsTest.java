/**
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.mcp;

import static io.meeds.mcp.server.util.McpToolUtils.toSnakeCase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.util.ReflectionUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pins the one thing about an MCP tool that fails <strong>silently</strong>.
 * <p>
 * A tool is a public method on a plugin bean, and the server publishes it only
 * if {@code ai-tool-definitions.json} holds an entry whose {@code name} is the
 * snake_case of that method's name. When they disagree, nothing throws and
 * nothing is logged for the tool that vanished: the method is simply never
 * offered, and the first sign of it is a model that cannot do the thing the
 * delivery was about. A misspelling in either file is therefore invisible to
 * every other test in this repo — hence this one.
 * <p>
 * The enumeration deliberately mirrors
 * {@code McpToolCallbackProviderService.getToolCallbacks()}: declared methods,
 * user-declared, public. The extra restriction to methods <em>declared on the
 * tool class itself</em> is what keeps the SPI's own default helpers
 * ({@code getCurrentUserName} and friends, which Spring's reflection helper
 * also surfaces) out of the count — the server drops those for want of a
 * definition, which is correct there and would be a false alarm here.
 */
class AgendaMcpToolDefinitionsTest {

  private static final String DEFINITIONS = "ai-tool-definitions.json";

  /**
   * The plugin beans this addon contributes. A new tool class must be added
   * here, or its tools go unchecked.
   */
  private static final List<Class<?>> TOOL_CLASSES = List.of(AgendaEventMcpTool.class);

  @Test
  void everyToolMethodHasADefinition() {
    Set<String> declared = declaredToolNames();
    Set<String> defined = definedToolNames();

    Set<String> undefined = new TreeSet<>(declared);
    undefined.removeAll(defined);
    assertTrue(undefined.isEmpty(),
               "these tool methods have no entry in %s and would be dropped silently: %s".formatted(DEFINITIONS, undefined));
  }

  @Test
  void everyDefinitionHasAToolMethod() {
    Set<String> declared = declaredToolNames();
    Set<String> defined = definedToolNames();

    Set<String> orphans = new TreeSet<>(defined);
    orphans.removeAll(declared);
    assertTrue(orphans.isEmpty(),
               "these entries in %s match no tool method, so the tool they describe does not exist: %s".formatted(DEFINITIONS,
                                                                                                                 orphans));
  }

  @Test
  void getScheduleConflictsIsRegistered() {
    assertTrue(declaredToolNames().contains("get_schedule_conflicts"), "get_schedule_conflicts is not a tool method");
    Map<String, Object> definition = definitions().stream()
                                                  .filter(tool -> "get_schedule_conflicts".equals(tool.get("name")))
                                                  .findFirst()
                                                  .orElse(null);
    assertNotNull(definition, "get_schedule_conflicts has no entry in " + DEFINITIONS);
  }

  /**
   * A read must not ask a human for permission, and must be annotated as the
   * read it is — otherwise the server routes it through the approval
   * round-trip this tool exists to avoid.
   */
  @Test
  void getScheduleConflictsIsAReadAndNeedsNoApproval() {
    Map<String, Object> definition = definitions().stream()
                                                  .filter(tool -> "get_schedule_conflicts".equals(tool.get("name")))
                                                  .findFirst()
                                                  .orElseThrow();

    assertEquals(Boolean.TRUE, annotations(definition).get("readOnlyHint"));
    assertEquals(Boolean.FALSE, annotations(definition).get("destructiveHint"));
    assertTrue(definition.get("require_approval") == null || Boolean.FALSE.equals(definition.get("require_approval")),
               "a read-only tool must not require approval");
  }

  /**
   * The names the MCP server would publish for this addon's tool beans.
   *
   * @return the snake_case tool names, as the server computes them
   */
  private Set<String> declaredToolNames() {
    return TOOL_CLASSES.stream()
                       .flatMap(toolClass -> Stream.of(ReflectionUtils.getDeclaredMethods(toolClass))
                                                   .filter(ReflectionUtils.USER_DECLARED_METHODS::matches)
                                                   .filter(method -> Modifier.isPublic(method.getModifiers()))
                                                   .filter(method -> method.getDeclaringClass() == toolClass))
                       .map(Method::getName)
                       .map(name -> toSnakeCase(name))
                       .collect(Collectors.toCollection(TreeSet::new));
  }

  /**
   * The tool names this addon's definition file describes.
   *
   * @return the names, sorted
   */
  private Set<String> definedToolNames() {
    return definitions().stream().map(tool -> (String) tool.get("name")).collect(Collectors.toCollection(TreeSet::new));
  }

  /**
   * Reads the definition file from the classpath, exactly as the server does.
   *
   * @return the tool definitions it holds
   */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> definitions() {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(DEFINITIONS)) {
      assertNotNull(stream, DEFINITIONS + " is not on the classpath");
      Map<String, Object> root = new ObjectMapper().readValue(stream, Map.class);
      return (List<Map<String, Object>>) root.get("tools");
    } catch (Exception e) {
      throw new AssertionError("could not read " + DEFINITIONS, e);
    }
  }

  /**
   * Reads one definition's annotations block.
   *
   * @param definition the tool definition
   * @return its annotations, never {@code null}
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> annotations(Map<String, Object> definition) {
    Map<String, Object> annotations = (Map<String, Object>) definition.get("annotations");
    assertNotNull(annotations, "tool '%s' carries no annotations".formatted(definition.get("name")));
    return annotations;
  }

}
