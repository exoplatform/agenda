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
 * The stored side of a calendar subscription (EXO-90278).
 * <p>
 * {@code CALENDAR_ID} and {@code URL_KEY} are unique in the table: a calendar
 * holds one subscription, a user subscribes once to a URL. {@link DynamicUpdate}
 * because three writers share the row — the owner's edits, the refresh outcome
 * and the job's claim — and the refresh writes go through targeted UPDATE
 * statements that name their own columns, so a save of the entity must never
 * put back columns it did not change.
 */
@Entity(name = "AgendaCalendarSubscription")
@Table(name = "EXO_AGENDA_SUBSCRIPTION")
@DynamicUpdate
@Getter
@Setter
public class CalendarSubscriptionEntity implements Serializable {

  private static final long serialVersionUID = -1720361480329115846L;

  /** Technical identifier of the row. */
  @Id
  @PortableSequence(name = "SEQ_AGENDA_SUBSCRIPTION_ID")
  @Column(name = "SUBSCRIPTION_ID")
  private Long              id;

  /** The calendar the feed fills; unique. */
  @Column(name = "CALENDAR_ID", nullable = false)
  private long              calendarId;

  /** Identity identifier of the user who subscribed. */
  @Column(name = "USER_IDENTITY_ID", nullable = false)
  private long              userIdentityId;

  /** The URL encrypted by the platform codec. */
  @Column(name = "URL_ENCRYPTED", nullable = false)
  private String            urlEncrypted;

  /** SHA-256 of the owner and the normalized URL; unique. */
  @Column(name = "URL_KEY", nullable = false)
  private String            urlKey;

  /** Entity tag of the last read. */
  @Column(name = "ETAG")
  private String            etag;

  /** Last-Modified header of the last read. */
  @Column(name = "LAST_MODIFIED")
  private String            lastModified;

  /** SHA-256 of the last body imported. */
  @Column(name = "CONTENT_HASH")
  private String            contentHash;

  /** Refresh interval the feed advertised, in minutes. */
  @Column(name = "REFRESH_MINUTES")
  private Integer           refreshMinutes;

  /** When the feed was last imported successfully. */
  @Column(name = "LAST_SUCCESS_DATE")
  private Date              lastSuccessDate;

  /** When a refresh was last attempted. */
  @Column(name = "LAST_ATTEMPT_DATE")
  private Date              lastAttemptDate;

  /** Message code of the last failure, null after a success. */
  @Column(name = "LAST_ERROR")
  private String            lastError;

  /** Whether the last import kept only part of a feed holding too many occurrences. */
  @Column(name = "TRUNCATED", nullable = false)
  private boolean           truncated;

  /** When the next refresh is due. */
  @Column(name = "NEXT_REFRESH_DATE", nullable = false)
  private Date              nextRefreshDate;

  /** The node holding the refresh claim. */
  @Column(name = "CLAIMED_BY")
  private String            claimedBy;

  /** When the refresh claim was taken. */
  @Column(name = "CLAIMED_DATE")
  private Date              claimedDate;

  /** When the user subscribed. */
  @Column(name = "CREATED_DATE", nullable = false)
  private Date              createdDate;

}
