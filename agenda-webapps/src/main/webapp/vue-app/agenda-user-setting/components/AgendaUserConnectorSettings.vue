<template>
  <v-list-item>
    <v-list-item-content>
      <v-list-item-title class="text-header">
        {{ $t('agenda.connectYourPersonalAgenda') }}
      </v-list-item-title>
      <v-list-item-subtitle class="d-flex align-center">
        <agenda-connector-status :connectors="connectors">
          <template slot="connectButton">
            <span class="text-subtitle">
              {{ $t('agenda.connectYourPersonalAgendaSubTitle') }}
            </span>
          </template>
        </agenda-connector-status>
        <!--
          On the account's own line, after a separator: what makes the action
          beside it worth pressing is knowing how stale the account is, and
          reading it as a second line detached it from the account it is about.
          Shown only once the connector has answered, and only for one that
          can answer at all.
        -->
        <span v-if="lastSyncLabel" class="text-subtitle ms-1 text-truncate">
          · {{ lastSyncLabel }}
        </span>
      </v-list-item-subtitle>
    </v-list-item-content>
    <!--
      Both icons at one size and one weight, on one row: the pencil used to be
      an eXo icon font glyph in the primary colour while everything else on
      this settings page is a grey Vuetify icon, so it sat higher than its
      neighbour and read as the only thing worth clicking.
    -->
    <v-list-item-action class="d-flex flex-row align-center">
      <v-btn
        v-if="syncableConnector"
        :loading="syncing"
        :disabled="syncing"
        :aria-label="$t('agenda.connectors.syncNow')"
        :title="$t('agenda.connectors.syncNow')"
        icon
        class="me-2"
        @click="syncNow">
        <v-icon size="20" class="icon-default-color">fa-sync-alt</v-icon>
      </v-btn>
      <v-btn
        :aria-label="$t('agenda.connectYourPersonalAgenda')"
        :title="$t('agenda.connectYourPersonalAgenda')"
        icon
        @click="openDrawer">
        <v-icon size="20" class="icon-default-color">fa-edit</v-icon>
      </v-btn>
    </v-list-item-action>
    <agenda-connectors-drawer :connectors="enabledConnectors" />
    <agenda-connector
      :settings="settings"
      :connectors="connectors"
      auto-connect
      @connectors-loaded="connectorsLoaded" />
  </v-list-item>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null,
    },
  },
  data: () => ({
    connectors: [],
    syncing: false,
    syncStateRead: false,
    lastSynchronisedAt: null,
  }),
  computed: {
    enabledConnectors() {
      return this.connectors && this.connectors.filter(connector => connector.enabled) || [];
    },
    /**
     * The connected connector that can be asked to synchronise on demand.
     *
     * Declared by the connector, not assumed: a calendar whose events arrive
     * by push has nothing to run, and a button that does nothing is worse
     * than no button.
     *
     * @returns {Object} the connector, or null when none can
     */
    syncableConnector() {
      return (this.connectors || [])
        .find(connector => connector
          && connector.connected
          && typeof connector.sync === 'function') || null;
    },
    /**
     * When the connected account last finished synchronising, in words.
     *
     * @returns {String} the line to display, empty while unknown
     */
    lastSyncLabel() {
      if (!this.syncStateRead) {
        return '';
      }
      const phrase = this.$remoteEventConnector.lastSyncPhrase(this.lastSynchronisedAt);
      return phrase && this.$t(phrase.key, {0: phrase.count}) || '';
    },
  },
  methods: {
    /**
     * Hands the loaded connectors to the page and reads their state.
     *
     * @param {Array} connectors the connectors the connector component built
     * @returns {void}
     */
    connectorsLoaded(connectors) {
      this.connectors = connectors;
      this.$emit('connectors-loaded', connectors);
      this.retrieveSyncState();
    },
    /**
     * Reads when the connected account last synchronised.
     *
     * A connector that fails to answer leaves the line absent rather than
     * showing a time that is not true.
     *
     * @returns {Promise} resolves once the connector has answered or failed
     */
    retrieveSyncState() {
      const connector = (this.connectors || [])
        .find(c => c && c.connected && typeof c.lastSynchronised === 'function');
      if (!connector) {
        return Promise.resolve();
      }
      return Promise.resolve(connector.lastSynchronised())
        .then(lastSync => {
          this.lastSynchronisedAt = lastSync || null;
          this.syncStateRead = true;
        })
        .catch(error => console.error('cannot read when the account last synchronised', error));
    },
    /**
     * Synchronises the connected account now.
     *
     * The state is read again afterwards: pressing the button and seeing the
     * line stay where it was is the one outcome that would make it look
     * broken.
     *
     * @returns {Promise} resolves once the synchronisation has run
     */
    syncNow() {
      const connector = this.syncableConnector;
      this.syncing = true;
      return Promise.resolve(connector.sync())
        .then(() => {
          this.$root.$emit('agenda-refresh');
          this.$root.$emit('agenda-settings-refresh');
          return this.retrieveSyncState();
        })
        .catch(error => {
          console.error('cannot synchronise the connected account', error);
          this.$root.$emit('alert-message', this.$t('agenda.connectors.syncError'), 'error');
        })
        .finally(() => this.syncing = false);
    },
    openDrawer() {
      this.$root.$emit('agenda-connectors-drawer-open');
    },
    getDayFromAbbreviation(day) {
      return this.$agendaUtils.getDayNameFromDayAbbreviation(day, eXo.env.portal.language);
    },
  }
};
</script>
