<template>
  <v-app class="connectorsAdminSettings">
    <v-main class="white rounded-lg ma-5 pl-7">
      <div v-if="loading">
        <v-skeleton-loader
          class="mx-auto"
          type="table-heading,table-tbody" />
      </div>
      <div v-else>
        <h4 class="py-5 font-weight-bold">
          {{ $t("agenda.connectors.label") }}
        </h4>
        <v-divider />
        <v-data-table
          :headers="headers"
          :items="connectors"
          :footer-props="{
            itemsPerPageText: `${$t('agenda.table.footer.label')}:`,
          }"
          :no-data-text="$t('appCenter.adminSetupForm.noApp')"
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
                  {{ $t(`${props.item.name}`) }}
                </div>
              </td>
              <td>
                <div class="align-center">
                  {{ $t(`${props.item.description}`) }}
                </div>
              </td>
              <td>
                <div class="d-flex flex-column align-center">
                  <v-switch
                    v-model=" props.item.enabled"
                    :ripple="false"
                    color="#568dc9"
                    class="connectorSwitcher"
                    @change="enableDisableConnector(props.item)" />
                </div>
              </td>
            </tr>
          </template>
        </v-data-table>
      </div>
    </v-main>
  </v-app>
</template>

<script>
export default {
  data: () => ({
    loading: true,
    connectors: [],
    headers: [],
  }),
  computed: {
    connectorsStatusSettings() {
      return this.connectors.slice().map(connector => {
        return {
          name: connector.name,
          enabled: connector.enabled
        };
      });
    }
  },
  mounted() {
    document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
    this.skeleton = false;
  },
  created() {
    this.headers = [
      { text: this.$t('agenda.avatar'), align: 'center' },
      { text: this.$t('agenda.connectors.calendar'), align: 'center' },
      { text: this.$t('agenda.description'), align: 'center' },
      { text: this.$t('agenda.active'), align: 'center' }
    ];
    // Retrieving list of registered connectors from extensionRegistry
    document.addEventListener('agenda-accounts-connectors-refresh', this.refreshConnectorsList);
    this.refreshConnectorsList();
  },
  methods: {
    refreshConnectorsList() {
      const connectors = extensionRegistry.loadExtensions('agenda', 'connectors') || [];
      this.$settingsService.getSettingsValue()
        .then(connectorsStatusSettings => {
          if (connectorsStatusSettings && connectorsStatusSettings && connectorsStatusSettings.remoteProviders) {
            Object.assign(this.connectorsStatusSettings,connectorsStatusSettings.remoteProviders);
            //in case of a new connector is added.
            connectors.forEach(connector => {
              const connectorObj = this.connectorsStatusSettings.find(connectorSettings => connectorSettings.name === connector.name);
              connector.enabled = connectorObj ? connectorObj.enabled : true;
            });
            this.connectors = connectors;
          } else {
            connectors.forEach(connector => {
              connector.enabled = true;
            });
            this.connectors = connectors;
          }
        }).finally(() => {
          this.loading = false;
        });
    },
    enableDisableConnector(connector) {
      this.$settingsService.saveRemoteProviderStatus(connector.name, connector.enabled);
    }
  }
};
</script>
