<template>
  <div>
    <div v-if="loading" class="d-flex justify-center py-2">
      <v-progress-circular
        color="primary"
        size="20"
        width="2"
        indeterminate />
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
  data: () => ({
    calendars: [],
    hiddenIds: [],
    loading: false,
  }),
  created() {
    this.retrieveCalendars();
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
      const connectors = this.connectedConnectors();
      if (!connectors.length) {
        this.$emit('availability', false);
        return;
      }
      this.loading = true;
      Promise.all(connectors.map(connector =>
        connector.listCalendars()
          .catch(error => {
            console.error(`cannot list the calendars of ${connector.name}`, error);
            return [];
          })
      )).then(lists => {
        this.calendars = lists.flat();
        this.$emit('availability', this.calendars.length > 0);
      }).finally(() => this.loading = false);
    },
    /**
     * The connectors that are connected, enabled and able to list calendars.
     *
     * Read through the bare extensionRegistry rather than through
     * window.extensionRegistry, which is undefined: reaching for it yields an
     * empty list and a section that silently never appears.
     *
     * @returns {Array} the connectors worth asking
     */
    connectedConnectors() {
      const connectors = extensionRegistry.loadExtensions('agenda', 'connectors') || [];
      return connectors.filter(connector => connector
          && connector.canListCalendars
          && connector.isSignedIn
          && typeof connector.listCalendars === 'function');
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
