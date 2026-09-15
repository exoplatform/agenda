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
package org.exoplatform.agenda.service;

import java.util.List;

import org.exoplatform.agenda.model.CalendarSubscription;
import org.exoplatform.commons.exception.ObjectNotFoundException;

/**
 * Subscribes a user to a calendar link — an iCal or webcal URL — and keeps its
 * events in a read-only personal calendar, refreshed periodically (EXO-90278).
 * <p>
 * <b>Who</b>: a subscription is personal; only the user who subscribed reads,
 * edits, refreshes or removes it. <b>What the platform fetches</b>: only the
 * addresses the subscription guard lets through — never a loopback, private,
 * link-local or cloud-metadata address unless the deployment allows internal
 * addresses — with bounded size, time and redirects, no credentials and no
 * cookies. <b>What is imported</b>: title, time, location and a plain-text
 * description of each occurrence in a bounded window; never an organizer, an
 * attendee, an alarm or an attachment; and nothing imported produces an
 * invitation, a notification or a copy to a connected remote account.
 * <p>
 * Refusals are thrown as {@link IllegalArgumentException} carrying a message
 * code {@code agenda.calendarSubscription.*}; a refresh asked too soon as an
 * {@link IllegalStateException} with such a code.
 */
public interface AgendaCalendarSubscriptionService {

  /**
   * Lists the user's subscriptions, oldest first, each with its URL in clear.
   *
   * @param username the user
   * @return the subscriptions, bounded
   * @throws IllegalAccessException when the user has no usable identity
   */
  List<CalendarSubscription> getSubscriptions(String username) throws IllegalAccessException;

  /**
   * Reads one of the user's subscriptions.
   *
   * @param subscriptionId technical identifier of the subscription
   * @param username the user
   * @return the subscription, with its URL in clear
   * @throws ObjectNotFoundException when no such subscription exists
   * @throws IllegalAccessException when it is not the user's
   */
  CalendarSubscription getSubscription(long subscriptionId, String username) throws ObjectNotFoundException,
                                                                             IllegalAccessException;

  /**
   * Checks that a URL can be subscribed to, by reading it once, without storing
   * anything.
   *
   * @param url the URL as the user typed it; {@code webcal://} is accepted
   * @param username the user
   * @return the calendar's own name ({@code X-WR-CALNAME}), or null when it
   *         names none
   * @throws IllegalAccessException when the user has no usable identity
   * @throws IllegalArgumentException with a message code when the URL is
   *           refused or does not serve a calendar
   */
  String checkUrl(String url, String username) throws IllegalAccessException;

  /**
   * Subscribes the user to a URL: reads it, creates the calendar and imports its
   * events.
   *
   * @param url the URL as the user typed it
   * @param name the calendar name, or blank for the feed's own name
   * @param color the calendar colour {@code #RRGGBB}, or blank for an automatic
   *          one
   * @param username the user
   * @return the subscription
   * @throws IllegalAccessException when the user has no usable identity
   * @throws IllegalArgumentException with a message code when the URL is
   *           refused, already subscribed, or does not serve a calendar
   */
  CalendarSubscription createSubscription(String url, String name, String color, String username) throws IllegalAccessException;

  /**
   * Changes the name, the colour or the URL of one of the user's subscriptions.
   * A new URL is read before it is stored, and its events replace the old ones.
   *
   * @param subscriptionId technical identifier of the subscription
   * @param url the new URL, or blank to keep the current one
   * @param name the new name, or blank to keep the current one
   * @param color the new colour, or blank to keep the current one
   * @param username the user
   * @return the subscription as it now stands
   * @throws ObjectNotFoundException when no such subscription exists
   * @throws IllegalAccessException when it is not the user's
   */
  CalendarSubscription updateSubscription(long subscriptionId,
                                          String url,
                                          String name,
                                          String color,
                                          String username) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Refreshes one of the user's subscriptions now. A failure is not thrown: it is
   * recorded on the subscription returned, as a scheduled refresh records it.
   *
   * @param subscriptionId technical identifier of the subscription
   * @param username the user
   * @return the subscription after the refresh
   * @throws ObjectNotFoundException when no such subscription exists
   * @throws IllegalAccessException when it is not the user's
   * @throws IllegalStateException with a message code when it was refreshed a
   *           moment ago or is being refreshed
   */
  CalendarSubscription refreshSubscription(long subscriptionId, String username) throws ObjectNotFoundException,
                                                                                 IllegalAccessException;

  /**
   * Unsubscribes: removes the subscription, its calendar and every event it
   * imported.
   *
   * @param subscriptionId technical identifier of the subscription
   * @param username the user
   * @throws ObjectNotFoundException when no such subscription exists
   * @throws IllegalAccessException when it is not the user's
   */
  void deleteSubscription(long subscriptionId, String username) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Removes the subscription of a calendar that was deleted by another path,
   * with no permission check, for the platform's own clean-up.
   *
   * @param calendarId technical identifier of the deleted calendar
   */
  void deleteCalendarSubscription(long calendarId);

  /**
   * Refreshes the subscriptions whose refresh is due, at most a batch of them,
   * each claimed in the database first so that a single node of a cluster
   * refreshes it.
   *
   * @param batchSize most subscriptions to refresh
   * @return how many were refreshed by this call
   */
  int refreshDueSubscriptions(int batchSize);

}
