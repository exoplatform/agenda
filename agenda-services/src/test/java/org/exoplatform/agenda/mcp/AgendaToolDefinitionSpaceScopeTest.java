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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import io.meeds.mcp.server.constant.Origin;
import io.meeds.mcp.server.model.ActingIdentity;
import io.meeds.mcp.server.model.SimpleToolDefinition;
import io.meeds.mcp.server.model.SpaceScopeDeclaration;
import io.meeds.mcp.server.model.SpaceScopeRefusedException;
import io.meeds.mcp.server.plugin.SpaceScopeResolver;
import io.meeds.mcp.server.service.McpToolSpaceScopeService;
import io.meeds.mcp.server.util.McpToolUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pins the {@code space_scope} declarations the agenda add-on ships: which
 * tools a call limited to one space may use, on which arguments, and that
 * every other tool stays refused.
 */
class AgendaToolDefinitionSpaceScopeTest {

  private static final Set<String> SPACE_TOOLS       = Set.of("get_agenda_events",
                                                              "search_agenda_events",
                                                              "create_agenda_event",
                                                              "create_date_poll");

  private static final Set<String> OBJECT_TOOLS      = Set.of("get_agenda_event_by_id",
                                                              "get_event_attendees",
                                                              "get_date_poll_results",
                                                              "get_event_conference",
                                                              "update_agenda_event",
                                                              "delete_agenda_event",
                                                              "cancel_agenda_event",
                                                              "set_event_conference",
                                                              "invite_users_to_agenda_event",
                                                              "invite_space_to_agenda_event",
                                                              "confirm_date_poll");

  private static final Set<String> WRITES            = Set.of("create_agenda_event",
                                                              "create_date_poll",
                                                              "update_agenda_event",
                                                              "delete_agenda_event",
                                                              "cancel_agenda_event",
                                                              "set_event_conference",
                                                              "invite_users_to_agenda_event",
                                                              "invite_space_to_agenda_event",
                                                              "confirm_date_poll");

  /**
   * Tools that read a person's own agenda, availability or conflicts, or
   * answer, vote or remind on one's own invitation: refused under a space
   * scope.
   */
  private static final Set<String> REFUSED           = Set.of("get_availability",
                                                              "suggest_meeting_time",
                                                              "get_schedule_conflicts",
                                                              "accept_agenda_event_invitation",
                                                              "decline_agenda_event_invitation",
                                                              "respond_to_agenda_event",
                                                              "set_event_reminders",
                                                              "vote_date_poll");

  private static final ObjectMapper MAPPER           = new ObjectMapper();

  /**
   * Exactly the expected tools declare a space scope, by the space argument
   * or by the event they reach and the {@value AgendaEventSpaceScopeResolver#NAME}
   * resolver, well formed for what they are, a read or a write. A tool added,
   * or one that stops declaring, fails here, every write needs an approval,
   * and every refused tool carries none.
   */
  @Test
  void declaredToolsAreTheExpectedOnes() {
    Map<String, SimpleToolDefinition> shipped = shipped();

    Set<String> declared = shipped.values()
                                  .stream()
                                  .filter(tool -> tool.getSpaceScope() != null)
                                  .map(SimpleToolDefinition::getName)
                                  .collect(Collectors.toCollection(TreeSet::new));
    Set<String> expected = Stream.concat(SPACE_TOOLS.stream(), OBJECT_TOOLS.stream()).collect(Collectors.toCollection(TreeSet::new));
    assertEquals(expected, declared);
    REFUSED.forEach(name -> {
      assertTrue(shipped.containsKey(name), name);
      assertEquals(null, shipped.get(name).getSpaceScope(), name);
    });
    assertEquals(shipped.keySet(), Stream.of(expected, REFUSED).flatMap(Set::stream).collect(Collectors.toSet()));
    declared.forEach(name -> {
      SimpleToolDefinition tool = shipped.get(name);
      SpaceScopeDeclaration scope = tool.getSpaceScope();
      boolean write = WRITES.contains(name);
      assertNotNull(tool.getAnnotations(), name);
      assertEquals(!write, McpToolSpaceScopeService.isReadTool(tool), name);
      assertEquals(write, tool.isRequireApproval(), name);
      assertTrue(scope.isWellFormedFor(write), name);
      if (SPACE_TOOLS.contains(name)) {
        assertTrue(scope.isSpaceKind(), name);
        assertEquals("space_id", scope.getSpaceArg(), name);
        assertEquals(!write, scope.isUseScopeWhenAbsent(), name);
      } else if ("confirm_date_poll".equals(name)) {
        assertTrue(scope.isObjectKind(), name);
        assertEquals("date_option_id", scope.getObjectArg(), name);
        assertEquals(AgendaEventDateOptionSpaceScopeResolver.NAME, scope.getResolver(), name);
      } else {
        assertTrue(scope.isObjectKind(), name);
        assertEquals("event_id", scope.getObjectArg(), name);
        assertEquals(AgendaEventSpaceScopeResolver.NAME, scope.getResolver(), name);
      }
    });
  }

