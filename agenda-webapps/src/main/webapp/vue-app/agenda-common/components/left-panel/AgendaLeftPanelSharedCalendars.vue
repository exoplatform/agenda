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
 * along with this program. If not, see gnu.org/licenses.
 */
<template>
  <!--
    The calendars colleagues shared with the user in eXo (EXO-90357): one row
    each, the owner's avatar for who it belongs to, a visibility checkbox, and
    a menu to hide it for good. The section owns its root and draws nothing
    while nothing is shared with the user, the rule the other sections follow;
    a calendar the user hid stays out of it and comes back from the Hidden
    calendars of the settings.

    Titled "Shared with me", the same title the CalDAV connector's section
    carries for the calendars a server shares with the user: a colleague who
    shared through eXo appears here, and the connector's copy of the same
    share — when the share was also delivered to the server — is left out of
    the other section by the references this one publishes (see
    publishNativeRefs), so a calendar is never drawn twice.
  -->
  <section
    v-if="visibleCalendars.length"
    class="agenda-left-panel-section d-flex flex-column mb-5 agenda-shared-calendars">
    <div class="agenda-left-panel-title text-sub-title">
      <span class="flex-grow-1">{{ $t('agenda.leftPanel.sharedWithMe') }}</span>
    </div>
    <div class="agenda-left-panel-calendars">
      <v-list class="pa-0" dense>
        <v-list-item
          v-for="calendar in visibleCalendars"
          :key="calendar.calendarId"
          class="agenda-calendar-settings px-0 agenda-shared-calendar">
          <v-list-item-content :title="rowTitle(calendar)" class="flex-grow-1 pa-0">
            <v-checkbox
              :input-value="isDisplayed(calendar)"
              :color="calendar.color"
              :label="calendar.name"
              class="agenda-calendar-settings-color ms-4"
              dense
              hide-details
              @change="toggle(calendar)" />
          </v-list-item-content>
          <!--
            Hiding for good, the same three-dots menu the personal rows carry,
            revealed on hover and on keyboard focus by the same class. Placed
            before the owner marker, as the CalDAV section places its menu:
            the avatar keeps the 24px column the section headers' icons share.
          -->
          <v-list-item-action class="my-0 ms-2 flex-grow-0 agenda-calendar-actions">
            <v-menu
              :value="isRowMenuOpen(`native:${calendar.calendarId}`)"
              content-class="agendaCalendarRowMenu"
              offset-y
              left
              @input="toggleRowMenu(`native:${calendar.calendarId}`, $event)">
              <template #activator="{ on, attrs }">
                <v-btn
                  v-bind="attrs"
                  v-on="on"
                  :title="$t('agenda.calendar.actions')"
                  :aria-label="$t('agenda.leftPanel.sharedCalendarActions', {0: calendar.name})"
                  icon
                  x-small>
                  <v-icon size="14">fa-ellipsis-v</v-icon>
                </v-btn>
              </template>
              <v-list dense class="pa-0">
                <v-list-item class="agenda-shared-calendar-hide" @click="hideCalendar(calendar)">
                  <v-list-item-title>{{ $t('agenda.leftPanel.hideSharedCalendar') }}</v-list-item-title>
                </v-list-item>
              </v-list>
            </v-menu>
          </v-list-item-action>
          <!--
            Who the calendar belongs to: the owner's avatar, in the picture-only
            mode with its popover and profile link, as the CalDAV section draws
            a colleague's share. The wrapper carries the accessible name.
          -->
          <v-list-item-action class="my-0 ms-2 flex-grow-0 justify-center">
            <exo-user-avatar
              :profile-id="calendar.ownerUsername"
              :name="calendar.ownerDisplayName"
              :aria-label="sharedLabel(calendar)"
              :size="20"
              avatar
              popover />
          </v-list-item-action>
        </v-list-item>
      </v-list>
    </div>
  </section>
</template>

<script>
import calendarRowMenuMixin from '../../js/CalendarRowMenuMixin.js';

