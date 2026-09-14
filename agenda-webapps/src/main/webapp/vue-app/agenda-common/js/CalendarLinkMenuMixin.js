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
 * The publishing state of the calendars a list draws (EXO-90252): the personal
 * calendar list and each space calendar row mix this in, so both menus and both
 * row icons say the same thing from the same data.
 *
 * The state comes from one listing of every link the user manages, shared by
 * every row that asks within a second ($calendarLinkService.getCalendarLinks),
 * and is read again when the calendar link drawer says a link changed and on
 * the agenda's general refresh.
 *
 * Nothing here changes a link: publishing and resetting happen in the drawer,
 * and unpublishing is asked of the drawer too, which holds the one confirmation
 * dialog, so a list of fifty space rows does not carry fifty dialogs.
 */
export default {
  data: () => ({
    calendarLinks: {},
  }),
  created() {
    this.$root.$on('agenda-calendar-links-changed', this.refreshCalendarLinks);
    this.$root.$on('agenda-refresh', this.refreshCalendarLinks);
    this.retrieveCalendarLinks(false);
  },
  beforeDestroy() {
    this.$root.$off('agenda-calendar-links-changed', this.refreshCalendarLinks);
    this.$root.$off('agenda-refresh', this.refreshCalendarLinks);
  },
  methods: {
    /**
     * Reads the links of the calendars the user manages, indexed by calendar.
     * A failure keeps the states already drawn: a menu that forgets a link is
     * published would offer to publish it again.
     *
     * @param {Boolean} refresh whether the listing must be read again
     * @returns {Promise} resolved once the states are known
     */
    retrieveCalendarLinks(refresh) {
      const service = this.$calendarLinkService;
      if (!service || typeof service.getCalendarLinks !== 'function') {
        return Promise.resolve();
      }
      return service.getCalendarLinks(refresh)
        .then(links => {
          const byCalendar = {};
          (links || []).forEach(link => byCalendar[link.calendarId] = link);
          this.calendarLinks = byCalendar;
        })
        .catch(() => null);
    },
    /**
     * Reads the links again, after a change or a refresh.
     *
     * @returns {Promise} resolved once the states are known
     */
    refreshCalendarLinks() {
      return this.retrieveCalendarLinks(true);
    },
    /**
     * The publishing state of a calendar.
     *
     * @param {Object} calendar the row's calendar
     * @returns {String} none, published or stopped
     */
    linkStateOf(calendar) {
      const link = calendar && Number(calendar.id) > 0 && this.calendarLinks[calendar.id];
      if (!link || !link.exists) {
        return 'none';
      }
      return link.active ? 'published' : 'stopped';
    },
    /**
     * The menu entry that opens the drawer, worded after the state.
     *
     * @param {Object} calendar the row's calendar
     * @returns {String} Publish, Published or Publishing stopped
     */
    publishMenuLabel(calendar) {
      const state = this.linkStateOf(calendar);
      if (state === 'published') {
        return this.$t('agenda.calendarPublish.published');
      } else if (state === 'stopped') {
        return this.$t('agenda.calendarPublish.stopped');
      }
      return this.$t('agenda.calendarPublish.menu');
    },
    /**
     * The hover text of the row's published sign.
     *
     * @param {Object} calendar the row's calendar
     * @returns {String} the text, empty when the calendar is not published
     */
    publishedTooltip(calendar) {
      const state = this.linkStateOf(calendar);
      if (state === 'published') {
        return this.$t('agenda.calendarPublish.publishedTooltip');
      } else if (state === 'stopped') {
        return this.$t('agenda.calendarPublish.stoppedTooltip');
      }
      return '';
    },
    /**
     * Opens the drawer managing a calendar's link. The drawer lives once in the
     * application, never in a row (EXO-90239).
     *
     * @param {Object} calendar the calendar whose link is managed
     * @returns {void}
     */
    openCalendarLink(calendar) {
      this.$root.$emit('agenda-calendar-link-drawer-open', calendar);
    },
    /**
     * Asks the drawer to unpublish a calendar: it confirms, deletes and says so,
     * without opening.
     *
     * @param {Object} calendar the calendar to unpublish
     * @returns {void}
     */
    unpublishCalendar(calendar) {
      this.$root.$emit('agenda-calendar-link-unpublish', calendar);
    },
  },
};