  /**
   * The arguments a limited call may carry are schema properties, and none of
   * them reaches outside the space unchecked: the invited users and the
   * invited space are accepted by the invitation tools only, which check them
   * against the call's space ({@code AgendaEventMcpTool}); no other tool takes
   * attendees, and none takes a space name or an availability ranking. The
   * conflict probe of a write is false-only, and a date poll is confirmed by
   * its date only.
   */
  @Test
  void acceptedArgumentsAreTheExpectedSchemaProperties() throws Exception {
    Map<String, SimpleToolDefinition> shipped = shipped();
    List<String> recurrence = List.of("recurrence_frequency", "recurrence_interval", "recurrence_until", "recurrence_count");

    assertEquals(List.of("start", "end", "limit"), allowed(shipped.get("get_agenda_events")));
    assertEquals(List.of("query", "start", "end"), allowed(shipped.get("search_agenda_events")));
    assertEquals(Stream.concat(Stream.of("summary", "description", "location", "start", "end"), recurrence.stream()).toList(),
                 allowed(shipped.get("create_agenda_event")));
    assertEquals(Stream.concat(Stream.of("summary", "description", "location", "start", "end"), recurrence.stream()).toList(),
                 allowed(shipped.get("update_agenda_event")));
    assertEquals(List.of("title", "proposed_slots", "description"), allowed(shipped.get("create_date_poll")));
    assertEquals(List.of("occurrence_id"), allowed(shipped.get("cancel_agenda_event")));
    assertEquals(List.of("url", "type"), allowed(shipped.get("set_event_conference")));
    assertEquals(List.of("attendee_usernames"), allowed(shipped.get("invite_users_to_agenda_event")));
    assertEquals(List.of("space_id"), allowed(shipped.get("invite_space_to_agenda_event")));
    assertEquals(List.of(), allowed(shipped.get("confirm_date_poll")));
    for (String name : List.of("create_agenda_event", "update_agenda_event")) {
      assertEquals(List.of("fail_on_conflict"), shipped.get(name).getSpaceScope().getFalseOnlyArgs(), name);
    }
    for (SimpleToolDefinition tool : shipped.values()) {
      SpaceScopeDeclaration scope = tool.getSpaceScope();
      if (scope != null) {
        JsonNode properties = MAPPER.readTree(tool.inputSchema()).path("properties");
        List<String> accepted = Stream.of(allowed(tool),
                                          scope.getFalseOnlyArgs() == null ? List.<String> of() : scope.getFalseOnlyArgs(),
                                          List.of(scope.isSpaceKind() ? scope.getSpaceArg() : scope.getObjectArg()))
                                      .flatMap(List::stream)
                                      .toList();
        accepted.forEach(argument -> assertTrue(properties.has(argument), tool.getName() + " takes " + argument));
        assertEquals("invite_users_to_agenda_event".equals(tool.getName()), accepted.contains("attendee_usernames"), tool.getName());
        assertFalse(accepted.contains("attendees"), tool.getName());
        assertFalse(accepted.contains("space"), tool.getName());
        assertFalse(accepted.contains("rank_slots_by_availability"), tool.getName());
      }
    }
  }

