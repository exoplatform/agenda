/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.model.AgendaConnectorAccount;
import org.exoplatform.agenda.model.AgendaUserSettings;

/**
 * A user who holds the eXo Meetings copy of a meeting <b>and</b> the
 * {@code event.ics} file mailed with their notification ends up with the same
 * meeting twice in their calendar, under two different UIDs no client can
 * reconcile. The file is the one that must go — but only for the recipients
 * who will actually hold a copy, and <b>never</b> for a guest, who has no copy
 * by definition and for whom the file is the only way to get the meeting.
 *
 * <p>
 * These pin the whole truth table of that decision, which is a prediction read
 * from the recipient's own settings rather than an observation of a copy: at
 * the moment the mail is built the copy does not exist yet.
 */
class IcsAttachmentSuppressionTest {

  private static final long   INTERNAL_USER    = 2223L;

  private static final long   GUEST            = 0L;

  private static final String CALDAV           = "agenda.caldavCalendar";

  private static final String DECLARED_CALDAV  = "agenda.caldavCalendar.7";

  private static final String GOOGLE           = "agenda.googleCalendar";

  /**
   * The case the task exists for: connected to CalDAV with meeting copies on,
   * so the copy is coming and the file would duplicate it.
   */
  @Test
  void suppressesForAConnectedUserWithCopiesEnabled() {
    AgendaUserSettings settings = settingsWith(true, account(CALDAV, true));

    assertFalse(NotificationUtils.shouldAttachIcsFile(INTERNAL_USER, settings),
                "a user connected to CalDAV with copies enabled must not receive the redundant file");
  }

  /**
   * A CalDAV server declared beside the seed one registers itself as
   * {@code agenda.caldavCalendar.<id>} and pushes into the same mirror
   * calendar, so it suppresses just the same.
   */
  @Test
  void suppressesForAnAccountOnADeclaredCaldavServer() {
    AgendaUserSettings settings = settingsWith(true, account(DECLARED_CALDAV, true));

    assertFalse(NotificationUtils.shouldAttachIcsFile(INTERNAL_USER, settings),
                "an account on a declared CalDAV server holds copies too");
  }

  /**
   * A legacy settings blob carries the single connected account in the two
   * deprecated scalar fields; it materialises into the account list on read,
   * and must suppress exactly like a blob written in the new shape.
   */
  @Test
  @SuppressWarnings("deprecation")
  void suppressesForALegacySingleConnectorBlob() {
    AgendaUserSettings settings = new AgendaUserSettings();
    settings.setAutomaticPushEvents(true);
    settings.setConnectedRemoteProvider(CALDAV);
    settings.setConnectedRemoteUserId("user@example.com");

    assertFalse(NotificationUtils.shouldAttachIcsFile(INTERNAL_USER, settings),
                "a legacy blob names a connected CalDAV account just as well");
  }

  /**
   * Copies switched off per account: nothing is pushed, so the file is the
   * only calendar object this user gets.
   */
  @Test
  void attachesWhenTheAccountOptedOutOfCopies() {
    AgendaUserSettings settings = settingsWith(true, account(CALDAV, false));

    assertTrue(NotificationUtils.shouldAttachIcsFile(INTERNAL_USER, settings),
               "an account opted out of copies receives no copy, so it keeps the file");
  }

  /**
   * Copies switched off globally, which is the switch the settings page
   * offers: same conclusion, by the other half of the condition.
   */
  @Test
  void attachesWhenCopiesAreSwitchedOffGlobally() {
    AgendaUserSettings settings = settingsWith(false, account(CALDAV, true));

    assertTrue(NotificationUtils.shouldAttachIcsFile(INTERNAL_USER, settings),
               "copies switched off globally means no copy is pushed to any account");
  }

  /**
   * Not connected at all — the common case, and the one that must not
   * regress.
   */
  @Test
  void attachesWhenNoAccountIsConnected() {
    AgendaUserSettings settings = settingsWith(true);

    assertTrue(NotificationUtils.shouldAttachIcsFile(INTERNAL_USER, settings),
               "a user with no connected account has no copy to be duplicated");
  }

  /**
   * Connected to a remote provider that is not CalDAV: the eXo Meetings mirror
   * this task is about is the CalDAV one, and the decision is scoped to it.
   */
  @Test
  void attachesWhenOnlyANonCaldavAccountIsConnected() {
    AgendaUserSettings settings = settingsWith(true, account(GOOGLE, true));

    assertTrue(NotificationUtils.shouldAttachIcsFile(INTERNAL_USER, settings),
               "the suppression is scoped to the CalDAV mirror");
  }

  /**
   * The clause that holds under any condition: a guest never resolves to an
   * organization identity, so it arrives here with an id of 0. Even handed
   * settings that would suppress for an internal user, it keeps its file —
   * until EXO-89705 lands, that file is the only way a guest gets the meeting
   * at all.
   */
  @Test
  void neverSuppressesForAGuest() {
    AgendaUserSettings settings = settingsWith(true, account(CALDAV, true));

    assertTrue(NotificationUtils.shouldAttachIcsFile(GUEST, settings),
               "a guest keeps the file whatever the settings say");
  }

  /**
   * Settings that could not be read at all: nothing is known, so nothing is
   * predicted and the file stays.
   */
  @Test
  void attachesWhenSettingsAreUnknown() {
    assertTrue(NotificationUtils.shouldAttachIcsFile(INTERNAL_USER, null),
               "unknown settings predict no copy");
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
