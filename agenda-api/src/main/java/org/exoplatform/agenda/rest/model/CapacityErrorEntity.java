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

import java.io.Serializable;

import lombok.*;

/**
 * The body of a 400 refusing a capacity limit below the Yes answers of a date
 * of a series: the code, and the date it names. Any other refusal is answered
 * with its code alone.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CapacityErrorEntity implements Serializable {

  private static final long serialVersionUID = 4120394766287051230L;

  /** The message code of the refusal. */
  private String            code;

  /**
   * The day, {@code yyyy-MM-dd} in the series' zone, of the first date whose
   * Yes answers the limit is below.
   */
  private String            date;
}