  /**
   * The shipped definitions run through the server's check under a space
   * scope: a space read is filled with the call's space, a write names it, an
   * event of another space (or of none) is refused, and the arguments that
   * reach outside are refused.
   */
  @Test
  @SuppressWarnings("unchecked")
  void shippedDefinitionsAreEnforcedAsDeclared() {
    SpaceScopeResolver resolver = mock(SpaceScopeResolver.class);
    when(resolver.getName()).thenReturn("event");
    when(resolver.spaceOf("100")).thenReturn(12L);
    when(resolver.spaceOf("200")).thenReturn(13L);
    ObjectProvider<SpaceScopeResolver> provider = mock(ObjectProvider.class);
    when(provider.orderedStream()).thenAnswer(invocation -> Stream.of(resolver));
    McpToolSpaceScopeService service = new McpToolSpaceScopeService(provider);
    ActingIdentity read = ActingIdentity.agent("agent", "ASSISTANT", Origin.MENTION, 12L);
    ActingIdentity proposal = ActingIdentity.person("john", Origin.PROPOSAL, 12L);
    Map<String, SimpleToolDefinition> shipped = shipped();

    String filled = service.enforce("get_agenda_events", shipped.get("get_agenda_events"), "{\"limit\":5}", read);
    assertTrue(filled.contains("\"space_id\":12"), filled);
    assertThrows(SpaceScopeRefusedException.class,
                 () -> service.enforce("get_agenda_events", shipped.get("get_agenda_events"), "{\"space_id\":13}", read));
    assertThrows(SpaceScopeRefusedException.class,
                 () -> service.enforce("get_agenda_events", shipped.get("get_agenda_events"), "{\"space_id\":0}", read));
    String event = "{\"event_id\":100}";
    assertSame(event, service.enforce("get_agenda_event_by_id", shipped.get("get_agenda_event_by_id"), event, read));
    assertThrows(SpaceScopeRefusedException.class,
                 () -> service.enforce("get_agenda_event_by_id", shipped.get("get_agenda_event_by_id"), "{\"event_id\":200}", read));
    assertThrows(SpaceScopeRefusedException.class,
                 () -> service.enforce("get_agenda_event_by_id", shipped.get("get_agenda_event_by_id"), "{\"event_id\":300}", read));
    String create = "{\"space_id\":12,\"summary\":\"Sync\",\"start\":\"a\",\"end\":\"b\"}";
    assertSame(create, service.enforce("create_agenda_event", shipped.get("create_agenda_event"), create, proposal));
    for (String outside : List.of("{\"space_id\":12,\"summary\":\"s\",\"attendee_usernames\":[\"mary\"]}",
                                  "{\"space_id\":12,\"summary\":\"s\",\"fail_on_conflict\":true}",
                                  "{\"space\":\"Other\",\"summary\":\"s\"}",
                                  "{\"summary\":\"s\"}")) {
      assertThrows(SpaceScopeRefusedException.class,
                   () -> service.enforce("create_agenda_event", shipped.get("create_agenda_event"), outside, proposal));
    }
    assertThrows(SpaceScopeRefusedException.class,
                 () -> service.enforce("create_agenda_event", shipped.get("create_agenda_event"), create, read));
    String invite = "{\"event_id\":100,\"attendee_usernames\":[\"mary\"]}";
    assertSame(invite, service.enforce("invite_users_to_agenda_event", shipped.get("invite_users_to_agenda_event"), invite, proposal));
    String inviteSpace = "{\"event_id\":100,\"space_id\":12}";
    assertSame(inviteSpace,
               service.enforce("invite_space_to_agenda_event", shipped.get("invite_space_to_agenda_event"), inviteSpace, proposal));
    for (String outside : List.of("{\"event_id\":200,\"attendee_usernames\":[\"mary\"]}",
                                  "{\"event_id\":100,\"attendee_usernames\":[\"mary\"],\"space_id\":12}",
                                  "{\"attendee_usernames\":[\"mary\"]}")) {
      assertThrows(SpaceScopeRefusedException.class,
                   () -> service.enforce("invite_users_to_agenda_event", shipped.get("invite_users_to_agenda_event"), outside, proposal));
    }
    assertThrows(SpaceScopeRefusedException.class,
                 () -> service.enforce("invite_users_to_agenda_event", shipped.get("invite_users_to_agenda_event"), invite, read));
    assertThrows(SpaceScopeRefusedException.class,
                 () -> service.enforce("invite_space_to_agenda_event",
                                       shipped.get("invite_space_to_agenda_event"),
                                       "{\"event_id\":200,\"space_id\":12}",
                                       proposal));
  }

