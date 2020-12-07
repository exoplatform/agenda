<template>
  <div class="white rounded-lg">
    <h4 class="py-5 font-weight-bold">
      {{ $t('agenda.agendaConnectors') }}
    </h4>
    <v-divider />
    <v-data-table
      :headers="headers"
      :items="connectors"
      :items-per-page="itemsPerPage"
      :hide-default-footer="hideFooter"
      :footer-props="{
        itemsPerPageText: `${$t('agenda.itemsPerPage')}:`,
      }"
      :no-data-text="$t('agenda.noConnectors')"
      disable-sort>
      <template slot="item" slot-scope="props">
        <tr>
          <td>
            <div class="align-center">
              <v-avatar tile size="40">
                <img :alt=" props.item.name" :src=" props.item.avatar">
              </v-avatar>
            </div>
          </td>
          <td>
            <div class="align-center">
              {{ $t(props.item.name) }}
            </div>
          </td>
          <td>
            <div class="align-center">
              {{ $t(props.item.description) }}
            </div>
          </td>
          <td>
            <div class="d-flex flex-column align-center">
              <v-switch
                v-model=" props.item.enabled"
                :loading="loading"
                :ripple="false"
                color="primary"
                class="connectorSwitcher"
                @change="enableDisableConnector(props.item)" />
            </div>
          </td>
        </tr>
      </template>
    </v-data-table>
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
    loading: false,
    connectors: [],
    headers: [],
    itemsPerPage : 10,
    settings: null,
  }),
  computed: {
    hideFooter() {
      return this.connectors && this.connectors.length <= this.itemsPerPage;
    },
  },
  watch: {
    settings() {
      this.refreshConnectorsList();
    },
  },
  mounted() {
    document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
    this.skeleton = false;
  },
  created() {
    this.headers = [
      { text: this.$t('agenda.avatar'), align: 'center' },
      { text: this.$t('agenda.name'), align: 'center' },
      { text: this.$t('agenda.description'), align: 'center' },
      { text: this.$t('agenda.active'), align: 'center' }
    ];
    // Retrieving list of registered connectors from extensionRegistry
    document.addEventListener('agenda-connectors-refresh', this.refreshConnectorsList);
  },
  methods: {
    refreshConnectorsList() {
      const connectors = extensionRegistry.loadExtensions('agenda', 'connectors') || [];
      if (this.settings && this.settings.remoteProviders) {
        //in case of a new connector is added.
        connectors.forEach(connector => {
          const connectorObj = this.settings.remoteProviders.find(connectorSettings => connectorSettings.name === connector.name);
          connector.enabled = connectorObj && connectorObj.enabled || false;
        });
      } else {
        connectors.forEach(connector => connector.enabled = false);
      }
      this.connectors = connectors;
    },
    enableDisableConnector(connector) {
      this.loading = true;
      this.$settingsService.saveRemoteProviderStatus(connector.name, connector.enabled)
        .catch(() => connector.enabled = !connector.enabled)
        .finally(() => this.loading = false);
    }
  }
};
</script>
