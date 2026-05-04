<!--
 *
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
 *
-->
<template>
  <exo-drawer
    id="agendaTimelineSettingsDrawer"
    ref="drawer"
    v-model="drawer"
    :loading="saving"
    allow-expand
    right>
    <template #title>
      {{ $t('agenda.timeline.settings.drawer.title') }}
    </template>
    <template v-if="drawer" #content>
      <div class="pa-5" flat>
        <div class="d-flex flex-column mb-1">
          <div class="mb-3 text-header">{{ $t('agenda.timeline.settings.drawer.label.displayOptions') }}</div>
          <div class="mb-2 font-weight-bold">{{ $t('agenda.timeline.settings.drawer.label.headerOptions') }}</div>
          <div class="d-flex my-2 align-center justify-space-between">
            <label class="v-label text-color align-start">
              {{ $t('agenda.timeline.settings.drawer.label.updateHeader') }}
            </label>
            <div class="align-end">
              <v-switch
                v-model="timelineSettings.customHeader"
                color="primary"
                class="pa-0 my-auto"
                hide-details />
            </div>
          </div>
          <translation-text-field
            v-if="timelineSettings.customHeader"
            :object-id="$root.settingName"
            :object-type="objectType"
            :field-name="fieldName"
            :field-value="displayedValue"
            :drawer-title="$t('agenda.timeline.settings.header.translation.title')"
            class="mt-2"
            no-expand-icon
            back-icon
            required
            @input="translationUpdated" />
        </div>
        <div class="d-flex flex-column mb-1">
          <div class="mb-3 text-header">{{ $t('agenda.timeline.settings.drawer.label.management') }}</div>
          <div class="mb-2 font-weight-bold">{{ $t('agenda.settings.drawer.label.source') }}</div>
          <v-radio-group
            v-model="timelineSettings.agendaSource"
            class="pa-0 ma-0 ms-n1 full-width"
            mandatory>
            <v-radio
              :label="$t('agenda.label.allUsersSpaces')"
              value="allUsersSpaces" />
            <v-radio
              :label="$t('agenda.label.selectedSpaces')"
              value="selectedSpaces" />
          </v-radio-group>
          <exo-identity-suggester
            v-show="showSuggester"
            ref="timelineSourcesSuggester"
            v-model="timelineSettings.selectedSpaces"
            :labels="sourcesLabels"
            :include-users="false"
            :width="220"
            name="timelineSourcesAutocomplete"
            class="user-suggester timelineSourcesAutocomplete"
            include-spaces
            multiple
            only-redactor />
        </div>
      </div>
    </template>
    <template #footer>
      <div class="d-flex">
        <v-btn
          :disabled="saving"
          class="btn ms-auto me-2"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </v-btn>
        <v-btn
          :disabled="!modified"
          :loading="saving"
          class="btn btn-primary"
          elevation="0"
          @click="save">
          {{ $t('agenda.button.save') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  data: () => ({
    drawer: false,
    saving: false,
    objectType: 'agendaTimeline',
    fieldName: 'headerTitle',
    translations: [],
    userLocale: eXo.env.portal.language,
    translationsInitialized: false,
    transUpdated: false,
    currentTranslations: [],
    timelineSettings: {
      agendaSource: 'allUsersSpaces',
      selectedSpaces: [],
      customHeader: false,
    },
  }),
  computed: {
    modified() {
      return JSON.stringify(this.timelineSettings) !== JSON.stringify(this.$root.timelineSettings) && (this.timelineSettings.agendaSource === 'selectedSpaces' ? this.timelineSettings.selectedSpaces.length > 0 : true) || this.transUpdated;
    },
    showSuggester() {
      return this.timelineSettings.agendaSource === 'selectedSpaces';
    },
    sourcesLabels() {
      return {
        searchPlaceholder: this.$t('agenda.searchPlaceholder'),
        placeholder: this.$t('agenda.chooseCalendar'),
        noDataLabel: this.$t('agenda.noDataLabel'),
      };
    },
    displayedValue() {
      return this.translations?.[this.userLocale];
    },
  },
  created() {
    this.$root.$on('open-agenda-timeline-settings', this.open);
  },
  beforeDestroy() {
    this.$root.$off('open-agenda-timeline-settings', this.open);
  },
  methods: {
    open() {
      this.timelineSettings = JSON.parse(JSON.stringify(this.$root.timelineSettings));
      if (this.timelineSettings.agendaSource === '') {
        if (eXo.env.portal.spaceId){
          this.timelineSettings.agendaSource = 'selectedSpaces';
          this.$spaceService.getSpaceById(eXo.env.portal.spaceId)
            .then((data) => {
              if (data) {
                const space = {
                  id: `space:${data.prettyName}`,
                  remoteId: data.prettyName,
                  spaceId: data.id,
                  groupId: data.groupId,
                  providerId: 'space',
                  displayName: data.displayName,
                  identityId: data.identityId,
                  profile: {
                    fullName: data.displayName,
                    originalName: data.shortName,
                    avatarUrl: data.avatarUrl ? data.avatarUrl : `/portal/rest/v1/social/spaces/${data.prettyName}/avatar`,
                    membersCount: data.membersCount,
                  },
                };
                this.timelineSettings.selectedSpaces = [space];
              }
            });
        }  else {
          this.timelineSettings.agendaSource =   'allUsersSpaces';
        }     
      }
      this.$refs.drawer.open();
    },
    close() {
      this.$refs.drawer.close();
    },
    async save() {
      this.saving = true;
      this.saveHeaderTranslations();
      try { 
        if (this.timelineSettings.agendaSource === 'allUsersSpaces') {
          this.timelineSettings.selectedSpaces = [];
        } 
        const formData = new FormData();
        formData.append('settings', JSON.stringify(this.timelineSettings));
        const urlParams = new URLSearchParams(formData).toString();
        const response = await fetch(this.$root.settingsSaveUrl, {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
          body: urlParams,
        });
        if (response?.ok) {
          this.$root.timelineSettings = this.timelineSettings;
          this.$root.$emit('alert-message', this.$t('agenda.settings.savedSuccessfully'), 'success');
          this.$root.$emit('timeline-settings-updated', this.timelineSettings);
          this.close();
        } else {
          this.$root.$emit('alert-message', this.$t('agenda.settings.saveError'), 'error');
        }
      } finally {
        this.saving = false;
        this.transUpdated = false;
      }
    },
    async saveHeaderTranslations() {
      if (this.timelineSettings.customHeader) {
        await this.$translationService.saveTranslations(this.objectType, this.$root.settingName, this.fieldName, this.translations);
        this.currentTranslations = structuredClone(this.translations);
      }
    },
    translationUpdated(translations) {
      this.translations = translations;
      if (!this.translationsInitialized) {
        this.currentTranslations = structuredClone(this.translations);
        this.translationsInitialized = true;
      } else {
        this.transUpdated=true;
      }
    },
  },
};
</script>