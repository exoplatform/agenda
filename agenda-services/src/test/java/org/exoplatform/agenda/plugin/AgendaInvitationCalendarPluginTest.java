/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.LandedMailInvitation;
import org.exoplatform.agenda.model.MailInvitation;
import org.exoplatform.agenda.service.AgendaInvitationLandingService;
import org.exoplatform.emailConnector.model.InvitationAnswer;
import org.exoplatform.emailConnector.model.InvitationLanding;
import org.exoplatform.emailConnector.model.LandedInvitation;
import org.exoplatform.emailConnector.plugin.InvitationCalendarPlugin;

/**
 * Agenda holds a user's calendar for the mail reader only when no other add-on
 * does — the reader asks the first holder alone, so agenda answering yes for a
 * user caldav-integration holds would land the invitation in the wrong
 * calendar (EXO-90866).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendaInvitationCalendarPluginTest {

  private static final String            USER = "john";

  @Mock
  private AgendaInvitationLandingService landingService;

  @Mock
  private ApplicationContext             applicationContext;

  private AgendaInvitationCalendarPlugin plugin;

  private final Map<String, InvitationCalendarPlugin> plugins = new LinkedHashMap<>();

  /**
   * The plugin over a context holding itself and the plugins a test adds.
   */
  @BeforeEach
  void setUp() {
    plugin = new AgendaInvitationCalendarPlugin(landingService, applicationContext);
    plugins.put("agendaInvitationCalendarPlugin", plugin);
    when(applicationContext.getBeansOfType(InvitationCalendarPlugin.class)).thenReturn(plugins);
    when(landingService.holdsCalendarFor(USER)).thenReturn(true);
  }

  /**
   * With no other add-on, agenda holds the calendar of a user it can serve, and
   * of nobody else.
   */
  @Test
  void holdsTheCalendarWhenNoOtherAddonDoes() {
    assertTrue(plugin.holdsCalendarFor(USER));
    assertFalse(plugin.holdsCalendarFor("ghost"));
  }

  /**
   * A user whose calendar another add-on holds is that add-on's; one the other
   * add-on does not hold is agenda's.
   */
  @Test
  void yieldsToAnAddonHoldingTheCalendar() {
    InvitationCalendarPlugin caldav = mock(InvitationCalendarPlugin.class);
    plugins.put("caldavInvitationCalendarPlugin", caldav);

    when(caldav.holdsCalendarFor(USER)).thenReturn(true);
    assertFalse(plugin.holdsCalendarFor(USER));

    when(caldav.holdsCalendarFor(USER)).thenReturn(false);
    assertTrue(plugin.holdsCalendarFor(USER));
  }

  /**
   * An add-on that cannot say, or add-ons that cannot be listed, make agenda
   * not claim the calendar: a landing in the wrong calendar is the worse error.
   */
  @Test
  void failsClosedWhenItCannotAsk() {
    InvitationCalendarPlugin broken = mock(InvitationCalendarPlugin.class);
    when(broken.holdsCalendarFor(USER)).thenThrow(new IllegalStateException("database down"));
    plugins.put("broken", broken);
    assertFalse(plugin.holdsCalendarFor(USER));

    when(applicationContext.getBeansOfType(InvitationCalendarPlugin.class)).thenThrow(new IllegalStateException("closed"));
    assertFalse(plugin.holdsCalendarFor(USER));
  }

  /**
   * An add-on that asks agenda back, by the same rule, is told no rather than
   * asked again for ever, and agenda then holds the calendar if that add-on
   * does not.
   */
  @Test
  void anAddonAskingBackIsToldNo() {
    InvitationCalendarPlugin fallback = mock(InvitationCalendarPlugin.class);
    when(fallback.holdsCalendarFor(USER)).thenAnswer(invocation -> plugin.holdsCalendarFor(USER));
    plugins.put("fallback", fallback);

    assertTrue(plugin.holdsCalendarFor(USER));
  }

  /**
   * The landing is translated both ways, the answer by its name.
   */
  @Test
  void translatesTheLanding() {
    when(landingService.land(any())).thenReturn(new LandedMailInvitation(42L, "http://x/portal/dw/agenda?eventId=42", false, false));

    LandedInvitation landed = plugin.land(new InvitationLanding(USER,
                                                                "john@mail.example",
                                                                "REQUEST",
                                                                "uid-1",
                                                                null,
                                                                3,
                                                                InvitationAnswer.TENTATIVE,
                                                                "BEGIN:VCALENDAR"));

    assertEquals(new LandedInvitation(42L, "http://x/portal/dw/agenda?eventId=42", false, false), landed);
    ArgumentCaptor<MailInvitation> captor = ArgumentCaptor.forClass(MailInvitation.class);
    verify(landingService).land(captor.capture());
    MailInvitation invitation = captor.getValue();
    assertEquals(USER, invitation.username());
    assertEquals("john@mail.example", invitation.attendeeAddress());
    assertEquals("uid-1", invitation.uid());
    assertEquals(3, invitation.sequence());
    assertEquals(EventAttendeeResponse.TENTATIVE, invitation.response());
  }

  /**
   * Asked to land for a user whose calendar another add-on holds, agenda lands
   * nothing: the contract's null, never a second copy.
   */
  @Test
  void landsNothingForAUserItDoesNotHold() {
    InvitationCalendarPlugin caldav = mock(InvitationCalendarPlugin.class);
    when(caldav.holdsCalendarFor(USER)).thenReturn(true);
    plugins.put("caldavInvitationCalendarPlugin", caldav);

    assertNull(plugin.land(new InvitationLanding(USER, null, "REQUEST", "uid-1", null, 0, null, "BEGIN:VCALENDAR")));
    verify(landingService, never()).land(any());
  }

  /**
   * The wiring the reader depends on: a {@code @Service} with a name of its
   * own, which the bridge exports, created only when the reader's SPI is on
   * the class path. A test calling the methods by hand cannot see either
   * annotation removed.
   */
  @Test
  void isAnExportedBeanConditionalOnTheReader() {
    Service service = AgendaInvitationCalendarPlugin.class.getAnnotation(Service.class);
    assertEquals("agendaInvitationCalendarPlugin", service.value());
    ConditionalOnClass condition = AgendaInvitationCalendarPlugin.class.getAnnotation(ConditionalOnClass.class);
    assertEquals(InvitationCalendarPlugin.class, condition.value()[0]);
  }
}
