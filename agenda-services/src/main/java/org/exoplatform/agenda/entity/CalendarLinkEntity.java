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
package org.exoplatform.agenda.entity;

import java.io.Serializable;
import java.util.Date;

import org.hibernate.annotations.DynamicUpdate;

import io.meeds.common.persistence.PortableSequence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The stored side of a calendar link: which calendar, who created it, when,
 * the digest of its token and the token encrypted by the platform codec.
 * <p>
 * {@code CALENDAR_ID} is unique in the table, so a calendar carries at most one
 * link; {@code TOKEN_HASH} is unique too, and is what a feed request is looked
 * up by. {@link DynamicUpdate} because a reset rewrites a row another manager
 * may be resetting at the same moment: only the columns that changed are
 * written.
 */
@Entity(name = "AgendaCalendarLink")
@Table(name = "EXO_AGENDA_CALENDAR_LINK")
@DynamicUpdate
@Getter
@Setter
public class CalendarLinkEntity implements Serializable {

  private static final long serialVersionUID = 4172285071739427358L;

  /** Technical identifier of the row. */
  @Id
  @PortableSequence(name = "SEQ_AGENDA_CALENDAR_LINK_ID")
  @Column(name = "LINK_ID")
  private Long              id;

  /** The calendar the link publishes; unique. */
  @Column(name = "CALENDAR_ID", nullable = false)
  private long              calendarId;

  /** Identity identifier of the user who created or last reset the link. */
  @Column(name = "CREATOR_ID", nullable = false)
  private long              creatorId;

  /** Lowercase hexadecimal SHA-256 digest of the token; unique. */
  @Column(name = "TOKEN_HASH", nullable = false)
  private String            tokenHash;

  /** The token encrypted by the platform codec, for display to managers. */
  @Column(name = "TOKEN_ENCRYPTED", nullable = false)
  private String            tokenEncrypted;

  /** When the link was created or last reset. */
  @Column(name = "CREATED_DATE", nullable = false)
  private Date              createdDate;

}
