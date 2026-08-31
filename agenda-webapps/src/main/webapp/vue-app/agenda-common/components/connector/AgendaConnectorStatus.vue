<template>
  <div class="connector-status">
    <!-- The connected account is one icon, not a row carrying an address.
         Benjamin, on the Suggest dates header: "use the connect (plug) we use
         everywhere instead of displaying the email address". The address is a
         detail about a state, not the state itself, so it moves into the
         tooltip and the header gets its width back. Same `fas fa-plug` this
         product uses for a calendar account everywhere else — the connect
         button and the contemporary-events panel both draw it. -->
    <div v-if="connectedConnectorUser" class="connector-connected d-flex align-center">
      <v-btn
        :title="connectedAccountLabel"
        :aria-label="connectedAccountLabel"
        icon
        max-width="28"
        max-height="28"
        @click="openPersonalCalendarDrawer">
        <v-icon size="18" class="text-light-color">
          fas fa-plug
        </v-icon>
      </v-btn>
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
    /**
     * What the plug says on hover: the action it performs, and the account it
     * performs it on.
     *
     * <p>
     * The address is here rather than on screen, and it is here rather than
     * nowhere: an icon alone cannot say WHICH account is connected, and a user
     * with two of them has to be able to find out. Both halves reuse strings
     * that already exist — no new one is introduced for a tooltip.
     *
     * @returns {String} the tooltip of the connected-account plug
     */
    connectedAccountLabel() {
      return `${this.$t('agenda.manageYourPersonalAgenda')} — ${this.connectedConnectorUser}`;
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