export default {
  mixins: [calendarRowMenuMixin],
  data: () => ({
    calendars: [],
    hiddenIds: [],
    hidingIds: [],
    // Only the newest listing may write, as for the CalDAV section: a share
    // changes on the owner's side and the refresh signals overlap
    listingRequestId: 0,
  }),
  computed: {
    /**
     * @returns {String} the browser storage key of the shared calendars the
     *          user unticked, per user
     */
    storageKey() {
      return `agenda.hiddenSharedCalendars.${eXo.env.portal.userIdentityId}`;
    },
    /**
     * The rows to draw: the shares the user did not hide for good, minus the
     * ones a hide is in flight for.
     *
     * @returns {Array} the calendars
     */
    visibleCalendars() {
      return this.calendars.filter(calendar => !calendar.hidden && !this.hidingIds.includes(calendar.calendarId));
    },
    /**
     * The calendars whose events the grid must ask for: the visible rows the
     * user left ticked. Published to the agenda, which sends them to the
     * events REST as calendarIds.
     *
     * @returns {Array} calendar identifiers
     */
    displayedCalendarIds() {
      return this.visibleCalendars.filter(this.isDisplayed).map(calendar => Number(calendar.calendarId));
    },
    /**
     * What the delivery channels recorded for the visible shares — the CalDAV
     * collection the colleague sees — so the CalDAV section can leave out the
     * server's copy of a share eXo already draws here.
     *
     * @returns {Array} the delivery references, none empty
     */
    nativeRefs() {
      return this.visibleCalendars.map(calendar => calendar.deliveryRef).filter(ref => !!ref);
    },
  },
  watch: {
    /**
     * Tells the grid which shared calendars to draw, whenever the set changes.
     * @returns {void}
     */
    displayedCalendarIds: {
      immediate: true,
      handler() {
        this.$root.$emit('agenda-shared-calendars-displayed-changed', this.displayedCalendarIds.slice());
      },
    },
    /**
     * Tells the CalDAV section which of its rows eXo already draws.
     * @returns {void}
     */
    nativeRefs: {
      immediate: true,
      handler() {
        this.publishNativeRefs();
      },
    },
  },
  created() {
    this.hiddenIds = this.readHiddenIds();
    this.$root.$on('agenda-refresh-shared-calendars', this.retrieve);
    this.$root.$on('agenda-refresh', this.retrieve);
    // The settings page shows a hidden calendar again from another Vue app:
    // a document event is the only signal that crosses that boundary
    document.addEventListener('agenda-refresh-shared-calendars', this.retrieve);
    // The CalDAV section may mount after this one answered: it asks for the
    // references again once it exists
    this.$root.$on('agenda-native-shared-calendars-requested', this.publishNativeRefs);
    this.retrieve();
  },
  beforeDestroy() {
    this.$root.$off('agenda-refresh-shared-calendars', this.retrieve);
    this.$root.$off('agenda-refresh', this.retrieve);
    document.removeEventListener('agenda-refresh-shared-calendars', this.retrieve);
    this.$root.$off('agenda-native-shared-calendars-requested', this.publishNativeRefs);
  },
  methods: {
    /**
     * Reads the calendars shared with the user. A failure keeps what is drawn:
     * a section that empties on a network hiccup reads as shares that went
     * away.
     *
     * @returns {Promise} resolved once listed; never rejects
     */
    retrieve() {
      const requestId = ++this.listingRequestId;
      return this.$calendarShareService.getSharedWithMe()
        .then(calendars => {
          if (requestId === this.listingRequestId) {
            this.calendars = calendars || [];
          }
        })
        .catch(error => console.error('cannot list the calendars shared with me', error));
    },
    /**
     * Publishes the delivery references of the visible shares on the root, for
     * the CalDAV section.
     *
     * @returns {void}
     */
    publishNativeRefs() {
      this.$root.$emit('agenda-native-shared-calendars', this.nativeRefs.slice());
    },
    /**
     * The row's hover: the calendar's full name and who shared it.
     *
     * @param {Object} calendar the row
     * @returns {String} the hover text
     */
    rowTitle(calendar) {
      return `${calendar.name} — ${this.sharedLabel(calendar)}`;
    },
    /**
     * "Shared by <owner>", or "Shared with you" when the owner cannot be named.
     *
     * @param {Object} calendar the row
     * @returns {String} the sentence
     */
    sharedLabel(calendar) {
      return calendar.ownerDisplayName
        ? this.$t('agenda.leftPanel.sharedBy', {0: calendar.ownerDisplayName})
        : this.$t('agenda.leftPanel.sharedCalendar');
    },
    /**
     * @param {Object} calendar a row
     * @returns {Boolean} whether its events are shown
     */
    isDisplayed(calendar) {
      return !this.hiddenIds.includes(Number(calendar.calendarId));
    },
    /**
     * Shows or hides a shared calendar's events for this browser, and
     * remembers it for this user: the tick is a display choice, hiding for
     * good is the menu's.
     *
     * @param {Object} calendar the row toggled
     * @returns {void}
     */
    toggle(calendar) {
      const calendarId = Number(calendar.calendarId);
      this.hiddenIds = this.hiddenIds.includes(calendarId)
        ? this.hiddenIds.filter(id => id !== calendarId)
        : this.hiddenIds.concat(calendarId);
      this.writeHiddenIds();
    },
    /**
     * @returns {Array} the unticked calendar ids stored for this user
     */
    readHiddenIds() {
      try {
        const stored = JSON.parse(localStorage.getItem(this.storageKey) || '[]');
        return Array.isArray(stored) ? stored.map(Number) : [];
      } catch (e) {
        return [];
      }
    },
    /**
     * Stores the unticked calendar ids for this user.
     *
     * @returns {void}
     */
    writeHiddenIds() {
      try {
        localStorage.setItem(this.storageKey, JSON.stringify(this.hiddenIds));
      } catch (e) {
        // A browser refusing storage keeps the choice for this page only
      }
    },
    /**
     * Hides a shared calendar for good: the row goes at once, the server
     * records the choice, the snackbar says where it can be shown again. The
     * share itself stays — the owner still lists the user — and the calendar
     * comes back from the Hidden calendars of the settings. On failure the row
     * comes back and the error is said.
     *
     * @param {Object} calendar the row
     * @returns {Promise} resolved once the outcome has been shown; never rejects
     */
    hideCalendar(calendar) {
      if (this.hidingIds.includes(calendar.calendarId)) {
        return Promise.resolve();
      }
      this.hidingIds = this.hidingIds.concat(calendar.calendarId);
      return this.$calendarShareService.setHidden(calendar.calendarId, true)
        .then(() => {
          this.calendars = this.calendars.map(row => (row.calendarId === calendar.calendarId ? {...row, hidden: true} : row));
          this.$root.$emit('alert-message', this.$t('agenda.leftPanel.sharedCalendarHidden', {0: calendar.name}), 'success');
          document.dispatchEvent(new CustomEvent('agenda-refresh-shared-calendars'));
        })
        .catch(error => {
          console.error(`cannot hide the calendar ${calendar.name}`, error);
          this.$root.$emit('alert-message', this.$t('agenda.leftPanel.hideSharedCalendarError'), 'error');
        })
        .finally(() => {
          this.hidingIds = this.hidingIds.filter(id => id !== calendar.calendarId);
        });
    },
  },
};
</script>
