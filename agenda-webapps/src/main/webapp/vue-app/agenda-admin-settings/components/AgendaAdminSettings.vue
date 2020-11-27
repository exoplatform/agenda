<template>
  <v-app id="agenda-admin" class="VuetifyApp">
    <v-main fluid class="px-8 pt-3">
      <v-row>
        <v-col
          xs12
          px-3
          class="d-flex align-center">
          <h4 class="pr-3">
            {{ $t("agenda.connectors.label") }}
          </h4>
          <v-divider />
        </v-col>
      </v-row>
      <v-row>
        <v-col xs12>
          <v-simple-table :dense="true" class="v-data-table uiGrid table table-hover v-data-table--dense theme--light">
            <template v-slot:default>
              <thead>
                <tr class="providersTableRow">
                  <th class="text-left" style="width: 5%">{{ $t("agenda.avatar") }}</th>
                  <th class="text-left">{{ $t("agenda.connectors.calendar") }}</th>
                  <th class="text-left">{{ $t("agenda.description") }}</th>
                  <th class="text-left" style="width: 5%">{{ $t("agenda.active") }}</th>
                </tr>
              </thead>
              <tbody v-if="connectors.length > 0">
                <tr
                  v-for="connector in connectors"
                  :key="connector.name">
                  <td>
                    <div>
                      <v-avatar tile size="40">
                        <img :alt="connector.name" :src="connector.avatar">
                      </v-avatar>
                    </div>
                  </td>
                  <td>
                    <div>
                      {{ $t(`${connector.name}`) }}
                    </div>
                  </td>
                  <td>
                    <div>
                      {{ $t(`${connector.description}`) }}
                    </div>
                  </td>
                  <td>
                    <div>
                      <v-switch
                        :ripple="false"
                        color="#568dc9"
                        class="connectorSwitcher"
                        @change="changeActive(connector)" />
                    </div>
                  </td>
                </tr>
              </tbody>
            </template>
          </v-simple-table>
        </v-col>
      </v-row>
    </v-main>
  </v-app>
</template>

<script>
export default {
  data: () => ({
    skeleton: true,
    connectors: [],
  }),
  mounted() {
    document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
    this.skeleton = false;
  },
  created() {
    // Retrieving list of registered connectors from extensionRegistry
    document.addEventListener('agenda-accounts-connectors-refresh', this.refreshConnectorsList);
    this.refreshConnectorsList();
  },
  methods: {
    refreshConnectorsList() {
      this.connectors = extensionRegistry.loadExtensions('agenda', 'connectors') || [];
    },
    changeActive(connector) {
      this.
    }
  }
};
</script>
