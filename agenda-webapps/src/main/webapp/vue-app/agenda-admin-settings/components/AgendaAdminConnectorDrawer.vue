<template>
  <exo-drawer
    id="agendaConnectorDrawer"
    ref="agendaConnectorDrawer"
    v-model="connectorDrawer"
    :loading="loading"
    right
    allow-expand
    @closed="close">
    <template #title>
      <span>{{ drawerTitle }}</span>
    </template>
    <template v-if="connectorDrawer && connector" #content>
      <form
        ref="adminConnectorForm"
        class="mx-5 mt-5"
        @submit.stop.prevent="0">
        <div class="d-flex align-center mb-7">
          <v-avatar
            size="40"
            tile>
            <img
              :alt="$t(connector.name)"
              :src="connector.avatar">
          </v-avatar>
          <span class="ms-4 text-header">
            {{ $t(connector.name) }}
          </span>
        </div>
        <template v-if="connector.isOauth">
          <v-label for="agendaConnectorApiKey">
            {{ $t('agenda.connectorClientApiKey') }}
          </v-label>
          <v-text-field
            id="agendaConnectorApiKey"
            v-model="apiKey"
            :placeholder="$t('agenda.connectorClientApiKey')"
            :aria-label="$t('agenda.connectorClientApiKey')"
            class="width-auto flex-grow-1 mt-3 mb-7 pt-0"
            name="agendaConnectorApiKey"
            type="text"
            maxlength="1000"
            outlined
            dense />
          <template v-if="connector.mandatorySecretKey">
            <v-label for="agendaConnectorSecretKey">
              {{ $t('agenda.connectorSecretApiKey') }}
            </v-label>
            <v-text-field
              id="agendaConnectorSecretKey"
              v-model="secretKey"
              :placeholder="$t('agenda.connectorSecretApiKey')"
              :aria-label="$t('agenda.connectorSecretApiKey')"
              class="width-auto flex-grow-1 mt-3 mb-7 pt-0"
              name="agendaConnectorSecretKey"
              type="text"
              maxlength="1000"
              outlined
              dense />
          </template>
        </template>
        <div
          v-else
          class="text-sub-title">
          {{ $t('agenda.noConnectorClientApiKey') }}
        </div>
      </form>
    </template>
    <template #footer>
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </v-btn>
        <v-btn
          v-if="connector && connector.isOauth"
          :loading="loading"
          class="btn btn-primary ms-5"
          @click="saveConnector">
          {{ $t('agenda.button.save') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  data: () => ({
    connectorDrawer: false,
    loading: false,
    connector: null,
    apiKey: '',
    secretKey: '',
  }),
  computed: {
    /**
     * The drawer title: the translated name of the connector being edited.
     *
     * @returns {String} the translated connector name, or an empty string
     *          while no connector is loaded
     */
    drawerTitle() {
      return this.connector && this.$t(this.connector.name) || '';
    },
  },
  created() {
    this.$root.$on('open-agenda-connector-drawer', this.open);
  },
  methods: {
    /**
     * Opens the drawer on a connector, editing local copies of its
     * credentials — an abandoned drawer leaves the row untouched.
     *
     * @param {Object} connector the built-in connector of the clicked row
     * @returns {void}
     */
    open(connector) {
      this.connector = connector;
      this.apiKey = connector.apiKey || '';
      this.secretKey = connector.secretKey || '';
      this.$refs.agendaConnectorDrawer.open();
    },
    /**
     * Closes the drawer, dropping whatever was typed.
     *
     * @returns {void}
     */
    close() {
      this.connector = null;
      this.apiKey = '';
      this.secretKey = '';
      this.$refs.agendaConnectorDrawer.close();
    },
    /**
     * Saves the drawer's credentials on the connector's remote provider,
     * then reflects the stored result on the table row (the server turns an
     * OAuth connector off when its API key is blanked).
     *
     * @returns {Promise} resolves once saved and reflected on the row
     */
    async saveConnector() {
      this.loading = true;
      const connector = this.connector;
      try {
        const result = await this.$settingsService.saveRemoteProviderApiKey(connector.name, this.apiKey);
        Object.assign(connector, result);
        if (connector.mandatorySecretKey) {
          const secretResult = await this.$settingsService.saveRemoteProviderSecretKey(connector.name, this.secretKey);
          Object.assign(connector, secretResult);
        }
        this.$root.$emit('alert-message', this.$t('agenda.connectors.drawer.save.success'), 'success');
        this.close();
      } catch (e) {
        this.$root.$emit('alert-message', this.$t('agenda.connectors.drawer.save.error'), 'error');
      } finally {
        this.loading = false;
      }
    },
  }
};
</script>
