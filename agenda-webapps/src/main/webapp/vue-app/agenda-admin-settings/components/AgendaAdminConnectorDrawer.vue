/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
<template>
  <exo-drawer
    id="agendaConnectorDrawer"
    ref="agendaConnectorDrawer"
    v-model="connectorDrawer"
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
            ref="apiKeyInput"
            v-model="apiKey"
            :readonly="!apiEditing"
            :placeholder="$t('agenda.connectorClientApiKey')"
            :aria-label="$t('agenda.connectorClientApiKey')"
            class="width-auto flex-grow-1 mt-3 mb-7 pt-0"
            name="agendaConnectorApiKey"
            type="text"
            maxlength="1000"
            outlined
            dense>
            <template #append>
              <v-slide-x-reverse-transition mode="out-in">
                <em
                  :key="`api-icon-${apiEditing}`"
                  :class="apiEditing ? 'uiIcon uiIconTick clickable success--text' : 'uiIcon uiIconEdit clickable primary--text'"
                  :aria-label="$t('agenda.connectorClientApiKey')"
                  role="button"
                  tabindex="0"
                  @keydown.enter="editApiKey"
                  @click="editApiKey"></em>
              </v-slide-x-reverse-transition>
            </template>
          </v-text-field>
          <template v-if="connector.mandatorySecretKey">
            <v-label for="agendaConnectorSecretKey">
              {{ $t('agenda.connectorSecretApiKey') }}
            </v-label>
            <v-text-field
              id="agendaConnectorSecretKey"
              ref="secretKeyInput"
              v-model="secretKey"
              :readonly="!secretEditing"
              :placeholder="$t('agenda.connectorSecretApiKey')"
              :aria-label="$t('agenda.connectorSecretApiKey')"
              class="width-auto flex-grow-1 mt-3 mb-7 pt-0"
              name="agendaConnectorSecretKey"
              type="text"
              maxlength="1000"
              outlined
              dense>
              <template #append>
                <v-slide-x-reverse-transition mode="out-in">
                  <em
                    :key="`secret-icon-${secretEditing}`"
                    :class="secretEditing ? 'uiIcon uiIconTick clickable success--text' : 'uiIcon uiIconEdit clickable primary--text'"
                    :aria-label="$t('agenda.connectorSecretApiKey')"
                    role="button"
                    tabindex="0"
                    @keydown.enter="editSecretKey"
                    @click="editSecretKey"></em>
                </v-slide-x-reverse-transition>
              </template>
            </v-text-field>
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
          {{ $t('agenda.button.close') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  data: () => ({
    connectorDrawer: false,
    connector: null,
    apiKey: '',
    secretKey: '',
    apiEditing: false,
    secretEditing: false,
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
     * credentials — a key not confirmed with its own tick leaves the stored
     * value untouched.
     *
     * @param {Object} connector the built-in connector of the clicked row
     * @returns {void}
     */
    open(connector) {
      this.connector = connector;
      this.apiKey = connector.apiKey || '';
      this.secretKey = connector.secretKey || '';
      this.apiEditing = false;
      this.secretEditing = false;
      this.$refs.agendaConnectorDrawer.open();
    },
    /**
     * Closes the drawer, dropping any edit not confirmed with its tick.
     *
     * @returns {void}
     */
    close() {
      this.connector = null;
      this.apiKey = '';
      this.secretKey = '';
      this.apiEditing = false;
      this.secretEditing = false;
      this.$refs.agendaConnectorDrawer.close();
    },
    /**
     * Toggles the API key edition: a first click makes the field editable, a
     * second one confirms it through the key's own save endpoint and
     * reflects the stored result on the table row (the server turns an OAuth
     * connector off when its API key is blanked).
     *
     * @returns {void}
     */
    editApiKey() {
      if (this.apiEditing) {
        this.$settingsService.saveRemoteProviderApiKey(this.connector.name, this.apiKey)
          .then(result => {
            Object.assign(this.connector, result);
            this.$root.$emit('alert-message', this.$t('agenda.connectors.drawer.save.success'), 'success');
          })
          .catch(() => this.$root.$emit('alert-message', this.$t('agenda.connectors.drawer.save.error'), 'error'))
          .finally(() => this.apiEditing = false);
      } else {
        this.apiEditing = true;
        this.$nextTick(() => this.$refs.apiKeyInput && this.$refs.apiKeyInput.focus());
      }
    },
    /**
     * Toggles the secret key edition: a first click makes the field
     * editable, a second one confirms it through the key's own save endpoint
     * and reflects the stored result on the table row.
     *
     * @returns {void}
     */
    editSecretKey() {
      if (this.secretEditing) {
        this.$settingsService.saveRemoteProviderSecretKey(this.connector.name, this.secretKey)
          .then(result => {
            Object.assign(this.connector, result);
            this.$root.$emit('alert-message', this.$t('agenda.connectors.drawer.save.success'), 'success');
          })
          .catch(() => this.$root.$emit('alert-message', this.$t('agenda.connectors.drawer.save.error'), 'error'))
          .finally(() => this.secretEditing = false);
      } else {
        this.secretEditing = true;
        this.$nextTick(() => this.$refs.secretKeyInput && this.$refs.secretKeyInput.focus());
      }
    },
  }
};
</script>
