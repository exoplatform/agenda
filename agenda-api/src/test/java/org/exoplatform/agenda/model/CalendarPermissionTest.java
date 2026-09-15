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
package org.exoplatform.agenda.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

/**
 * Pins {@code CalendarPermission.clone()} against the field order it must
 * copy (EXO-90276): {@code clone()} used to build the copy with its first
 * two constructor arguments swapped ({@code canEdit, canCreate} instead of
 * {@code canCreate, canEdit}), which the compiler cannot catch since both
 * are booleans. Every field is given a distinct value here, in both
 * directions, so a swap between any two of them fails the test.
 */
class CalendarPermissionTest {

  @Test
  void cloneCopiesEveryFieldInDeclarationOrder() {
    CalendarPermission original = new CalendarPermission(true, false, true, false);

    CalendarPermission copy = original.clone();

    assertNotSame(original, copy, "clone() must return a distinct instance");
    assertEquals(original, copy);
    assertEquals(true, copy.isCanCreate(), "canCreate was not copied to canCreate");
    assertEquals(false, copy.isCanEdit(), "canEdit was not copied to canEdit");
    assertEquals(true, copy.isCanInviteeEdit(), "canInviteeEdit was not copied to canInviteeEdit");
    assertEquals(false, copy.isCanPublish(), "canPublish was not copied to canPublish");
  }

  @Test
  void cloneCopiesEveryFieldInTheInverseCombination() {
    CalendarPermission original = new CalendarPermission(false, true, false, true);

    CalendarPermission copy = original.clone();

    assertNotSame(original, copy, "clone() must return a distinct instance");
    assertEquals(original, copy);
    assertEquals(false, copy.isCanCreate(), "canCreate was not copied to canCreate");
    assertEquals(true, copy.isCanEdit(), "canEdit was not copied to canEdit");
    assertEquals(false, copy.isCanInviteeEdit(), "canInviteeEdit was not copied to canInviteeEdit");
    assertEquals(true, copy.isCanPublish(), "canPublish was not copied to canPublish");
  }
}
