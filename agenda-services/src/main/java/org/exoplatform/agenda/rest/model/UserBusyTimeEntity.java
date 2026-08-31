/**
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.rest.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One participant's busy time as it leaves the server, with the status of the
 * read that produced it.
 * <p>
 * Three fields, and the middle one is load-bearing. {@code disclosure} is
 * {@code "disclosed"}, {@code "not_disclosed"} or {@code "failed"}; only the
 * first ever carries {@code busy}, and only the first means a calendar was
 * read. A client that draws {@code busy} without reading {@code disclosure}
 * draws a person who withheld their calendar exactly like a person who has
 * nothing on — which is the failure this whole endpoint exists to make
 * impossible.
 * <p>
 * {@code busy} is {@code null}, never {@code []}, for the other two: an empty
 * array is already the wire spelling of "read, and empty", and one idea gets
 * one spelling.
 * <p>
 * No name, no avatar and no profile travel here. The client already holds the
 * participant list it asked about and matches on {@code identityId}; sending
 * profile data back would widen what this endpoint discloses for no gain.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserBusyTimeEntity {

  private long                  identityId;

  private String                disclosure;

  private List<TimeBlockEntity> busy;

}
