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

import org.exoplatform.agenda.model.Event;

/**
 * Answers, for the add-on that writes meeting copies into a user's connected
 * calendar account, whether it is going to write one — asked by agenda before
 * it decides anything that depends on the answer.
 *
 * <h2>Why agenda asks instead of predicting (EXO-90247)</h2>
 *
 * <p>
 * Agenda has one decision that turns on this: whether the invitation mail
 * carries an {@code event.ics} file. The file exists so that a recipient whose
 * calendar eXo cannot reach still gets the meeting; it is left out for a
 * recipient who will hold a synced copy, because the two documents carry
 * different UIDs and no client can tell they are one meeting.
 *
 * <p>
 * Agenda used to answer that by reading the recipient's own settings, which is
 * a guess at a decision another add-on owns — and the two drifted, in both
 * directions at once. The add-on's server-side seeding pass writes a copy for a
 * user whose {@code automaticPushEvents} is off, where agenda predicted none
 * and attached the file: the meeting twice. And when the add-on gained a reason
 * of its own to decline a copy, agenda knew nothing of it, predicted a copy, and
 * left the file out: the meeting <i>nowhere</i>, which is how the first attempt
 * at EXO-90247 turned a duplicate into total silence for the invitee it was
 * meant to help.
 *
 * <p>
 * So the answer moves to the only party that can give it — the one that will do
 * the writing. Agenda keeps its settings-derived guess for the deployment where
 * no add-on is installed to be asked, and nowhere else.
 *
 * <h2>The contract, and which way to err</h2>
 *
 * <p>
 * The two mistakes do not cost the same. Answering <b>true</b> when no copy is
 * written takes the file away from somebody who then receives the meeting
 * through nothing at all — a missing invitation, with nothing in their calendar
 * or their mail to suggest why. Answering <b>false</b> when a copy is written
 * gives them the meeting twice, which is visible and which they can delete. So
 * an implementation that cannot establish its answer — a setting it cannot
 * read, a server it cannot reach, a failure of any kind — answers
 * <b>false</b>, never true.
 *
 * <p>
 * It is a prediction and cannot be anything else: the invitation is dispatched
 * synchronously inside event creation, while a copy is written by a background
 * pass minutes later. What the implementation is being asked is "will you",
 * not "did you".
 *
 * <h2>How it is registered</h2>
 *
 * <p>
 * As a Spring {@code @Service} bean, non-final, in the contributing add-on's
 * own context — agenda collects every bean of this type at the moment it asks,
 * so an add-on whose WAR boots after agenda's is found just the same, and a
 * deployment without the add-on simply has none.
 */
public interface RemoteEventCopyPlugin {

  /**
   * Whether this add-on will write its own copy of this meeting into that
   * recipient's connected calendar account.
   *
   * <p>
   * Answers false rather than throwing on anything it cannot establish; see the
   * asymmetry stated on this interface.
   *
   * @param event the meeting, as agenda holds it — never null
   * @param recipientIdentityId organization identity id of the recipient, which
   *          is 0 for a guest, who has no account and therefore never a copy
   * @return true only when a copy of this meeting is going to be written for
   *         that recipient
   */
  boolean writesCopyOf(Event event, long recipientIdentityId);

}
