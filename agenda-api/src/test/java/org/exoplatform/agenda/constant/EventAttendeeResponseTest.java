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
package org.exoplatform.agenda.constant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The attendee answer is stored by ordinal (EXO_AGENDA_ATTENDEE.RESPONSE, no
 * converter): its position is the storage format. A value inserted anywhere
 * but last would change what every stored row means, and the changelog's
 * rollback turns the value 4 back into "no answer" on the assumption that 4 is
 * WAITING.
 */
class EventAttendeeResponseTest {

  /**
   * Each answer keeps its stored ordinal, WAITING being the last one.
   */
  @Test
  void theStoredOrdinalsDoNotMove() {
    assertArrayEquals(new EventAttendeeResponse[] {
        EventAttendeeResponse.NEEDS_ACTION,
        EventAttendeeResponse.ACCEPTED,
        EventAttendeeResponse.DECLINED,
        EventAttendeeResponse.TENTATIVE,
        EventAttendeeResponse.WAITING,
    }, EventAttendeeResponse.values());
    assertEquals(4, EventAttendeeResponse.WAITING.ordinal());
  }

  /**
   * The waiting answer reads back from its value like the others.
   */
  @Test
  void theWaitingAnswerReadsBackFromItsValue() {
    assertEquals("WAITING", EventAttendeeResponse.WAITING.getValue());
    assertEquals(EventAttendeeResponse.WAITING, EventAttendeeResponse.fromValue("WAITING"));
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, EventAttendeeResponse.fromValue("NEEDS-ACTION"));
  }

}
