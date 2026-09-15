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
package org.exoplatform.agenda.rest.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What a user sends to check, create or change a calendar subscription
 * (EXO-90278). A blank field means "not given".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendarSubscriptionRequestEntity {

  /** The calendar link, as typed. */
  private String url;

  /** The calendar name. */
  private String name;

  /** The calendar colour, {@code #RRGGBB}. */
  private String color;

}
