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

/**
 * What a surface says an event read live from a connected account is "in".
 *
 * <p>Such an event holds no eXo calendar and no owner at all — the read carries
 * a summary, its dates and the href of the collection it lives in — so the
 * owner avatar and owner display name every stored event is presented by have
 * nothing to show for it. A header that keeps asking for them prints its label
 * followed by nothing, which is how the mobile details came to read a bare
 * "in".
 *
 * <p>The answer, settled for the desktop header in EXO-89825 and held here so
 * that the two headers cannot diverge: name the collection the account itself
 * calls it, fall back to the account when the collection cannot be named, and
 * to a plain "a calendar of the connected account" when there is not even an
 * account to name — never to a calendar that exists nowhere.
 *
 * <p>Usage: a host component provides the `event` and `connectedConnector`
 * props and renders `remoteCalendarLabel` (with `remoteCalendarTitle` as its
 * hover text) under `isRemoteEvent`. The mixin owns the resolution, including
 * re-resolving when the host is reused for another event.
 */
export default {
  data: () => ({
    resolvedRemoteCalendarName: null,
  }),
  computed: {
    /**
     * Whether the event was read live from a connected account rather than
     * stored in eXo.
     *
     * @returns {Boolean} true for a live-read event
     */
    isRemoteEvent() {
      return this.event && this.event.type === 'remoteEvent';
    },
    /**
     * The account the event was read from: the one carried by the event
     * itself, since several can be connected at once and the header must name
     * the one this event came from, not whichever happens to be first.
     *
     * @returns {Object} the connector, or a falsy value for a stored event
     */
    eventConnector() {
      return this.event && this.event.connector || this.connectedConnector;
    },
    /**
     * The href of the collection the event was read from. It is the only
     * thing a live read carries about where the event lives — there is no eXo
     * calendar behind it.
     *
     * @returns {String} the collection href, empty when the read carried none
     */
    remoteCalendarHref() {
      return this.event && this.event.calendarId || '';
    },
    /**
     * The account's own identifier, as the connector reports it — a mailbox
     * or a login, not the connector's name. Used only to say *whose* account
     * a collection belongs to when the collection itself cannot be named.
     *
     * @returns {String} the account, empty when the connector reports none
     */
    remoteAccount() {
      return this.eventConnector && this.eventConnector.user || '';
    },
    /**
     * What the header says a live-read event is in.
     *
     * <p>
     * The collection's own name whenever the account can give it. When it
     * cannot — the account is unreachable, or the collection is one the
     * connector reads from but leaves out of its listing — the header says so
     * in terms of the account instead of inventing a name. What it never does
     * again is claim a "Personal Calendar" that exists nowhere, which is the
     * label that made a stray event impossible to place.
     *
     * @returns {String} the label to display
     */
    remoteCalendarLabel() {
      if (this.resolvedRemoteCalendarName) {
        return this.resolvedRemoteCalendarName;
      }
      if (this.remoteAccount) {
        return this.$t('agenda.remoteEvent.calendarOfAccount', {0: this.remoteAccount});
      }
      return this.$t('agenda.remoteEvent.unnamedCalendar');
    },
    /**
     * The hover text of the label: the collection href, which is the only
     * thing that distinguishes two collections sharing a display name — and
     * the thing one needs when tracking down where an unexpected event
     * actually came from.
     *
     * @returns {String} the tooltip, the label itself when there is no href
     */
    remoteCalendarTitle() {
      if (!this.remoteCalendarHref) {
        return this.remoteCalendarLabel;
      }
      return this.$t('agenda.remoteEvent.calendarLocation', {0: this.remoteCalendarHref});
    },
  },
  watch: {
    /**
     * The dialog is reused from one event to the next, so a new event must
     * re-resolve rather than keep the previous event's calendar name.
     *
     * @returns {void}
     */
    event() {
      this.resolveRemoteCalendarName();
    },
  },
  created() {
    this.resolveRemoteCalendarName();
  },
  methods: {
    /**
     * Asks the account what it calls the collection the event was read from.
     *
     * <p>
     * Only for a live read: a stored event already names its calendar through
     * the owner header and must not pay for a request. The answer is dropped
     * unless it still matches the collection on display — the dialog can be
     * moved to another event while a listing is in flight, and a name landing
     * on the wrong event is worse than the honest fallback.
     *
     * @returns {void}
     */
    resolveRemoteCalendarName() {
      this.resolvedRemoteCalendarName = null;
      if (!this.isRemoteEvent) {
        return;
      }
      const requestedHref = this.remoteCalendarHref;
      this.$remoteEventConnector.remoteCalendarName(this.eventConnector, requestedHref)
        .then(name => {
          if (requestedHref === this.remoteCalendarHref) {
            this.resolvedRemoteCalendarName = name;
          }
        });
    },
  },
};
