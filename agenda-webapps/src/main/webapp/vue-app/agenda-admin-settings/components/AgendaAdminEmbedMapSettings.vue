<!--
 Copyright (C) 2026 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
-->

<template>
  <div>
    <div class="mb-5 text-title">
      {{ $t('agenda.embedMap.settings.title') }}
    </div>
    <div
      :class="{
        'mb-7': !embedMapEnabled,
        'mb-2': embedMapEnabled
      }"
      class="d-flex">
      <span
        class="my-auto">
        {{ $t('agenda.embedMap.settings.message') }}
      </span>
      <v-switch
        v-model="embedMapEnabled"
        :ripple="false"
        color="primary"
        class="ms-8 pa-0 my-auto"
        hide-details />
    </div>
    <div
      v-if="embedMapEnabled"
      class="full-width">
      <v-radio-group
        v-model="selectedProviderId"
        class="pa-0 mt-0 mb-7">
        <v-radio
          v-for="provider in mapProviders"
          :key="provider.id"
          :label="provider.label"
          :value="provider.id" />
      </v-radio-group>
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {
      embedMapEnabled: false,
      selectedProviderId: null,
      mapProviders: [],
      initializing: false,
    };
  },
  props: {
    settings: {
      type: Object,
      default: null,
    },
  },
  watch: {
    settings() {
      this.loadActiveMapProvider();
    },
    embedMapEnabled(newVal, oldVal) {
      if (this.initializing || newVal === oldVal) {
        return;
      }
      this.onEmbedMapEnabledChange(newVal);
    },
    selectedProviderId(newProvider, oldProvider) {
      if (this.initializing || !oldProvider || newProvider === oldProvider) {
        return;
      }
      this.onSelectedProviderIdChange(newProvider, oldProvider);
    },
  },
  methods: {
    onEmbedMapEnabledChange(enabled) {
      if (enabled) {
        this.selectedProviderId = this.mapProviders[0]?.id || null;
        if (this.selectedProviderId) {
          this.$settingsService.saveEmbedMapProvider(this.selectedProviderId);
        }
      } else {
        this.$settingsService.removeEmbedMapProvider().catch(() => {
          this.embedMapEnabled = true;
        });
      }
    },
    onSelectedProviderIdChange(newProvider, oldProvider) {
      this.$settingsService.saveEmbedMapProvider(newProvider).catch(() => {
        this.selectedProviderId = oldProvider;
      });
    },
    loadActiveMapProvider() {
      const savedProviderId = this.settings?.embedMapProvider;

      this.mapProviders = extensionRegistry
        .loadExtensions('EmbedMapProviders', 'embedMapProviders')
        .filter(p => p.enabled())
        .sort((a, b) => a.rank - b.rank);

      this.initializing = true;
      if (savedProviderId) {
        this.selectedProviderId = this.mapProviders.find(p => p.id === savedProviderId)?.id
            || this.mapProviders[0]?.id
            || null;
        this.embedMapEnabled = true;
      } else {
        this.embedMapEnabled = false;
        this.selectedProviderId = null;
      }
      this.$nextTick(() => {
        this.initializing = false;
      });
    },
  }
};
</script>