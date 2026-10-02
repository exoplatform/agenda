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

import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.HeldMailInvitation;
import org.exoplatform.agenda.model.LandedMailInvitation;
import org.exoplatform.agenda.model.MailInvitation;
import org.exoplatform.agenda.service.AgendaInvitationLandingService;
import org.exoplatform.emailConnector.model.HeldInvitation;
import org.exoplatform.emailConnector.model.InvitationAnswer;
import org.exoplatform.emailConnector.model.InvitationLanding;
import org.exoplatform.emailConnector.model.InvitationProbe;
import org.exoplatform.emailConnector.model.LandedInvitation;
import org.exoplatform.emailConnector.plugin.InvitationCalendarPlugin;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Lands, for the mail reader, an invitation the user received by mail in their
 * personal eXo calendar, when no calendar server holds that user's calendar
 * (EXO-90866).
 * <p>
 * The mail reader finds this bean by type across add-ons and asks the first
 * that holds the user's calendar, alone. So agenda holds it only when no other
 * add-on does: a user whose calendar caldav-integration holds is that
 * add-on's, and landing the invitation here as well would put the meeting
 * twice in eXo. Agenda does not read the other add-on's settings to predict its
 * answer — two predicates drift (EXO-90247) — it asks it, through the same SPI,
 * at the moment the reader asks.
 * <p>
 * The bean is not created at all on a server without the mail reader
 * ({@code @ConditionalOnClass}, read from the class metadata, never loading
 * the class). A {@code @Service} with a name of its own, because that is what
 * the bridge exports to the reader's context. <b>The only class of agenda that
 * names a type of the reader's</b>: Spring introspects every method of every
 * bean it creates, so the landing service speaks agenda's own
 * {@link MailInvitation} and {@link LandedMailInvitation}, and this bean
 * translates both ways. It carries no landing logic:
 * {@link AgendaInvitationLandingService} decides and acts.
 */
@Service("agendaInvitationCalendarPlugin")
@ConditionalOnClass(InvitationCalendarPlugin.class)
public class AgendaInvitationCalendarPlugin implements InvitationCalendarPlugin {

  private static final Log                     LOG    = ExoLogger.getLogger(AgendaInvitationCalendarPlugin.class);

  /**
   * Set while this bean asks the other add-ons, so that an add-on asking agenda
   * back, by the same rule, is told no rather than asked again for ever.
   */
  private static final ThreadLocal<Boolean>    ASKING = new ThreadLocal<>();

  private final AgendaInvitationLandingService agendaInvitationLandingService;

  private final ApplicationContext             applicationContext;

  /**
   * Builds the plugin over the landing service and the context the other
   * add-ons' plugins are found in.
   *
   * @param agendaInvitationLandingService decides and acts
   * @param applicationContext the Spring context of this WAR, into which the
   *          bridge publishes every other WAR's exported beans
   */
  @Autowired
  public AgendaInvitationCalendarPlugin(AgendaInvitationLandingService agendaInvitationLandingService,
                                        ApplicationContext applicationContext) {
    this.agendaInvitationLandingService = agendaInvitationLandingService;
    this.applicationContext = applicationContext;
  }

  /**
   * Whether agenda holds the user's calendar: an enabled user of the platform
   * who connected no remote calendar account to agenda, and whose calendar no
   * other add-on holds.
   *
   * @param username the user's login
   * @return true when the invitation is agenda's to land
   */
  @Override
  public boolean holdsCalendarFor(String username) {
    if (Boolean.TRUE.equals(ASKING.get())) {
      return false;
    }
    return agendaInvitationLandingService.holdsCalendarFor(username) && !heldElsewhere(username);
  }

