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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Pins that {@code clone()} clones its {@link CalendarPermission} rather than
 * aliasing it: the exact shape EXO-90276 just fixed on {@code
 * CalendarPermission.clone()} itself, one field up. Mutation-verified: with
 * {@code acl.clone()} reverted to a bare {@code acl}, the "distinct ACL
 * instance" assertion fails.
 */
class CalendarTest {

  @Test
  void cloneCopiesADistinctAcl() {
    CalendarPermission acl = new CalendarPermission(true, false, true, false);
    Calendar original = new Calendar(1, 2, false, false, "Title", "Desc", "created", "updated", "#fff", acl);

    Calendar copy = original.clone();

    assertNotSame(original, copy, "clone() must return a distinct instance");
    assertNotSame(acl, copy.getAcl(), "the ACL must be cloned, not aliased");
    assertEquals(acl, copy.getAcl(), "the cloned ACL still carries the same values");
  }

  @Test
  void cloneOfACalendarWithNoAclCopiesNoAcl() {
    Calendar original = new Calendar(1, 2, false, false, "Title", "Desc", "created", "updated", "#fff", null);

    Calendar copy = original.clone();

    assertNull(copy.getAcl());
  }

}
