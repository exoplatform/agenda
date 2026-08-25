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
<template>
  <aside class="agenda-left-panel border-box-sizing full-height d-flex flex-column">
    <!--
      Mini month calendar, the way Gmail and macOS Calendar carry one: a way to
      jump the main view to any day without leaving the current view type.
      no-title drops the coloured band Vuetify shows above the grid, and
      full-width makes the picker take the 220px column instead of its own
      290px default, which would overflow the panel.
    -->
    <v-date-picker
      v-model="pickerValue"
      :picker-date.sync="pickerMonth"
      :first-day-of-week="firstDayOfWeek"
      :locale="language"
      color="primary"
      class="agenda-left-panel-mini-calendar"
      no-title
      flat
      full-width
      @input="displayDate" />
    <!-- Every section carries the same mb-5, one rule for all of them: the
         vertical rhythm between sections stays identical whichever sections
         render, and a future section only has to repeat the same class -->
    <!--
      The user's own agenda calendars: the default one plus any calendar the
      user created to organize personal events. Visibility, rename, creation
      and deletion all live here — the same place the other calendar lists are
      managed. This is distinct from the per-provider sections below, which
      list calendars living in a remote account.
    -->
    <section class="agenda-left-panel-section d-flex flex-column mb-5">
      <div class="agenda-left-panel-title text-sub-title">
        <span class="flex-grow-1">{{ $t('agenda.leftPanel.personalCalendars') }}</span>
        <!--
          Connecting an account lives here, beside adding a calendar: both add
          calendars to this section, and this is the section a user looks at
          when they wonder where their calendars are. It used to sit on the
          Remote header, which meant the only way to discover connecting was
          to be shown a section that has nothing to do with it — and kept that
          section alive for no other reason.
        -->
        <agenda-connect-to-remote-button
          :connectors="connectors"
          :settings="settings"
          height="24"
          width="24"
          size="14"
          class="flex-grow-0"
          :show-toggle-action="false"
          :show-manage-action="true" />
        <v-btn
          :title="$t('agenda.calendar.addCalendar')"
          icon
          max-width="24"
          max-height="24"
          class="flex-grow-0"
          @click="openPersonalCalendarDrawer">
          <v-icon size="14" class="text-light-color">
            fas fa-plus
          </v-icon>
        </v-btn>
      </div>
      <agenda-personal-calendar-list class="agenda-left-panel-calendars" />
    </section>
    <!--
      One section per connected remote provider (Google, Office 365…), titled
      with the provider's own label, listing the calendars of the account and
      a visibility checkbox for each. Placed after My Calendars because that
      is where a remote calendar sits in the user's mind: my own first, then
      each account I look at. CalDAV gets no section here — its collections
      are materialised as the user's own personal calendars, so they already
      appear above. The component draws its own sections, headers included,
      and draws nothing at all when no connected provider lists calendars.
    -->
    <agenda-left-panel-remote-calendars
      v-if="connectorsAvailable"
      :connectors="connectors" />
    <!-- section: Spaces -->
    <section class="agenda-left-panel-section d-flex flex-column mb-5">
      <div class="agenda-left-panel-title text-sub-title">
        {{ $t('agenda.leftPanel.spaces') }}
      </div>
      <agenda-filter-calendar-list
        ref="calendarList"
        :value="selectedOwnerIds"
        class="agenda-left-panel-calendars"
        compact
        @input="changeSelection" />
    </section>
  </aside>
</template>

