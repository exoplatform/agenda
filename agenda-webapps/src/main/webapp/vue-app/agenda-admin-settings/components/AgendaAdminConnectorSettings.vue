<template>
  <div class="mt-8">
    <div class="text-title mb-3">
      {{ $t('agenda.agendaConnectors') }}
    </div>
    <v-data-table
      :headers="headers"
      :items="connectors"
      :no-data-text="$t('agenda.noConnectors')"
      hide-default-footer
      disable-pagination
      disable-filtering
      disable-sort
      dense>
      <template #[`item.avatar`]="{ item }">
        <v-avatar
          class="py-1"
          size="40"
          tile>
          <img
            :alt="$t(item.name)"
            :src="item.avatar">
        </v-avatar>
      </template>
      <template #[`item.name`]="{ item }">
        <span>
          {{ $t(item.name) }}
        </span>
      </template>
      <template #[`item.enabled`]="{ item }">
        <div class="d-flex justify-center">
          <v-tooltip
            :disabled="!missingApiKey(item)"
            bottom>
            <template #activator="{ on, attrs }">
              <div
                v-bind="attrs"
                v-on="on">
                <v-switch
                  v-model="item.enabled"
                  :disabled="item.isOauth && (item.loading || !item.apiKey)"
                  :loading="item.loading"
                  :ripple="false"
                  class="ma-0 pa-0"
                  color="primary"
                  hide-details
                  @change="enableDisableConnector(item)" />
              </div>
            </template>
            <span>
              {{ $t('agenda.connectors.enableRequiresApiKey') }}
            </span>
          </v-tooltip>
        </div>
      </template>
      <template #[`item.actions`]="{ item }">
        <v-btn
          icon
          @click="editItem(item)">
          <v-icon
            size="20"
            class="icon-default-color">
            fa-edit
          </v-icon>
        </v-btn>
      </template>
    </v-data-table>
    <agenda-admin-connector-drawer />
  </div>
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
    headers: [],
  }),
  watch: {
    settings() {
      this.refreshConnectorsList();
    },
  },
  mounted() {
    document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
  },
  created() {
    this.headers = [
      { text: '', value: 'avatar', width: '40px' },
      { text: this.$t('agenda.name'), value: 'name' },
      { text: this.$t('agenda.active'), align: 'center', value: 'enabled', width: '80px' },
      { text: this.$t('agenda.connectors.list.actions'), align: 'center', value: 'actions', width: '80px' }
    ];
    // Retrieving list of registered connectors from extensionRegistry
    document.addEventListener('agenda-connectors-refresh', this.refreshConnectorsList);
  },
  methods: {
    /**
     * Reloads the built-in connectors from the extension registry and
     * decorates each with its stored settings (activation and credentials).
     *
     * @returns {void}
     */
    refreshConnectorsList() {
      // Multi-instance connectors (CalDAV servers) are managed in their own
      // admin section, one row per declared server: this table keeps the
      // single-instance OAuth connectors only.
      const connectors = (extensionRegistry.loadExtensions('agenda', 'connectors') || [])
        .filter(connector => !connector.multiInstance);
      if (this.settings && this.settings.remoteProviders) {
        //in case of a new connector is added.
        connectors.forEach(connector => {
          const connectorObj = this.settings.remoteProviders.find(connectorSettings => connectorSettings.name === connector.name);
          connector.enabled = connectorObj && connectorObj.enabled || false;
          connector.apiKey = connectorObj && connectorObj.apiKey || '';
          connector.secretKey = connectorObj && connectorObj.secretKey || '';
          connector.loading = false;
        });
      } else {
        connectors.forEach(connector => connector.enabled = false);
      }
      this.connectors = connectors;
    },
    /**
     * Opens the connector drawer on the clicked row, where the connector's
     * credentials are read and edited.
     *
     * @param {Object} connector the built-in connector of the clicked row
     * @returns {void}
     */
    editItem(connector) {
      this.$root.$emit('open-agenda-connector-drawer', connector);
    },
    /**
     * Whether a row's activation switch is held down for lack of an API key
     * — the case worth a tooltip now that the keys live behind the drawer.
     *
     * @param {Object} connector the built-in connector of the row
     * @returns {Boolean} true when the connector needs an API key it does not have
     */
    missingApiKey(connector) {
      return !!connector.isOauth && !connector.apiKey;
    },
    /**
     * Propagates the activation switch of a row to the stored remote
     * provider settings.
     *
     * @param {Object} connector the built-in connector whose switch was flipped
     * @returns {void}
     */
    enableDisableConnector(connector) {
      connector.loading = true;
      this.$settingsService.saveRemoteProviderStatus(connector.name, connector.enabled, connector.isOauth)
        .then(result => Object.assign(connector, result))
        .catch(() => connector.enabled = !connector.enabled)
        .finally(() => connector.loading = false);
    },
  }
};
</script>
