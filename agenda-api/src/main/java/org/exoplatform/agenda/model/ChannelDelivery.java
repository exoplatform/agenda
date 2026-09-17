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

import lombok.Data;

/**
 * What a delivery channel answers when agenda asks it to carry a calendar
 * share (EXO-90357).
 * <p>
 * Three answers, and the eXo record stands whatever the answer:
 * {@link Status#DELIVERED} — the channel granted the share on its side and
 * says under which reference; {@link Status#NOT_APPLICABLE} — this share is
 * none of the channel's business (the owner or the colleague has no account
 * there, the calendar lives on no server); {@link Status#FAILED} — the channel
 * should have carried it and could not, with a code the drawer can word and
 * offer to retry.
 */
@Data
public class ChannelDelivery {

  /** The three outcomes of a delivery. */
  public enum Status {
    DELIVERED,
    NOT_APPLICABLE,
    FAILED
  }

  private final Status status;

  /**
   * The channel that carries the share, as {@code CalendarShareChannelPlugin.id()}
   * names it; set on {@link Status#DELIVERED} only.
   */
  private final String channelId;

  /**
   * What the channel hands back to recognise its delivery again — for CalDAV
   * the href of the collection the colleague sees. Optional, on
   * {@link Status#DELIVERED} only.
   */
  private final String deliveryRef;

  /** A code naming the failure, on {@link Status#FAILED} only. */
  private final String failureCode;

  /**
   * Builds an answer.
   *
   * @param status the outcome
   * @param channelId the channel, on a delivery
   * @param deliveryRef the channel's reference, on a delivery
   * @param failureCode the failure code, on a failure
   */
  private ChannelDelivery(Status status, String channelId, String deliveryRef, String failureCode) {
    this.status = status;
    this.channelId = channelId;
    this.deliveryRef = deliveryRef;
    this.failureCode = failureCode;
  }

  /**
   * The channel granted the share on its side.
   *
   * @param channelId the channel, never blank
   * @param deliveryRef the channel's reference, may be null
   * @return the answer
   */
  public static ChannelDelivery delivered(String channelId, String deliveryRef) {
    return new ChannelDelivery(Status.DELIVERED, channelId, deliveryRef, null);
  }

  /**
   * The share is none of the channel's business.
   *
   * @return the answer
   */
  public static ChannelDelivery notApplicable() {
    return new ChannelDelivery(Status.NOT_APPLICABLE, null, null, null);
  }

  /**
   * The channel should have carried the share and could not.
   *
   * @param failureCode a code the drawer can word, never blank
   * @return the answer
   */
  public static ChannelDelivery failed(String failureCode) {
    return new ChannelDelivery(Status.FAILED, null, null, failureCode);
  }

}
