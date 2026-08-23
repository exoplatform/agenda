<template>
  <div>
    <div v-if="loading" class="d-flex justify-center py-2">
      <v-progress-circular
        color="primary"
        size="20"
        width="2"
        indeterminate />
    </div>
    <div
      v-else-if="!calendars.length"
      class="agenda-left-panel-empty text-sub-title">
      {{ emptyLabel }}
    </div>
    <v-list
      v-else
      class="pa-0"
      dense>
      <v-list-item
        v-for="calendar in calendars"
        :key="calendar.id"
        class="agenda-calendar-settings px-0">
        <v-list-item-content :title="calendar.name" class="flex-grow-1 pa-0">
          <v-checkbox
            :input-value="isDisplayed(calendar)"
            :color="calendar.color"
            :label="calendar.name"
            class="agenda-calendar-settings-color ms-4"
            dense
            hide-details
            @change="toggle(calendar)" />
        </v-list-item-content>
      </v-list-item>
    </v-list>
  </div>
</template>

<script>
export default {
  props: {
    connectors: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    calendarsRequestId: 0,
    calendars: [],
    hiddenIds: [],
    loading: false,
  }),
  computed: {
    /**
     * The connectors that are connected and able to list calendars.
     *
     * `connected` is the runtime state and is what decides. `isSignedIn` is a
     * static property on the CalDAV descriptor, always true whether or not an
     * account is configured, so gating on it alone meant the section kept
     * asking after the user had disconnected — and the request went out with a
     * null username, drawing the browser's own credentials prompt over the
     * agenda.
     *
     * @returns {Array} the connectors worth asking
     */
    connectedConnectors() {
      return (this.connectors || []).filter(connector => connector
          && connector.canListCalendars
          && connector.connected
          && typeof connector.listCalendars === 'function');
    },
    /**
     * What to say when there is nothing to list, which is two different
     * situations and should not read as one. No account connected is an
     * invitation to connect one; an account holding no calendar is a statement
     * of fact about that account.
     *
     * @returns {String} the message for the current empty case
     */
    emptyLabel() {
      return this.connectedConnectors.length
        ? this.$t('agenda.leftPanel.noRemoteCalendar')
        : this.$t('agenda.leftPanel.notConnected');
    },
  },
  watch: {
    /**
     * Reacts to an account being connected or disconnected while the agenda is
     * open, so the list fills or empties without a reload.
     * @returns {void}
     */
    connectedConnectors() {
      this.retrieveCalendars();
    },
  },
  created() {
    this.retrieveCalendars();
    // The same signal the personal list listens to, because materialising a
    // collection changes both panels at once: it leaves this one and joins
    // that one. Listening on only one side is what let a calendar sit under
    // Remote while already being shown under Personal.
    this.$root.$on('agenda-refresh-personal-calendars', this.retrieveCalendars);
  },
  beforeDestroy() {
    this.$root.$off('agenda-refresh-personal-calendars', this.retrieveCalendars);
  },
  methods: {
    /**
     * Asks every connected connector able to answer for the calendars of the
     * account behind it, and renders them as one list.
     *
     * A connector that does not declare canListCalendars is skipped rather
     * than called, so the ones that predate this contract — Office 365 and
     * Outlook Exchange — are untouched. A connector that fails is logged and
     * dropped: one unreachable account must not empty the whole section.
     *
     * @returns {void}
     */
    retrieveCalendars() {
      const connectors = this.connectedConnectors;
      if (!connectors.length) {
        this.calendars = [];
        return;
      }
      this.loading = true;
      // Connecting fires two of these in quick succession — one the moment the
      // account is marked connected, one once its first synchronisation has
      // finished — and they answer different things: before the collections
      // are bound, every one of them is remote. Landing out of order, the
      // first overwrites the second and the panel keeps offering a calendar
      // that is already in the user's list, until the page is reloaded.
      //
      // The same guard the events grid uses, for the same reason: only the
      // newest request may write.
      const requestId = ++this.calendarsRequestId;
      Promise.all(connectors.map(connector =>
        connector.listCalendars()
          // The calendar eXo writes its copies to is left out of the list: it
          // holds nothing but duplicates of events the agenda already shows,
          // so displaying it would double every meeting on the grid.
          .then(calendars => this.$remoteEventConnector.excludeMirrorCalendar(connector, calendars))
          .catch(error => {
            console.error(`cannot list the calendars of ${connector.name}`, error);
            return [];
          })
      )).then(lists => {
        if (requestId !== this.calendarsRequestId) {
          // A newer retrieval was started since: its answer is the one that
          // reflects the account as it now stands.
          return;
        }
        this.calendars = lists.flat();
      }).finally(() => {
        if (requestId === this.calendarsRequestId) {
          this.loading = false;
        }
      });
    },
    /**
     * Whether a calendar's events are currently shown. Calendars are displayed
     * unless deliberately hidden, so a newly appearing one shows by default
     * rather than staying invisible until noticed.
     *
     * @param {Object} calendar calendar as the connector described it
     * @returns {Boolean} true when its events are shown
     */
    isDisplayed(calendar) {
      return !this.hiddenIds.includes(calendar.id);
    },
    /**
     * Shows or hides one calendar and publishes the resulting hidden set.
     *
     * @param {Object} calendar calendar the user just toggled
     * @returns {void}
     */
    toggle(calendar) {
      this.hiddenIds = this.isDisplayed(calendar)
        ? this.hiddenIds.concat(calendar.id)
        : this.hiddenIds.filter(id => id !== calendar.id);
      this.$root.$emit('agenda-remote-calendars-changed', this.hiddenIds.slice());
    },
  },
};
</script>
