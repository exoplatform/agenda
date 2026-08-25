<template>
  <div class="connector-status">
    <div v-if="connectedConnectorUser" class="connector-connected d-flex">
      <agenda-connector-avatar
        :connector="connectedConnector"
        size="24" />
      <a
        class="mx-2 my-auto"
        @click="openPersonalCalendarDrawer">
        {{ connectedConnectorUser }}
      </a>
    </div>
    <div
      v-else
      class="connect-button"
      @click="openPersonalCalendarDrawer">
      <slot
        name="connectButton">
      </slot>
    </div>
  </div>
</template>
<script>
export default {
  props: {
    connectors: {
      type: Array,
      default: () => null
    },
  },
  computed: {
    connectedConnector() {
      return this.connectors.find(connector => connector.user);
    },
    connectedConnectorName() {
      return this.connectedConnector && this.connectedConnector.name || '';
    },
    connectedConnectorUser() {
      return this.connectedConnector && this.connectedConnector.user || '';
    },
  },
  methods: {
    /**
     * Opens the shared connectors drawer on the CalDAV connectors alone, for
     * the same reason as the agenda's connect button: this status sits in the
     * application, where the offer is to connect the calendars that become
     * the user's own. Remote accounts are added from the settings section.
     *
     * The filter is passed only when a CalDAV connector is recognisable, so a
     * deployment without one keeps the legacy full list rather than an empty
     * drawer.
     *
     * @returns {void}
     */
    openPersonalCalendarDrawer() {
      const caldavKnown = this.connectors && this.connectors.some(connector => connector.isCaldav === true);
      this.$root.$emit('agenda-connectors-drawer-open', caldavKnown && {filter: 'caldav'} || null);
    },
  }
};
</script>