  /**
   * A date poll is confirmed under a space scope only through a date of a
   * poll of the call's space, through the real
   * {@value AgendaEventDateOptionSpaceScopeResolver#NAME} resolver and the
   * event's space: a date of another space's poll, a missing date, an id that
   * isn't one and any other argument are refused, and so is a call that isn't
   * an approved proposal.
   * Mutants: the declaration dropped, its resolver renamed, or its object
   * argument changed.
   */
  @Test
  @SuppressWarnings("unchecked")
  void aDatePollIsConfirmedThroughADateOfThePollsSpace() {
    org.exoplatform.agenda.service.AgendaEventDatePollService datePollService =
                                                                               mock(org.exoplatform.agenda.service.AgendaEventDatePollService.class);
    AgendaEventSpaceScopeResolver eventResolver = mock(AgendaEventSpaceScopeResolver.class);
    when(eventResolver.spaceOf("100")).thenReturn(12L);
    when(eventResolver.spaceOf("200")).thenReturn(13L);
    org.exoplatform.agenda.model.EventDateOption inSpace = new org.exoplatform.agenda.model.EventDateOption();
    inSpace.setEventId(100L);
    org.exoplatform.agenda.model.EventDateOption outside = new org.exoplatform.agenda.model.EventDateOption();
    outside.setEventId(200L);
    when(datePollService.getEventDateOption(eq(7L), any())).thenReturn(inSpace);
    when(datePollService.getEventDateOption(eq(8L), any())).thenReturn(outside);
    AgendaEventDateOptionSpaceScopeResolver resolver = new AgendaEventDateOptionSpaceScopeResolver(datePollService, eventResolver);
    ObjectProvider<SpaceScopeResolver> provider = mock(ObjectProvider.class);
    when(provider.orderedStream()).thenAnswer(invocation -> Stream.of(resolver));
    McpToolSpaceScopeService service = new McpToolSpaceScopeService(provider);
    ActingIdentity proposal = ActingIdentity.person("john", Origin.PROPOSAL, 12L);
    ActingIdentity read = ActingIdentity.agent("agent", "ASSISTANT", Origin.MENTION, 12L);
    SimpleToolDefinition tool = shipped().get("confirm_date_poll");

    String confirm = "{\"date_option_id\":7}";
    assertSame(confirm, service.enforce("confirm_date_poll", tool, confirm, proposal));
    assertThrows(SpaceScopeRefusedException.class, () -> service.enforce("confirm_date_poll", tool, confirm, read));
    for (String refused : List.of("{\"date_option_id\":8}",
                                  "{\"date_option_id\":9}",
                                  "{\"date_option_id\":0}",
                                  "{\"date_option_id\":7,\"event_id\":100}",
                                  "{}")) {
      assertThrows(SpaceScopeRefusedException.class, () -> service.enforce("confirm_date_poll", tool, refused, proposal), refused);
    }
  }

  /**
   * @param tool a tool declaring a space scope
   * @return its allowed arguments, empty when it names none
   */
  private static List<String> allowed(SimpleToolDefinition tool) {
    List<String> allowed = tool.getSpaceScope().getAllowedArgs();
    return allowed == null ? List.of() : allowed;
  }

  /**
   * @return the shipped agenda tool definitions, by name
   */
  private static Map<String, SimpleToolDefinition> shipped() {
    URL url = AgendaToolDefinitionSpaceScopeTest.class.getClassLoader().getResource("ai-tool-definitions.json");
    assertNotNull(url, "ai-tool-definitions.json is not on the classpath");
    return McpToolUtils.parseToolDefinitions(url).stream().collect(Collectors.toMap(SimpleToolDefinition::getName, tool -> tool));
  }

}
