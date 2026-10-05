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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.rest.model.EventEntity;

/**
 * eXIP 7.3.0.20 Open Event (EXO-89477, then US06): the open flag put on the
 * wire is the row's own as soon as the event has a row — a series, a standalone
 * event, an occurrence individually modified; a computed occurrence has no row
 * and answers with its parent's flag; a search hit, whose source carries no
 * flag and no parent, answers "unknown" (null), never "locked".
 */
class RestEntityBuilderOpenFlagTest {

  @Test
  void computedOccurrenceCarriesTheParentFlag() {
    EventEntity parent = new EventEntity();
    parent.setOpen(true);
    Event occurrence = new Event();
    occurrence.setParentId(41L);
    occurrence.setOpen(false);

    assertEquals(Boolean.TRUE, RestEntityBuilder.effectiveOpen(parent, occurrence));

    parent.setOpen(false);
    occurrence.setOpen(true);
    assertEquals(Boolean.FALSE, RestEntityBuilder.effectiveOpen(parent, occurrence));
  }

  @Test
  void storedOccurrenceCarriesItsOwnFlag() {
    EventEntity parent = new EventEntity();
    parent.setOpen(false);
    Event occurrence = new Event();
    occurrence.setId(42L);
    occurrence.setParentId(41L);
    occurrence.setOpen(true);

    assertEquals(Boolean.TRUE, RestEntityBuilder.effectiveOpen(parent, occurrence));

    parent.setOpen(true);
    occurrence.setOpen(false);
    assertEquals(Boolean.FALSE, RestEntityBuilder.effectiveOpen(parent, occurrence));
  }

  @Test
  void standaloneEventCarriesItsOwnFlag() {
    Event event = new Event();
    event.setOpen(true);
    assertEquals(Boolean.TRUE, RestEntityBuilder.effectiveOpen(null, event));

    event.setOpen(false);
    assertEquals(Boolean.FALSE, RestEntityBuilder.effectiveOpen(null, event));
  }

  @Test
  void searchHitWithoutFlagStaysUnknown() {
    Event searchHit = new Event();
    assertNull(RestEntityBuilder.effectiveOpen(null, searchHit));
  }
}
