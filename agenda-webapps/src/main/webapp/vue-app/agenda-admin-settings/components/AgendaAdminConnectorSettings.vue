<template>
  <div class="white">
    <h4 class="pb-5 font-weight-bold">
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
            <div class="align-center">
              <v-text-field
                :ref="`${props.item.name}ApiInput`"
                v-model="props.item.apiKey"
                :readonly="!props.item.apiEditing"
                :placeholder="!props.item.isOauth ? $t('agenda.noConnectorClientApiKey') : $t('agenda.connectorClientApiKey')"
                :class="props.item.isOauth ? 'mx-2 pa-0' : 'mx-2 pa-0 me-8' "
                dense>
                <template #prepend>
                  <i 
                    :class="!props.item.isOauth ? 'uiIcon uiIconLock grey--text mt-1':'uiIcon uiIconLock primary--text mt-1'">
                  </i>
                </template>
                <template v-if="props.item.isOauth" #append-outer>
                  <v-slide-x-reverse-transition mode="out-in">
                    <i
                      :key="`icon-${props.item.apiEditing}`"
                      :class="props.item.apiEditing ? 'uiIcon uiIconTick clickable success--text mt-1' : 'uiIcon uiIconEdit clickable primary--text mt-1'"
                      @click="editApiKey(props.item)"></i>
                  </v-slide-x-reverse-transition>
                </template>
              </v-text-field>
            </div>
          </td>
          <td>
            <v-text-field
              :ref="`${props.item.name}SecretInput`"
              v-model="props.item.secretKey"
              :readonly="!props.item.secretEditing"
              :placeholder="!props.item.mandatorySecretKey ? $t('agenda.noConnectorClientSecretKey') : $t('agenda.connectorSecretApiKey')"
              :class="props.item.mandatorySecretKey ? 'mx-2 pa-0' : 'mx-2 pa-0 me-8' "
              dense>
              <template #prepend>
                <em
                  :class="!props.item.mandatorySecretKey ? 'uiIcon uiIconLock grey--text mt-1':'uiIcon uiIconLock primary--text mt-1'">
                </em>
              </template>
              <template v-if="props.item.mandatorySecretKey" #append-outer>
                <v-slide-x-reverse-transition mode="out-in">
                  <em
                    :key="`icon-${props.item.secretEditing}`"
                    :class="props.item.secretEditing ? 'uiIcon uiIconTick clickable success--text mt-1' : 'uiIcon uiIconEdit clickable primary--text mt-1'"
                    @click="editSecretKey(props.item)"></em>
                </v-slide-x-reverse-transition>
              </template>
            </v-text-field>
          </td>
          <td>
            <div class="d-flex flex-column align-center">
              <v-switch
                v-model="props.item.enabled"
                :disabled="props.item.isOauth && (props.item.loading || props.item.editing || !props.item.apiKey)"
                :loading="props.item.loading"
                :ripple="false"
                color="primary"
                class="connectorSwitcher my-auto"
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
    connectors: [],
    headers: [],
    itemsPerPage: 10,
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
      { text: this.$t('agenda.connectorClientApiKey'), align: 'center', width: '40%' },
      { text: this.$t('agenda.connectorSecretApiKey'), align: 'center', width: '15%' },
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
          connector.apiKey = connectorObj && connectorObj.apiKey || '';
          connector.secretKey = connectorObj && connectorObj.secretKey || '';
          connector.loading = false;
          connector.apiEditing = false;
          connector.secretEditing = false;
        });
      } else {
        connectors.forEach(connector => connector.enabled = false);
      }
      this.connectors = connectors;
    },
    enableDisableConnector(connector) {
      connector.loading = true;
      this.$settingsService.saveRemoteProviderStatus(connector.name, connector.enabled, connector.isOauth)
        .then(result => Object.assign(connector, result))
        .catch(() => connector.enabled = !connector.enabled)
        .finally(() => connector.loading = false);
    },
    editApiKey(connector) {
      if (connector.apiEditing) {
        this.$settingsService.saveRemoteProviderApiKey(connector.name, connector.apiKey)
          .then(result => Object.assign(connector, result))
          .finally(() => connector.apiEditing = false);
      } else {
        connector.apiEditing = true;
        this.$nextTick(() => {
          const $input = this.$refs[`${connector.name}ApiInput`];
          if ($input) {
            $input.focus();
          }
        });
      }
    },
    editSecretKey(connector) {
      if (connector.secretEditing) {
        this.$settingsService.saveRemoteProviderSecretKey(connector.name, connector.secretKey)
          .then(result => Object.assign(connector, result))
          .finally(() => connector.secretEditing = false);
      } else {
        connector.secretEditing = true;
        this.$nextTick(() => {
          const $input = this.$refs[`${connector.name}SecretInput`];
          if ($input) {
            $input.focus();
          }
        });
      }
    },
  }
};
</script>
