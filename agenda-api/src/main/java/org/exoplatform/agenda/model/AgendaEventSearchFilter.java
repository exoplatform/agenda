/*
 * Copyright (C) 2025 eXo Platform SAS.
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
package org.exoplatform.agenda.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.ZoneId;
import java.util.List;

@Data
@AllArgsConstructor
public class AgendaEventSearchFilter {

  private long       currentUserId;

  private ZoneId     userTimeZone;

  private String     term;

  private List<Long> spaceIdentityIds;

  private String     sortFiled;

  private String     sortDirection;

  private int        offset;

  private int        limit;

  /**
   * The calendars other users shared with the reader (EXO-90357), whose events
   * the search may answer on top of the reader's own permissions. <b>Set by the
   * service from the share records, never from a client</b>: a client cannot
   * name a calendar here, only the service does.
   */
  private List<Long> sharedCalendarIds;

  /**
   * Builds a filter as a client asks it: no shared calendar, the service adds
   * them.
   *
   * @param currentUserId identity identifier of the reader
   * @param userTimeZone time zone of the reader
   * @param term the words searched
   * @param spaceIdentityIds space identities restricting the search, may be null
   * @param sortFiled the sort field
   * @param sortDirection the sort direction
   * @param offset first result
   * @param limit most results
   */
  public AgendaEventSearchFilter(long currentUserId,
                                 ZoneId userTimeZone,
                                 String term,
                                 List<Long> spaceIdentityIds,
                                 String sortFiled,
                                 String sortDirection,
                                 int offset,
                                 int limit) {
    this(currentUserId, userTimeZone, term, spaceIdentityIds, sortFiled, sortDirection, offset, limit, null);
  }
}
