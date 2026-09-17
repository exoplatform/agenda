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
 * The sharing state of the calendars a list draws (EXO-90357): the personal
 * calendar list mixes this in, so its menu entry and its row sign say the
 * same thing from the same data.
 *
 * The state is the native eXo share records only — one listing of the sharee
 * counts of every calendar the user owns, shared by every row that asks
 * within a second ($calendarShareService.getShareCounts) — and is read again
 * when the share drawer says a share changed and on the agenda's general
 * refresh. Shares made outside eXo, on a CalDAV server, are listed in the
 * drawer but counted nowhere: the sign says what eXo knows.
 *
 * Nothing here changes a share: sharing and revoking happen in the drawer the
 * entry opens.
 */
export default {
  data: () => ({
    shareCounts: {},
  }),
  created() {
    this.$root.$on('agenda-calendar-shares-changed', this.refreshShareCounts);
    this.$root.$on('agenda-refresh', this.refreshShareCounts);
    this.retrieveShareCounts(false);
  },
  beforeDestroy() {
    this.$root.$off('agenda-calendar-shares-changed', this.refreshShareCounts);
    this.$root.$off('agenda-refresh', this.refreshShareCounts);
  },
  methods: {
    /**
     * Reads how many colleagues each of the user's calendars is shared with.
     * A failure keeps the counts already drawn: a sign that vanishes on a
     * network hiccup would read as a share that went away.
     *
     * @param {Boolean} refresh whether the counts must be read again
     * @returns {Promise} resolved once the counts are known
     */
    retrieveShareCounts(refresh) {
      const service = this.$calendarShareService;
      if (!service || typeof service.getShareCounts !== 'function') {
        return Promise.resolve();
      }
      return service.getShareCounts(refresh)
        .then(counts => this.shareCounts = counts || {})
        .catch(() => null);
    },
    /**
     * Reads the counts again, after a change or a refresh.
     *
     * @returns {Promise} resolved once the counts are known
     */
    refreshShareCounts() {
      return this.retrieveShareCounts(true);
    },
    /**
     * Whether the user may share a calendar: the server's word, on the
     * calendar's permissions — the owner of a personal calendar that is not a
     * subscription, whether or not they connected a CalDAV account.
     *
     * @param {Object} calendar the row's calendar
     * @returns {Boolean} true when Share belongs in its menu
     */
    canShare(calendar) {
      return !!calendar && Number(calendar.id) > 0 && !!calendar.acl && calendar.acl.canShare === true;
    },
    /**
     * Whether a calendar is shared with at least one colleague in eXo.
     *
     * @param {Object} calendar the row's calendar
     * @returns {Boolean} true when the row carries the shared sign
     */
    isShared(calendar) {
      return !!calendar && Number(this.shareCounts[calendar.id] || 0) > 0;
    },
    /**
     * Opens the drawer sharing a calendar. The drawer lives once in the
     * application, never in a row (EXO-90239).
     *
     * @param {Object} calendar the calendar to share
     * @returns {void}
     */
    openCalendarShare(calendar) {
      this.$root.$emit('agenda-calendar-share-drawer-open', calendar);
    },
  },
};
