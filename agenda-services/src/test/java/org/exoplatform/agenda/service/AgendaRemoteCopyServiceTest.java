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
package org.exoplatform.agenda.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;

import org.exoplatform.agenda.model.AgendaConnectorAccount;
import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.plugin.RemoteEventCopyPlugin;
import org.exoplatform.agenda.util.NotificationUtils;

/**
 * Agenda decides whether the invitation mail carries {@code event.ics} by
 * asking the add-on that would write the recipient's synced copy, and only
 * falls back to reading the recipient's own settings when there is no add-on to
 * ask (EXO-90247).
 *
 * <p>
 * The two cases these exist for are the two directions the old settings-only
 * prediction drifted in, each measured on the rig:
 * <ul>
 * <li>the add-on writes a copy where the settings predicted none — the meeting
 * arrived twice;</li>
 * <li>the add-on declines a copy where the settings predicted one — the meeting
 * arrived <b>nowhere</b>, which is the regression the first attempt at this
 * task shipped.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendaRemoteCopyServiceTest {

  private static final long   INTERNAL_USER = 2223L;

  private static final long   GUEST         = 0L;

  private static final String CALDAV        = "agenda.caldavCalendar";

  @Mock
  private ApplicationContext  applicationContext;

  @InjectMocks
  private AgendaRemoteCopyService service;

  /**
   * The Finding-2 case, and the one this class exists for: the recipient's
   * settings say a copy is coming — connected to CalDAV, copies on, the global
   * switch on — but the add-on that would write it says it is writing none. The
   * file is the recipient's only remaining delivery and must be attached.
   */
  @Test
  void attachesWhenTheAddonDeclinesTheCopyTheSettingsPredict() {
    registerPlugins(pluginAnswering(false));

    assertTrue(service.shouldAttachIcsFile(event(), INTERNAL_USER, settingsWith(true, account(CALDAV, true))),
               "an invitee the add-on writes no copy for must still receive the file, whatever their settings predict");
  }

  /**
   * The other direction of the same drift: the settings predict no copy — the
   * global switch is off — while the add-on's server-side seeding pass, which
   * does not read that switch, writes one anyway. Attaching the file there is
   * what put the meeting twice in the recipient's calendar.
   */
  @Test
  void suppressesWhenTheAddonWritesACopyTheSettingsDoNotPredict() {
    registerPlugins(pluginAnswering(true));

    assertFalse(service.shouldAttachIcsFile(event(), INTERNAL_USER, settingsWith(false, account(CALDAV, true))),
                "a copy the add-on says it writes makes the file redundant, whatever the settings predict");
  }

  /**
   * A guest has no account for a copy to land in, so the file is their only way
   * to get the meeting — and no add-on's answer may take it away from them.
   */
  @Test
  void neverSuppressesForAGuestEvenWhenAnAddonClaimsACopy() {
    registerPlugins(pluginAnswering(true));

    assertTrue(service.shouldAttachIcsFile(event(), GUEST, null), "a guest is never suppressed");
  }

  /**
   * One add-on answering yes is enough, and the ones after it are not asked —
   * the recipient holds a copy, whoever writes it.
   */
  @Test
  void oneAddonClaimingACopyIsEnough() {
    RemoteEventCopyPlugin writes = pluginAnswering(true);
    RemoteEventCopyPlugin declines = pluginAnswering(false);
    registerPlugins(declines, writes);

    assertFalse(service.shouldAttachIcsFile(event(), INTERNAL_USER, settingsWith(true, account(CALDAV, true))),
                "the second add-on writing a copy suppresses the file");
    verify(declines).writesCopyOf(any(Event.class), eq(INTERNAL_USER));
  }

  /**
   * An add-on that fails is read as writing no copy, and the others are still
   * asked: the one that throws must not suppress the file on behalf of the ones
   * that did not answer.
   */
  @Test
  void anAddonThatFailsWritesNoCopyAndDoesNotStopTheOthers() {
    RemoteEventCopyPlugin broken = mock(RemoteEventCopyPlugin.class);
    when(broken.writesCopyOf(any(), anyLong())).thenThrow(new IllegalStateException("cannot tell"));
    RemoteEventCopyPlugin declines = pluginAnswering(false);
    registerPlugins(broken, declines);

    assertTrue(service.shouldAttachIcsFile(event(), INTERNAL_USER, settingsWith(true, account(CALDAV, true))),
               "a failure answers no copy, so the file is attached");
    verify(declines).writesCopyOf(any(Event.class), eq(INTERNAL_USER));
  }

  /**
   * With no add-on installed there is nobody to ask, and the settings-derived
   * prediction agenda made before this existed is still the best answer: a user
   * connected with copies on is predicted to hold one.
   */
  @Test
  void fallsBackToTheSettingsWhenNoAddonIsRegistered() {
    registerPlugins();

    assertFalse(service.shouldAttachIcsFile(event(), INTERNAL_USER, settingsWith(true, account(CALDAV, true))),
                "with nobody to ask, the connected-with-copies prediction stands");
    assertTrue(service.shouldAttachIcsFile(event(), INTERNAL_USER, settingsWith(false, account(CALDAV, true))),
               "with nobody to ask, the global switch being off predicts no copy");
  }

  /**
   * A cancellation whose event agenda no longer holds cannot be put to any
   * add-on, so the settings-derived prediction stands there too — and no
   * add-on is asked about a meeting that is not in hand.
   */
  @Test
  void fallsBackToTheSettingsWhenTheEventIsGone() {
    RemoteEventCopyPlugin plugin = pluginAnswering(false);
    registerPlugins(plugin);

    assertFalse(service.shouldAttachIcsFile(null, INTERNAL_USER, settingsWith(true, account(CALDAV, true))),
                "with no event to ask about, the settings prediction stands");
  }

  /**
   * A context that cannot be asked is "nobody to ask", not a failure of the
   * notification: the settings prediction stands and the mail is still built.
   */
  @Test
  void fallsBackToTheSettingsWhenThePluginsCannotBeListed() {
    when(applicationContext.getBeansOfType(RemoteEventCopyPlugin.class)).thenThrow(new IllegalStateException("no context"));

    assertTrue(service.shouldAttachIcsFile(event(), INTERNAL_USER, settingsWith(true)),
               "an unlistable context falls back rather than failing");
  }

  /**
   * <b>The safety property of the whole delivery, enumerated.</b> Over every
   * combination of the two settings switches, the recipient being connected or
   * not, a guest or an internal user, and the add-on answering yes, no or
   * throwing: the file is withheld <i>only</i> when the add-on positively says
   * it is writing that recipient a copy. Anything else — a no, a failure, a
   * guest — leaves the recipient the file.
   *
   * <p>
   * This is the invariant the first attempt at EXO-90247 broke, and it broke it
   * in a combination nobody had enumerated: the add-on declining while the
   * settings predicted a copy. Written as a sweep rather than as cases so that
   * a new reason to decline a copy cannot slip through an unlisted corner, and
   * as an <b>equivalence</b> rather than an implication so that neither
   * direction can drift — the file must be there whenever no copy is claimed,
   * and gone whenever one is.
   */
  @Test
  void theFileGoesOnlyWhenAnAddonClaimsTheCopy() {
    for (boolean automaticPushEvents : new boolean[] { true, false }) {
      for (boolean pushEnabled : new boolean[] { true, false }) {
        for (boolean connected : new boolean[] { true, false }) {
          for (long recipient : new long[] { INTERNAL_USER, GUEST }) {
            for (String addon : new String[] { "writes", "declines", "throws" }) {
              AgendaUserSettings settings = connected ? settingsWith(automaticPushEvents, account(CALDAV, pushEnabled))
                                                      : settingsWith(automaticPushEvents);
              boolean copyClaimed = "writes".equals(addon) && recipient > 0;
              installAddon(addon);

              boolean attached = service.shouldAttachIcsFile(event(), recipient, settings);

              assertEquals(!copyClaimed,
                           attached,
                           "recipient=" + recipient + " addon=" + addon + " connected=" + connected
                               + " automaticPushEvents=" + automaticPushEvents + " pushEnabled=" + pushEnabled);
            }
          }
        }
      }
    }
  }

  /**
   * And with no add-on installed the answer is exactly the one agenda gave
   * before any of this existed, over the same sweep — the fallback is a
   * fallback, not a second opinion. Kept apart from the sweep above because
   * there is nobody to claim a copy there, so the invariant it states is a
   * different one.
   */
  @Test
  void withNoAddonTheAnswerIsTheLegacyPrediction() {
    registerPlugins();
    for (boolean automaticPushEvents : new boolean[] { true, false }) {
      for (boolean pushEnabled : new boolean[] { true, false }) {
        for (boolean connected : new boolean[] { true, false }) {
          AgendaUserSettings settings = connected ? settingsWith(automaticPushEvents, account(CALDAV, pushEnabled))
                                                  : settingsWith(automaticPushEvents);

          assertEquals(NotificationUtils.shouldAttachIcsFile(INTERNAL_USER, settings),
                       service.shouldAttachIcsFile(event(), INTERNAL_USER, settings),
                       "connected=" + connected + " automaticPushEvents=" + automaticPushEvents + " pushEnabled="
                           + pushEnabled);
        }
      }
    }
  }

  /**
   * Publishes one of the three add-on behaviours the sweep enumerates.
   *
   * @param addon "writes", "declines" or "throws"
   */
  private void installAddon(String addon) {
    switch (addon) {
    case "writes" -> registerPlugins(pluginAnswering(true));
    case "declines" -> registerPlugins(pluginAnswering(false));
    default -> {
      RemoteEventCopyPlugin broken = mock(RemoteEventCopyPlugin.class);
      when(broken.writesCopyOf(any(), anyLong())).thenThrow(new IllegalStateException("cannot tell"));
      registerPlugins(broken);
    }
    }
  }

  /**
   * Publishes the given plugins as the beans of their type in the context.
   *
   * @param plugins the add-ons agenda will find, possibly none
   */
  private void registerPlugins(RemoteEventCopyPlugin... plugins) {
    Map<String, RemoteEventCopyPlugin> beans = new LinkedHashMap<>();
    for (int i = 0; i < plugins.length; i++) {
      beans.put("plugin" + i, plugins[i]);
    }
    when(applicationContext.getBeansOfType(RemoteEventCopyPlugin.class)).thenReturn(beans);
  }

  /**
   * An add-on that always gives the same answer.
   *
   * @param writesCopy what it answers
   * @return the plugin
   */
  private RemoteEventCopyPlugin pluginAnswering(boolean writesCopy) {
    RemoteEventCopyPlugin plugin = mock(RemoteEventCopyPlugin.class);
    when(plugin.writesCopyOf(any(), anyLong())).thenReturn(writesCopy);
    return plugin;
  }

  /**
   * A meeting to ask about; only its identity matters here.
   *
   * @return the event
   */
  private Event event() {
    Event event = new Event();
    event.setId(4242L);
    return event;
  }

  /**
   * Builds settings holding the given connected accounts.
   *
   * @param automaticPushEvents value of the global copy switch
   * @param accounts connected accounts, possibly none
   * @return the settings
   */
  private AgendaUserSettings settingsWith(boolean automaticPushEvents, AgendaConnectorAccount... accounts) {
    AgendaUserSettings settings = new AgendaUserSettings();
    settings.setAutomaticPushEvents(automaticPushEvents);
    List<AgendaConnectorAccount> connectedAccounts = new ArrayList<>();
    for (AgendaConnectorAccount account : accounts) {
      connectedAccounts.add(account);
    }
    settings.setConnectedConnectors(connectedAccounts);
    return settings;
  }

  /**
   * One connected account on the given provider.
   *
   * @param providerName name of the remote provider
   * @param pushEnabled whether that account receives copies
   * @return the account
   */
  private AgendaConnectorAccount account(String providerName, boolean pushEnabled) {
    return new AgendaConnectorAccount(providerName, "user@example.com", pushEnabled);
  }
}