<script>
export default {
  props: {
    selectedOwnerIds: {
      type: [Array, Boolean],
      default: () => [],
    },
    expanded: {
      type: Boolean,
      default: false,
    },
    connectors: {
      type: Array,
      default: () => [],
    },
    settings: {
      type: Object,
      default: null,
    },
    period: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    calendarsLoaded: false,
    pickerValue: null,
    pickerMonth: null,
    connectorsAvailable: false,
  }),
  computed: {
    /**
     * The day the picker starts its weeks on, taken from the user setting
     * rather than assumed: the same util drives the main grid, so a user who
     * starts weeks on Sunday sees both agree.
     *
     * @returns {Number} index of the first weekday, 0 being Sunday
     */
    firstDayOfWeek() {
      return this.settings && this.$agendaUtils.getWeekSequenceFromDay(this.settings, null, true)[0] || 1;
    },
    /**
     * The language used to label months and weekdays in the picker.
     *
     * @returns {String} the current user language
     */
    language() {
      return eXo.env.portal.language;
    },
  },
  created() {
    // Whether the remote calendar feature exists here is read from the
    // extension registry, which is NOT reactive — and connectors may register
    // late (the CalDAV add-on registers one connector per declared server,
    // after fetching them). As a computed this evaluated once, usually before
    // that registration, and My Calendars silently disappeared. So it is
    // plain data, refreshed on the same event every other connectors consumer
    // already listens to.
    document.addEventListener('agenda-connectors-refresh', this.refreshConnectorsAvailable);
    this.refreshConnectorsAvailable();
  },
  beforeDestroy() {
    document.removeEventListener('agenda-connectors-refresh', this.refreshConnectorsAvailable);
  },
  watch: {
    /**
     * Lazily loads the space calendar list the first time the panel becomes
     * visible, so a user who keeps the panel collapsed never pays the REST
     * calls behind it.
     * @returns {void}
     */
    expanded() {
      if (this.expanded && !this.calendarsLoaded) {
        this.loadCalendars();
      }
    },
    /**
     * Keeps the picker on the period the main view displays, so navigating by
     * week or month moves the highlighted day with it.
     * @returns {void}
     */
    period: {
      immediate: true,
      handler() {
        this.synchronizeWithDisplayedPeriod();
      },
    },
  },
  mounted() {
    if (this.expanded) {
      this.loadCalendars();
    }
  },
  methods: {
    /**
     * Re-reads the extension registry to decide whether any connector
     * implements the calendar contract — which is what decides if the
     * My Calendars section is worth showing. Not whether an account is
     * connected: an empty section with a way to connect is how the feature
     * is discovered.
     *
     * @returns {void}
     */
    refreshConnectorsAvailable() {
      const connectors = extensionRegistry.loadExtensions('agenda', 'connectors') || [];
      this.connectorsAvailable = connectors.some(connector => connector && connector.canListCalendars);
    },
    /**
     * Triggers the initial retrieval of the space calendars displayed in the
     * Spaces section, at most once per application load.
     * @returns {void}
     */
    loadCalendars() {
      this.calendarsLoaded = true;
      if (this.$refs.calendarList) {
        this.$refs.calendarList.reset();
      }
    },
    /**
     * Opens the drawer creating a personal calendar, empty: this button is
     * the creation entry, editing goes through each calendar's action menu.
     * @returns {void}
     */
    openPersonalCalendarDrawer() {
      this.$root.$emit('agenda-personal-calendar-drawer-open');
    },
    /**
     * Relays a calendar selection change to the Agenda application through the
     * shared root event, so desktop panel and mobile filter drawer use the
     * same selection path.
     *
     * @param {Array|boolean} selectedOwnerIds selected calendar owner
     *          identity ids: an empty array means 'all calendars', false means
     *          'no calendar'
     * @returns {void}
     */
    changeSelection(selectedOwnerIds) {
      this.$root.$emit('agenda-calendar-owners-changed', selectedOwnerIds);
    },
    /**
     * Moves the main calendar to the day picked, keeping the current view type:
     * picking a day in week view stays in week view, on that day's week.
     *
     * @param {String} value picked day, as the YYYY-MM-DD the picker emits
     * @returns {void}
     */
    displayDate(value) {
      if (!value) {
        return;
      }
      // toDate first: toRFC3339 given the raw YYYY-MM-DD string would parse it
      // as UTC midnight, which is the previous day in a negative UTC offset
      this.$root.$emit('agenda-display-calendar-atDate', this.$agendaUtils.toRFC3339(this.$agendaUtils.toDate(value), true));
    },
    /**
     * Aligns the picker with the period currently displayed, moving the
     * selected day only when it falls outside that period. Without that guard
     * the selection would jump back to the first day of the week as soon as
     * the view answers the pick, so picking Wednesday would highlight Monday.
     * @returns {void}
     */
    synchronizeWithDisplayedPeriod() {
      if (!this.period || !this.period.start) {
        return;
      }
      const start = this.$agendaUtils.toDate(this.period.start);
      const end = this.period.end && this.$agendaUtils.toDate(this.period.end);
      const selected = this.pickerValue && this.$agendaUtils.toDate(this.pickerValue);
      if (!selected || selected < start || end && selected > end) {
        this.pickerValue = this.$agendaUtils.toRFC3339(start, true).substring(0, 10);
      }
      this.pickerMonth = this.pickerValue.substring(0, 7);
    },
  },
};
</script>