  /**
   * Lands the invitation in the user's personal eXo calendar.
   *
   * @param landing the invitation, the user and what they asked
   * @return what was done, null when agenda does not hold the user's calendar
   *         or there was nothing to do
   * @throws IllegalArgumentException when the invitation cannot be landed as it
   *           is
   * @throws RuntimeException when the landing was attempted and failed
   */
  @Override
  public LandedInvitation land(InvitationLanding landing) {
    if (!holdsCalendarFor(landing.username())) {
      // Asked anyway: the contract's null, never a second copy.
      return null;
    }
    LandedMailInvitation landed =
                                agendaInvitationLandingService.land(new MailInvitation(landing.username(),
                                                                                       landing.attendeeAddress(),
                                                                                       landing.method(),
                                                                                       landing.uid(),
                                                                                       landing.recurrenceId(),
                                                                                       landing.sequence(),
                                                                                       landing.answer() == null ? null
                                                                                                                : EventAttendeeResponse.valueOf(landing.answer()
                                                                                                                                                       .name()),
                                                                                       landing.icalendar()));
    return landed == null ? null : new LandedInvitation(landed.eventId(), landed.link(), landed.removed(), landed.alreadyHeld());
  }

  /**
   * The event the user's personal calendar holds for the invitation, when agenda
   * holds that calendar (EXO-90873): the answer as agenda holds it, NEEDS_ACTION
   * told as none.
   *
   * @param probe the user and the event's UID
   * @return the event held, null when agenda does not hold the user's calendar
   *         or holds no event for the invitation
   */
  @Override
  public HeldInvitation held(InvitationProbe probe) {
    if (!holdsCalendarFor(probe.username())) {
      // The same step-aside as a landing: another add-on's calendar is not told of here.
      return null;
    }
    HeldMailInvitation held = agendaInvitationLandingService.held(probe.username(),
                                                                               probe.attendeeAddress(),
                                                                               probe.uid(),
                                                                               probe.recurrenceId(),
                                                                               probe.organizer());
    if (held == null) {
      return null;
    }
    InvitationAnswer answer = held.response() == null ? null : InvitationAnswer.ofPartStat(held.response().name());
    return new HeldInvitation(held.eventId(), held.link(), answer, held.sequence());
  }

  /**
   * Whether another add-on holds the user's calendar, or may: one that cannot
   * say, or a list of add-ons that cannot be read, is read as holding it.
   * Failing closed costs the user an offer to add the invitation; failing open
   * could land it in eXo for a user whose calendar server holds it, where the
   * server's own copy, once synced, makes it a second event.
   *
   * @param username the user's login
   * @return true when another add-on holds it, or cannot say it does not
   */
  private boolean heldElsewhere(String username) {
    ASKING.set(Boolean.TRUE);
    try {
      Collection<InvitationCalendarPlugin> others = otherPlugins();
      if (others == null) {
        return true;
      }
      for (InvitationCalendarPlugin plugin : others) {
        try {
          if (plugin.holdsCalendarFor(username)) {
            return true;
          }
        } catch (RuntimeException | LinkageError e) {
          LOG.debug("Add-on {} could not say whether it holds a calendar for user {}; agenda does not claim it",
                    plugin.getClass().getName(),
                    username,
                    e);
          return true;
        }
      }
      return false;
    } finally {
      ASKING.remove();
    }
  }

  /**
   * The other add-ons' plugins, looked up on every call: a type lookup Spring
   * caches, and the only way an add-on booting after agenda is seen.
   *
   * @return the plugins other than agenda's, empty when there is none; null
   *         when they cannot be listed
   */
  private Collection<InvitationCalendarPlugin> otherPlugins() {
    try {
      return applicationContext == null ? null
                                        : applicationContext.getBeansOfType(InvitationCalendarPlugin.class)
                                                            .values()
                                                            .stream()
                                                            .filter(plugin -> plugin != this
                                                                && !(plugin instanceof AgendaInvitationCalendarPlugin))
                                                            .toList();
    } catch (RuntimeException | LinkageError e) {
      LOG.debug("The add-ons that land invitations could not be listed; agenda does not claim the user's calendar", e);
      return null; // NOSONAR null is "cannot say", distinct from "nobody else"
    }
  }
}
