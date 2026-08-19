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
    <!--
      The calendars of a connected remote account.
      The section is shown whether or not an account is connected: it is how a
      user discovers that connecting one is possible at all, and the button
      beside the title is the same control the toolbar carries, so both lead to
      the same drawer rather than to two ways of doing one thing.
    -->
    <section
      v-if="connectorsAvailable"
      class="agenda-left-panel-section agenda-left-panel-my-calendars d-flex flex-column">
      <div class="agenda-left-panel-title text-sub-title">
        <span class="flex-grow-1">{{ $t('agenda.leftPanel.myCalendars') }}</span>
        <agenda-connect-to-remote-button
          :connectors="connectors"
          :settings="settings"
          :show-default-remote-events="showDefaultRemoteEvents"
          height="24"
          width="24"
          size="14"
          class="flex-grow-0"
          :show-toggle-action="false"
          :show-manage-action="true" />
      </div>
      <agenda-left-panel-remote-calendars
        :connectors="connectors"
        class="agenda-left-panel-calendars" />
    </section>
    <!--
      The user's own agenda calendars: the default one plus any calendar the
      user created to organize personal events. Visibility, rename, creation
      and deletion all live here — the same place the other calendar lists are
      managed. This is distinct from the connected-account section above,
      which lists calendars living in a remote account.
    -->
    <section class="agenda-left-panel-section d-flex flex-column">
      <div class="agenda-left-panel-title text-sub-title">
        <span class="flex-grow-1">{{ $t('agenda.leftPanel.personalCalendars') }}</span>
        <!-- Same affordance as the section above: a small icon button beside
             the title, opening a drawer, rather than an inline field -->
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
    <!-- section: Spaces -->
    <section class="agenda-left-panel-section d-flex flex-column">
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
    showDefaultRemoteEvents: {
      type: Boolean,
      default: false,
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
    /**
     * Whether the remote calendar feature exists in this deployment at all,
     * which is what decides if the section is worth showing. Not whether an
     * account is connected: an empty section with a way to connect is how the
     * feature is discovered, and hiding it means a user who has never
     * connected never learns it is there.
     *
     * @returns {Boolean} true when a connector implements the calendar contract
     */
    connectorsAvailable() {
      const connectors = extensionRegistry.loadExtensions('agenda', 'connectors') || [];
      return connectors.some(connector => connector && connector.canListCalendars);
    },
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
