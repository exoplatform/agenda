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

import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.plugin.RemoteEventCopyPlugin;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Whether a recipient is going to hold a synced copy of a meeting in a remote
 * calendar account — asked of the add-on that would write it, not guessed at
 * (EXO-90247).
 *
 * <h2>The defect this exists to make impossible</h2>
 *
 * <p>
 * Two repositories were each predicting what the other would do. Agenda read
 * the recipient's settings to decide whether to attach {@code event.ics} to the
 * invitation mail; the CalDAV add-on read its own, different set of conditions
 * to decide whether to write the copy. Neither knew the other's rules, so the
 * two answers drifted — and the drift is invisible in both repositories'
 * tests, because each is right about its own half.
 *
 * <p>
 * Both directions of the drift were live at once. The add-on's server-side
 * seeding pass writes a copy for a user whose {@code automaticPushEvents} is
 * off, while agenda's prediction requires that switch and so attached the file
 * as well: the meeting twice. And when the add-on gained a reason of its own to
 * decline a copy for a particular invitee, agenda still predicted one and left
 * the file out: the meeting nowhere at all, which is what the first attempt at
 * EXO-90247 shipped and what the rig measured.
 *
 * <p>
 * The fix is not to align the two predicates by hand — they would drift again,
 * and the next drift is the next silent invitation. It is to leave exactly one
 * party holding the answer: whoever writes the copy says whether it is coming.
 *
 * <h2>Why the plugins are looked up when the question is asked</h2>
 *
 * <p>
 * The contributing add-on depends on agenda, so its WAR boots after agenda's.
 * Injecting a {@code List} of the plugins would take a snapshot at agenda's own
 * refresh; asking the context at call time takes none, so nothing here depends
 * on the boot order of the two add-ons. It costs a type lookup Spring caches,
 * on a path that already builds a mail.
 *
 * <h2>What it answers when it cannot ask</h2>
 *
 * <p>
 * Agenda ships alone in deployments where no add-on writes copies at all, and
 * there the settings-derived prediction is still the best available answer —
 * so it stays, as the fallback for "nobody to ask", and is used nowhere else.
 */
@Service
public class AgendaRemoteCopyService {

  private static final Log         LOG = ExoLogger.getLogger(AgendaRemoteCopyService.class);

  private final ApplicationContext applicationContext;

  /**
   * Builds the service over the context the plugins are collected from.
   *
   * <p>
   * The context is taken through the constructor rather than a field so the
   * wiring can be exercised end to end: a test can stand one of these up over a
   * context holding a chosen plugin, register it in the container, and assert
   * on the mail the notification builder actually produces.
   *
   * @param applicationContext the Spring context of this WAR, into which the
   *          bridge has published every other WAR's exported beans
   */
  @Autowired
  public AgendaRemoteCopyService(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  /**
   * Whether the {@code event.ics} file must be attached to the notification
   * built for this recipient.
   *
   * <p>
   * The file is left out only for a recipient a copy of this meeting is really
   * going to be written for. Everything else — a guest, a user no add-on claims,
   * a meeting the add-on will not copy, an add-on that cannot answer — gets the
   * file, because a redundant file is a visible nuisance and a missing one is an
   * invitation nobody ever receives.
   *
   * @param event the meeting the notification is about, null when it no longer
   *          exists (a cancellation), in which case no add-on can be asked
   *          about it and the settings-derived prediction stands
   * @param recipientIdentityId organization identity id of the recipient, 0
   *          when the recipient is a guest
   * @param recipientSettings agenda settings of that recipient, possibly null
   * @return true when the file must be attached
   */
  public boolean shouldAttachIcsFile(Event event, long recipientIdentityId, AgendaUserSettings recipientSettings) {
    if (recipientIdentityId <= 0) {
      // A guest has no account for a copy to land in, so the file is the only
      // way they get the meeting at all. Stated here as well as inside the
      // fallback, because this arm never reaches the fallback.
      return true;
    }
    Collection<RemoteEventCopyPlugin> plugins = event == null ? List.of() : copyPlugins();
    if (plugins.isEmpty()) {
      return NotificationUtils.shouldAttachIcsFile(recipientIdentityId, recipientSettings);
    }
    return !willHoldRemoteCopy(plugins, event, recipientIdentityId);
  }

  /**
   * Whether any registered add-on says it will write this recipient's copy.
   *
   * <p>
   * One add-on answering yes is enough: the recipient holds a copy, whoever
   * wrote it. A plugin that fails is read as "no copy from me" and the others
   * are still asked — the one that throws must not be able to suppress the
   * file on behalf of the ones that did not.
   *
   * @param plugins the add-ons to ask, never empty
   * @param event the meeting, never null here
   * @param recipientIdentityId organization identity id of the recipient
   * @return true when at least one add-on will write a copy
   */
  private boolean willHoldRemoteCopy(Collection<RemoteEventCopyPlugin> plugins, Event event, long recipientIdentityId) {
    for (RemoteEventCopyPlugin plugin : plugins) {
      try {
        if (plugin.writesCopyOf(event, recipientIdentityId)) {
          return true;
        }
      } catch (RuntimeException | LinkageError e) {
        LOG.debug("Add-on {} could not tell whether it writes a copy of event {} for user {}; it is read as writing none",
                  plugin.getClass().getName(),
                  event.getId(),
                  recipientIdentityId,
                  e);
      }
    }
    return false;
  }

  /**
   * The add-ons registered to answer for the copies they write.
   *
   * <p>
   * Empty when none is installed, and empty rather than a failure when the
   * context cannot be asked: the caller reads both as "nobody to ask" and falls
   * back to the prediction agenda made before this existed.
   *
   * @return every registered plugin, in no particular order
   */
  private Collection<RemoteEventCopyPlugin> copyPlugins() {
    try {
      return applicationContext == null ? List.of()
                                        : applicationContext.getBeansOfType(RemoteEventCopyPlugin.class).values();
    } catch (RuntimeException | LinkageError e) {
      LOG.debug("The add-ons that write meeting copies could not be listed; the recipient's own settings are read instead", e);
      return List.of();
    }
  }
}
